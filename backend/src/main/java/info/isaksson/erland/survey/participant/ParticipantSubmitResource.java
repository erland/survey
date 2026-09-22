package info.isaksson.erland.survey.participant;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/public/runs/{publicId}/participants/current/submit")
@Produces(MediaType.APPLICATION_JSON)
public class ParticipantSubmitResource {
    @Inject ParticipantSubmitService service;

    @POST
    public ParticipantSubmitService.SubmitResponse submit(
            @PathParam("publicId") String publicId,
            @HeaderParam("X-Participant-Token") String token) {
        return service.submit(publicId, token);
    }
}
