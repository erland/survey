package info.isaksson.erland.survey.runapi;

import info.isaksson.erland.survey.domain.SurveyRunStatus;

import java.time.Instant;
import java.util.UUID;

public final class SurveyRunDtos {
    private SurveyRunDtos() {}

    public record CreateRunRequest(String title) {}

    public record RunView(
            UUID id,
            UUID surveyId,
            String publicId,
            String joinCode,
            String title,
            SurveyRunStatus status,
            Instant opensAt,
            Instant closesAt,
            Instant createdAt,
            Instant openedAt,
            Instant closedAt
    ) {}
}
