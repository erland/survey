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
class SurveyDomainTest {
    @Inject SurveyRepository surveys;
    @Inject AgroalDataSource dataSource;

    @Test
    @TestTransaction
    void persistsSurveyWithQuestionAndOptions() {
        UUID ownerId = lookupTestAdminId();
        Survey survey = new Survey();
        survey.id = UUID.randomUUID();
        survey.ownerId = ownerId;
        survey.title = "Workshop";
        survey.createdAt = Instant.now();
        survey.updatedAt = survey.createdAt;

        SurveyQuestion q = new SurveyQuestion();
        q.id = UUID.randomUUID();
        q.survey = survey;
        q.position = 0;
        q.type = QuestionType.SINGLE_CHOICE;
        q.text = "Choose";
        survey.questions.add(q);

        QuestionOption a = new QuestionOption();
        a.id = UUID.randomUUID(); a.question = q; a.position = 0; a.value = "A"; a.label = "Option A";
        q.options.add(a);

        surveys.persist(survey);
        surveys.flush();
        assertNotNull(surveys.findById(survey.id));
        assertEquals(1, surveys.findById(survey.id).questions.size());
    }

    @Test
    void migrationCreatesSurveyTablesAndConstraints() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertTrue(tableExists(c, "survey"));
            assertTrue(tableExists(c, "survey_question"));
            assertTrue(tableExists(c, "question_option"));
        }
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
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }
}
