package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class SurveyExportApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    @Test
    void exportsPortableVersionedSurveyDefinition() {
        String cookie = login();
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                {
                  "title":"Arkitekturworkshop",
                  "description":"Exporttest",
                  "status":"ACTIVE",
                  "questions":[
                    {"type":"YES_NO","text":"Är målet tydligt?","required":true,"options":[]},
                    {"type":"MULTIPLE_CHOICE","text":"Välj områden","required":false,
                     "options":[{"value":"a","label":"Område A"},{"value":"b","label":"Område B"}]},
                    {"type":"SCALE","text":"Hur tydligt?","required":true,"scaleMin":1,"scaleMax":5,
                     "scaleMinLabel":"Otydligt","scaleMaxLabel":"Tydligt","options":[]}
                  ]
                }
                """)
                .post("/api/admin/surveys").then().statusCode(201)
                .extract().path("id");

        given().cookie("survey_admin_session", cookie)
                .get("/api/admin/surveys/" + surveyId + "/export")
                .then().statusCode(200)
                .contentType(ContentType.JSON)
                .header("Content-Disposition", equalTo("attachment; filename=\"survey-definition-" + surveyId + ".json\""))
                .body("format", equalTo("survey-definition"))
                .body("version", equalTo(1))
                .body("survey.title", equalTo("Arkitekturworkshop"))
                .body("survey.description", equalTo("Exporttest"))
                .body("survey.questions", hasSize(3))
                .body("survey.questions[1].options", hasSize(2))
                .body("survey.questions[2].scaleMin", equalTo(1))
                .body("survey.questions[2].scaleMax", equalTo(5))
                .body("survey.id", nullValue())
                .body("survey.status", nullValue());
    }

    @Test
    void exportRequiresAdminAuthentication() {
        given().get("/api/admin/surveys/00000000-0000-0000-0000-000000000000/export")
                .then().statusCode(401)
                .body("code", equalTo("ADMIN_AUTH_REQUIRED"));
    }
}
