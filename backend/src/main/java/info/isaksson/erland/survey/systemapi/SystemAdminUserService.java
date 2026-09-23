package info.isaksson.erland.survey.systemapi;

import info.isaksson.erland.survey.auth.AdminPasswordTokenService;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SystemAdminUserService {
    @Inject AgroalDataSource dataSource;
    @Inject AdminPasswordTokenService passwordTokens;

    public List<AdminUserView> list(AdminPrincipal principal) {
        requireSystemAdmin(principal);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT u.id, u.username, u.system_admin, u.active, u.created_at, u.updated_at,
                            COUNT(DISTINCT m.survey_account_id) AS account_count
                     FROM admin_user u
                     LEFT JOIN survey_account_admin m ON m.admin_user_id = u.id
                     GROUP BY u.id, u.username, u.system_admin, u.active, u.created_at, u.updated_at
                     ORDER BY lower(u.username), u.id
                     """);
             ResultSet rs = ps.executeQuery()) {
            List<AdminUserView> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new AdminUserView(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getBoolean("system_admin"),
                        rs.getBoolean("active"),
                        rs.getLong("account_count"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list administrator users", e);
        }
    }

    public PasswordResetLink createPasswordResetLink(AdminPrincipal principal, UUID userId) {
        requireSystemAdmin(principal);

        try (Connection connection = dataSource.getConnection()) {
            UserState state = loadUser(connection, userId);
            if (state.systemAdmin()) {
                throw new ApiException(409, "SYSTEM_ADMIN_PROTECTED",
                        "Systemadministratörskonton hanteras inte via lösenordsåterställning.");
            }
            if (!state.active()) {
                throw new ApiException(409, "ADMIN_INACTIVE",
                        "Ett inaktivt administratörskonto måste återaktiveras innan lösenordet kan återställas.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not validate administrator user for password reset", e);
        }

        AdminPasswordTokenService.IssuedToken token = passwordTokens.issue(
                userId,
                AdminPasswordTokenService.Purpose.PASSWORD_RESET,
                principal.userId()
        );
        return new PasswordResetLink(passwordTokens.setupPath(token), token.expiresAt());
    }

    public void deactivate(AdminPrincipal principal, UUID userId) {
        requireSystemAdmin(principal);
        requireNotSelf(principal, userId);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UserState state = lockUser(connection, userId);
                if (state.systemAdmin()) {
                    throw new ApiException(409, "SYSTEM_ADMIN_PROTECTED",
                            "Systemadministratörskonton kan inte inaktiveras via användarlivscykeln.");
                }

                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE admin_user
                        SET active = FALSE, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """)) {
                    ps.setObject(1, userId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE admin_session
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE admin_user_id = ? AND revoked_at IS NULL
                        """)) {
                    ps.setObject(1, userId);
                    ps.executeUpdate();
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
            throw new IllegalStateException("Could not deactivate administrator user", e);
        }
    }

    public void activate(AdminPrincipal principal, UUID userId) {
        requireSystemAdmin(principal);
        try (Connection connection = dataSource.getConnection()) {
            UserState state = loadUser(connection, userId);
            if (state.systemAdmin()) {
                throw new ApiException(409, "SYSTEM_ADMIN_PROTECTED",
                        "Systemadministratörskonton hanteras inte via användarlivscykeln.");
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE admin_user
                    SET active = TRUE, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """)) {
                ps.setObject(1, userId);
                ps.executeUpdate();
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not activate administrator user", e);
        }
    }

    public void delete(AdminPrincipal principal, UUID userId) {
        requireSystemAdmin(principal);
        requireNotSelf(principal, userId);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UserState state = lockUser(connection, userId);
                if (state.systemAdmin()) {
                    throw new ApiException(409, "SYSTEM_ADMIN_PROTECTED",
                            "Systemadministratörskonton kan inte tas bort.");
                }
                if (state.active()) {
                    throw new ApiException(409, "ADMIN_MUST_BE_INACTIVE",
                            "Administratörskontot måste inaktiveras innan det kan tas bort.");
                }
                if (membershipCount(connection, userId) > 0) {
                    throw new ApiException(409, "ADMIN_HAS_ACCOUNT_MEMBERSHIPS",
                            "Administratören måste tas bort från alla enkätkonton innan användarkontot kan tas bort.");
                }
                if (hasHistoricalReferences(connection, userId)) {
                    throw new ApiException(409, "ADMIN_HAS_HISTORY",
                            "Administratörskontot har historiska referenser och kan därför inte tas bort säkert.");
                }

                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM admin_user WHERE id = ?")) {
                    ps.setObject(1, userId);
                    ps.executeUpdate();
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
            throw new IllegalStateException("Could not delete administrator user", e);
        }
    }

    private UserState lockUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, system_admin, active FROM admin_user WHERE id = ? FOR UPDATE
                """)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "ADMIN_NOT_FOUND", "Administratörskontot kunde inte hittas.");
                }
                return new UserState(rs.getObject("id", UUID.class), rs.getBoolean("system_admin"), rs.getBoolean("active"));
            }
        }
    }

    private UserState loadUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, system_admin, active FROM admin_user WHERE id = ?
                """)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "ADMIN_NOT_FOUND", "Administratörskontot kunde inte hittas.");
                }
                return new UserState(rs.getObject("id", UUID.class), rs.getBoolean("system_admin"), rs.getBoolean("active"));
            }
        }
    }

    private long membershipCount(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM survey_account_admin WHERE admin_user_id = ?")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private boolean hasHistoricalReferences(Connection connection, UUID userId) throws SQLException {
        for (String sql : List.of(
                "SELECT EXISTS (SELECT 1 FROM survey WHERE created_by_admin_user_id = ?)",
                "SELECT EXISTS (SELECT 1 FROM survey_run WHERE created_by = ?)",
                "SELECT EXISTS (SELECT 1 FROM presentation_token WHERE created_by = ?)"
        )) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getBoolean(1)) return true;
                }
            }
        }
        return false;
    }

    private void requireSystemAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
        }
        if (!principal.systemAdmin()) {
            throw new ApiException(403, "SYSTEM_ADMIN_REQUIRED", "Systemadministratörsbehörighet krävs.");
        }
    }

    private void requireNotSelf(AdminPrincipal principal, UUID userId) {
        if (principal.userId().equals(userId)) {
            throw new ApiException(409, "SELF_ADMIN_PROTECTED", "Du kan inte inaktivera eller ta bort ditt eget systemadministratörskonto.");
        }
    }

    private record UserState(UUID id, boolean systemAdmin, boolean active) {}

    public record AdminUserView(
            UUID id,
            String username,
            boolean systemAdmin,
            boolean active,
            long accountCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PasswordResetLink(String path, Instant expiresAt) {}
}
