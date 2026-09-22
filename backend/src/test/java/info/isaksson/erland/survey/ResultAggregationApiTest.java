package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ResultAggregationApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200).extract().cookie("survey_admin_session");
    }

    @Test
    void adminCanReadAggregatedResultsForAllQuestionTypes() {
        String cookie = login();
        String accountId = accountId(cookie);
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                    {"title":"Resultat","status":"DRAFT","questions":[
                      {"type":"YES_NO","text":"Redo?","required":false,"options":[]},
                      {"type":"SINGLE_CHOICE","text":"Ett val?","required":false,"options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]},
                      {"type":"MULTIPLE_CHOICE","text":"Flera val?","required":false,"options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]},
                      {"type":"SCALE","text":"Betyg?","required":false,"scaleMin":1,"scaleMax":5,"options":[]},
                      {"type":"TEXT","text":"Kommentar?","required":false,"options":[]}
                    ]}
                    """)
                .post("/api/admin/accounts/{accountId}/surveys", accountId).then().statusCode(201).extract().path("id");
        String runId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON).body("{}")
                .post("/api/admin/accounts/{accountId}/surveys/{surveyId}/runs", accountId, surveyId).then().statusCode(201).extract().path("id");
        String publicId = given().cookie("survey_admin_session", cookie).post("/api/admin/accounts/{accountId}/runs/{runId}/open", accountId, runId)
                .then().statusCode(200).extract().path("publicId");

        String token1 = given().post("/api/public/runs/{publicId}/participants", publicId).then().statusCode(201).extract().path("participantToken");
        List<String> questions = given().header("X-Participant-Token", token1)
                .get("/api/public/runs/{publicId}/participants/current/survey", publicId)
                .then().statusCode(200).extract().path("questions.id");
        answer(publicId, token1, questions.get(0), "{\"booleanValue\":true}");
        answer(publicId, token1, questions.get(1), "{\"optionValues\":[\"a\"]}");
        answer(publicId, token1, questions.get(2), "{\"optionValues\":[\"a\",\"b\"]}");
        answer(publicId, token1, questions.get(3), "{\"numericValue\":4}");
        answer(publicId, token1, questions.get(4), "{\"textValue\":\"Bra workshop\"}");

        String token2 = given().post("/api/public/runs/{publicId}/participants", publicId).then().statusCode(201).extract().path("participantToken");
        answer(publicId, token2, questions.get(0), "{\"booleanValue\":false}");
        answer(publicId, token2, questions.get(1), "{\"optionValues\":[\"b\"]}");
        answer(publicId, token2, questions.get(2), "{\"optionValues\":[\"b\"]}");
        answer(publicId, token2, questions.get(3), "{\"numericValue\":5}");
        answer(publicId, token2, questions.get(4), "{\"textValue\":\"Tydligt\"}");

        given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts/{accountId}/runs/{runId}/results", accountId, runId)
                .then().statusCode(200)
                .body("$", hasSize(5))
                .body("[0].type", equalTo("YES_NO"))
                .body("[0].responseCount", equalTo(2))
                .body("[0].yesCount", equalTo(1))
                .body("[0].noCount", equalTo(1))
                .body("[1].choices[0].count", equalTo(1))
                .body("[1].choices[1].count", equalTo(1))
                .body("[2].choices[0].count", equalTo(1))
                .body("[2].choices[1].count", equalTo(2))
                .body("[3].scale[3].count", equalTo(1))
                .body("[3].scale[4].count", equalTo(1))
                .body("[4].texts", hasSize(2));

        given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts/{accountId}/runs/{runId}/results/{questionId}", accountId, runId, questions.get(0))
                .then().statusCode(200)
                .body("responseCount", equalTo(2));
    }

    private void answer(String publicId, String token, String questionId, String json) {
        given().header("X-Participant-Token", token).contentType(ContentType.JSON).body(json)
                .put("/api/public/runs/{publicId}/participants/current/responses/{questionId}", publicId, questionId)
                .then().statusCode(200);
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
