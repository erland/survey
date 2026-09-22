package info.isaksson.erland.survey;

import info.isaksson.erland.survey.domain.QuestionOption;
import info.isaksson.erland.survey.domain.QuestionType;
import info.isaksson.erland.survey.domain.Survey;
import info.isaksson.erland.survey.domain.SurveyQuestion;
import info.isaksson.erland.survey.domain.SurveyRepository;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.run.SurveyRunSnapshotService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SurveyRunSnapshotServiceTest {
    @Inject SurveyRepository surveys;
    @Inject SurveyRunRepository runs;
    @Inject SurveyRunSnapshotService snapshots;
    @Inject AgroalDataSource dataSource;

    @Test
    @TestTransaction
    void createsIndependentSnapshotOfQuestionsAndOptions() {
        Survey source = createSurvey();

        SurveyRun run = snapshots.createDraft(source.ownerId, source.id, "Workshop 22 september");

        assertNotNull(run.id);
        assertEquals("Workshop 22 september", run.title);
        assertNotNull(run.publicId);
        assertEquals(32, run.publicId.length());
        assertNotNull(run.joinCode);
        assertEquals(6, run.joinCode.length());
        assertEquals(2, run.questions.size());
        assertEquals(source.questions.get(0).id, run.questions.get(0).sourceQuestionId);
        assertEquals("Vilket spår?", run.questions.get(0).text);
        assertEquals(2, run.questions.get(0).options.size());
        assertEquals("Spår A", run.questions.get(0).options.get(0).label);

        source.questions.get(0).text = "Ändrad mallfråga";
        source.questions.get(0).options.get(0).label = "Ändrat alternativ";
        surveys.flush();

        SurveyRun stored = runs.findById(run.id);
        assertEquals("Vilket spår?", stored.questions.get(0).text);
        assertEquals("Spår A", stored.questions.get(0).options.get(0).label);
    }

    @Test
    @TestTransaction
    void usesSurveyTitleWhenRunTitleIsBlank() {
        Survey source = createSurvey();
        SurveyRun run = snapshots.createDraft(source.ownerId, source.id, "   ");
        assertEquals("Arkitekturworkshop", run.title);
    }

    private Survey createSurvey() {
        Survey survey = new Survey();
        survey.id = UUID.randomUUID();
        survey.ownerId = lookupTestAdminId();
        survey.title = "Arkitekturworkshop";
        survey.createdAt = Instant.now();
        survey.updatedAt = survey.createdAt;

        SurveyQuestion choice = new SurveyQuestion();
        choice.id = UUID.randomUUID();
        choice.survey = survey;
        choice.position = 0;
        choice.type = QuestionType.SINGLE_CHOICE;
        choice.text = "Vilket spår?";
        choice.required = true;
        survey.questions.add(choice);

        QuestionOption a = new QuestionOption();
        a.id = UUID.randomUUID();
        a.question = choice;
        a.position = 0;
        a.value = "a";
        a.label = "Spår A";
        choice.options.add(a);

        QuestionOption b = new QuestionOption();
        b.id = UUID.randomUUID();
        b.question = choice;
        b.position = 1;
        b.value = "b";
        b.label = "Spår B";
        choice.options.add(b);

        SurveyQuestion scale = new SurveyQuestion();
        scale.id = UUID.randomUUID();
        scale.survey = survey;
        scale.position = 1;
        scale.type = QuestionType.SCALE;
        scale.text = "Hur tydligt är målet?";
        scale.required = false;
        scale.scaleMin = 1;
        scale.scaleMax = 5;
        scale.scaleMinLabel = "Otydligt";
        scale.scaleMaxLabel = "Tydligt";
        survey.questions.add(scale);

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
}
