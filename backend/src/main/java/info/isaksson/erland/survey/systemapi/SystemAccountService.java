package info.isaksson.erland.survey.systemapi;

import info.isaksson.erland.survey.auth.AdminCredentialPolicy;
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
public class SystemAccountService {
    @Inject AgroalDataSource dataSource;
    @Inject AdminCredentialPolicy credentialPolicy;
    @Inject AdminPasswordTokenService passwordTokens;

    public List<AccountSummary> list(AdminPrincipal principal) {
        requireSystemAdmin(principal);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT a.id, a.name, a.created_at, a.updated_at,
                            COUNT(DISTINCT m.admin_user_id) AS admin_count,
                            COUNT(DISTINCT s.id) AS survey_count
                     FROM survey_account a
                     LEFT JOIN survey_account_admin m ON m.survey_account_id = a.id
                     LEFT JOIN survey s ON s.survey_account_id = a.id
                     GROUP BY a.id, a.name, a.created_at, a.updated_at
                     ORDER BY lower(a.name), a.id
                     """);
             ResultSet rs = ps.executeQuery()) {
            List<AccountSummary> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new AccountSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getLong("admin_count"),
                        rs.getLong("survey_count"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list survey accounts", e);
        }
    }

    public CreatedAccount create(AdminPrincipal principal, CreateAccountRequest request) {
        requireSystemAdmin(principal);
        validate(request);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AdminResolution admin = resolveOrCreateAdmin(connection, request.adminUsername().trim());
                UUID accountId = UUID.randomUUID();

                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO survey_account (id, name, created_at, updated_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)) {
                    ps.setObject(1, accountId);
                    ps.setString(2, request.accountName().trim());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO survey_account_admin (survey_account_id, admin_user_id, role, created_at)
                        VALUES (?, ?, 'ADMIN', CURRENT_TIMESTAMP)
                        """)) {
                    ps.setObject(1, accountId);
                    ps.setObject(2, admin.userId());
                    ps.executeUpdate();
                }

                AdminPasswordTokenService.IssuedToken initialToken = admin.newlyCreated()
                        ? passwordTokens.issue(connection, admin.userId(),
                                AdminPasswordTokenService.Purpose.INITIAL_PASSWORD, principal.userId())
                        : null;

                connection.commit();
                return new CreatedAccount(
                        accountId,
                        request.accountName().trim(),
                        admin.userId(),
                        admin.loginName(),
                        initialToken == null ? null : passwordTokens.setupPath(initialToken),
                        initialToken == null ? null : initialToken.expiresAt()
                );
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create survey account", e);
        }
    }

    public AccountSummary rename(AdminPrincipal principal, UUID accountId, RenameAccountRequest request) {
        requireSystemAdmin(principal);
        String name = validateAccountName(request == null ? null : request.name());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE survey_account
                     SET name = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                     """)) {
            ps.setString(1, name);
            ps.setObject(2, accountId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "ACCOUNT_NOT_FOUND", "Enkätkontot finns inte.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not rename survey account", e);
        }
        return list(principal).stream()
                .filter(account -> account.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "ACCOUNT_NOT_FOUND", "Enkätkontot finns inte."));
    }

    public void delete(AdminPrincipal principal, UUID accountId, boolean confirmed) {
        requireSystemAdmin(principal);
        if (!confirmed) {
            throw new ApiException(400, "ACCOUNT_DELETE_CONFIRMATION_REQUIRED",
                    "Bekräfta att enkätkontot och alla dess enkäter ska tas bort permanent.");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT id FROM survey_account WHERE id = ? FOR UPDATE")) {
                    lock.setObject(1, accountId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) {
                            throw new ApiException(404, "ACCOUNT_NOT_FOUND", "Enkätkontot finns inte.");
                        }
                    }
                }

                try (PreparedStatement deleteSurveys = connection.prepareStatement(
                        "DELETE FROM survey WHERE survey_account_id = ?")) {
                    deleteSurveys.setObject(1, accountId);
                    deleteSurveys.executeUpdate();
                }

                try (PreparedStatement deleteAccount = connection.prepareStatement(
                        "DELETE FROM survey_account WHERE id = ?")) {
                    deleteAccount.setObject(1, accountId);
                    deleteAccount.executeUpdate();
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
            throw new IllegalStateException("Could not delete survey account", e);
        }
    }

    private String validateAccountName(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "INVALID_ACCOUNT", "Kontonamn måste anges.");
        }
        String name = value.trim();
        if (name.length() > 300) {
            throw new ApiException(400, "INVALID_ACCOUNT", "Kontonamn får vara högst 300 tecken.");
        }
        return name;
    }

    private AdminResolution resolveOrCreateAdmin(Connection connection, String username) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, username, active
                FROM admin_user
                WHERE lower(username) = lower(?)
                """)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (!rs.getBoolean("active")) {
                        throw new ApiException(409, "ADMIN_INACTIVE", "Administratörskontot är inaktivt.");
                    }
                    return new AdminResolution(
                            rs.getObject("id", UUID.class),
                            rs.getString("username"),
                            false
                    );
                }
            }
        }

        String email = credentialPolicy.normalizeNewAdminEmail(username);
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO admin_user (id, username, password_hash, system_admin, active, created_at, updated_at)
                VALUES (?, ?, NULL, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            ps.setObject(1, id);
            ps.setString(2, email);
            ps.executeUpdate();
        }
        return new AdminResolution(id, email, true);
    }

    private void validate(CreateAccountRequest request) {
        validateAccountName(request == null ? null : request.accountName());
        if (request.adminUsername() == null || request.adminUsername().isBlank()) {
            throw new ApiException(400, "INVALID_ADMIN", "Första administratörens e-postadress eller befintliga användarnamn måste anges.");
        }
        if (request.adminUsername().trim().length() > 200) {
            throw new ApiException(400, "INVALID_ADMIN", "Administratörens användarnamn får vara högst 200 tecken.");
        }
    }

    private void requireSystemAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
        }
        if (!principal.systemAdmin()) {
            throw new ApiException(403, "SYSTEM_ADMIN_REQUIRED", "Systemadministratörsbehörighet krävs.");
        }
    }

    private record AdminResolution(UUID userId, String loginName, boolean newlyCreated) {}

    public record CreateAccountRequest(String accountName, String adminUsername, String adminPassword) {}
    public record RenameAccountRequest(String name) {}
    public record CreatedAccount(
            UUID id,
            String name,
            UUID adminUserId,
            String adminUsername,
            String initialPasswordPath,
            Instant initialPasswordExpiresAt
    ) {}
    public record AccountSummary(UUID id, String name, long adminCount, long surveyCount, Instant createdAt, Instant updatedAt) {}
}
