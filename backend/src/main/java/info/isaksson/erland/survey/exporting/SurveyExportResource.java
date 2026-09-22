package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/admin/surveys/{surveyId}/export")
public class SurveyExportResource {
    @Inject SurveyExportService service;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response export(@PathParam("surveyId") UUID surveyId) {
        var export = service.exportDefinition(principal().userId(), surveyId);
        String filename = "survey-definition-" + surveyId + ".json";
        return Response.ok(export)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
