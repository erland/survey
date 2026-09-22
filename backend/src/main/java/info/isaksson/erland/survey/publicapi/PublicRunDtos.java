package info.isaksson.erland.survey.publicapi;

import info.isaksson.erland.survey.domain.SurveyRunStatus;
import java.time.Instant;

public final class PublicRunDtos {
    private PublicRunDtos() {}

    public record RunLookupResponse(
            String publicId,
            String joinCode,
            String title,
            SurveyRunStatus status,
            Instant opensAt,
            Instant closesAt,
            int questionCount
    ) {}
}
