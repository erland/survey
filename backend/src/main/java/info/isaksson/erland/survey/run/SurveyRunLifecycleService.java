package info.isaksson.erland.survey.run;

import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.domain.SurveyRunStatus;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SurveyRunLifecycleService {

    @Inject SurveyRunRepository runRepository;
    @Inject AccountAccessService accountAccess;

    @Transactional
    public SurveyRun schedule(UUID userId, UUID runId, Instant opensAt, Instant closesAt) {
        return schedule(userId, accountAccess.requireSingleAccount(userId), runId, opensAt, closesAt);
    }

    @Transactional
    public SurveyRun schedule(UUID userId, UUID accountId, UUID runId, Instant opensAt, Instant closesAt) {
        if (opensAt == null) {
            throw new ApiException(400, "INVALID_RUN_SCHEDULE", "Öppningstid måste anges för ett schemalagt genomförande.");
        }
        validateWindow(opensAt, closesAt);

        SurveyRun run = accountAccess.requireRun(userId, accountId, runId);
        requireNotClosed(run);

        run.opensAt = opensAt;
        run.closesAt = closesAt;
        run.openedAt = null;
        run.closedAt = null;
        run.status = SurveyRunStatus.SCHEDULED;
        synchronizeTemporalStatus(run, Instant.now());
        return run;
    }

    @Transactional
    public SurveyRun openNow(UUID userId, UUID runId) {
        return openNow(userId, accountAccess.requireSingleAccount(userId), runId);
    }

    @Transactional
    public SurveyRun openNow(UUID userId, UUID accountId, UUID runId) {
        SurveyRun run = accountAccess.requireRun(userId, accountId, runId);
        requireNotClosed(run);

        Instant now = Instant.now();
        if (run.closesAt != null && !now.isBefore(run.closesAt)) {
            throw new ApiException(409, "RUN_WINDOW_ENDED", "Genomförandets sluttid har redan passerat.");
        }

        run.status = SurveyRunStatus.OPEN;
        run.opensAt = now;
        run.openedAt = now;
        return run;
    }

    @Transactional
    public SurveyRun close(UUID userId, UUID runId) {
        return close(userId, accountAccess.requireSingleAccount(userId), runId);
    }

    @Transactional
    public SurveyRun close(UUID userId, UUID accountId, UUID runId) {
        SurveyRun run = accountAccess.requireRun(userId, accountId, runId);
        if (run.status == SurveyRunStatus.CLOSED) {
            return run;
        }
        run.status = SurveyRunStatus.CLOSED;
        run.closedAt = Instant.now();
        return run;
    }

    @Transactional
    public SurveyRun synchronize(UUID runId, Instant now) {
        SurveyRun run = runRepository.findByIdOptional(runId)
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
        synchronizeTemporalStatus(run, now);
        return run;
    }

    public boolean acceptsNewParticipants(SurveyRun run, Instant now) {
        if (run == null || run.status == SurveyRunStatus.DRAFT || run.status == SurveyRunStatus.CLOSED) {
            return false;
        }
        if (run.opensAt != null && now.isBefore(run.opensAt)) {
            return false;
        }
        if (run.closesAt != null && !now.isBefore(run.closesAt)) {
            return false;
        }
        return run.status == SurveyRunStatus.OPEN || run.status == SurveyRunStatus.SCHEDULED;
    }

    void synchronizeTemporalStatus(SurveyRun run, Instant now) {
        if (run.status == SurveyRunStatus.CLOSED || run.status == SurveyRunStatus.DRAFT) {
            return;
        }

        if (run.closesAt != null && !now.isBefore(run.closesAt)) {
            run.status = SurveyRunStatus.CLOSED;
            if (run.closedAt == null) {
                run.closedAt = now;
            }
            return;
        }

        if (run.status == SurveyRunStatus.SCHEDULED
                && run.opensAt != null
                && !now.isBefore(run.opensAt)) {
            run.status = SurveyRunStatus.OPEN;
            if (run.openedAt == null) {
                run.openedAt = now;
            }
        }
    }

    private void requireNotClosed(SurveyRun run) {
        if (run.status == SurveyRunStatus.CLOSED) {
            throw new ApiException(409, "RUN_CLOSED", "Ett stängt genomförande kan inte öppnas eller schemaläggas på nytt.");
        }
    }

    private void validateWindow(Instant opensAt, Instant closesAt) {
        if (closesAt != null && !closesAt.isAfter(opensAt)) {
            throw new ApiException(400, "INVALID_RUN_SCHEDULE", "Sluttiden måste ligga efter öppningstiden.");
        }
    }
}
