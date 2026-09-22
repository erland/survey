package info.isaksson.erland.survey.accountapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.exporting.*;
import info.isaksson.erland.survey.presentation.PresentationDtos;
import info.isaksson.erland.survey.presentation.PresentationTokenService;
import info.isaksson.erland.survey.result.ResultDtos;
import info.isaksson.erland.survey.result.ResultService;
import info.isaksson.erland.survey.runapi.*;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/admin/accounts/{accountId}/runs")
@Produces(MediaType.APPLICATION_JSON)
public class AccountRunResource {
    @Inject SurveyRunAdminService runs;
    @Inject RunSummaryService summaries;
    @Inject ResultService results;
    @Inject RunResultExportService jsonExport;
    @Inject RunResultCsvExportService csvExport;
    @Inject SurveyPackageExportService packageExport;
    @Inject PresentationTokenService presentationTokens;
    @Inject AdminRequestContext adminRequestContext;

    @GET @Path("/{runId}")
    public SurveyRunDtos.RunView get(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId) {
        return runs.get(principal().userId(), accountId, runId);
    }

    @POST @Path("/{runId}/open")
    public SurveyRunDtos.RunView open(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId) {
        return runs.open(principal().userId(), accountId, runId);
    }

    @GET @Path("/{runId}/summary")
    public RunSummaryDtos.LiveSummary summary(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId) {
        return summaries.get(principal().userId(), accountId, runId, Instant.now());
    }

    @GET @Path("/{runId}/results")
    public List<ResultDtos.QuestionResult> results(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId) {
        return results.all(principal().userId(), accountId, runId);
    }

    @GET @Path("/{runId}/results/{questionId}")
    public ResultDtos.QuestionResult result(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId,
                                            @PathParam("questionId") UUID questionId) {
        return results.one(principal().userId(), accountId, runId, questionId);
    }

    @GET @Path("/{runId}/export/json")
    public Response exportJson(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId) {
        return Response.ok(jsonExport.export(principal().userId(), accountId, runId)).header("Cache-Control","no-store").build();
    }

    @GET @Path("/{runId}/export/csv") @Produces("text/csv; charset=UTF-8")
    public Response exportCsv(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId) {
        return Response.ok(csvExport.export(principal().userId(), accountId, runId), "text/csv; charset=UTF-8")
                .header("Cache-Control","no-store").build();
    }

    @GET @Path("/{runId}/export/package") @Produces("application/zip")
    public Response exportPackage(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId) {
        return Response.ok(packageExport.export(principal().userId(), accountId, runId), "application/zip")
                .header("Cache-Control","no-store").build();
    }

    @POST @Path("/{runId}/presentation-tokens")
    public PresentationDtos.CreatedPresentationToken createPresentationToken(@PathParam("accountId") UUID accountId,
                                                                              @PathParam("runId") UUID runId) {
        return presentationTokens.create(principal().userId(), accountId, runId);
    }

    @DELETE @Path("/{runId}/presentation-tokens/{tokenId}")
    public Response revokePresentationToken(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId,
                                            @PathParam("tokenId") UUID tokenId) {
        presentationTokens.revoke(principal().userId(), accountId, runId, tokenId);
        return Response.noContent().build();
    }

    private AdminPrincipal principal() {
        var principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
