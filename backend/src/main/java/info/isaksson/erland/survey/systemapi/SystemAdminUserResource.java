package info.isaksson.erland.survey.systemapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/system/admins")
@Produces(MediaType.APPLICATION_JSON)
public class SystemAdminUserResource {
    @Inject SystemAdminUserService service;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<SystemAdminUserService.AdminUserView> list() {
        return service.list(principal());
    }

    @POST
    @Path("/{userId}/deactivate")
    public Response deactivate(@PathParam("userId") UUID userId) {
        service.deactivate(principal(), userId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{userId}/activate")
    public Response activate(@PathParam("userId") UUID userId) {
        service.activate(principal(), userId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{userId}")
    public Response delete(@PathParam("userId") UUID userId) {
        service.delete(principal(), userId);
        return Response.noContent().build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
