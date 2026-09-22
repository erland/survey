package info.isaksson.erland.survey.auth;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.Map;

@Path("/api/admin/ping")
@Produces(MediaType.APPLICATION_JSON)
public class AdminPingResource {
    @GET
    public Map<String, Object> ping(@Context jakarta.ws.rs.core.HttpHeaders headers) {
        return Map.of("status", "ok", "scope", "admin");
    }
}
