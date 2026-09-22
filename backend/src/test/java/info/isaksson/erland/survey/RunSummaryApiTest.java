package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class RunSummaryApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    @Test
    void summaryCountsStartedActiveAndSubmitted() {
        String cookie = login();
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                    {"title":"Live summary","status":"DRAFT","questions":[
                      {"type":"YES_NO","text":"Redo?","required":false,"options":[]}
                    ]}
                    """)
                .post("/api/admin/surveys").then().statusCode(201).extract().path("id");

        String runId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("{\"title\":\"Live workshop\"}")
                .post("/api/admin/surveys/{surveyId}/runs", surveyId)
                .then().statusCode(201).extract().path("id");
        String publicId = given().cookie("survey_admin_session", cookie)
                .post("/api/admin/runs/{runId}/open", runId)
                .then().statusCode(200).extract().path("publicId");

        String token1 = createParticipant(publicId);
        createParticipant(publicId);
        createParticipant(publicId);

        given().header("X-Participant-Token", token1)
                .post("/api/public/runs/{publicId}/participants/current/submit", publicId)
                .then().statusCode(200);

        given().cookie("survey_admin_session", cookie)
                .get("/api/admin/runs/{runId}/summary", runId)
                .then().statusCode(200)
                .body("started", equalTo(3))
                .body("active", equalTo(2))
                .body("submitted", equalTo(1));
    }

    private String createParticipant(String publicId) {
        return given().post("/api/public/runs/{publicId}/participants", publicId)
                .then().statusCode(201).extract().path("participantToken");
    }
}
