package info.isaksson.erland.survey.surveyapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static info.isaksson.erland.survey.surveyapi.SurveyDtos.*;

@Path("/api/admin/surveys")
@Produces(MediaType.APPLICATION_JSON)
public class SurveyResource {
    @Inject SurveyService service;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    public List<SurveySummary> list() {
        return service.list(principal().userId());
    }

    @GET
    @Path("/{id}")
    public SurveyView get(@PathParam("id") UUID id) {
        return service.get(principal().userId(), id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(SurveyInput input) {
        SurveyView created = service.create(principal().userId(), input);
        return Response.created(URI.create("/api/admin/surveys/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public SurveyView update(@PathParam("id") UUID id, SurveyInput input) {
        return service.update(principal().userId(), id, input);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        service.delete(principal().userId(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/copy")
    public Response copy(@PathParam("id") UUID id) {
        SurveyView created = service.copy(principal().userId(), id);
        return Response.created(URI.create("/api/admin/surveys/" + created.id())).entity(created).build();
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
