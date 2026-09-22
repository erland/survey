package info.isaksson.erland.survey.importing;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.exporting.SurveyExportDtos.SurveyDefinitionExport;
import info.isaksson.erland.survey.surveyapi.ApiException;
import info.isaksson.erland.survey.surveyapi.SurveyDtos.SurveyView;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/api/admin/surveys/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SurveyImportResource {
    @Inject SurveyImportService service;
    @Inject AdminRequestContext adminRequestContext;

    @POST
    public Response importDefinition(SurveyDefinitionExport document) {
        SurveyView created = service.importDefinition(principal().userId(), document);
        return Response.created(URI.create("/api/admin/surveys/" + created.id())).entity(created).build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
