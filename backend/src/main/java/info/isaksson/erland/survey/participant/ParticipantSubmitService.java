package info.isaksson.erland.survey.participant;

import info.isaksson.erland.survey.domain.*;
import info.isaksson.erland.survey.surveyapi.ApiException;
import info.isaksson.erland.survey.live.LiveEventService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class ParticipantSubmitService {
    @Inject SurveyRunRepository runs;
    @Inject ParticipantSessionRepository sessions;
    @Inject ResponseRepository responses;
    @Inject LiveEventService liveEvents;

    public record SubmitResponse(String status, Instant submittedAt, boolean alreadySubmitted) {}

    @Transactional
    public SubmitResponse submit(String publicId, String token) {
        ParticipantSession session = session(publicId, token);
        if (session.status == ParticipantSessionStatus.SUBMITTED) {
            return new SubmitResponse("SUBMITTED", session.submittedAt, true);
        }
        if (session.status != ParticipantSessionStatus.ACTIVE) {
            throw new ApiException(409, "PARTICIPANT_SESSION_NOT_ACTIVE", "Deltagarsessionen är inte längre aktiv.");
        }

        Map<UUID, info.isaksson.erland.survey.domain.Response> byQuestion = new HashMap<>();
        for (var response : responses.listBySession(session.id)) byQuestion.put(response.question.id, response);

        List<UUID> missing = session.run.questions.stream()
                .filter(q -> q.required)
                .filter(q -> !hasAnswer(byQuestion.get(q.id)))
                .map(q -> q.id)
                .toList();
        if (!missing.isEmpty()) {
            throw new ApiException(400, "REQUIRED_ANSWERS_MISSING",
                    "Alla obligatoriska frågor måste besvaras innan enkäten skickas in.");
        }

        Instant now = Instant.now();
        session.status = ParticipantSessionStatus.SUBMITTED;
        session.submittedAt = now;
        session.lastActivityAt = now;
        liveEvents.publishAfterCommit(session.run.id, "participant_submitted");
        return new SubmitResponse("SUBMITTED", now, false);
    }

    private boolean hasAnswer(info.isaksson.erland.survey.domain.Response response) {
        if (response == null || response.values == null || response.values.isEmpty()) return false;
        return response.values.stream().anyMatch(v ->
                (v.textValue != null && !v.textValue.isBlank()) ||
                v.booleanValue != null ||
                v.numericValue != null ||
                v.option != null);
    }

    private ParticipantSession session(String publicId, String token) {
        if (publicId == null || publicId.isBlank() || token == null || token.isBlank()) {
            throw new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte valideras.");
        }
        SurveyRun run = runs.find("publicId", publicId.trim()).firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return sessions.findByRunAndTokenHash(run.id, hash)
                .orElseThrow(() -> new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte valideras."));
    }
}
