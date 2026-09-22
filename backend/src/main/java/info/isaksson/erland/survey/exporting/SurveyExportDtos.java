package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.domain.QuestionType;

import java.time.Instant;
import java.util.List;

public final class SurveyExportDtos {
    private SurveyExportDtos() {}

    public record SurveyDefinitionExport(
            String format,
            int version,
            Instant exportedAt,
            SurveyDefinition survey) {}

    public record SurveyDefinition(
            String title,
            String description,
            List<ExportQuestion> questions) {}

    public record ExportQuestion(
            QuestionType type,
            String text,
            boolean required,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            List<ExportOption> options) {}

    public record ExportOption(String value, String label) {}
}
