package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.auth.AccountAccessService;
import info.isaksson.erland.survey.domain.QuestionOption;
import info.isaksson.erland.survey.domain.Survey;
import info.isaksson.erland.survey.domain.SurveyQuestion;
import info.isaksson.erland.survey.domain.SurveyRepository;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

import static info.isaksson.erland.survey.exporting.SurveyExportDtos.*;

@ApplicationScoped
public class SurveyExportService {
    public static final String FORMAT = "survey-definition";
    public static final int VERSION = 1;

    @Inject SurveyRepository surveyRepository;
    @Inject AccountAccessService accountAccess;

    public SurveyDefinitionExport exportDefinition(UUID userId, UUID accountId, UUID surveyId) {
        Survey survey = accountAccess.requireSurvey(userId, accountId, surveyId);

        return new SurveyDefinitionExport(
                FORMAT,
                VERSION,
                Instant.now(),
                new SurveyDefinition(
                        survey.title,
                        survey.description,
                        survey.questions.stream().map(this::question).toList()));
    }

    private ExportQuestion question(SurveyQuestion question) {
        return new ExportQuestion(
                question.type,
                question.text,
                question.required,
                question.scaleMin,
                question.scaleMax,
                question.scaleMinLabel,
                question.scaleMaxLabel,
                question.options.stream().map(this::option).toList());
    }

    private ExportOption option(QuestionOption option) {
        return new ExportOption(option.value, option.label);
    }
}
