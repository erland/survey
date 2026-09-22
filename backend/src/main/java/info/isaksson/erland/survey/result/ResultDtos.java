package info.isaksson.erland.survey.result;

import info.isaksson.erland.survey.domain.QuestionType;
import java.util.List;
import java.util.UUID;

public final class ResultDtos {
    private ResultDtos() {}

    public record ChoiceCount(String value, String label, long count) {}
    public record ScaleCount(int value, long count) {}
    public record TextValue(String text, java.time.Instant updatedAt) {}

    public record QuestionResult(
            UUID questionId,
            int position,
            QuestionType type,
            String text,
            long responseCount,
            Long yesCount,
            Long noCount,
            List<ChoiceCount> choices,
            List<ScaleCount> scale,
            List<TextValue> texts
    ) {}
}
