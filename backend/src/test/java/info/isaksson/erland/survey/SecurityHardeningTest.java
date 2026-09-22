package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class SecurityHardeningTest {

    @Test
    void apiResponsesContainSecurityHeaders() {
        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("Referrer-Policy", equalTo("no-referrer"))
                .header("Cache-Control", equalTo("no-store"));
    }

    @Test
    void adminCookieUsesStrictSameSite() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .header("Set-Cookie", notNullValue())
                .header("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict"));
    }

    @Test
    void crossOriginUnsafeAdminRequestIsRejectedBeforeAuthorization() {
        given()
                .header("Origin", "https://evil.example")
                .header("Host", "localhost")
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/admin/surveys")
                .then()
                .statusCode(403)
                .body("code", equalTo("CSRF_ORIGIN_REJECTED"));
    }
}
