package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.domain.*;
import io.quarkus.test.junit.QuarkusTest;
import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RunResultExportServiceTest {
    @Inject RunResultExportService service;
    @Inject AgroalDataSource dataSource;

    @Test
    @Transactional
    void exportsPortableAnonymousResultDocument() {
        UUID ownerId = lookupTestAdminId();
        Survey survey = new Survey();
        survey.id = UUID.randomUUID(); survey.surveyAccountId = lookupTestAccountId(ownerId); survey.createdByAdminUserId = ownerId; survey.title = "Mall"; survey.status = SurveyStatus.DRAFT;
        survey.createdAt = Instant.now(); survey.updatedAt = Instant.now(); survey.persist();

        SurveyRun run = new SurveyRun();
        run.id = UUID.randomUUID(); run.survey = survey; run.createdBy = ownerId; run.publicId = "public-x"; run.joinCode = "ABC123";
        run.title = "Workshop"; run.status = SurveyRunStatus.OPEN; run.createdAt = Instant.now(); run.openedAt = Instant.now(); run.persist();

        SurveyRunQuestion q = new SurveyRunQuestion();
        q.id = UUID.randomUUID(); q.run = run; q.position = 0; q.type = QuestionType.TEXT; q.text = "Kommentar"; q.required = false; q.persist();
        run.questions.add(q);

        ParticipantSession p = new ParticipantSession();
        p.id = UUID.randomUUID(); p.run = run; p.clientTokenHash = "hash"; p.status = ParticipantSessionStatus.SUBMITTED;
        p.startedAt = Instant.now(); p.lastActivityAt = Instant.now(); p.submittedAt = Instant.now(); p.persist();

        Response response = new Response();
        response.id = UUID.randomUUID(); response.participantSession = p; response.question = q; response.updatedAt = Instant.now(); response.persist();
        ResponseValue value = new ResponseValue();
        value.id = UUID.randomUUID(); value.response = response; value.textValue = "Svar"; value.persist();
        response.values.add(value);

        var exported = service.export(ownerId, survey.surveyAccountId, run.id);

        assertEquals("survey-result-export", exported.format());
        assertEquals(1, exported.version());
        assertEquals("Q1", exported.questions().getFirst().key());
        assertEquals("anon-001", exported.responses().getFirst().participantId());
        assertEquals("Svar", exported.responses().getFirst().answers().getFirst().textValue());
        assertFalse(exported.responses().getFirst().participantId().contains(p.id.toString()));
    }
    private UUID lookupTestAccountId(UUID adminId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT survey_account_id FROM survey_account_admin WHERE admin_user_id = ? ORDER BY created_at LIMIT 1")) {
            statement.setObject(1, adminId);
            try (var rs = statement.executeQuery()) {
                assertTrue(rs.next(), "test admin must belong to a survey account");
                return rs.getObject(1, UUID.class);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID lookupTestAdminId() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT id FROM admin_user WHERE username = 'test-admin'");
             var rs = statement.executeQuery()) {
            assertTrue(rs.next(), "test admin must be bootstrapped");
            return rs.getObject(1, UUID.class);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
