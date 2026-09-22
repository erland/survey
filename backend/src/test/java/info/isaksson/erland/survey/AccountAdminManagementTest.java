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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class AccountAdminManagementTest {
    @Inject AgroalDataSource dataSource;

    @Test
    void accountAdminCanAddListAndRemoveAnotherAdmin() throws Exception {
        String actorUsername = "actor-" + UUID.randomUUID();
        UUID accountId = createAccountWithAdmin(actorUsername);
        String actorCookie = login(actorUsername);

        String newUsername = "member-" + UUID.randomUUID();
        String newUserId = given()
                .cookie("survey_admin_session", actorCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username":"%s",
                          "initialPassword":"new-admin-password-123"
                        }
                        """.formatted(newUsername))
                .post("/api/admin/accounts/" + accountId + "/admins")
                .then().statusCode(201)
                .body("username", equalTo(newUsername))
                .body("role", equalTo("ADMIN"))
                .extract().path("userId");

        given()
                .cookie("survey_admin_session", actorCookie)
                .get("/api/admin/accounts/" + accountId + "/admins")
                .then().statusCode(200)
                .body("username", hasItem(newUsername))
                .body("username", hasItem(actorUsername))
                .body("$", hasSize(2));

        String newAdminCookie = login(newUsername);
        given()
                .cookie("survey_admin_session", newAdminCookie)
                .get("/api/admin/accounts/" + accountId + "/surveys")
                .then().statusCode(200);

        given()
                .cookie("survey_admin_session", actorCookie)
                .delete("/api/admin/accounts/" + accountId + "/admins/" + newUserId)
                .then().statusCode(204);

        given()
                .cookie("survey_admin_session", actorCookie)
                .get("/api/admin/accounts/" + accountId + "/admins")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("username[0]", equalTo(actorUsername));
    }

    @Test
    void existingActiveAdminCanBeAddedToAnotherAccountWithoutPassword() throws Exception {
        String sharedUsername = "shared-" + UUID.randomUUID();
        UUID firstAccount = createAccountWithAdmin(sharedUsername);
        UUID secondAccount = createAccountWithAdmin("owner-" + UUID.randomUUID());
        String secondOwner = accountAdminUsername(secondAccount);
        String secondCookie = login(secondOwner);

        given()
                .cookie("survey_admin_session", secondCookie)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + sharedUsername + "\"}")
                .post("/api/admin/accounts/" + secondAccount + "/admins")
                .then().statusCode(201)
                .body("username", equalTo(sharedUsername));

        given()
                .cookie("survey_admin_session", login(sharedUsername))
                .get("/api/admin/accounts/" + firstAccount + "/surveys")
                .then().statusCode(200);

        given()
                .cookie("survey_admin_session", login(sharedUsername))
                .get("/api/admin/accounts/" + secondAccount + "/surveys")
                .then().statusCode(200);
    }

    @Test
    void lastAccountAdministratorCannotBeRemoved() throws Exception {
        String actorUsername = "solo-" + UUID.randomUUID();
        UUID accountId = createAccountWithAdmin(actorUsername);
        UUID actorUserId = userId(actorUsername);
        String actorCookie = login(actorUsername);

        given()
                .cookie("survey_admin_session", actorCookie)
                .delete("/api/admin/accounts/" + accountId + "/admins/" + actorUserId)
                .then().statusCode(409)
                .body("code", equalTo("LAST_ACCOUNT_ADMIN"));
    }

    @Test
    void administratorCannotManageAdminsInAnotherAccount() throws Exception {
        String usernameA = "tenant-a-" + UUID.randomUUID();
        UUID accountA = createAccountWithAdmin(usernameA);
        UUID accountB = createAccountWithAdmin("tenant-b-" + UUID.randomUUID());
        String cookieA = login(usernameA);

        given()
                .cookie("survey_admin_session", cookieA)
                .get("/api/admin/accounts/" + accountB + "/admins")
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", cookieA)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username":"forbidden-%s",
                          "initialPassword":"new-admin-password-123"
                        }
                        """.formatted(UUID.randomUUID()))
                .post("/api/admin/accounts/" + accountB + "/admins")
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", cookieA)
                .delete("/api/admin/accounts/" + accountB + "/admins/" + userId(accountAdminUsername(accountB)))
                .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_ACCESS_DENIED"));

        given()
                .cookie("survey_admin_session", cookieA)
                .get("/api/admin/accounts/" + accountA + "/admins")
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

    private UUID createAccountWithAdmin(String username) throws Exception {
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
                    "INSERT INTO survey_account (id, name) VALUES (?, ?)")) {
                ps.setObject(1, accountId);
                ps.setString(2, "Account " + username);
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

    private String accountAdminUsername(UUID accountId) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT u.username
                     FROM survey_account_admin m
                     JOIN admin_user u ON u.id = m.admin_user_id
                     WHERE m.survey_account_id = ?
                     ORDER BY m.created_at
                     LIMIT 1
                     """)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
