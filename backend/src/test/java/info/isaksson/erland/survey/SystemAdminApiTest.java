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
    void systemAdminCreatesAccountAndFirstAdministratorWithSetupLink() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        String username = "account-admin-" + UUID.randomUUID() + "@example.test";
        String accountName = "Account " + UUID.randomUUID();

        var created = given()
                .cookie("survey_admin_session", systemCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountName":"%s",
                          "adminUsername":"%s"
                        }
                        """.formatted(accountName, username))
                .post("/api/system/accounts")
                .then().statusCode(201)
                .body("name", equalTo(accountName))
                .body("adminUsername", equalTo(username))
                .extract();

        String accountId = created.path("id");
        String setupPath = created.path("initialPasswordPath");
        assertNotNull(setupPath);
        assertTrue(setupPath.startsWith("/admin/set-password?token="));
        assertTrue(accountAndMembershipExist(UUID.fromString(accountId), username));

        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"12345678\"}")
                .post("/api/auth/login")
                .then().statusCode(401);

        consumeSetupLink(setupPath, "12345678");

        String adminCookie = login(username, "12345678");
        given()
                .cookie("survey_admin_session", adminCookie)
                .get("/api/admin/accounts/" + accountId + "/surveys")
                .then().statusCode(200);
    }

    @Test
    void systemAccountOverviewIncludesSurveyAndAdministratorCounts() {
        String systemCookie = login("test-admin", "test-password-123");

        given()
                .cookie("survey_admin_session", systemCookie)
                .get("/api/system/accounts")
                .then().statusCode(200)
                .body("[0].adminCount", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
                .body("[0].surveyCount", org.hamcrest.Matchers.greaterThanOrEqualTo(0));
    }

    @Test
    void setupLinkAcceptsEightCharacterPasswordAndIsSingleUse() {
        String systemCookie = login("test-admin", "test-password-123");
        String email = "eight-" + UUID.randomUUID() + "@example.test";
        String accountName = "Eight chars " + UUID.randomUUID();

        String setupPath = given()
                .cookie("survey_admin_session", systemCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountName":"%s",
                          "adminUsername":"%s"
                        }
                        """.formatted(accountName, email))
                .post("/api/system/accounts")
                .then().statusCode(201)
                .body("adminUsername", equalTo(email))
                .extract().path("initialPasswordPath");

        consumeSetupLink(setupPath, "12345678");
        login(email, "12345678");

        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + tokenFrom(setupPath) + "\",\"password\":\"abcdefgh\"}")
                .post("/api/auth/password-token/consume")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_PASSWORD_TOKEN"));
    }

    @Test
    void newAdministratorRequiresEmailButBootstrapNameRemainsValid() {
        String systemCookie = login("test-admin", "test-password-123");

        given()
                .cookie("survey_admin_session", systemCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountName":"Invalid email",
                          "adminUsername":"not-an-email",
                          "adminPassword":"12345678"
                        }
                        """)
                .post("/api/system/accounts")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_ADMIN_EMAIL"));
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
    void invalidEmailAccountCreationRollsBackNewAdministrator() throws Exception {
        String systemCookie = login("test-admin", "test-password-123");
        String username = "not-an-email";
        String accountName = "Rollback " + UUID.randomUUID();

        given()
                .cookie("survey_admin_session", systemCookie)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountName":"%s",
                          "adminUsername":"%s"
                        }
                        """.formatted(accountName, username))
                .post("/api/system/accounts")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_ADMIN_EMAIL"));

        assertFalse(adminExists(username));
        assertFalse(accountExists(accountName));
    }

    private void consumeSetupLink(String setupPath, String password) {
        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + tokenFrom(setupPath) + "\",\"password\":\"" + password + "\"}")
                .post("/api/auth/password-token/consume")
                .then().statusCode(204);
    }

    private String tokenFrom(String setupPath) {
        return setupPath.substring(setupPath.indexOf("token=") + "token=".length());
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
