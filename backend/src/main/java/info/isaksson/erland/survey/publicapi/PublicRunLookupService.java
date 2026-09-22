package info.isaksson.erland.survey.publicapi;

import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.domain.SurveyRunStatus;
import info.isaksson.erland.survey.run.SurveyRunLifecycleService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;

import static info.isaksson.erland.survey.publicapi.PublicRunDtos.RunLookupResponse;

@ApplicationScoped
public class PublicRunLookupService {

    @Inject SurveyRunRepository runRepository;
    @Inject SurveyRunLifecycleService lifecycleService;

    @Transactional
    public RunLookupResponse findByPublicId(String publicId) {
        String normalized = requireNonBlank(publicId, "INVALID_PUBLIC_ID", "Ogiltig enkätlänk.");
        SurveyRun run = runRepository.find("publicId", normalized)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
        return validateAndMap(run, Instant.now());
    }

    @Transactional
    public RunLookupResponse findByJoinCode(String joinCode) {
        String normalized = requireNonBlank(joinCode, "INVALID_JOIN_CODE", "Ogiltig anslutningskod.")
                .replace(" ", "")
                .toUpperCase();
        SurveyRun run = runRepository.find("upper(joinCode) = ?1", normalized)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Ingen enkät hittades för den angivna koden."));
        return validateAndMap(run, Instant.now());
    }

    private RunLookupResponse validateAndMap(SurveyRun run, Instant now) {
        lifecycleService.synchronize(run.id, now);

        if (run.status == SurveyRunStatus.DRAFT) {
            throw new ApiException(409, "RUN_NOT_OPEN", "Enkäten är inte öppen ännu.");
        }
        if (run.status == SurveyRunStatus.SCHEDULED && run.opensAt != null && now.isBefore(run.opensAt)) {
            throw new ApiException(409, "RUN_NOT_OPEN", "Enkäten är inte öppen ännu.");
        }
        if (run.status == SurveyRunStatus.CLOSED
                || (run.closesAt != null && !now.isBefore(run.closesAt))) {
            throw new ApiException(410, "RUN_CLOSED", "Enkäten är stängd.");
        }
        if (!lifecycleService.acceptsNewParticipants(run, now)) {
            throw new ApiException(409, "RUN_NOT_AVAILABLE", "Enkäten kan inte ta emot nya deltagare just nu.");
        }

        return new RunLookupResponse(
                run.publicId,
                run.joinCode,
                run.title,
                run.status,
                run.opensAt,
                run.closesAt,
                run.questions.size()
        );
    }

    private String requireNonBlank(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, code, message);
        }
        return value.trim();
    }
}
