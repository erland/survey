package info.isaksson.erland.survey.auth;

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
                if (!rs.next() || !passwordHasher.verify(password, rs.getString("password_hash"))) {
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
                 SELECT u.id, u.username
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
                return Optional.of(new AdminPrincipal(rs.getObject("id", UUID.class), rs.getString("username")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not validate admin session", e);
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
    public record AdminPrincipal(UUID userId, String username) {}
}
