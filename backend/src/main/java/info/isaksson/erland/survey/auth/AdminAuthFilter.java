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
    public static final String PRINCIPAL_PROPERTY = "survey.admin.principal";

    @Inject
    AuthService authService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (!path.equals("api/admin") && !path.startsWith("api/admin/")) {
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
}
