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
                     SELECT a.id, a.name, a.created_at, a.updated_at, COUNT(m.admin_user_id) AS admin_count
                     FROM survey_account a
                     LEFT JOIN survey_account_admin m ON m.survey_account_id = a.id
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
        if (request == null || request.accountName() == null || request.accountName().isBlank()) {
            throw new ApiException(400, "INVALID_ACCOUNT", "Kontonamn måste anges.");
        }
        if (request.accountName().trim().length() > 300) {
            throw new ApiException(400, "INVALID_ACCOUNT", "Kontonamn får vara högst 300 tecken.");
        }
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
    public record CreatedAccount(
            UUID id,
            String name,
            UUID adminUserId,
            String adminUsername,
            String initialPasswordPath,
            Instant initialPasswordExpiresAt
    ) {}
    public record AccountSummary(UUID id, String name, long adminCount, Instant createdAt, Instant updatedAt) {}
}
