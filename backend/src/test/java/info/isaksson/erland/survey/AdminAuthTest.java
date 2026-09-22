package info.isaksson.erland.survey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import java.sql.PreparedStatement;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class AdminAuthTest {
    @Inject AgroalDataSource dataSource;

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
    void bootstrapAdminIsSystemAdminAndMemberOfInitialAccount() throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT u.system_admin, u.active, COUNT(m.survey_account_id)
                     FROM admin_user u
                     LEFT JOIN survey_account_admin m ON m.admin_user_id = u.id
                     WHERE u.username = 'test-admin'
                     GROUP BY u.system_admin, u.active
                     """);
             var rs = statement.executeQuery()) {
            assert rs.next();
            org.junit.jupiter.api.Assertions.assertTrue(rs.getBoolean(1));
            org.junit.jupiter.api.Assertions.assertTrue(rs.getBoolean(2));
            org.junit.jupiter.api.Assertions.assertTrue(rs.getLong(3) >= 1);
        }
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
