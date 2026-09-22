package info.isaksson.erland.survey.result;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

import static info.isaksson.erland.survey.result.ResultDtos.QuestionResult;

@Path("/api/admin/runs/{runId}/results")
@Produces(MediaType.APPLICATION_JSON)
public class ResultResource {
    @Inject ResultService service;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<QuestionResult> all(@PathParam("runId") UUID runId) {
        return service.all(principal().userId(), runId);
    }

    @GET
    @Path("/{questionId}")
    public QuestionResult one(@PathParam("runId") UUID runId, @PathParam("questionId") UUID questionId) {
        return service.one(principal().userId(), runId, questionId);
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
