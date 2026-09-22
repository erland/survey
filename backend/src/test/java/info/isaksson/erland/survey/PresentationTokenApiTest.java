package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class PresentationTokenApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    @Test
    void readOnlyPresentationTokenWorksWithoutAdminCookieAndCanBeRevoked() {
        String cookie = login();
        String accountId = accountId(cookie);
        String surveyId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                    {"title":"Presentation","status":"DRAFT","questions":[
                      {"type":"YES_NO","text":"Redo?","required":false,"options":[]},
                      {"type":"TEXT","text":"Kommentar?","required":false,"options":[]}
                    ]}
                    """)
                .post("/api/admin/accounts/{accountId}/surveys", accountId).then().statusCode(201).extract().path("id");
        String runId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON).body("{}")
                .post("/api/admin/accounts/{accountId}/surveys/{surveyId}/runs", accountId, surveyId).then().statusCode(201).extract().path("id");
        given().cookie("survey_admin_session", cookie).post("/api/admin/accounts/{accountId}/runs/{runId}/open", accountId, runId)
                .then().statusCode(200);

        var created = given().cookie("survey_admin_session", cookie)
                .post("/api/admin/accounts/{accountId}/runs/{runId}/presentation-tokens", accountId, runId)
                .then().statusCode(200)
                .body("token", notNullValue())
                .body("presentationPath", notNullValue())
                .extract();
        String tokenId = created.path("id");
        String token = created.path("token");

        given().get("/api/presentation/{token}", token)
                .then().statusCode(200)
                .body("title", equalTo("Presentation"))
                .body("results[1].texts", empty());

        // The same token is intentionally not an admin credential.
        given().get("/api/admin/accounts/{accountId}/runs/{runId}", accountId, runId)
                .then().statusCode(401);

        given().cookie("survey_admin_session", cookie)
                .delete("/api/admin/accounts/{accountId}/runs/{runId}/presentation-tokens/{tokenId}", accountId, runId, tokenId)
                .then().statusCode(204);

        given().get("/api/presentation/{token}", token)
                .then().statusCode(401)
                .body("code", equalTo("PRESENTATION_TOKEN_INVALID"));
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
