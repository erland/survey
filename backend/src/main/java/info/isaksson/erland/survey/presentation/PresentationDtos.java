package info.isaksson.erland.survey.presentation;

import info.isaksson.erland.survey.domain.SurveyRunStatus;
import info.isaksson.erland.survey.result.ResultDtos.QuestionResult;
import info.isaksson.erland.survey.runapi.RunSummaryDtos.LiveSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PresentationDtos {
    private PresentationDtos() {}

    public record CreatedPresentationToken(
            UUID id,
            String token,
            Instant expiresAt,
            String presentationPath
    ) {}

    public record PresentationView(
            UUID runId,
            String title,
            SurveyRunStatus status,
            LiveSummary summary,
            List<QuestionResult> results
    ) {}
}
