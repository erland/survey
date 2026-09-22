package info.isaksson.erland.survey.auth;

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

    @ConfigProperty(name = "app.auth.password-token-hours", defaultValue = "24")
    long tokenHours;

    public IssuedToken issue(UUID adminUserId, Purpose purpose, UUID createdByAdminUserId) {
        String token = newToken();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(tokenHours));

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
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

                connection.commit();
                return new IssuedToken(token, expiresAt, purpose);
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
