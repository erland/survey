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

import static info.isaksson.erland.survey.participant.ParticipantResponseDtos.*;

@ApplicationScoped
public class ParticipantResponseService {
    @Inject SurveyRunRepository runs;
    @Inject ParticipantSessionRepository sessions;
    @Inject ResponseRepository responses;
    @Inject LiveEventService liveEvents;

    @Transactional
    public AnswersResponse list(String publicId, String token) {
        ParticipantSession s = session(publicId, token);
        return new AnswersResponse(responses.listBySession(s.id).stream().map(this::map).toList());
    }

    @Transactional
    public AnswerResponse save(String publicId, UUID questionId, String token, SaveAnswerRequest input) {
        ParticipantSession s = session(publicId, token);
        if (s.status != ParticipantSessionStatus.ACTIVE) throw new ApiException(409, "PARTICIPANT_SESSION_NOT_ACTIVE", "Deltagarsessionen är inte längre aktiv.");
        SurveyRunQuestion q = s.run.questions.stream().filter(x -> x.id.equals(questionId)).findFirst()
                .orElseThrow(() -> new ApiException(404, "QUESTION_NOT_FOUND", "Frågan kunde inte hittas i enkätgenomförandet."));
        validate(q, input);
        info.isaksson.erland.survey.domain.Response r = responses.findBySessionAndQuestion(s.id, q.id).orElseGet(() -> {
            var created = new info.isaksson.erland.survey.domain.Response();
            created.id = UUID.randomUUID(); created.participantSession = s; created.question = q; created.updatedAt = Instant.now();
            responses.persist(created); return created;
        });
        r.values.clear();
        addValues(r, q, input);
        r.updatedAt = Instant.now();
        s.lastActivityAt = r.updatedAt;
        liveEvents.publishAfterCommit(s.run.id, "response_updated");
        return map(r);
    }

    private void addValues(info.isaksson.erland.survey.domain.Response r, SurveyRunQuestion q, SaveAnswerRequest in) {
        switch (q.type) {
            case TEXT -> { if (in.textValue()!=null && !in.textValue().isBlank()) add(r, v -> v.textValue = in.textValue()); }
            case YES_NO -> { if (in.booleanValue()!=null) add(r, v -> v.booleanValue = in.booleanValue()); }
            case SCALE -> { if (in.numericValue()!=null) add(r, v -> v.numericValue = in.numericValue()); }
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                if (in.optionValues()!=null) for (String ov : in.optionValues()) {
                    SurveyRunOption option = q.options.stream().filter(o -> o.value.equals(ov)).findFirst().orElseThrow();
                    add(r, v -> v.option = option);
                }
            }
        }
    }
    private interface Setter { void apply(ResponseValue v); }
    private void add(info.isaksson.erland.survey.domain.Response r, Setter setter) {
        ResponseValue v = new ResponseValue(); v.id = UUID.randomUUID(); v.response = r; setter.apply(v); r.values.add(v);
    }

    private void validate(SurveyRunQuestion q, SaveAnswerRequest in) {
        if (in == null) throw new ApiException(400, "INVALID_ANSWER", "Svaret saknas.");
        switch (q.type) {
            case TEXT -> { if (in.textValue()!=null && in.textValue().length()>10000) throw invalid(); }
            case YES_NO -> { if (in.booleanValue()==null && hasAny(in)) throw invalid(); }
            case SCALE -> { if (in.numericValue()!=null && (q.scaleMin==null || q.scaleMax==null || in.numericValue()<q.scaleMin || in.numericValue()>q.scaleMax)) throw invalid(); }
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                List<String> vals = in.optionValues()==null ? List.of() : in.optionValues();
                if (q.type == QuestionType.SINGLE_CHOICE && vals.size()>1) throw invalid();
                Set<String> allowed = q.options.stream().map(o->o.value).collect(java.util.stream.Collectors.toSet());
                if (!allowed.containsAll(vals) || vals.size()!=new HashSet<>(vals).size()) throw invalid();
            }
        }
    }
    private boolean hasAny(SaveAnswerRequest in){ return in.textValue()!=null || in.numericValue()!=null || (in.optionValues()!=null && !in.optionValues().isEmpty()); }
    private ApiException invalid(){ return new ApiException(400, "INVALID_ANSWER", "Svaret är inte giltigt för frågetypen."); }

    private ParticipantSession session(String publicId, String token) {
        if (publicId==null || publicId.isBlank() || token==null || token.isBlank()) throw new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte valideras.");
        SurveyRun run = runs.find("publicId", publicId.trim()).firstResultOptional().orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
        String hash;
        try { hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.trim().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
        return sessions.findByRunAndTokenHash(run.id, hash).orElseThrow(() -> new ApiException(401, "PARTICIPANT_SESSION_INVALID", "Deltagarsessionen kunde inte valideras."));
    }

    private AnswerResponse map(info.isaksson.erland.survey.domain.Response r) {
        String text = r.values.stream().map(v->v.textValue).filter(Objects::nonNull).findFirst().orElse(null);
        Boolean bool = r.values.stream().map(v->v.booleanValue).filter(Objects::nonNull).findFirst().orElse(null);
        Integer num = r.values.stream().map(v->v.numericValue).filter(Objects::nonNull).findFirst().orElse(null);
        List<String> opts = r.values.stream().map(v->v.option).filter(Objects::nonNull).map(o->o.value).toList();
        return new AnswerResponse(r.question.id, text, bool, num, opts, r.updatedAt);
    }
}
