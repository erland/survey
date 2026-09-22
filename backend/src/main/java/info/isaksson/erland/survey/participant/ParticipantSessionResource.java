package info.isaksson.erland.survey.participant;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static info.isaksson.erland.survey.participant.ParticipantDtos.ParticipantHeartbeatResponse;
import static info.isaksson.erland.survey.participant.ParticipantDtos.ParticipantSessionResponse;

@Path("/api/public/runs/{publicId}/participants")
@Produces(MediaType.APPLICATION_JSON)
public class ParticipantSessionResource {
    @Inject ParticipantSessionService service;

    @POST
    public Response create(@PathParam("publicId") String publicId) {
        return Response.status(Response.Status.CREATED).entity(service.create(publicId)).build();
    }

    @POST
    @Path("/current/heartbeat")
    public ParticipantHeartbeatResponse heartbeat(@PathParam("publicId") String publicId,
                                                   @HeaderParam("X-Participant-Token") String token) {
        return service.heartbeat(publicId, token);
    }

    @GET
    @Path("/current")
    public ParticipantSessionResponse resume(@PathParam("publicId") String publicId,
                                             @HeaderParam("X-Participant-Token") String token) {
        return service.resume(publicId, token);
    }

    @GET
    @Path("/current/survey")
    public ParticipantSurveyDtos.ParticipantSurveyResponse survey(@PathParam("publicId") String publicId,
                                                                   @HeaderParam("X-Participant-Token") String token) {
        return service.survey(publicId, token);
    }
}
