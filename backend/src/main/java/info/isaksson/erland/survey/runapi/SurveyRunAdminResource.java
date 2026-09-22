package info.isaksson.erland.survey.runapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/api/admin/runs")
@Produces(MediaType.APPLICATION_JSON)
public class SurveyRunAdminResource {
    @Inject SurveyRunAdminService service;
    @Inject RunSummaryService summaryService;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    @Path("/{runId}")
    public SurveyRunDtos.RunView get(@PathParam("runId") UUID runId) {
        return service.get(principal().userId(), runId);
    }

    @POST
    @Path("/{runId}/open")
    public SurveyRunDtos.RunView open(@PathParam("runId") UUID runId) {
        return service.open(principal().userId(), runId);
    }

    @GET
    @Path("/{runId}/summary")
    public RunSummaryDtos.LiveSummary summary(@PathParam("runId") UUID runId) {
        return summaryService.get(principal().userId(), runId, java.time.Instant.now());
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
