package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class AdminAuthTest {

    @Test
    void adminEndpointRequiresAuthentication() {
        given()
                .when().get("/api/admin/ping")
                .then()
                .statusCode(401)
                .body("code", equalTo("ADMIN_AUTH_REQUIRED"));
    }

    @Test
    void loginCookieAllowsAdminAccessAndLogoutRevokesIt() {
        String cookie = given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("username", equalTo("test-admin"))
                .header("Set-Cookie", notNullValue())
                .extract().cookie("survey_admin_session");

        given()
                .cookie("survey_admin_session", cookie)
                .when().get("/api/admin/ping")
                .then()
                .statusCode(200)
                .body("scope", equalTo("admin"));

        given()
                .cookie("survey_admin_session", cookie)
                .when().post("/api/auth/logout")
                .then()
                .statusCode(204);

        given()
                .cookie("survey_admin_session", cookie)
                .when().get("/api/admin/ping")
                .then()
                .statusCode(401);
    }

    @Test
    void invalidPasswordIsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"wrong\"}")
                .when().post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }
}
