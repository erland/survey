package info.isaksson.erland.survey;

import info.isaksson.erland.survey.domain.*;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SurveyRunDomainTest {
    @Inject SurveyRepository surveys;
    @Inject SurveyRunRepository runs;
    @Inject AgroalDataSource dataSource;

    @Test
    @TestTransaction
    void persistsRunWithQuestionSnapshotAndOptions() {
        Survey survey = createSurvey();

        SurveyRun run = new SurveyRun();
        run.id = UUID.randomUUID();
        run.survey = survey;
        run.createdBy = survey.ownerId;
        run.publicId = "pub-" + UUID.randomUUID();
        run.joinCode = "AB12C";
        run.title = "Workshop run";
        run.createdAt = Instant.now();

        SurveyRunQuestion q = new SurveyRunQuestion();
        q.id = UUID.randomUUID();
        q.run = run;
        q.sourceQuestionId = survey.questions.get(0).id;
        q.position = 0;
        q.type = QuestionType.SINGLE_CHOICE;
        q.text = "Choose";
        q.required = true;
        run.questions.add(q);

        SurveyRunOption o = new SurveyRunOption();
        o.id = UUID.randomUUID();
        o.question = q;
        o.sourceOptionId = survey.questions.get(0).options.get(0).id;
        o.position = 0;
        o.value = "A";
        o.label = "Option A";
        q.options.add(o);

        runs.persist(run);
        runs.flush();

        SurveyRun stored = runs.findById(run.id);
        assertNotNull(stored);
        assertEquals(SurveyRunStatus.DRAFT, stored.status);
        assertEquals(1, stored.questions.size());
        assertEquals("Choose", stored.questions.get(0).text);
        assertEquals(1, stored.questions.get(0).options.size());
    }

    @Test
    void migrationCreatesSurveyRunTables() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertTrue(tableExists(c, "survey_run"));
            assertTrue(tableExists(c, "survey_run_question"));
            assertTrue(tableExists(c, "survey_run_option"));
        }
    }

    private Survey createSurvey() {
        Survey survey = new Survey();
        survey.id = UUID.randomUUID();
        survey.ownerId = lookupTestAdminId();
        survey.title = "Source survey";
        survey.createdAt = Instant.now();
        survey.updatedAt = survey.createdAt;

        SurveyQuestion q = new SurveyQuestion();
        q.id = UUID.randomUUID();
        q.survey = survey;
        q.position = 0;
        q.type = QuestionType.SINGLE_CHOICE;
        q.text = "Choose";
        survey.questions.add(q);

        QuestionOption o = new QuestionOption();
        o.id = UUID.randomUUID();
        o.question = q;
        o.position = 0;
        o.value = "A";
        o.label = "Option A";
        q.options.add(o);

        surveys.persist(survey);
        surveys.flush();
        return survey;
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

    private boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, "public." + table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
