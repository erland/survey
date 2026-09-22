package info.isaksson.erland.survey.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class SecurityResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        response.getHeaders().putSingle("X-Frame-Options", "DENY");
        response.getHeaders().putSingle("Referrer-Policy", "no-referrer");
        response.getHeaders().putSingle("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.getHeaders().putSingle("Cross-Origin-Resource-Policy", "same-origin");

        String path = request.getUriInfo().getPath();
        if (path.startsWith("api/")) {
            response.getHeaders().putSingle("Cache-Control", "no-store");
        }
    }
}
