package info.isaksson.erland.survey.surveyapi;

import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static info.isaksson.erland.survey.surveyapi.SurveyDtos.*;

@ApplicationScoped
public class SurveyService {
    @Inject SurveyRepository surveyRepository;
    @Inject AccountAccessService accountAccess;

    public List<SurveySummary> list(UUID userId, UUID accountId) {
        accountAccess.requireMembership(userId, accountId);
        return surveyRepository.find("surveyAccountId = ?1 order by updatedAt desc", accountId).list().stream()
                .map(this::toSummary)
                .toList();
    }

    public SurveyView get(UUID userId, UUID accountId, UUID surveyId) {
        return toView(accountAccess.requireSurvey(userId, accountId, surveyId));
    }

    @Transactional
    public SurveyView create(UUID userId, UUID accountId, SurveyInput input) {
        validate(input);
        accountAccess.requireMembership(userId, accountId);
        Survey survey = new Survey();
        survey.id = UUID.randomUUID();
        survey.surveyAccountId = accountId;
        survey.createdByAdminUserId = userId;
        survey.createdAt = Instant.now();
        survey.updatedAt = survey.createdAt;
        apply(survey, input);
        surveyRepository.persist(survey);
        return toView(survey);
    }

    @Transactional
    public SurveyView update(UUID userId, UUID accountId, UUID surveyId, SurveyInput input) {
        validate(input);
        Survey survey = accountAccess.requireSurvey(userId, accountId, surveyId);
        apply(survey, input);
        survey.updatedAt = Instant.now();
        return toView(survey);
    }

    @Transactional
    public void delete(UUID userId, UUID accountId, UUID surveyId) {
        Survey survey = accountAccess.requireSurvey(userId, accountId, surveyId);
        surveyRepository.delete(survey);
    }

    @Transactional
    public SurveyView copy(UUID userId, UUID accountId, UUID surveyId) {
        Survey source = accountAccess.requireSurvey(userId, accountId, surveyId);
        Survey copy = new Survey();
        copy.id = UUID.randomUUID();
        copy.surveyAccountId = accountId;
        copy.createdByAdminUserId = userId;
        copy.title = source.title + " (kopia)";
        copy.description = source.description;
        copy.status = SurveyStatus.DRAFT;
        copy.createdAt = Instant.now();
        copy.updatedAt = copy.createdAt;
        copy.questions = cloneQuestions(copy, source.questions);
        surveyRepository.persist(copy);
        return toView(copy);
    }

    private void apply(Survey survey, SurveyInput input) {
        survey.title = input.title().trim();
        survey.description = trimToNull(input.description());
        survey.status = input.status() == null ? SurveyStatus.DRAFT : input.status();
        survey.questions.clear();
        List<QuestionInput> inputs = input.questions() == null ? List.of() : input.questions();
        for (int i = 0; i < inputs.size(); i++) {
            QuestionInput qi = inputs.get(i);
            SurveyQuestion q = new SurveyQuestion();
            q.id = UUID.randomUUID();
            q.survey = survey;
            q.position = i;
            q.type = qi.type();
            q.text = qi.text().trim();
            q.required = qi.required();
            q.scaleMin = qi.type() == QuestionType.SCALE ? qi.scaleMin() : null;
            q.scaleMax = qi.type() == QuestionType.SCALE ? qi.scaleMax() : null;
            q.scaleMinLabel = qi.type() == QuestionType.SCALE ? trimToNull(qi.scaleMinLabel()) : null;
            q.scaleMaxLabel = qi.type() == QuestionType.SCALE ? trimToNull(qi.scaleMaxLabel()) : null;
            List<OptionInput> options = qi.options() == null ? List.of() : qi.options();
            if (qi.type() == QuestionType.SINGLE_CHOICE || qi.type() == QuestionType.MULTIPLE_CHOICE) {
                for (int j = 0; j < options.size(); j++) {
                    OptionInput oi = options.get(j);
                    QuestionOption option = new QuestionOption();
                    option.id = UUID.randomUUID();
                    option.question = q;
                    option.position = j;
                    option.value = oi.value().trim();
                    option.label = oi.label().trim();
                    q.options.add(option);
                }
            }
            survey.questions.add(q);
        }
    }

    private List<SurveyQuestion> cloneQuestions(Survey target, List<SurveyQuestion> source) {
        List<SurveyQuestion> result = new ArrayList<>();
        for (SurveyQuestion sq : source) {
            SurveyQuestion q = new SurveyQuestion();
            q.id = UUID.randomUUID();
            q.survey = target;
            q.position = sq.position;
            q.type = sq.type;
            q.text = sq.text;
            q.required = sq.required;
            q.scaleMin = sq.scaleMin;
            q.scaleMax = sq.scaleMax;
            q.scaleMinLabel = sq.scaleMinLabel;
            q.scaleMaxLabel = sq.scaleMaxLabel;
            for (QuestionOption so : sq.options) {
                QuestionOption o = new QuestionOption();
                o.id = UUID.randomUUID();
                o.question = q;
                o.position = so.position;
                o.value = so.value;
                o.label = so.label;
                q.options.add(o);
            }
            result.add(q);
        }
        return result;
    }

    private void validate(SurveyInput input) {
        if (input == null || input.title() == null || input.title().isBlank()) {
            throw new ApiException(400, "INVALID_SURVEY", "Titel måste anges.");
        }
        if (input.title().trim().length() > 300) {
            throw new ApiException(400, "INVALID_SURVEY", "Titel får vara högst 300 tecken.");
        }
        List<QuestionInput> questions = input.questions() == null ? List.of() : input.questions();
        for (int i = 0; i < questions.size(); i++) validateQuestion(questions.get(i), i);
    }

    private void validateQuestion(QuestionInput q, int index) {
        if (q == null || q.type() == null || q.text() == null || q.text().isBlank()) {
            throw new ApiException(400, "INVALID_QUESTION", "Fråga " + (index + 1) + " saknar typ eller text.");
        }
        List<OptionInput> options = q.options() == null ? List.of() : q.options();
        if (q.type() == QuestionType.SINGLE_CHOICE || q.type() == QuestionType.MULTIPLE_CHOICE) {
            if (options.size() < 2) {
                throw new ApiException(400, "INVALID_QUESTION", "Valfrågor måste ha minst två svarsalternativ.");
            }
            var values = new java.util.HashSet<String>();
            for (OptionInput option : options) {
                if (option == null || option.value() == null || option.value().isBlank() || option.label() == null || option.label().isBlank()) {
                    throw new ApiException(400, "INVALID_OPTION", "Svarsalternativ måste ha värde och etikett.");
                }
                if (!values.add(option.value().trim().toLowerCase(Locale.ROOT))) {
                    throw new ApiException(400, "INVALID_OPTION", "Svarsalternativens värden måste vara unika inom frågan.");
                }
            }
        }
        if (q.type() == QuestionType.SCALE) {
            if (q.scaleMin() == null || q.scaleMax() == null || q.scaleMin() >= q.scaleMax()) {
                throw new ApiException(400, "INVALID_SCALE", "Skalfrågor måste ha minvärde mindre än maxvärde.");
            }
        }
    }

    private SurveySummary toSummary(Survey survey) {
        return new SurveySummary(survey.id, survey.title, survey.description, survey.status,
                survey.createdAt, survey.updatedAt, survey.questions.size());
    }

    private SurveyView toView(Survey survey) {
        return new SurveyView(survey.id, survey.title, survey.description, survey.status,
                survey.createdAt, survey.updatedAt, survey.version,
                survey.questions.stream().map(this::toQuestionView).toList());
    }

    private QuestionView toQuestionView(SurveyQuestion q) {
        return new QuestionView(q.id, q.position, q.type, q.text, q.required,
                q.scaleMin, q.scaleMax, q.scaleMinLabel, q.scaleMaxLabel,
                q.options.stream().map(o -> new OptionView(o.id, o.position, o.value, o.label)).toList());
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
