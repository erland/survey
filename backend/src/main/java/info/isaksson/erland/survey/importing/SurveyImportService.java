package info.isaksson.erland.survey.importing;

import info.isaksson.erland.survey.domain.SurveyStatus;
import info.isaksson.erland.survey.exporting.SurveyExportDtos;
import info.isaksson.erland.survey.exporting.SurveyExportDtos.ExportOption;
import info.isaksson.erland.survey.exporting.SurveyExportDtos.ExportQuestion;
import info.isaksson.erland.survey.exporting.SurveyExportDtos.SurveyDefinitionExport;
import info.isaksson.erland.survey.exporting.SurveyExportService;
import info.isaksson.erland.survey.surveyapi.ApiException;
import info.isaksson.erland.survey.surveyapi.SurveyDtos.OptionInput;
import info.isaksson.erland.survey.surveyapi.SurveyDtos.QuestionInput;
import info.isaksson.erland.survey.surveyapi.SurveyDtos.SurveyInput;
import info.isaksson.erland.survey.surveyapi.SurveyDtos.SurveyView;
import info.isaksson.erland.survey.surveyapi.SurveyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SurveyImportService {
    @Inject SurveyService surveyService;

    @Transactional
    public SurveyView importDefinition(UUID userId, SurveyDefinitionExport document) {
        validateEnvelope(document);
        var definition = document.survey();
        List<QuestionInput> questions = definition.questions() == null ? List.of() : definition.questions().stream()
                .map(this::question)
                .toList();
        return surveyService.create(userId,
                new SurveyInput(definition.title(), definition.description(), SurveyStatus.DRAFT, questions));
    }

    @Transactional
    public SurveyView importDefinition(UUID userId, UUID accountId, SurveyDefinitionExport document) {
        validateEnvelope(document);
        var definition = document.survey();
        List<QuestionInput> questions = definition.questions() == null ? List.of() : definition.questions().stream()
                .map(this::question)
                .toList();
        return surveyService.create(userId, accountId,
                new SurveyInput(definition.title(), definition.description(), SurveyStatus.DRAFT, questions));
    }

    private void validateEnvelope(SurveyDefinitionExport document) {
        if (document == null) {
            throw new ApiException(400, "INVALID_IMPORT", "Importfilen saknar innehåll.");
        }
        if (!SurveyExportService.FORMAT.equals(document.format())) {
            throw new ApiException(400, "UNSUPPORTED_IMPORT_FORMAT", "Importfilen har ett format som inte stöds.");
        }
        if (document.version() != SurveyExportService.VERSION) {
            throw new ApiException(400, "UNSUPPORTED_IMPORT_VERSION", "Importfilens version stöds inte.");
        }
        if (document.survey() == null) {
            throw new ApiException(400, "INVALID_IMPORT", "Importfilen saknar enkätdefinition.");
        }
    }

    private QuestionInput question(ExportQuestion question) {
        if (question == null) {
            throw new ApiException(400, "INVALID_IMPORT", "Importfilen innehåller en tom fråga.");
        }
        List<OptionInput> options = question.options() == null ? List.of() : question.options().stream()
                .map(this::option)
                .toList();
        return new QuestionInput(question.type(), question.text(), question.required(),
                question.scaleMin(), question.scaleMax(), question.scaleMinLabel(), question.scaleMaxLabel(), options);
    }

    private OptionInput option(ExportOption option) {
        if (option == null) {
            throw new ApiException(400, "INVALID_IMPORT", "Importfilen innehåller ett tomt svarsalternativ.");
        }
        return new OptionInput(option.value(), option.label());
    }
}
