package info.isaksson.erland.survey;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.Map;

@Path("/api/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @GET
    public Map<String, Object> status() {
        return Map.of(
                "status", "ok",
                "service", "survey-service",
                "timestamp", Instant.now().toString()
        );
    }
}
