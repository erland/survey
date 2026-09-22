package info.isaksson.erland.survey.accountapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.exporting.SurveyExportService;
import info.isaksson.erland.survey.importing.SurveyImportService;
import info.isaksson.erland.survey.runapi.SurveyRunAdminService;
import info.isaksson.erland.survey.runapi.SurveyRunDtos;
import info.isaksson.erland.survey.surveyapi.ApiException;
import info.isaksson.erland.survey.surveyapi.SurveyDtos;
import info.isaksson.erland.survey.surveyapi.SurveyService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/api/admin/accounts/{accountId}/surveys")
@Produces(MediaType.APPLICATION_JSON)
public class AccountSurveyResource {
    @Inject SurveyService surveys;
    @Inject SurveyRunAdminService runs;
    @Inject SurveyExportService exports;
    @Inject SurveyImportService imports;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<SurveyDtos.SurveySummary> list(@PathParam("accountId") UUID accountId) {
        return surveys.list(principal().userId(), accountId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("accountId") UUID accountId, SurveyDtos.SurveyInput input) {
        var created = surveys.create(principal().userId(), accountId, input);
        return Response.created(URI.create("/api/admin/accounts/" + accountId + "/surveys/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response importDefinition(@PathParam("accountId") UUID accountId,
                                     info.isaksson.erland.survey.exporting.SurveyExportDtos.SurveyDefinitionExport document) {
        var created = imports.importDefinition(principal().userId(), accountId, document);
        return Response.created(URI.create("/api/admin/accounts/" + accountId + "/surveys/" + created.id())).entity(created).build();
    }

    @GET @Path("/{surveyId}")
    public SurveyDtos.SurveyView get(@PathParam("accountId") UUID accountId, @PathParam("surveyId") UUID surveyId) {
        return surveys.get(principal().userId(), accountId, surveyId);
    }

    @PUT @Path("/{surveyId}") @Consumes(MediaType.APPLICATION_JSON)
    public SurveyDtos.SurveyView update(@PathParam("accountId") UUID accountId, @PathParam("surveyId") UUID surveyId,
                                        SurveyDtos.SurveyInput input) {
        return surveys.update(principal().userId(), accountId, surveyId, input);
    }

    @DELETE @Path("/{surveyId}")
    public Response delete(@PathParam("accountId") UUID accountId, @PathParam("surveyId") UUID surveyId) {
        surveys.delete(principal().userId(), accountId, surveyId);
        return Response.noContent().build();
    }

    @POST @Path("/{surveyId}/copy")
    public Response copy(@PathParam("accountId") UUID accountId, @PathParam("surveyId") UUID surveyId) {
        var created = surveys.copy(principal().userId(), accountId, surveyId);
        return Response.created(URI.create("/api/admin/accounts/" + accountId + "/surveys/" + created.id())).entity(created).build();
    }

    @GET @Path("/{surveyId}/export")
    public Response export(@PathParam("accountId") UUID accountId, @PathParam("surveyId") UUID surveyId) {
        var document = exports.exportDefinition(principal().userId(), accountId, surveyId);
        return Response.ok(document).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GET @Path("/{surveyId}/runs")
    public List<SurveyRunDtos.RunView> listRuns(@PathParam("accountId") UUID accountId, @PathParam("surveyId") UUID surveyId) {
        return runs.list(principal().userId(), accountId, surveyId);
    }

    @POST @Path("/{surveyId}/runs") @Consumes(MediaType.APPLICATION_JSON)
    public Response createRun(@PathParam("accountId") UUID accountId, @PathParam("surveyId") UUID surveyId,
                              SurveyRunDtos.CreateRunRequest request) {
        var created = runs.create(principal().userId(), accountId, surveyId, request == null ? null : request.title());
        return Response.created(URI.create("/api/admin/accounts/" + accountId + "/runs/" + created.id())).entity(created).build();
    }

    private AdminPrincipal principal() {
        var principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
