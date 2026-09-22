package info.isaksson.erland.survey.accountapi;

import info.isaksson.erland.survey.auth.AdminRequestContext;
import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import info.isaksson.erland.survey.live.LiveEventService;
import info.isaksson.erland.survey.runapi.RunSummaryService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.time.Instant;
import java.util.UUID;

@Path("/api/admin/accounts/{accountId}/runs/{runId}/events")
public class AccountLiveEventResource {
    @Inject LiveEventService liveEvents;
    @Inject RunSummaryService summaries;
    @Inject AdminRequestContext adminRequestContext;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void events(@PathParam("accountId") UUID accountId, @PathParam("runId") UUID runId,
                       @Context SseEventSink sink, @Context Sse sse) {
        summaries.get(principal().userId(), accountId, runId, Instant.now());
        liveEvents.register(runId, sink, sse);
    }

    private AdminPrincipal principal() {
        var principal = adminRequestContext.principal();
        if (principal != null) return principal;
        throw new ApiException(401, "ADMIN_AUTH_REQUIRED", "Administratörsinloggning krävs.");
    }
}
