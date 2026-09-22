package info.isaksson.erland.survey.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION - 20)
public class CsrfOriginFilter implements ContainerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public void filter(ContainerRequestContext request) {
        if (SAFE_METHODS.contains(request.getMethod())) return;

        String path = request.getUriInfo().getPath();
        if (!(path.startsWith("api/admin/") || path.equals("api/admin") || path.equals("api/auth/logout"))) {
            return;
        }

        String origin = request.getHeaderString("Origin");
        if (origin == null || origin.isBlank()) {
            return; // Non-browser clients/tests may omit Origin; SameSite cookie is still enforced in browsers.
        }

        String host = request.getHeaderString("Host");
        try {
            URI uri = URI.create(origin);
            String originAuthority = uri.getRawAuthority();
            if (host == null || originAuthority == null || !originAuthority.equalsIgnoreCase(host)) {
                request.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of(
                                "code", "CSRF_ORIGIN_REJECTED",
                                "message", "Begäran kommer från ett otillåtet ursprung."
                        )).build());
            }
        } catch (IllegalArgumentException e) {
            request.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "code", "CSRF_ORIGIN_REJECTED",
                            "message", "Begäran kommer från ett otillåtet ursprung."
                    )).build());
        }
    }
}
