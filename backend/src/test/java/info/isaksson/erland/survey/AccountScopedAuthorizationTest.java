package info.isaksson.erland.survey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class AccountScopedAuthorizationTest {
    @Inject AgroalDataSource dataSource;

    @Test
    void accountScopedApiPreventsCrossAccountAccess() throws Exception {
        UUID defaultAccount = defaultAccountForTestAdmin();
        String otherUsername = "tenant-admin-" + UUID.randomUUID();
        UUID otherAccount = createOtherAccountAndAdmin(otherUsername);

        String otherCookie = login(otherUsername);
        String testAdminCookie = login("test-admin");

        String surveyId = given()
                .cookie("survey_admin_session", otherCookie)
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Tenant B survey\",\"questions\":[]}")
                .post("/api/admin/accounts/" + otherAccount + "/surveys")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .cookie("survey_admin_session", otherCookie)
                .get("/api/admin/accounts/" + otherAccount + "/surveys/" + surveyId)
                .then().statusCode(200)
                .body("title", equalTo("Tenant B survey"));

        String runId = given()
                .cookie("survey_admin_session", otherCookie)
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Tenant B run\"}")
                .post("/api/admin/accounts/" + otherAccount + "/surveys/" + surveyId + "/runs")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .cookie("survey_admin_session", testAdminCookie)
                .get("/api/admin/accounts/" + otherAccount + "/surveys/" + surveyId)
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", testAdminCookie)
                .get("/api/admin/accounts/" + otherAccount + "/runs/" + runId)
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", testAdminCookie)
                .get("/api/admin/accounts/" + otherAccount + "/runs/" + runId + "/results")
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", testAdminCookie)
                .get("/api/admin/accounts/" + otherAccount + "/runs/" + runId + "/export/json")
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", testAdminCookie)
                .get("/api/admin/accounts/" + otherAccount + "/runs/" + runId + "/summary")
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", otherCookie)
                .get("/api/admin/accounts/" + defaultAccount + "/surveys")
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", testAdminCookie)
                .get("/api/admin/accounts/" + defaultAccount + "/surveys")
                .then().statusCode(200);
    }

    private String login(String username) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    private UUID defaultAccountForTestAdmin() throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT m.survey_account_id
                     FROM survey_account_admin m
                     JOIN admin_user u ON u.id = m.admin_user_id
                     WHERE u.username = 'test-admin'
                     ORDER BY m.created_at
                     LIMIT 1
                     """);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getObject(1, UUID.class);
        }
    }

    private UUID createOtherAccountAndAdmin(String username) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            String passwordHash;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT password_hash FROM admin_user WHERE username = 'test-admin'");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                passwordHash = rs.getString(1);
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO admin_user (id, username, password_hash, system_admin, active)
                    VALUES (?, ?, ?, FALSE, TRUE)
                    """)) {
                ps.setObject(1, userId);
                ps.setString(2, username);
                ps.setString(3, passwordHash);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO survey_account (id, name) VALUES (?, 'Tenant B')")) {
                ps.setObject(1, accountId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO survey_account_admin (survey_account_id, admin_user_id, role)
                    VALUES (?, ?, 'ADMIN')
                    """)) {
                ps.setObject(1, accountId);
                ps.setObject(2, userId);
                ps.executeUpdate();
            }
        }
        return accountId;
    }
}
