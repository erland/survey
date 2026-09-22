package info.isaksson.erland.survey.runapi;

import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.domain.ParticipantSessionRepository;
import info.isaksson.erland.survey.domain.ParticipantSessionStatus;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static info.isaksson.erland.survey.runapi.RunSummaryDtos.LiveSummary;

@ApplicationScoped
public class RunSummaryService {
    static final Duration ACTIVE_WINDOW = Duration.ofSeconds(90);

    @Inject SurveyRunRepository runRepository;
    @Inject AccountAccessService accountAccess;
    @Inject ParticipantSessionRepository participantSessionRepository;

    @Transactional
    public LiveSummary get(UUID userId, UUID runId, Instant now) {
        return get(userId, accountAccess.requireSingleAccount(userId), runId, now);
    }

    @Transactional
    public LiveSummary get(UUID userId, UUID accountId, UUID runId, Instant now) {
        SurveyRun run = accountAccess.requireRun(userId, accountId, runId);
        return getForRun(run.id, now);
    }

    @Transactional
    public LiveSummary getForRun(UUID runId, Instant now) {
        long started = participantSessionRepository.count("run.id = ?1", runId);
        long submitted = participantSessionRepository.count("run.id = ?1 and status = ?2", runId, ParticipantSessionStatus.SUBMITTED);
        long active = participantSessionRepository.countActiveForRun(runId, now.minus(ACTIVE_WINDOW));
        return new LiveSummary(started, active, submitted);
    }
}
