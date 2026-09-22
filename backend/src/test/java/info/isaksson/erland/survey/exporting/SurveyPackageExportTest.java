package info.isaksson.erland.survey.exporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.isaksson.erland.survey.domain.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.agroal.api.AgroalDataSource;
import java.sql.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SurveyPackageExportTest {
    @Inject SurveyPackageExportService service;
    @Inject ObjectMapper mapper;
    @Inject AgroalDataSource dataSource;

    @Test
    @Transactional
    void packageContainsDocumentedFilesAndSnapshotSurvey() throws Exception {
        UUID ownerId = lookupTestAdminId();
        Survey survey = new Survey();
        survey.id = UUID.randomUUID();
        survey.surveyAccountId = lookupTestAccountId(ownerId);
        survey.createdByAdminUserId = ownerId;
        survey.title = "Mall efter ändring";
        survey.status = SurveyStatus.DRAFT;
        survey.createdAt = Instant.now();
        survey.updatedAt = survey.createdAt;
        survey.persist();

        SurveyRun run = new SurveyRun();
        run.id = UUID.randomUUID();
        run.survey = survey;
        run.createdBy = ownerId;
        run.publicId = UUID.randomUUID().toString();
        run.joinCode = "PKG123";
        run.title = "Workshop snapshot";
        run.status = SurveyRunStatus.CLOSED;
        run.createdAt = Instant.now();
        run.persist();

        SurveyRunQuestion q = new SurveyRunQuestion();
        q.id = UUID.randomUUID();
        q.run = run;
        q.position = 1;
        q.type = QuestionType.YES_NO;
        q.text = "Snapshotfråga";
        q.required = true;
        q.persist();
        run.questions.add(q);

        byte[] archive = service.export(ownerId, survey.surveyAccountId, run.id);
        Map<String, byte[]> files = unzip(archive);

        assertEquals(5, files.size());
        assertTrue(files.containsKey("manifest.json"));
        assertTrue(files.containsKey("survey.json"));
        assertTrue(files.containsKey("run.json"));
        assertTrue(files.containsKey("responses.json"));
        assertTrue(files.containsKey("responses.csv"));

        JsonNode manifest = mapper.readTree(files.get("manifest.json"));
        assertEquals("survey-package", manifest.get("format").asText());
        assertEquals(1, manifest.get("version").asInt());

        JsonNode exportedSurvey = mapper.readTree(files.get("survey.json"));
        assertEquals("Workshop snapshot", exportedSurvey.at("/survey/title").asText());
        assertEquals("Snapshotfråga", exportedSurvey.at("/survey/questions/0/text").asText());
        assertFalse(new String(files.get("responses.json"), StandardCharsets.UTF_8).contains("clientTokenHash"));
    }

    private UUID lookupTestAccountId(UUID adminId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT survey_account_id FROM survey_account_admin WHERE admin_user_id = ? ORDER BY created_at LIMIT 1")) {
            ps.setObject(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "test admin must belong to a survey account");
                return rs.getObject(1, UUID.class);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
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

    private Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                result.put(entry.getName(), zip.readAllBytes());
            }
        }
        return result;
    }
}
