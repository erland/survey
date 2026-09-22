package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class SurveyRunAdminApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    @Test
    void adminCanCreateOpenListAndShareRun() {
        String cookie = login();
        String accountId = accountId(cookie);
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                    {"title":"Delningsenkät","status":"DRAFT","questions":[
                      {"type":"YES_NO","text":"Redo?","required":true,"options":[]}
                    ]}
                    """)
                .post("/api/admin/accounts/{accountId}/surveys", accountId)
                .then().statusCode(201).extract().path("id");

        String runId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("{\"title\":\"Workshop idag\"}")
                .post("/api/admin/accounts/{accountId}/surveys/{surveyId}/runs", accountId, surveyId)
                .then().statusCode(201)
                .body("status", equalTo("DRAFT"))
                .extract().path("id");

        String publicId = given().cookie("survey_admin_session", cookie)
                .post("/api/admin/accounts/{accountId}/runs/{runId}/open", accountId, runId)
                .then().statusCode(200)
                .body("status", equalTo("OPEN"))
                .extract().path("publicId");

        given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts/{accountId}/surveys/{surveyId}/runs", accountId, surveyId)
                .then().statusCode(200).body("$", hasSize(1));

        given().get("/api/public/runs/{publicId}", publicId)
                .then().statusCode(200).body("title", equalTo("Workshop idag"));
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
