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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
public class AdminPasswordTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject AgroalDataSource dataSource;
    @Inject PasswordHasher passwordHasher;
    @Inject AdminCredentialPolicy credentialPolicy;

    @ConfigProperty(name = "app.auth.password-token-hours", defaultValue = "24")
    long tokenHours;

    public IssuedToken issue(UUID adminUserId, Purpose purpose, UUID createdByAdminUserId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                IssuedToken token = issue(connection, adminUserId, purpose, createdByAdminUserId);
                connection.commit();
                return token;
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not issue admin password token", e);
        }
    }

    public IssuedToken issue(Connection connection, UUID adminUserId, Purpose purpose,
                             UUID createdByAdminUserId) throws SQLException {
        String token = newToken();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(tokenHours));

        try (PreparedStatement revoke = connection.prepareStatement("""
                UPDATE admin_password_token
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE admin_user_id = ?
                  AND used_at IS NULL
                  AND revoked_at IS NULL
                """)) {
            revoke.setObject(1, adminUserId);
            revoke.executeUpdate();
        }

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO admin_password_token (
                    id, admin_user_id, token_hash, purpose,
                    created_by_admin_user_id, expires_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, adminUserId);
            insert.setString(3, sha256(token));
            insert.setString(4, purpose.name());
            insert.setObject(5, createdByAdminUserId);
            insert.setTimestamp(6, Timestamp.from(expiresAt));
            insert.executeUpdate();
        }

        return new IssuedToken(token, expiresAt, purpose);
    }

    public String setupPath(IssuedToken token) {
        return "/admin/set-password?token=" + token.token();
    }

    public void consume(String token, String password) {
        credentialPolicy.requireValidPassword(password);
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID userId;
                try (PreparedStatement lookup = connection.prepareStatement("""
                        SELECT t.admin_user_id
                        FROM admin_password_token t
                        JOIN admin_user u ON u.id = t.admin_user_id
                        WHERE t.token_hash = ?
                          AND t.used_at IS NULL
                          AND t.revoked_at IS NULL
                          AND t.expires_at > CURRENT_TIMESTAMP
                          AND u.active = TRUE
                        FOR UPDATE OF t
                        """)) {
                    lookup.setString(1, sha256(token));
                    try (var rs = lookup.executeQuery()) {
                        if (!rs.next()) {
                            throw invalidToken();
                        }
                        userId = rs.getObject("admin_user_id", UUID.class);
                    }
                }

                try (PreparedStatement updateUser = connection.prepareStatement("""
                        UPDATE admin_user
                        SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """)) {
                    updateUser.setString(1, passwordHasher.hash(password));
                    updateUser.setObject(2, userId);
                    updateUser.executeUpdate();
                }

                try (PreparedStatement useToken = connection.prepareStatement("""
                        UPDATE admin_password_token
                        SET used_at = CURRENT_TIMESTAMP
                        WHERE token_hash = ?
                        """)) {
                    useToken.setString(1, sha256(token));
                    useToken.executeUpdate();
                }

                try (PreparedStatement revokeSessions = connection.prepareStatement("""
                        UPDATE admin_session
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE admin_user_id = ? AND revoked_at IS NULL
                        """)) {
                    revokeSessions.setObject(1, userId);
                    revokeSessions.executeUpdate();
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
            throw new IllegalStateException("Could not consume admin password token", e);
        }
    }

    private ApiException invalidToken() {
        return new ApiException(400, "INVALID_PASSWORD_TOKEN",
                "Lösenordslänken är ogiltig, har gått ut eller har redan använts.");
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

    public enum Purpose {
        INITIAL_PASSWORD,
        PASSWORD_RESET
    }

    public record IssuedToken(String token, Instant expiresAt, Purpose purpose) {}
}
