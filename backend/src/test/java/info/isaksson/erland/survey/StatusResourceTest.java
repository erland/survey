package info.isaksson.erland.survey;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class StatusResourceTest {

    @Test
    void statusEndpointReturnsOk() {
        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("service", equalTo("survey-service"));
    }
}
