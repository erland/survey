package info.isaksson.erland.survey;

import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.participant.ParticipantSessionService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ParticipantHeartbeatApiTest {
    @Inject SurveyRunRepository runs;
    @Inject ParticipantSessionService participantService;

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
                    {"title":"Heartbeat","status":"DRAFT","questions":[
                      {"type":"YES_NO","text":"Redo?","required":false,"options":[]}
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
    void heartbeatUpdatesLastActivity() {
        String publicId = openRun();
        String token = given().post("/api/public/runs/{publicId}/participants", publicId)
                .then().statusCode(201).extract().path("participantToken");

        given().header("X-Participant-Token", token)
                .post("/api/public/runs/{publicId}/participants/current/heartbeat", publicId)
                .then().statusCode(200)
                .body("lastActivityAt", notNullValue());
    }

    @Test
    void activeCountUsesNinetySecondWindow() {
        String publicId = openRun();
        String token = given().post("/api/public/runs/{publicId}/participants", publicId)
                .then().statusCode(201).extract().path("participantToken");
        UUID runId = runs.find("publicId", publicId).firstResult().id;

        Instant now = Instant.now();
        assertEquals(1, participantService.countActiveParticipants(runId, now));
        assertEquals(0, participantService.countActiveParticipants(runId, now.plusSeconds(91)));
    }

    @Test
    void submittedSessionCannotHeartbeat() {
        String publicId = openRun();
        String token = given().post("/api/public/runs/{publicId}/participants", publicId)
                .then().statusCode(201).extract().path("participantToken");
        given().header("X-Participant-Token", token)
                .post("/api/public/runs/{publicId}/participants/current/submit", publicId)
                .then().statusCode(200);

        given().header("X-Participant-Token", token)
                .post("/api/public/runs/{publicId}/participants/current/heartbeat", publicId)
                .then().statusCode(409);
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
