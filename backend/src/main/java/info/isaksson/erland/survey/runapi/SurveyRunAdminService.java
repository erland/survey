package info.isaksson.erland.survey.runapi;

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
    @Inject SurveyRunSnapshotService snapshotService;
    @Inject SurveyRunLifecycleService lifecycleService;

    @Transactional
    public List<RunView> list(UUID ownerId, UUID surveyId) {
        return runRepository.find("survey.id = ?1 and createdBy = ?2 order by createdAt desc", surveyId, ownerId)
                .list().stream().map(this::map).toList();
    }

    @Transactional
    public RunView get(UUID ownerId, UUID runId) {
        return map(ownedRun(ownerId, runId));
    }

    @Transactional
    public RunView create(UUID ownerId, UUID surveyId, String title) {
        return map(snapshotService.createDraft(ownerId, surveyId, title));
    }

    @Transactional
    public RunView open(UUID ownerId, UUID runId) {
        return map(lifecycleService.openNow(ownerId, runId));
    }

    private SurveyRun ownedRun(UUID ownerId, UUID runId) {
        SurveyRun run = runRepository.find("id = ?1 and createdBy = ?2", runId, ownerId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
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
