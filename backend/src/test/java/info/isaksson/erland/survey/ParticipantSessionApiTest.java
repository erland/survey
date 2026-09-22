package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ParticipantSessionApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    private String openRun() {
        String cookie = login();
        String accountId = accountId(cookie);
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                    {"title":"Participants","status":"DRAFT","questions":[
                      {"type":"YES_NO","text":"Redo?","required":true,"options":[]}
                    ]}
                    """)
                .post("/api/admin/accounts/{accountId}/surveys", accountId).then().statusCode(201).extract().path("id");
        String runId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("{}")
                .post("/api/admin/accounts/{accountId}/surveys/{surveyId}/runs", accountId, surveyId)
                .then().statusCode(201).extract().path("id");
        return given().cookie("survey_admin_session", cookie)
                .post("/api/admin/accounts/{accountId}/runs/{runId}/open", accountId, runId)
                .then().statusCode(200).extract().path("publicId");
    }

    @Test
    void participantSessionCanBeCreatedAndResumed() {
        String publicId = openRun();
        String token = given().post("/api/public/runs/{publicId}/participants", publicId)
                .then().statusCode(201)
                .body("status", equalTo("ACTIVE"))
                .body("participantToken", notNullValue())
                .body("resumed", equalTo(false))
                .extract().path("participantToken");

        given().header("X-Participant-Token", token)
                .get("/api/public/runs/{publicId}/participants/current", publicId)
                .then().statusCode(200)
                .body("status", equalTo("ACTIVE"))
                .body("resumed", equalTo(true));
    }

    @Test
    void participantCanReadSnapshotQuestionsWithValidSession() {
        String publicId = openRun();
        String token = given().post("/api/public/runs/{publicId}/participants", publicId)
                .then().statusCode(201).extract().path("participantToken");

        given().header("X-Participant-Token", token)
                .get("/api/public/runs/{publicId}/participants/current/survey", publicId)
                .then().statusCode(200)
                .body("publicId", equalTo(publicId))
                .body("title", equalTo("Participants"))
                .body("questions.size()", equalTo(1))
                .body("questions[0].type", equalTo("YES_NO"))
                .body("questions[0].text", equalTo("Redo?"))
                .body("questions[0].required", equalTo(true));
    }

    @Test
    void snapshotQuestionsRequireValidParticipantToken() {
        String publicId = openRun();
        given().header("X-Participant-Token", "invalid")
                .get("/api/public/runs/{publicId}/participants/current/survey", publicId)
                .then().statusCode(401)
                .body("code", equalTo("PARTICIPANT_SESSION_INVALID"));
    }

    @Test
    void invalidTokenCannotResumeSession() {
        String publicId = openRun();
        given().header("X-Participant-Token", "not-a-real-token")
                .get("/api/public/runs/{publicId}/participants/current", publicId)
                .then().statusCode(401)
                .body("code", equalTo("PARTICIPANT_SESSION_INVALID"));
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
