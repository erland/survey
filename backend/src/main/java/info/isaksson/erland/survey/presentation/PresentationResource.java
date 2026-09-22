package info.isaksson.erland.survey.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/presentation/{token}")
@Produces(MediaType.APPLICATION_JSON)
public class PresentationResource {
    @Inject PresentationReadService presentations;

    @GET
    public Response view(@PathParam("token") String token) {
        CacheControl cache = new CacheControl();
        cache.setNoStore(true);
        return Response.ok(presentations.view(token)).cacheControl(cache).build();
    }
}
