package info.isaksson.erland.survey.exporting;

import info.isaksson.erland.survey.domain.ParticipantSessionStatus;
import info.isaksson.erland.survey.domain.QuestionType;
import info.isaksson.erland.survey.domain.SurveyRunStatus;
import java.time.Instant;
import java.util.List;

public final class RunResultExportDtos {
    private RunResultExportDtos() {}

    public record ResultExportDocument(
            String format,
            int version,
            Instant exportedAt,
            RunMetadata run,
            List<QuestionExport> questions,
            List<ParticipantExport> responses
    ) {}

    public record RunMetadata(
            String title,
            SurveyRunStatus status,
            String publicId,
            String joinCode,
            Instant createdAt,
            Instant openedAt,
            Instant closedAt,
            Instant opensAt,
            Instant closesAt
    ) {}

    public record QuestionExport(
            String key,
            int position,
            QuestionType type,
            String text,
            boolean required,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            List<OptionExport> options
    ) {}

    public record OptionExport(String value, String label, int position) {}

    public record ParticipantExport(
            String participantId,
            ParticipantSessionStatus status,
            Instant startedAt,
            Instant submittedAt,
            List<AnswerExport> answers
    ) {}

    public record AnswerExport(
            String questionKey,
            String textValue,
            Boolean booleanValue,
            Integer numericValue,
            List<String> optionValues,
            Instant updatedAt
    ) {}
}
