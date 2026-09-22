package info.isaksson.erland.survey.auth;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AdminAuthFilter implements ContainerRequestFilter {
    public static final String COOKIE_NAME = "survey_admin_session";

    @Inject
    AuthService authService;

    @Inject
    AdminRequestContext adminRequestContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        boolean adminApi = path.equals("api/admin") || path.startsWith("api/admin/");
        boolean systemApi = path.equals("api/system") || path.startsWith("api/system/");
        if (!adminApi && !systemApi) {
            return;
        }

        var cookie = requestContext.getCookies().get(COOKIE_NAME);
        var principal = authService.authenticate(cookie == null ? null : cookie.getValue());
        if (principal.isEmpty()) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "code", "ADMIN_AUTH_REQUIRED",
                            "message", "Administratörsinloggning krävs."
                    ))
                    .build());
            return;
        }
        adminRequestContext.setPrincipal(principal.get());
    }

    private String normalizePath(String path) {
        return path != null && path.startsWith("/") ? path.substring(1) : path;
    }
}
