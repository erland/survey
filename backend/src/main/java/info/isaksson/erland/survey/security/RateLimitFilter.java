package info.isaksson.erland.survey.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION - 30)
public class RateLimitFilter implements ContainerRequestFilter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @ConfigProperty(name = "app.security.rate-limit.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "app.security.rate-limit.auth-per-minute", defaultValue = "20")
    int authLimit;

    @ConfigProperty(name = "app.security.rate-limit.public-per-minute", defaultValue = "600")
    int publicLimit;

    @ConfigProperty(name = "app.security.rate-limit.presentation-per-minute", defaultValue = "600")
    int presentationLimit;

    @Override
    public void filter(ContainerRequestContext request) {
        if (!enabled) return;

        String path = request.getUriInfo().getPath();
        if (path.startsWith("/")) path = path.substring(1);
        int limit;
        String bucket;
        if (path.equals("api/auth/login")) {
            limit = authLimit;
            bucket = "auth";
        } else if (path.startsWith("api/public/")) {
            limit = publicLimit;
            bucket = "public";
        } else if (path.startsWith("api/presentation/")) {
            limit = presentationLimit;
            bucket = "presentation";
        } else {
            return;
        }

        String clientKey = clientKey(request);
        long minute = Instant.now().getEpochSecond() / 60;
        String key = bucket + ":" + clientKey;
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.minute != minute) return new Window(minute);
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > limit) {
            request.abortWith(Response.status(429)
                    .type(MediaType.APPLICATION_JSON)
                    .header("Retry-After", "60")
                    .entity(Map.of(
                            "code", "RATE_LIMITED",
                            "message", "För många förfrågningar. Försök igen om en stund."
                    )).build());
        }

        // Keep the simple in-memory map bounded over long runtimes.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute < minute - 2);
        }
    }

    private String clientKey(ContainerRequestContext request) {
        String participant = request.getHeaderString("X-Participant-Token");
        if (participant != null && !participant.isBlank()) return "participant:" + Integer.toHexString(participant.hashCode());

        String forwarded = request.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",", 2)[0].trim();
        }
        String realIp = request.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return "ip:" + realIp.trim();

        // Direct local/dev traffic without a reverse proxy shares a bucket.
        return "direct";
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger(1);
        Window(long minute) { this.minute = minute; }
    }
}
