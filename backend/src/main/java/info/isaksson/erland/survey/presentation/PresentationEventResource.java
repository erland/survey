package info.isaksson.erland.survey.presentation;

import info.isaksson.erland.survey.live.LiveEventService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.UUID;

@Path("/api/presentation/{token}/events")
public class PresentationEventResource {
    @Inject PresentationTokenService tokens;
    @Inject LiveEventService liveEvents;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void events(@PathParam("token") String token, @Context SseEventSink sink, @Context Sse sse) {
        UUID runId = tokens.authenticateRunId(token);
        liveEvents.register(runId, sink, sse);
    }
}
