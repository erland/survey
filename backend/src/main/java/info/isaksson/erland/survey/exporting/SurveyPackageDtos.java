package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.domain.SurveyRunStatus;

import java.time.Instant;
import java.util.List;

public final class SurveyPackageDtos {
    private SurveyPackageDtos() {}

    public record PackageManifest(
            String format,
            int version,
            Instant createdAt,
            String applicationVersion,
            List<String> files
    ) {}

    public record RunDocument(
            String format,
            int version,
            RunInfo run
    ) {}

    public record RunInfo(
            String title,
            SurveyRunStatus status,
            String publicId,
            String joinCode,
            Instant createdAt,
            Instant openedAt,
            Instant closedAt,
            Instant opensAt,
            Instant closesAt
    ) {}
}
