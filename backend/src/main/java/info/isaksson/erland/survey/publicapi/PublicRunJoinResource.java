package info.isaksson.erland.survey.publicapi;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import static info.isaksson.erland.survey.publicapi.PublicRunDtos.RunLookupResponse;

@Path("/api/public/runs/join/{joinCode}")
@Produces(MediaType.APPLICATION_JSON)
public class PublicRunJoinResource {

    @Inject PublicRunLookupService lookupService;

    @GET
    public RunLookupResponse byJoinCode(@PathParam("joinCode") String joinCode) {
        return lookupService.findByJoinCode(joinCode);
    }
}
