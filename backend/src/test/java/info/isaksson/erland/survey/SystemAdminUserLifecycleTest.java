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
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SystemAdminUserLifecycleTest {
    @Inject AgroalDataSource dataSource;

    @Test
    void systemAdminCanDeactivateReactivateAndDeleteOrphanAdministrator() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        String username = "orphan-" + UUID.randomUUID();
        UUID userId = createAdmin(username, false, true);
        String userCookie = login(username, "test-password-123");

        given()
                .cookie("survey_admin_session", userCookie)
                .get("/api/auth/me")
                .then().statusCode(200)
                .body("username", equalTo(username));

        given()
                .cookie("survey_admin_session", systemCookie)
                .post("/api/system/admins/" + userId + "/deactivate")
                .then().statusCode(204);

        given()
                .cookie("survey_admin_session", userCookie)
                .get("/api/auth/me")
                .then().statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login")
                .then().statusCode(401);

        given()
                .cookie("survey_admin_session", systemCookie)
                .post("/api/system/admins/" + userId + "/activate")
                .then().statusCode(204);

        login(username, "test-password-123");

        given()
                .cookie("survey_admin_session", systemCookie)
                .post("/api/system/admins/" + userId + "/deactivate")
                .then().statusCode(204);

        given()
                .cookie("survey_admin_session", systemCookie)
                .delete("/api/system/admins/" + userId)
                .then().statusCode(204);

        assertFalse(adminExists(userId));
    }

    @Test
    void administratorWithAccountMembershipCannotBeDeleted() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        String username = "member-" + UUID.randomUUID();
        UUID userId = createAdmin(username, false, false);
        UUID accountId = UUID.randomUUID();

        try (var connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO survey_account (id, name) VALUES (?, ?)")) {
                ps.setObject(1, accountId);
                ps.setString(2, "Lifecycle " + username);
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

        given()
                .cookie("survey_admin_session", systemCookie)
                .delete("/api/system/admins/" + userId)
                .then().statusCode(409)
                .body("code", equalTo("ADMIN_HAS_ACCOUNT_MEMBERSHIPS"));

        assertTrue(adminExists(userId));
    }

    @Test
    void activeAdministratorMustBeDeactivatedBeforeDeletion() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        UUID userId = createAdmin("active-orphan-" + UUID.randomUUID(), false, true);

        given()
                .cookie("survey_admin_session", systemCookie)
                .delete("/api/system/admins/" + userId)
                .then().statusCode(409)
                .body("code", equalTo("ADMIN_MUST_BE_INACTIVE"));
    }

    @Test
    void systemAdministratorIsProtected() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        UUID systemUserId = userId("test-admin");

        given()
                .cookie("survey_admin_session", systemCookie)
                .post("/api/system/admins/" + systemUserId + "/deactivate")
                .then().statusCode(409)
                .body("code", equalTo("SELF_ADMIN_PROTECTED"));

        given()
                .cookie("survey_admin_session", systemCookie)
                .delete("/api/system/admins/" + systemUserId)
                .then().statusCode(409)
                .body("code", equalTo("SELF_ADMIN_PROTECTED"));
    }

    private String login(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    private UUID createAdmin(String username, boolean systemAdmin, boolean active) throws Exception {
        UUID id = UUID.randomUUID();
        String passwordHash;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT password_hash FROM admin_user WHERE username = 'test-admin'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            passwordHash = rs.getString(1);
        }

        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO admin_user (id, username, password_hash, system_admin, active)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            ps.setObject(1, id);
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.setBoolean(4, systemAdmin);
            ps.setBoolean(5, active);
            ps.executeUpdate();
        }
        return id;
    }

    private boolean adminExists(UUID userId) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM admin_user WHERE id = ?)")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private UUID userId(String username) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM admin_user WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }
}
