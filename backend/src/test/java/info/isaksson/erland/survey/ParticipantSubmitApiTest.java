package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class ParticipantSubmitApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200).extract().cookie("survey_admin_session");
    }

    private String[] openRun() {
        String cookie = login();
        String accountId = accountId(cookie);
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                    {"title":"Submit test","status":"DRAFT","questions":[
                      {"type":"TEXT","text":"Obligatorisk kommentar?","required":true,"options":[]},
                      {"type":"YES_NO","text":"Frivillig?","required":false,"options":[]}
                    ]}
                    """)
                .post("/api/admin/accounts/{accountId}/surveys", accountId).then().statusCode(201).extract().path("id");
        String runId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON).body("{}")
                .post("/api/admin/accounts/{accountId}/surveys/{surveyId}/runs", accountId, surveyId).then().statusCode(201).extract().path("id");
        String publicId = given().cookie("survey_admin_session", cookie).post("/api/admin/accounts/{accountId}/runs/{runId}/open", accountId, runId)
                .then().statusCode(200).extract().path("publicId");
        String token = given().post("/api/public/runs/{publicId}/participants", publicId)
                .then().statusCode(201).extract().path("participantToken");
        return new String[]{publicId, token};
    }

    @Test
    void requiredAnswerIsValidatedAndSubmitIsIdempotent() {
        String[] x = openRun();
        String publicId = x[0], token = x[1];

        given().header("X-Participant-Token", token)
                .post("/api/public/runs/{publicId}/participants/current/submit", publicId)
                .then().statusCode(400).body("code", equalTo("REQUIRED_ANSWERS_MISSING"));

        String questionId = given().header("X-Participant-Token", token)
                .get("/api/public/runs/{publicId}/participants/current/survey", publicId)
                .then().statusCode(200).extract().path("questions[0].id");

        given().header("X-Participant-Token", token).contentType(ContentType.JSON)
                .body("{\"textValue\":\"Klart\",\"optionValues\":[]}")
                .put("/api/public/runs/{publicId}/participants/current/responses/{questionId}", publicId, questionId)
                .then().statusCode(200);

        given().header("X-Participant-Token", token)
                .post("/api/public/runs/{publicId}/participants/current/submit", publicId)
                .then().statusCode(200).body("alreadySubmitted", equalTo(false));

        given().header("X-Participant-Token", token)
                .post("/api/public/runs/{publicId}/participants/current/submit", publicId)
                .then().statusCode(200).body("alreadySubmitted", equalTo(true));
    }

    @Test
    void responsesCannotBeChangedAfterSubmit() {
        String[] x = openRun();
        String publicId = x[0], token = x[1];
        String questionId = given().header("X-Participant-Token", token)
                .get("/api/public/runs/{publicId}/participants/current/survey", publicId)
                .then().extract().path("questions[0].id");
        given().header("X-Participant-Token", token).contentType(ContentType.JSON)
                .body("{\"textValue\":\"Klart\",\"optionValues\":[]}")
                .put("/api/public/runs/{publicId}/participants/current/responses/{questionId}", publicId, questionId).then().statusCode(200);
        given().header("X-Participant-Token", token)
                .post("/api/public/runs/{publicId}/participants/current/submit", publicId).then().statusCode(200);
        given().header("X-Participant-Token", token).contentType(ContentType.JSON)
                .body("{\"textValue\":\"Ändrat\",\"optionValues\":[]}")
                .put("/api/public/runs/{publicId}/participants/current/responses/{questionId}", publicId, questionId)
                .then().statusCode(409).body("code", equalTo("PARTICIPANT_SESSION_NOT_ACTIVE"));
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
