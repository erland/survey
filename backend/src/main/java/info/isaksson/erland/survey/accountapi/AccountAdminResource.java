package info.isaksson.erland.survey.accountapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/api/admin/accounts/{accountId}/admins")
@Produces(MediaType.APPLICATION_JSON)
public class AccountAdminResource {
    @Inject AccountAdminService service;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<AccountAdminService.AccountAdminView> list(@PathParam("accountId") UUID accountId) {
        return service.list(principal().userId(), accountId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(@PathParam("accountId") UUID accountId,
                        AccountAdminService.AddAccountAdminRequest request) {
        var created = service.add(principal().userId(), accountId, request);
        return Response.created(URI.create("/api/admin/accounts/" + accountId + "/admins/" + created.userId()))
                .entity(created)
                .build();
    }

    @DELETE
    @Path("/{adminUserId}")
    public Response remove(@PathParam("accountId") UUID accountId,
                           @PathParam("adminUserId") UUID adminUserId) {
        service.remove(principal().userId(), accountId, adminUserId);
        return Response.noContent().build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
