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
class SystemAdminApiTest {
    @Inject AgroalDataSource dataSource;

    @Test
    void systemAdminCreatesAccountAndFirstAdministratorAtomically() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        String username = "account-admin-" + UUID.randomUUID();
        String accountName = "Account " + UUID.randomUUID();

        String accountId = given()
                .cookie("survey_admin_session", systemCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountName":"%s",
                          "adminUsername":"%s",
                          "adminPassword":"first-admin-password-123"
                        }
                        """.formatted(accountName, username))
                .post("/api/system/accounts")
                .then().statusCode(201)
                .body("name", equalTo(accountName))
                .body("adminUsername", equalTo(username))
                .extract().path("id");

        assertTrue(accountAndMembershipExist(UUID.fromString(accountId), username));

        String adminCookie = login(username, "first-admin-password-123");
        given()
                .cookie("survey_admin_session", adminCookie)
                .get("/api/admin/accounts/" + accountId + "/surveys")
                .then().statusCode(200);
    }

    @Test
    void ordinaryAdministratorCannotUseSystemAdminApi() throws Exception {
        String username = "ordinary-admin-" + UUID.randomUUID();
        UUID accountId = createOrdinaryAdmin(username);
        String cookie = login(username, "test-password-123");

        given()
                .cookie("survey_admin_session", cookie)
                .get("/api/system/accounts")
                .then().statusCode(403)
                .body("code", equalTo("SYSTEM_ADMIN_REQUIRED"));

        given()
                .cookie("survey_admin_session", cookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountName":"Forbidden",
                          "adminUsername":"somebody",
                          "adminPassword":"password-password"
                        }
                        """)
                .post("/api/system/accounts")
                .then().statusCode(403)
                .body("code", equalTo("SYSTEM_ADMIN_REQUIRED"));

        assertNotNull(accountId);
    }

    @Test
    void failedAccountCreationRollsBackNewAdministrator() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        String username = "rollback-admin-" + UUID.randomUUID();
        String accountName = "Rollback " + UUID.randomUUID();

        given()
                .cookie("survey_admin_session", systemCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountName":"%s",
                          "adminUsername":"%s",
                          "adminPassword":"short"
                        }
                        """.formatted(accountName, username))
                .post("/api/system/accounts")
                .then().statusCode(400)
                .body("code", equalTo("ADMIN_PASSWORD_REQUIRED"));

        assertFalse(adminExists(username));
        assertFalse(accountExists(accountName));
    }

    private String login(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    private boolean accountAndMembershipExist(UUID accountId, String username) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT EXISTS (
                         SELECT 1
                         FROM survey_account a
                         JOIN survey_account_admin m ON m.survey_account_id = a.id
                         JOIN admin_user u ON u.id = m.admin_user_id
                         WHERE a.id = ? AND u.username = ? AND m.role = 'ADMIN'
                     )
                     """)) {
            ps.setObject(1, accountId);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private UUID createOrdinaryAdmin(String username) throws Exception {
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
                ps.setString(2, "Ordinary " + username);
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

    private boolean adminExists(String username) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM admin_user WHERE username = ?)")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private boolean accountExists(String name) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM survey_account WHERE name = ?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
