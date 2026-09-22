package info.isaksson.erland.survey.runapi;

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

import static info.isaksson.erland.survey.runapi.SurveyRunDtos.*;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class SurveyRunAdminResource {
    @Inject SurveyRunAdminService service;
    @Inject RunSummaryService summaryService;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    @Path("surveys/{surveyId}/runs")
    public List<RunView> list(@PathParam("surveyId") UUID surveyId) {
        return service.list(principal().userId(), surveyId);
    }

    @POST
    @Path("surveys/{surveyId}/runs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("surveyId") UUID surveyId, CreateRunRequest request) {
        RunView created = service.create(principal().userId(), surveyId, request == null ? null : request.title());
        return Response.created(URI.create("/api/admin/runs/" + created.id())).entity(created).build();
    }

    @GET
    @Path("runs/{runId}")
    public RunView get(@PathParam("runId") UUID runId) {
        return service.get(principal().userId(), runId);
    }

    @POST
    @Path("runs/{runId}/open")
    public RunView open(@PathParam("runId") UUID runId) {
        return service.open(principal().userId(), runId);
    }


    @GET
    @Path("runs/{runId}/summary")
    public RunSummaryDtos.LiveSummary summary(@PathParam("runId") UUID runId) {
        return summaryService.get(principal().userId(), runId, java.time.Instant.now());
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
