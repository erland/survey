package info.isaksson.erland.survey.participant;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/public/runs/{publicId}/participants/current/responses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParticipantResponseResource {
    @Inject ParticipantResponseService service;

    @GET
    public ParticipantResponseDtos.AnswersResponse list(@PathParam("publicId") String publicId, @HeaderParam("X-Participant-Token") String token) {
        return service.list(publicId, token);
    }

    @PUT @Path("/{questionId}")
    public ParticipantResponseDtos.AnswerResponse save(@PathParam("publicId") String publicId, @PathParam("questionId") UUID questionId,
                                                       @HeaderParam("X-Participant-Token") String token, ParticipantResponseDtos.SaveAnswerRequest input) {
        return service.save(publicId, questionId, token, input);
    }
}
