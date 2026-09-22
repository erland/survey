package info.isaksson.erland.survey.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Map;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject AuthService authService;

    @ConfigProperty(name = "app.auth.cookie-secure", defaultValue = "true")
    boolean cookieSecure;

    @ConfigProperty(name = "app.auth.session-hours", defaultValue = "12")
    long sessionHours;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return invalidCredentials();
        }
        return authService.login(request.username(), request.password())
                .map(result -> Response.ok(Map.of(
                                "username", result.username(),
                                "expiresAt", result.expiresAt().toString()))
                        .header("Set-Cookie", sessionCookie(result.token()))
                        .build())
                .orElseGet(this::invalidCredentials);
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(AdminAuthFilter.COOKIE_NAME) String token) {
        authService.logout(token);
        return Response.noContent()
                .header("Set-Cookie", expiredSessionCookie())
                .build();
    }

    @GET
    @Path("/me")
    public Response me(@CookieParam(AdminAuthFilter.COOKIE_NAME) String token) {
        return authService.authenticate(token)
                .map(principal -> Response.ok(Map.of(
                        "authenticated", true,
                        "username", principal.username())).build())
                .orElseGet(() -> Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of(
                                "code", "ADMIN_AUTH_REQUIRED",
                                "message", "Administratörsinloggning krävs."
                        )).build());
    }

    private Response invalidCredentials() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(
                        "code", "INVALID_CREDENTIALS",
                        "message", "Felaktigt användarnamn eller lösenord."
                ))
                .build();
    }

    private String sessionCookie(String token) {
        long maxAge = Duration.ofHours(sessionHours).toSeconds();
        return AdminAuthFilter.COOKIE_NAME + "=" + token +
                "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAge +
                (cookieSecure ? "; Secure" : "");
    }

    private String expiredSessionCookie() {
        return AdminAuthFilter.COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0" +
                (cookieSecure ? "; Secure" : "");
    }

    public record LoginRequest(String username, String password) {}
}
