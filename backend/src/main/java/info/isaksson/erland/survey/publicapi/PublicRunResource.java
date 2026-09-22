package info.isaksson.erland.survey.publicapi;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import static info.isaksson.erland.survey.publicapi.PublicRunDtos.RunLookupResponse;

@Path("/api/public/runs/{publicId}")
@Produces(MediaType.APPLICATION_JSON)
public class PublicRunResource {

    @Inject PublicRunLookupService lookupService;

    @GET
    public RunLookupResponse byPublicId(@PathParam("publicId") String publicId) {
        return lookupService.findByPublicId(publicId);
    }
}
