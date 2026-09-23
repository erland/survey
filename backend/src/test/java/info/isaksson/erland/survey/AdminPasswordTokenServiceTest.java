package info.isaksson.erland.survey;

import info.isaksson.erland.survey.auth.AdminPasswordTokenService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AdminPasswordTokenServiceTest {
    @Inject AdminPasswordTokenService tokens;
    @Inject AgroalDataSource dataSource;

    @Test
    void issuingNewTokenStoresOnlyHashAndRevokesPreviousToken() throws Exception {
        UUID userId = bootstrapUserId();

        var first = tokens.issue(userId, AdminPasswordTokenService.Purpose.PASSWORD_RESET, userId);
        assertNotNull(first.token());
        assertTrue(first.token().length() >= 40);

        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT token_hash, purpose, revoked_at
                     FROM admin_password_token
                     WHERE admin_user_id = ?
                     ORDER BY created_at DESC
                     LIMIT 1
                     """)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNotEquals(first.token(), rs.getString("token_hash"));
                assertEquals("PASSWORD_RESET", rs.getString("purpose"));
                assertNull(rs.getTimestamp("revoked_at"));
            }
        }

        var second = tokens.issue(userId, AdminPasswordTokenService.Purpose.INITIAL_PASSWORD, userId);
        assertNotEquals(first.token(), second.token());

        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT
                       COUNT(*) FILTER (WHERE used_at IS NULL AND revoked_at IS NULL) AS active_count,
                       COUNT(*) FILTER (WHERE revoked_at IS NOT NULL) AS revoked_count
                     FROM admin_password_token
                     WHERE admin_user_id = ?
                     """)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("active_count"));
                assertTrue(rs.getInt("revoked_count") >= 1);
            }
        }
    }

    private UUID bootstrapUserId() throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM admin_user WHERE username = 'test-admin'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            return rs.getObject(1, UUID.class);
        }
    }
}
