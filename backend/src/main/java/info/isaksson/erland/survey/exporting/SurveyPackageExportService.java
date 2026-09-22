package info.isaksson.erland.survey.exporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static info.isaksson.erland.survey.exporting.RunResultExportDtos.*;
import static info.isaksson.erland.survey.exporting.SurveyExportDtos.*;
import static info.isaksson.erland.survey.exporting.SurveyPackageDtos.*;

@ApplicationScoped
public class SurveyPackageExportService {
    public static final String FORMAT = "survey-package";
    public static final int VERSION = 1;

    @Inject RunResultExportService resultExportService;
    @Inject RunResultCsvExportService csvExportService;
    @Inject SurveyRunRepository runRepository;
    @Inject AccountAccessService accountAccess;
    @Inject ObjectMapper objectMapper;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    String applicationVersion;

    public byte[] export(UUID userId, UUID accountId, UUID runId) {
        ResultExportDocument resultDocument = resultExportService.export(userId, accountId, runId);
        SurveyRun run = accountAccess.requireRun(userId, accountId, runId);

        SurveyDefinitionExport surveyDocument = snapshotDefinition(resultDocument);
        RunDocument runDocument = new RunDocument(
                "survey-run",
                1,
                new RunInfo(
                        run.title,
                        run.status,
                        run.publicId,
                        run.joinCode,
                        run.createdAt,
                        run.openedAt,
                        run.closedAt,
                        run.opensAt,
                        run.closesAt
                )
        );

        List<String> files = List.of(
                "manifest.json",
                "survey.json",
                "run.json",
                "responses.json",
                "responses.csv"
        );
        PackageManifest manifest = new PackageManifest(FORMAT, VERSION, Instant.now(), applicationVersion, files);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            addJson(zip, "manifest.json", manifest);
            addJson(zip, "survey.json", surveyDocument);
            addJson(zip, "run.json", runDocument);
            addJson(zip, "responses.json", resultDocument);
            addBytes(zip, "responses.csv", csvExportService.export(userId, accountId, runId));
            zip.finish();
            return output.toByteArray();
        } catch (IOException e) {
            throw new ApiException(500, "EXPORT_PACKAGE_FAILED", "Exportpaketet kunde inte skapas.");
        }
    }

    private SurveyDefinitionExport snapshotDefinition(ResultExportDocument resultDocument) {
        List<ExportQuestion> questions = resultDocument.questions().stream()
                .map(q -> new ExportQuestion(
                        q.type(),
                        q.text(),
                        q.required(),
                        q.scaleMin(),
                        q.scaleMax(),
                        q.scaleMinLabel(),
                        q.scaleMaxLabel(),
                        q.options().stream()
                                .map(o -> new ExportOption(o.value(), o.label()))
                                .toList()
                ))
                .toList();
        return new SurveyDefinitionExport(
                SurveyExportService.FORMAT,
                SurveyExportService.VERSION,
                Instant.now(),
                new SurveyDefinition(resultDocument.run().title(), null, questions)
        );
    }

    private void addJson(ZipOutputStream zip, String name, Object value) throws IOException {
        addBytes(zip, name, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private void addBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
