package info.isaksson.erland.survey.accountapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("/api/admin/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class AccountMembershipResource {
    @Inject AgroalDataSource dataSource;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<AccountMembershipView> list() {
        AdminPrincipal principal = principal();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT a.id, a.name, m.role
                     FROM survey_account_admin m
                     JOIN survey_account a ON a.id = m.survey_account_id
                     WHERE m.admin_user_id = ?
                     ORDER BY lower(a.name), a.id
                     """)) {
            ps.setObject(1, principal.userId());
            try (ResultSet rs = ps.executeQuery()) {
                List<AccountMembershipView> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new AccountMembershipView(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("role")
                    ));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list survey accounts for admin", e);
        }
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }

    public record AccountMembershipView(UUID id, String name, String role) {}
}
