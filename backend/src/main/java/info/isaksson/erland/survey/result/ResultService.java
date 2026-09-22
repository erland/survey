package info.isaksson.erland.survey.result;

import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.domain.*;
import info.isaksson.erland.survey.run.SurveyRunLifecycleService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static info.isaksson.erland.survey.result.ResultDtos.*;

@ApplicationScoped
public class ResultService {
    @Inject SurveyRunRepository runs;
    @Inject AccountAccessService accountAccess;
    @Inject SurveyRunLifecycleService lifecycle;
    @Inject EntityManager em;

    @Transactional
    public List<QuestionResult> all(UUID userId, UUID accountId, UUID runId) {
        SurveyRun run = accountRun(userId, accountId, runId);
        return allForRun(run.id);
    }

    @Transactional
    public List<QuestionResult> allForRun(UUID runId) {
        SurveyRun run = runs.findByIdOptional(runId)
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
        return run.questions.stream()
                .sorted(Comparator.comparingInt(q -> q.position))
                .map(q -> aggregate(run.id, q))
                .toList();
    }

    @Transactional
    public QuestionResult one(UUID userId, UUID accountId, UUID runId, UUID questionId) {
        SurveyRun run = accountRun(userId, accountId, runId);
        SurveyRunQuestion question = run.questions.stream()
                .filter(q -> q.id.equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "QUESTION_NOT_FOUND", "Frågan kunde inte hittas i enkätgenomförandet."));
        return aggregate(run.id, question);
    }

    private SurveyRun accountRun(UUID userId, UUID accountId, UUID runId) {
        SurveyRun run = accountAccess.requireRun(userId, accountId, runId);
        lifecycle.synchronize(run.id, Instant.now());
        return run;
    }

    private QuestionResult aggregate(UUID runId, SurveyRunQuestion q) {
        List<Response> rs = em.createQuery("""
                select distinct r from Response r
                left join fetch r.values v
                left join fetch v.option o
                where r.question.id = :questionId
                  and r.participantSession.run.id = :runId
                  and r.participantSession.status <> :expired
                """, Response.class)
                .setParameter("questionId", q.id)
                .setParameter("runId", runId)
                .setParameter("expired", ParticipantSessionStatus.EXPIRED)
                .getResultList();

        long responseCount = rs.stream().filter(this::hasAnswer).count();

        return switch (q.type) {
            case YES_NO -> yesNo(q, rs, responseCount);
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> choices(q, rs, responseCount);
            case SCALE -> scale(q, rs, responseCount);
            case TEXT -> text(q, rs, responseCount);
        };
    }

    private QuestionResult yesNo(SurveyRunQuestion q, List<Response> rs, long responseCount) {
        long yes = rs.stream().flatMap(r -> r.values.stream()).filter(v -> Boolean.TRUE.equals(v.booleanValue)).count();
        long no = rs.stream().flatMap(r -> r.values.stream()).filter(v -> Boolean.FALSE.equals(v.booleanValue)).count();
        return base(q, responseCount, yes, no, List.of(), List.of(), List.of());
    }

    private QuestionResult choices(SurveyRunQuestion q, List<Response> rs, long responseCount) {
        Map<UUID, Long> counts = rs.stream()
                .flatMap(r -> r.values.stream())
                .map(v -> v.option)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(o -> o.id, Collectors.counting()));
        List<ChoiceCount> values = q.options.stream()
                .sorted(Comparator.comparingInt(o -> o.position))
                .map(o -> new ChoiceCount(o.value, o.label, counts.getOrDefault(o.id, 0L)))
                .toList();
        return base(q, responseCount, null, null, values, List.of(), List.of());
    }

    private QuestionResult scale(SurveyRunQuestion q, List<Response> rs, long responseCount) {
        Map<Integer, Long> counts = rs.stream()
                .flatMap(r -> r.values.stream())
                .map(v -> v.numericValue)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Integer::intValue, Collectors.counting()));
        List<ScaleCount> values = new ArrayList<>();
        if (q.scaleMin != null && q.scaleMax != null) {
            for (int i = q.scaleMin; i <= q.scaleMax; i++) values.add(new ScaleCount(i, counts.getOrDefault(i, 0L)));
        }
        return base(q, responseCount, null, null, List.of(), values, List.of());
    }

    private QuestionResult text(SurveyRunQuestion q, List<Response> rs, long responseCount) {
        List<TextValue> values = rs.stream()
                .filter(this::hasAnswer)
                .sorted(Comparator.comparing((Response r) -> r.updatedAt).reversed())
                .flatMap(r -> r.values.stream()
                        .filter(v -> v.textValue != null && !v.textValue.isBlank())
                        .map(v -> new TextValue(v.textValue, r.updatedAt)))
                .toList();
        return base(q, responseCount, null, null, List.of(), List.of(), values);
    }

    private boolean hasAnswer(Response r) {
        return r.values.stream().anyMatch(v ->
                (v.textValue != null && !v.textValue.isBlank()) ||
                v.numericValue != null ||
                v.booleanValue != null ||
                v.option != null);
    }

    private QuestionResult base(SurveyRunQuestion q, long responseCount, Long yes, Long no,
                                List<ChoiceCount> choices, List<ScaleCount> scale, List<TextValue> texts) {
        return new QuestionResult(q.id, q.position, q.type, q.text, responseCount, yes, no, choices, scale, texts);
    }
}
