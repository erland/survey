package info.isaksson.erland.survey.participant;

import info.isaksson.erland.survey.domain.ParticipantSession;
import info.isaksson.erland.survey.domain.ParticipantSessionRepository;
import info.isaksson.erland.survey.domain.ParticipantSessionStatus;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.run.SurveyRunLifecycleService;
import info.isaksson.erland.survey.live.LiveEventService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static info.isaksson.erland.survey.participant.ParticipantDtos.ParticipantHeartbeatResponse;
import static info.isaksson.erland.survey.participant.ParticipantDtos.ParticipantSessionResponse;

@ApplicationScoped
public class ParticipantSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject SurveyRunRepository runRepository;
    @Inject ParticipantSessionRepository participantRepository;
    @Inject SurveyRunLifecycleService lifecycleService;
    @Inject LiveEventService liveEvents;

    @Transactional
    public ParticipantSessionResponse create(String publicId) {
        SurveyRun run = findRun(publicId);
        Instant now = Instant.now();
        lifecycleService.synchronize(run.id, now);
        if (!lifecycleService.acceptsNewParticipants(run, now)) {
            throw unavailable(run, now);
        }

        String token = generateToken();
        ParticipantSession session = new ParticipantSession();
        session.id = UUID.randomUUID();
        session.run = run;
        session.clientTokenHash = sha256(token);
        session.status = ParticipantSessionStatus.ACTIVE;
        session.startedAt = now;
        session.lastActivityAt = now;
        participantRepository.persist(session);
        liveEvents.publishAfterCommit(run.id, "participant_started");

        return map(session, token, false);
    }

    @Transactional
    public ParticipantHeartbeatResponse heartbeat(String publicId, String token) {
        SurveyRun run = findRun(publicId);
        String normalizedToken = requireToken(token);
        ParticipantSession session = participantRepository.findByRunAndTokenHash(run.id, sha256(normalizedToken))
                .orElseThrow(() -> new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte valideras."));
        if (session.status == ParticipantSessionStatus.EXPIRED) {
            throw new ApiException(410, "PARTICIPANT_SESSION_EXPIRED", "Deltagarsessionen har gått ut.");
        }
        if (session.status != ParticipantSessionStatus.ACTIVE) {
            throw new ApiException(409, "PARTICIPANT_SESSION_NOT_ACTIVE", "Deltagarsessionen är inte längre aktiv.");
        }
        session.lastActivityAt = Instant.now();
        liveEvents.publishAfterCommit(run.id, "participant_activity");
        return new ParticipantHeartbeatResponse(session.lastActivityAt);
    }

    public long countActiveParticipants(UUID runId, Instant now) {
        return participantRepository.countActiveForRun(runId, now.minusSeconds(90));
    }

    @Transactional
    public ParticipantSessionResponse resume(String publicId, String token) {
        SurveyRun run = findRun(publicId);
        String normalizedToken = requireToken(token);
        ParticipantSession session = participantRepository.findByRunAndTokenHash(run.id, sha256(normalizedToken))
                .orElseThrow(() -> new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte återupptas."));

        if (session.status == ParticipantSessionStatus.EXPIRED) {
            throw new ApiException(410, "PARTICIPANT_SESSION_EXPIRED", "Deltagarsessionen har gått ut.");
        }
        session.lastActivityAt = Instant.now();
        return map(session, normalizedToken, true);
    }


    @Transactional
    public ParticipantSurveyDtos.ParticipantSurveyResponse survey(String publicId, String token) {
        SurveyRun run = findRun(publicId);
        String normalizedToken = requireToken(token);
        ParticipantSession session = participantRepository.findByRunAndTokenHash(run.id, sha256(normalizedToken))
                .orElseThrow(() -> new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte valideras."));
        if (session.status == ParticipantSessionStatus.EXPIRED) {
            throw new ApiException(410, "PARTICIPANT_SESSION_EXPIRED", "Deltagarsessionen har gått ut.");
        }
        session.lastActivityAt = Instant.now();

        var questions = run.questions.stream().map(q -> new ParticipantSurveyDtos.ParticipantQuestionResponse(
                q.id, q.position, q.type, q.text, q.required, q.scaleMin, q.scaleMax, q.scaleMinLabel, q.scaleMaxLabel,
                q.options.stream().map(o -> new ParticipantSurveyDtos.ParticipantOptionResponse(
                        o.id, o.position, o.value, o.label
                )).toList()
        )).toList();
        return new ParticipantSurveyDtos.ParticipantSurveyResponse(run.publicId, run.title, questions);
    }

    private SurveyRun findRun(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new ApiException(400, "INVALID_PUBLIC_ID", "Ogiltig enkätlänk.");
        }
        return runRepository.find("publicId", publicId.trim()).firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
    }

    private ApiException unavailable(SurveyRun run, Instant now) {
        if (run.status == info.isaksson.erland.survey.domain.SurveyRunStatus.CLOSED ||
                (run.closesAt != null && !now.isBefore(run.closesAt))) {
            return new ApiException(410, "RUN_CLOSED", "Enkäten är stängd.");
        }
        return new ApiException(409, "RUN_NOT_OPEN", "Enkäten är inte öppen ännu.");
    }

    private String requireToken(String token) {
        if (token == null || token.isBlank() || token.length() > 512) {
            throw new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte återupptas.");
        }
        return token.trim();
    }

    private ParticipantSessionResponse map(ParticipantSession session, String token, boolean resumed) {
        return new ParticipantSessionResponse(session.id, token, session.status, session.startedAt, session.lastActivityAt, resumed);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
