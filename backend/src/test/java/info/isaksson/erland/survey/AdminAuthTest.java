package info.isaksson.erland.survey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
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
    void signedInAdministratorCanChangeOwnPasswordAndKeepCurrentSession() throws Exception {
        String username = "self-change-" + UUID.randomUUID() + "@example.test";
        createAdmin(username);

        String currentCookie = login(username, "test-password-123");
        String otherCookie = login(username, "test-password-123");

        given()
                .cookie("survey_admin_session", currentCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "currentPassword":"wrong-password",
                          "newPassword":"newpass8"
                        }
                        """)
                .post("/api/auth/change-password")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_CURRENT_PASSWORD"));

        given()
                .cookie("survey_admin_session", currentCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "currentPassword":"test-password-123",
                          "newPassword":"newpass8"
                        }
                        """)
                .post("/api/auth/change-password")
                .then().statusCode(204);

        given()
                .cookie("survey_admin_session", currentCookie)
                .get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo(username));

        given()
                .cookie("survey_admin_session", otherCookie)
                .get("/api/auth/me")
                .then().statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login")
                .then().statusCode(401);

        login(username, "newpass8");
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

    private String login(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    private void createAdmin(String username) throws Exception {
        String passwordHash;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT password_hash FROM admin_user WHERE username = 'test-admin'");
             ResultSet rs = ps.executeQuery()) {
            org.junit.jupiter.api.Assertions.assertTrue(rs.next());
            passwordHash = rs.getString(1);
        }

        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO admin_user (id, username, password_hash, system_admin, active)
                     VALUES (?, ?, ?, FALSE, TRUE)
                     """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
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
