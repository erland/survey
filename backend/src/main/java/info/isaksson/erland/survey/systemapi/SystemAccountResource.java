package info.isaksson.erland.survey.systemapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;

@Path("/api/system/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class SystemAccountResource {
    @Inject SystemAccountService service;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<SystemAccountService.AccountSummary> list() {
        return service.list(principal());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(SystemAccountService.CreateAccountRequest request) {
        var created = service.create(principal(), request);
        return Response.created(URI.create("/api/system/accounts/" + created.id()))
                .entity(created)
                .build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
