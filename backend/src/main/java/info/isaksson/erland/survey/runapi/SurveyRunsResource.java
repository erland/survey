package info.isaksson.erland.survey.runapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/api/admin/surveys/{surveyId}/runs")
@Produces(MediaType.APPLICATION_JSON)
public class SurveyRunsResource {
    @Inject SurveyRunAdminService service;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<SurveyRunDtos.RunView> list(@PathParam("surveyId") UUID surveyId) {
        return service.list(principal().userId(), surveyId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("surveyId") UUID surveyId, SurveyRunDtos.CreateRunRequest request) {
        var created = service.create(principal().userId(), surveyId, request == null ? null : request.title());
        return Response.created(URI.create("/api/admin/runs/" + created.id())).entity(created).build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
