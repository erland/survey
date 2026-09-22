package info.isaksson.erland.survey.live;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.runapi.RunSummaryService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.time.Instant;
import java.util.UUID;

@Path("/api/admin/runs/{runId}/events")
public class LiveEventResource {
    @Inject LiveEventService liveEvents;
    @Inject RunSummaryService summaryService;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void events(@PathParam("runId") UUID runId, @Context SseEventSink sink, @Context Sse sse) {
        // Authorization + ownership check before holding the connection open.
        summaryService.get(principal().userId(), runId, Instant.now());
        liveEvents.register(runId, sink, sse);
    }

    private AdminPrincipal principal() {
        AdminPrincipal principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
