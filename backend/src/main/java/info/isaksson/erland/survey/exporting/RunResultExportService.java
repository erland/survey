package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.domain.*;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static info.isaksson.erland.survey.exporting.RunResultExportDtos.*;

@ApplicationScoped
public class RunResultExportService {
    @Inject SurveyRunRepository runRepository;
    @Inject ParticipantSessionRepository participantRepository;
    @Inject ResponseRepository responseRepository;

    @Transactional
    public ResultExportDocument export(UUID ownerId, UUID runId) {
        SurveyRun run = runRepository.find("id = ?1 and createdBy = ?2", runId, ownerId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));

        List<SurveyRunQuestion> questions = run.questions.stream()
                .sorted(Comparator.comparingInt(q -> q.position))
                .toList();

        Map<UUID, String> questionKeys = new HashMap<>();
        List<QuestionExport> questionExports = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            SurveyRunQuestion question = questions.get(i);
            String key = "Q" + (i + 1);
            questionKeys.put(question.id, key);
            questionExports.add(new QuestionExport(
                    key,
                    question.position,
                    question.type,
                    question.text,
                    question.required,
                    question.scaleMin,
                    question.scaleMax,
                    question.scaleMinLabel,
                    question.scaleMaxLabel,
                    question.options.stream()
                            .sorted(Comparator.comparingInt(o -> o.position))
                            .map(o -> new OptionExport(o.value, o.label, o.position))
                            .toList()
            ));
        }

        List<ParticipantSession> participants = participantRepository.find(
                        "run.id = ?1 and status <> ?2 order by startedAt asc, id asc",
                        runId, ParticipantSessionStatus.EXPIRED)
                .list();

        List<ParticipantExport> participantExports = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            ParticipantSession participant = participants.get(i);
            String participantId = String.format("anon-%03d", i + 1);
            List<Response> responses = responseRepository.listBySession(participant.id).stream()
                    .sorted(Comparator.comparingInt(r -> r.question.position))
                    .toList();
            List<AnswerExport> answers = responses.stream()
                    .map(response -> mapAnswer(response, questionKeys))
                    .toList();
            participantExports.add(new ParticipantExport(
                    participantId,
                    participant.status,
                    participant.startedAt,
                    participant.submittedAt,
                    answers
            ));
        }

        return new ResultExportDocument(
                "survey-result-export",
                1,
                Instant.now(),
                new RunMetadata(
                        run.title,
                        run.status,
                        run.publicId,
                        run.joinCode,
                        run.createdAt,
                        run.openedAt,
                        run.closedAt,
                        run.opensAt,
                        run.closesAt
                ),
                questionExports,
                participantExports
        );
    }

    private AnswerExport mapAnswer(Response response, Map<UUID, String> questionKeys) {
        String text = null;
        Boolean bool = null;
        Integer number = null;
        List<String> options = new ArrayList<>();
        for (ResponseValue value : response.values) {
            if (value.textValue != null) text = value.textValue;
            if (value.booleanValue != null) bool = value.booleanValue;
            if (value.numericValue != null) number = value.numericValue;
            if (value.option != null) options.add(value.option.value);
        }
        options = options.stream().sorted().collect(Collectors.toList());
        return new AnswerExport(
                questionKeys.get(response.question.id),
                text,
                bool,
                number,
                options,
                response.updatedAt
        );
    }
}
