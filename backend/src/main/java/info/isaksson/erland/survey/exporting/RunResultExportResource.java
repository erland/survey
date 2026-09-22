package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.auth.AdminAuthFilter;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/admin/runs/{runId}/export")
@Produces(MediaType.APPLICATION_JSON)
public class RunResultExportResource {
    @Inject RunResultExportService service;
    @Inject RunResultCsvExportService csvService;
    @Inject SurveyPackageExportService packageService;
    @Context ContainerRequestContext requestContext;

    @GET
    @Path("/json")
    public Response exportJson(@PathParam("runId") UUID runId) {
        var document = service.export(principal().userId(), runId);
        String filename = "survey-results-" + runId + ".json";
        return Response.ok(document)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Cache-Control", "no-store")
                .build();
    }


    @GET
    @Path("/csv")
    @Produces("text/csv; charset=UTF-8")
    public Response exportCsv(@PathParam("runId") UUID runId) {
        byte[] csv = csvService.export(principal().userId(), runId);
        String filename = "survey-results-" + runId + ".csv";
        return Response.ok(csv, "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Cache-Control", "no-store")
                .build();
    }


    @GET
    @Path("/package")
    @Produces("application/zip")
    public Response exportPackage(@PathParam("runId") UUID runId) {
        byte[] archive = packageService.export(principal().userId(), runId);
        String filename = "survey-package-" + runId + ".zip";
        return Response.ok(archive, "application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Cache-Control", "no-store")
                .build();
    }

    private AdminPrincipal principal() {
        Object value = requestContext.getProperty(AdminAuthFilter.PRINCIPAL_PROPERTY);
        if (value instanceof AdminPrincipal principal) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
