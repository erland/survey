package info.isaksson.erland.survey.runapi;

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
    @Inject ParticipantSessionRepository participantSessionRepository;

    @Transactional
    public LiveSummary get(UUID ownerId, UUID runId, Instant now) {
        SurveyRun run = runRepository.find("id = ?1 and createdBy = ?2", runId, ownerId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
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
