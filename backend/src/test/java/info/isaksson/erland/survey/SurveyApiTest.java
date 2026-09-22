package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class SurveyApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    @Test
    void surveyCrudAndCopyWork() {
        String cookie = login();
        String accountId = accountId(cookie);
        String body = """
                {
                  "title":"Workshop",
                  "description":"Första enkäten",
                  "status":"DRAFT",
                  "questions":[
                    {"type":"YES_NO","text":"Är målet tydligt?","required":true,"options":[]},
                    {"type":"SINGLE_CHOICE","text":"Välj spår","required":false,
                     "options":[{"value":"a","label":"Spår A"},{"value":"b","label":"Spår B"}]},
                    {"type":"SCALE","text":"Hur tydligt?","required":true,"scaleMin":1,"scaleMax":5,"options":[]}
                  ]
                }
                """;

        String id = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON).body(body)
                .post("/api/admin/accounts/{accountId}/surveys", accountId).then().statusCode(201)
                .body("title", equalTo("Workshop"))
                .body("questions", hasSize(3))
                .body("questions[1].options", hasSize(2))
                .extract().path("id");

        given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts/" + accountId + "/surveys/" + id).then().statusCode(200)
                .body("questions[2].scaleMin", equalTo(1))
                .body("questions[2].scaleMax", equalTo(5));

        given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts/{accountId}/surveys", accountId).then().statusCode(200)
                .body("id", hasItem(id));

        String copyId = given().cookie("survey_admin_session", cookie)
                .post("/api/admin/accounts/" + accountId + "/surveys/" + id + "/copy").then().statusCode(201)
                .body("title", equalTo("Workshop (kopia)"))
                .body("questions", hasSize(3))
                .extract().path("id");

        given().cookie("survey_admin_session", cookie).delete("/api/admin/accounts/" + accountId + "/surveys/" + id)
                .then().statusCode(204);
        given().cookie("survey_admin_session", cookie).get("/api/admin/accounts/" + accountId + "/surveys/" + id)
                .then().statusCode(404).body("code", equalTo("SURVEY_NOT_FOUND"));
        given().cookie("survey_admin_session", cookie).get("/api/admin/accounts/" + accountId + "/surveys/" + copyId)
                .then().statusCode(200);
    }

    @Test
    void validationRejectsInvalidChoiceQuestion() {
        String cookie = login();
        String accountId = accountId(cookie);
        given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                {"title":"Fel","questions":[{"type":"SINGLE_CHOICE","text":"Välj","required":true,
                "options":[{"value":"a","label":"Bara ett"}]}]}
                """)
                .post("/api/admin/accounts/{accountId}/surveys", accountId)
                .then().statusCode(400).body("code", equalTo("INVALID_QUESTION"));
    }

    @Test
    void surveysRequireAdminAuthentication() {
        given().get("/api/admin/accounts/00000000-0000-0000-0000-000000000000/surveys").then().statusCode(401)
                .body("code", equalTo("ADMIN_AUTH_REQUIRED"));
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
