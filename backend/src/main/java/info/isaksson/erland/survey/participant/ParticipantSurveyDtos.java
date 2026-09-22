package info.isaksson.erland.survey.participant;

import info.isaksson.erland.survey.domain.QuestionType;
import java.util.List;
import java.util.UUID;

public final class ParticipantSurveyDtos {
    private ParticipantSurveyDtos() {}

    public record ParticipantOptionResponse(
            UUID id,
            int position,
            String value,
            String label
    ) {}

    public record ParticipantQuestionResponse(
            UUID id,
            int position,
            QuestionType type,
            String text,
            boolean required,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            List<ParticipantOptionResponse> options
    ) {}

    public record ParticipantSurveyResponse(
            String publicId,
            String title,
            List<ParticipantQuestionResponse> questions
    ) {}
}
