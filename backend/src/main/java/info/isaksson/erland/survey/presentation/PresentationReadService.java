package info.isaksson.erland.survey.presentation;

import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.result.ResultService;
import info.isaksson.erland.survey.run.SurveyRunLifecycleService;
import info.isaksson.erland.survey.runapi.RunSummaryService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class PresentationReadService {
    @Inject PresentationTokenService tokens;
    @Inject SurveyRunRepository runs;
    @Inject SurveyRunLifecycleService lifecycle;
    @Inject RunSummaryService summaries;
    @Inject ResultService results;

    @Transactional
    public PresentationDtos.PresentationView view(String rawToken) {
        UUID runId = tokens.authenticateRunId(rawToken);
        lifecycle.synchronize(runId, Instant.now());
        SurveyRun run = runs.findByIdOptional(runId)
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
        var safeResults = results.allForRun(run.id).stream()
                .map(r -> r.type() == info.isaksson.erland.survey.domain.QuestionType.TEXT
                        ? new info.isaksson.erland.survey.result.ResultDtos.QuestionResult(
                                r.questionId(), r.position(), r.type(), r.text(), r.responseCount(),
                                r.yesCount(), r.noCount(), r.choices(), r.scale(), java.util.List.of())
                        : r)
                .toList();
        return new PresentationDtos.PresentationView(
                run.id,
                run.title,
                run.status,
                summaries.getForRun(run.id, Instant.now()),
                safeResults
        );
    }
}
