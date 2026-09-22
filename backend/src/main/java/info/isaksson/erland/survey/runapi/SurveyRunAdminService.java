package info.isaksson.erland.survey.runapi;

import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.run.SurveyRunLifecycleService;
import info.isaksson.erland.survey.run.SurveyRunSnapshotService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static info.isaksson.erland.survey.runapi.SurveyRunDtos.RunView;

@ApplicationScoped
public class SurveyRunAdminService {
    @Inject SurveyRunRepository runRepository;
    @Inject AccountAccessService accountAccess;
    @Inject SurveyRunSnapshotService snapshotService;
    @Inject SurveyRunLifecycleService lifecycleService;

    @Transactional
    public List<RunView> list(UUID userId, UUID accountId, UUID surveyId) {
        accountAccess.requireSurvey(userId, accountId, surveyId);
        return runRepository.find("survey.id = ?1 and survey.surveyAccountId = ?2 order by createdAt desc", surveyId, accountId)
                .list().stream().map(this::map).toList();
    }

    @Transactional
    public RunView get(UUID userId, UUID accountId, UUID runId) {
        return map(accountRun(userId, accountId, runId));
    }

    @Transactional
    public RunView create(UUID userId, UUID accountId, UUID surveyId, String title) {
        return map(snapshotService.createDraft(userId, accountId, surveyId, title));
    }

    @Transactional
    public RunView open(UUID userId, UUID accountId, UUID runId) {
        return map(lifecycleService.openNow(userId, accountId, runId));
    }

    private SurveyRun accountRun(UUID userId, UUID accountId, UUID runId) {
        SurveyRun run = accountAccess.requireRun(userId, accountId, runId);
        lifecycleService.synchronize(run.id, Instant.now());
        return run;
    }

    private RunView map(SurveyRun run) {
        return new RunView(
                run.id,
                run.survey.id,
                run.publicId,
                run.joinCode,
                run.title,
                run.status,
                run.opensAt,
                run.closesAt,
                run.createdAt,
                run.openedAt,
                run.closedAt
        );
    }
}
