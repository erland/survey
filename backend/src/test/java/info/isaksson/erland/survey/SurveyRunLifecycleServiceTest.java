package info.isaksson.erland.survey;

import info.isaksson.erland.survey.domain.*;
import info.isaksson.erland.survey.run.SurveyRunLifecycleService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SurveyRunLifecycleServiceTest {
    @Inject SurveyRepository surveys;
    @Inject SurveyRunRepository runs;
    @Inject SurveyRunLifecycleService lifecycle;
    @Inject AgroalDataSource dataSource;

    @Test
    @TestTransaction
    void scheduledRunOpensAndClosesAccordingToTimeWindow() {
        SurveyRun run = createDraftRun();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant opens = base.plus(10, ChronoUnit.MINUTES);
        Instant closes = base.plus(20, ChronoUnit.MINUTES);

        lifecycle.schedule(run.createdBy, run.id, opens, closes);
        assertEquals(SurveyRunStatus.SCHEDULED, run.status);
        assertFalse(lifecycle.acceptsNewParticipants(run, base));

        lifecycle.synchronize(run.id, opens.plusSeconds(1));
        assertEquals(SurveyRunStatus.OPEN, run.status);
        assertTrue(lifecycle.acceptsNewParticipants(run, opens.plusSeconds(1)));

        lifecycle.synchronize(run.id, closes);
        assertEquals(SurveyRunStatus.CLOSED, run.status);
        assertFalse(lifecycle.acceptsNewParticipants(run, closes));
        assertNotNull(run.closedAt);
    }

    @Test
    @TestTransaction
    void openNowAcceptsParticipantsUntilExplicitClose() {
        SurveyRun run = createDraftRun();

        lifecycle.openNow(run.createdBy, run.id);
        assertEquals(SurveyRunStatus.OPEN, run.status);
        assertNotNull(run.openedAt);
        assertTrue(lifecycle.acceptsNewParticipants(run, Instant.now()));

        lifecycle.close(run.createdBy, run.id);
        assertEquals(SurveyRunStatus.CLOSED, run.status);
        assertNotNull(run.closedAt);
        assertFalse(lifecycle.acceptsNewParticipants(run, Instant.now()));
    }

    @Test
    @TestTransaction
    void invalidScheduleWindowIsRejected() {
        SurveyRun run = createDraftRun();
        Instant opens = Instant.now().plus(1, ChronoUnit.HOURS);
        var ex = assertThrows(RuntimeException.class,
                () -> lifecycle.schedule(run.createdBy, run.id, opens, opens));
        assertTrue(ex.getMessage().contains("Sluttiden") || ex.getClass().getSimpleName().contains("ApiException"));
    }

    private SurveyRun createDraftRun() {
        Survey survey = new Survey();
        survey.id = UUID.randomUUID();
        survey.ownerId = lookupTestAdminId();
        survey.title = "Lifecycle survey";
        survey.createdAt = Instant.now();
        survey.updatedAt = survey.createdAt;
        surveys.persist(survey);

        SurveyRun run = new SurveyRun();
        run.id = UUID.randomUUID();
        run.survey = survey;
        run.createdBy = survey.ownerId;
        run.publicId = UUID.randomUUID().toString().replace("-", "");
        run.joinCode = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        run.title = "Lifecycle run";
        run.status = SurveyRunStatus.DRAFT;
        run.createdAt = Instant.now();
        runs.persist(run);
        runs.flush();
        return run;
    }

    private UUID lookupTestAdminId() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM admin_user WHERE username='test-admin'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "test admin must be bootstrapped");
            return rs.getObject(1, UUID.class);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
