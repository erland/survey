package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class SurveyImportApiTest {
    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .post("/api/auth/login").then().statusCode(200)
                .extract().cookie("survey_admin_session");
    }

    @Test
    void exportImportRoundtripCreatesIndependentDraft() {
        String cookie = login();
        String accountId = accountId(cookie);
        String sourceId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                {
                  "title":"Roundtrip",
                  "description":"Bevaras",
                  "status":"ACTIVE",
                  "questions":[
                    {"type":"TEXT","text":"Kommentar","required":false,"options":[]},
                    {"type":"MULTIPLE_CHOICE","text":"Välj","required":true,
                     "options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]},
                    {"type":"SCALE","text":"Betyg","required":true,"scaleMin":1,"scaleMax":7,
                     "scaleMinLabel":"Låg","scaleMaxLabel":"Hög","options":[]}
                  ]
                }
                """)
                .post("/api/admin/accounts/{accountId}/surveys", accountId).then().statusCode(201).extract().path("id");

        String exported = given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts/" + accountId + "/surveys/" + sourceId + "/export")
                .then().statusCode(200).extract().asString();

        String importedId = given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body(exported)
                .post("/api/admin/accounts/{accountId}/surveys/import", accountId)
                .then().statusCode(201)
                .body("title", equalTo("Roundtrip"))
                .body("description", equalTo("Bevaras"))
                .body("status", equalTo("DRAFT"))
                .body("questions", hasSize(3))
                .body("questions[1].options", hasSize(2))
                .body("questions[2].scaleMin", equalTo(1))
                .body("questions[2].scaleMax", equalTo(7))
                .extract().path("id");

        org.junit.jupiter.api.Assertions.assertNotEquals(sourceId, importedId);
    }

    @Test
    void rejectsUnsupportedFormatAndVersion() {
        String cookie = login();
        String accountId = accountId(cookie);
        given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("{\"format\":\"other\",\"version\":1,\"survey\":{\"title\":\"X\",\"questions\":[]}}")
                .post("/api/admin/surveys/import")
                .then().statusCode(400).body("code", equalTo("UNSUPPORTED_IMPORT_FORMAT"));

        given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("{\"format\":\"survey-definition\",\"version\":99,\"survey\":{\"title\":\"X\",\"questions\":[]}}")
                .post("/api/admin/surveys/import")
                .then().statusCode(400).body("code", equalTo("UNSUPPORTED_IMPORT_VERSION"));
    }

    @Test
    void invalidSurveyIsRejectedWithoutCreatingPartialSurvey() {
        String cookie = login();
        String accountId = accountId(cookie);
        int before = given().cookie("survey_admin_session", cookie).get("/api/admin/accounts/{accountId}/surveys", accountId)
                .then().statusCode(200).extract().jsonPath().getList("$").size();

        given().cookie("survey_admin_session", cookie).contentType(ContentType.JSON)
                .body("""
                {"format":"survey-definition","version":1,"survey":{"title":"Fel","questions":[
                  {"type":"SINGLE_CHOICE","text":"Välj","required":true,
                   "options":[{"value":"a","label":"Bara ett"}]}
                ]}}
                """)
                .post("/api/admin/surveys/import")
                .then().statusCode(400).body("code", equalTo("INVALID_QUESTION"));

        int after = given().cookie("survey_admin_session", cookie).get("/api/admin/surveys")
                .then().statusCode(200).extract().jsonPath().getList("$").size();
        org.junit.jupiter.api.Assertions.assertEquals(before, after);
    }

    @Test
    void importRequiresAdminAuthentication() {
        given().contentType(ContentType.JSON)
                .body("{\"format\":\"survey-definition\",\"version\":1,\"survey\":{\"title\":\"X\",\"questions\":[]}}")
                .post("/api/admin/accounts/00000000-0000-0000-0000-000000000000/surveys/import")
                .then().statusCode(401).body("code", equalTo("ADMIN_AUTH_REQUIRED"));
    }
    private String accountId(String cookie) {
        return given().cookie("survey_admin_session", cookie)
                .get("/api/admin/accounts")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

}
