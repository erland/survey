package info.isaksson.erland.survey.accountapi;

import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.auth.PasswordHasher;
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
public class AccountAdminService {
    @Inject AgroalDataSource dataSource;
    @Inject PasswordHasher passwordHasher;
    @Inject AccountAccessService accountAccess;

    public List<AccountAdminView> list(UUID actorUserId, UUID accountId) {
        accountAccess.requireMembership(actorUserId, accountId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT u.id, u.username, u.active, m.role, m.created_at
                     FROM survey_account_admin m
                     JOIN admin_user u ON u.id = m.admin_user_id
                     WHERE m.survey_account_id = ?
                     ORDER BY lower(u.username), u.id
                     """)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AccountAdminView> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new AccountAdminView(
                            rs.getObject("id", UUID.class),
                            rs.getString("username"),
                            rs.getBoolean("active"),
                            rs.getString("role"),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list account administrators", e);
        }
    }

    public AccountAdminView add(UUID actorUserId, UUID accountId, AddAccountAdminRequest request) {
        accountAccess.requireMembership(actorUserId, accountId);
        validate(request);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID adminUserId = resolveOrCreateAdmin(
                        connection,
                        request.username().trim(),
                        request.initialPassword()
                );

                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO survey_account_admin (survey_account_id, admin_user_id, role, created_at)
                        VALUES (?, ?, 'ADMIN', CURRENT_TIMESTAMP)
                        ON CONFLICT (survey_account_id, admin_user_id) DO NOTHING
                        """)) {
                    ps.setObject(1, accountId);
                    ps.setObject(2, adminUserId);
                    ps.executeUpdate();
                }

                AccountAdminView view = load(connection, accountId, adminUserId);
                connection.commit();
                return view;
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not add account administrator", e);
        }
    }

    public void remove(UUID actorUserId, UUID accountId, UUID adminUserId) {
        accountAccess.requireMembership(actorUserId, accountId);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int count;
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM survey_account_admin
                        WHERE survey_account_id = ?
                        FOR UPDATE
                        """)) {
                    ps.setObject(1, accountId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        count = rs.getInt(1);
                    }
                }

                if (count <= 1) {
                    throw new ApiException(409, "LAST_ACCOUNT_ADMIN",
                            "Den sista administratören på ett enkätkonto kan inte tas bort.");
                }

                int deleted;
                try (PreparedStatement ps = connection.prepareStatement("""
                        DELETE FROM survey_account_admin
                        WHERE survey_account_id = ? AND admin_user_id = ?
                        """)) {
                    ps.setObject(1, accountId);
                    ps.setObject(2, adminUserId);
                    deleted = ps.executeUpdate();
                }

                if (deleted == 0) {
                    throw new ApiException(404, "ACCOUNT_ADMIN_NOT_FOUND",
                            "Administratören är inte kopplad till enkätkontot.");
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
            throw new IllegalStateException("Could not remove account administrator", e);
        }
    }

    private UUID resolveOrCreateAdmin(Connection connection, String username, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, active
                FROM admin_user
                WHERE lower(username) = lower(?)
                """)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (!rs.getBoolean("active")) {
                        throw new ApiException(409, "ADMIN_INACTIVE", "Administratörskontot är inaktivt.");
                    }
                    return rs.getObject("id", UUID.class);
                }
            }
        }

        if (password == null || password.isBlank() || password.length() < 12) {
            throw new ApiException(400, "ADMIN_PASSWORD_REQUIRED",
                    "Ett nytt administratörskonto kräver ett lösenord på minst 12 tecken.");
        }

        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO admin_user (id, username, password_hash, system_admin, active, created_at, updated_at)
                VALUES (?, ?, ?, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            ps.setObject(1, id);
            ps.setString(2, username);
            ps.setString(3, passwordHasher.hash(password));
            ps.executeUpdate();
        }
        return id;
    }

    private AccountAdminView load(Connection connection, UUID accountId, UUID adminUserId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT u.id, u.username, u.active, m.role, m.created_at
                FROM survey_account_admin m
                JOIN admin_user u ON u.id = m.admin_user_id
                WHERE m.survey_account_id = ? AND m.admin_user_id = ?
                """)) {
            ps.setObject(1, accountId);
            ps.setObject(2, adminUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Created account membership could not be read back");
                }
                return new AccountAdminView(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getBoolean("active"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at").toInstant()
                );
            }
        }
    }

    private void validate(AddAccountAdminRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new ApiException(400, "INVALID_ADMIN", "Administratörens användarnamn måste anges.");
        }
        if (request.username().trim().length() > 200) {
            throw new ApiException(400, "INVALID_ADMIN", "Administratörens användarnamn får vara högst 200 tecken.");
        }
    }

    public record AddAccountAdminRequest(String username, String initialPassword) {}
    public record AccountAdminView(UUID userId, String username, boolean active, String role, Instant createdAt) {}
}
