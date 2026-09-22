package info.isaksson.erland.survey.surveyapi;

import info.isaksson.erland.survey.domain.QuestionType;
import info.isaksson.erland.survey.domain.SurveyStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SurveyDtos {
    private SurveyDtos() {}

    public record OptionInput(String value, String label) {}
    public record QuestionInput(
            QuestionType type,
            String text,
            boolean required,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            List<OptionInput> options) {}
    public record SurveyInput(String title, String description, SurveyStatus status, List<QuestionInput> questions) {}

    public record OptionView(UUID id, int position, String value, String label) {}
    public record QuestionView(
            UUID id,
            int position,
            QuestionType type,
            String text,
            boolean required,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            List<OptionView> options) {}
    public record SurveyView(
            UUID id,
            String title,
            String description,
            SurveyStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<QuestionView> questions) {}
    public record SurveySummary(
            UUID id,
            String title,
            String description,
            SurveyStatus status,
            Instant createdAt,
            Instant updatedAt,
            int questionCount) {}
}
