package info.isaksson.erland.survey.presentation;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/admin/runs/{runId}/presentation-tokens")
@Produces(MediaType.APPLICATION_JSON)
public class PresentationAdminResource {
    @Inject PresentationTokenService tokens;
    @Inject AdminRequestContext adminRequestContext;

    @POST
    public PresentationDtos.CreatedPresentationToken create(@PathParam("runId") UUID runId) {
        return tokens.create(principal().userId(), runId);
    }

    @DELETE
    @Path("/{tokenId}")
    public Response revoke(@PathParam("runId") UUID runId, @PathParam("tokenId") UUID tokenId) {
        tokens.revoke(principal().userId(), runId, tokenId);
        return Response.noContent().build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
