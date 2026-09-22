package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ParticipantResponseApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200).extract().cookie("survey_admin_session");
    }

    private String[] openRun() {
        String cookie = login();
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                    {"title":"Autosave","status":"DRAFT","questions":[
                      {"type":"TEXT","text":"Kommentar?","required":false,"options":[]},
                      {"type":"SINGLE_CHOICE","text":"Val?","required":true,"options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]},
                      {"type":"SCALE","text":"Betyg?","required":false,"scaleMin":1,"scaleMax":5,"options":[]}
                    ]}
                    """)
                .post("/api/admin/surveys").then().statusCode(201).extract().path("id");
        String runId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON).body("{}")
                .post("/api/admin/surveys/{surveyId}/runs", surveyId).then().statusCode(201).extract().path("id");
        String publicId = given().cookie("survey_admin_session", cookie).post("/api/admin/runs/{runId}/open", runId)
                .then().statusCode(200).extract().path("publicId");
        String token = given().post("/api/public/runs/{publicId}/participants", publicId).then().statusCode(201).extract().path("participantToken");
        return new String[]{publicId, token};
    }

    @Test
    void answersCanBeAutosavedAndRestored() {
        String[] x = openRun();
        String publicId = x[0], token = x[1];
        String questionId = given().header("X-Participant-Token", token)
                .get("/api/public/runs/{publicId}/participants/current/survey", publicId)
                .then().statusCode(200).extract().path("questions[0].id");

        given().header("X-Participant-Token", token).contentType(ContentType.JSON)
                .body("{\"textValue\":\"Första svaret\",\"optionValues\":[]}")
                .put("/api/public/runs/{publicId}/participants/current/responses/{questionId}", publicId, questionId)
                .then().statusCode(200).body("textValue", equalTo("Första svaret"));

        given().header("X-Participant-Token", token)
                .get("/api/public/runs/{publicId}/participants/current/responses", publicId)
                .then().statusCode(200).body("answers", hasSize(1)).body("answers[0].textValue", equalTo("Första svaret"));
    }

    @Test
    void invalidChoiceIsRejected() {
        String[] x = openRun();
        String publicId = x[0], token = x[1];
        String questionId = given().header("X-Participant-Token", token)
                .get("/api/public/runs/{publicId}/participants/current/survey", publicId)
                .then().extract().path("questions[1].id");

        given().header("X-Participant-Token", token).contentType(ContentType.JSON)
                .body("{\"optionValues\":[\"does-not-exist\"]}")
                .put("/api/public/runs/{publicId}/participants/current/responses/{questionId}", publicId, questionId)
                .then().statusCode(400).body("code", equalTo("INVALID_ANSWER"));
    }
}
