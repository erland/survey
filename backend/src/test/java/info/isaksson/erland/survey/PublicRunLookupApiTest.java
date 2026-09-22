package info.isaksson.erland.survey;

import info.isaksson.erland.survey.domain.*;
import info.isaksson.erland.survey.run.SurveyRunLifecycleService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class PublicRunLookupApiTest {
    @Inject SurveyRepository surveys;
    @Inject SurveyRunRepository runs;
    @Inject SurveyRunLifecycleService lifecycle;
    @Inject AgroalDataSource dataSource;

    @Test
    void openRunCanBeResolvedByPublicIdAndCaseInsensitiveJoinCode() {
        SurveyRun run = createRun("ABC234", SurveyRunStatus.DRAFT, null, null);
        QuarkusTransaction.requiringNew().run(() -> lifecycle.openNow(run.createdBy, run.id));

        given()
                .when().get("/api/public/runs/{publicId}", run.publicId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("publicId", equalTo(run.publicId))
                .body("joinCode", equalTo("ABC234"))
                .body("title", equalTo("Public lookup run"));

        given()
                .when().get("/api/public/runs/join/{joinCode}", "abc234")
                .then()
                .statusCode(200)
                .body("publicId", equalTo(run.publicId));
    }

    @Test
    void unknownJoinCodeReturnsNotFound() {
        given()
                .when().get("/api/public/runs/join/{joinCode}", "ZZZ999")
                .then()
                .statusCode(404)
                .body("code", equalTo("RUN_NOT_FOUND"));
    }

    @Test
    void draftAndFutureScheduledRunsAreNotOpen() {
        SurveyRun draft = createRun("DRF234", SurveyRunStatus.DRAFT, null, null);
        given().when().get("/api/public/runs/{publicId}", draft.publicId)
                .then().statusCode(409).body("code", equalTo("RUN_NOT_OPEN"));

        SurveyRun scheduled = createRun(
                "SCH234",
                SurveyRunStatus.SCHEDULED,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS));
        given().when().get("/api/public/runs/join/{joinCode}", scheduled.joinCode)
                .then().statusCode(409).body("code", equalTo("RUN_NOT_OPEN"));
    }

    @Test
    void closedRunReturnsGone() {
        SurveyRun closed = createRun("CLS234", SurveyRunStatus.CLOSED, null, null);
        QuarkusTransaction.requiringNew().run(() -> {
            SurveyRun stored = runs.findById(closed.id);
            stored.closedAt = Instant.now();
        });

        given().when().get("/api/public/runs/{publicId}", closed.publicId)
                .then().statusCode(410).body("code", equalTo("RUN_CLOSED"));
    }

    private SurveyRun createRun(String joinCode, SurveyRunStatus status, Instant opensAt, Instant closesAt) {
        UUID ownerId = lookupTestAdminId();
        return QuarkusTransaction.requiringNew().call(() -> {
            Survey survey = new Survey();
            survey.id = UUID.randomUUID();
            survey.ownerId = ownerId;
            survey.title = "Public lookup survey";
            survey.createdAt = Instant.now();
            survey.updatedAt = survey.createdAt;
            surveys.persist(survey);

            SurveyRun run = new SurveyRun();
            run.id = UUID.randomUUID();
            run.survey = survey;
            run.createdBy = survey.ownerId;
            run.publicId = UUID.randomUUID().toString().replace("-", "");
            run.joinCode = joinCode;
            run.title = "Public lookup run";
            run.status = status;
            run.opensAt = opensAt;
            run.closesAt = closesAt;
            run.createdAt = Instant.now();
            runs.persist(run);
            runs.flush();
            return run;
        });
    }

    private UUID lookupTestAdminId() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM admin_user WHERE username='test-admin'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("test admin must be bootstrapped");
            }
            return rs.getObject(1, UUID.class);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
