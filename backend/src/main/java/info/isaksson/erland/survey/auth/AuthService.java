package info.isaksson.erland.survey.auth;

import info.isaksson.erland.survey.surveyapi.ApiException;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    AgroalDataSource dataSource;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    AdminCredentialPolicy credentialPolicy;

    @ConfigProperty(name = "app.auth.session-hours", defaultValue = "12")
    long sessionHours;

    public Optional<LoginResult> login(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, username, password_hash FROM admin_user WHERE lower(username) = lower(?) AND active = TRUE")) {
            statement.setString(1, username.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String passwordHash = rs.getString("password_hash");
                if (passwordHash == null || !passwordHasher.verify(password, passwordHash)) {
                    return Optional.empty();
                }
                UUID userId = rs.getObject("id", UUID.class);
                String canonicalUsername = rs.getString("username");
                String token = newToken();
                Instant expiresAt = Instant.now().plus(Duration.ofHours(sessionHours));
                insertSession(connection, userId, token, expiresAt);
                return Optional.of(new LoginResult(userId, canonicalUsername, token, expiresAt));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not authenticate admin user", e);
        }
    }

    public Optional<AdminPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT u.id, u.username, u.system_admin
                 FROM admin_session s
                 JOIN admin_user u ON u.id = s.admin_user_id
                 WHERE s.token_hash = ?
                   AND u.active = TRUE
                   AND s.revoked_at IS NULL
                   AND s.expires_at > CURRENT_TIMESTAMP
                 """)) {
            statement.setString(1, sha256(token));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AdminPrincipal(rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("system_admin")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not validate admin session", e);
        }
    }

    public void changePassword(String sessionToken, String currentPassword, String newPassword) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
        }
        credentialPolicy.requireValidPassword(newPassword);
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new ApiException(400, "INVALID_CURRENT_PASSWORD", "Nuvarande lösenord är felaktigt.");
        }

        String currentSessionHash = sha256(sessionToken);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID userId;
                String currentHash;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT u.id, u.password_hash
                        FROM admin_session s
                        JOIN admin_user u ON u.id = s.admin_user_id
                        WHERE s.token_hash = ?
                          AND u.active = TRUE
                          AND s.revoked_at IS NULL
                          AND s.expires_at > CURRENT_TIMESTAMP
                        FOR UPDATE OF u
                        """)) {
                    statement.setString(1, currentSessionHash);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
                        }
                        userId = rs.getObject("id", UUID.class);
                        currentHash = rs.getString("password_hash");
                    }
                }

                if (currentHash == null || !passwordHasher.verify(currentPassword, currentHash)) {
                    throw new ApiException(400, "INVALID_CURRENT_PASSWORD", "Nuvarande lösenord är felaktigt.");
                }

                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE admin_user
                        SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """)) {
                    update.setString(1, passwordHasher.hash(newPassword));
                    update.setObject(2, userId);
                    update.executeUpdate();
                }

                try (PreparedStatement revokeSessions = connection.prepareStatement("""
                        UPDATE admin_session
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE admin_user_id = ?
                          AND token_hash <> ?
                          AND revoked_at IS NULL
                        """)) {
                    revokeSessions.setObject(1, userId);
                    revokeSessions.setString(2, currentSessionHash);
                    revokeSessions.executeUpdate();
                }

                try (PreparedStatement revokeTokens = connection.prepareStatement("""
                        UPDATE admin_password_token
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE admin_user_id = ?
                          AND used_at IS NULL
                          AND revoked_at IS NULL
                        """)) {
                    revokeTokens.setObject(1, userId);
                    revokeTokens.executeUpdate();
                }

                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not change administrator password", e);
        }
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE admin_session SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL")) {
            statement.setString(1, sha256(token));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not revoke admin session", e);
        }
    }

    private void insertSession(Connection connection, UUID userId, String token, Instant expiresAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO admin_session (id, admin_user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, sha256(token));
            statement.setTimestamp(4, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record LoginResult(UUID userId, String username, String token, Instant expiresAt) {}
    public record AdminPrincipal(UUID userId, String username, boolean systemAdmin) {}
}
