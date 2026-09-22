package info.isaksson.erland.survey.participant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ParticipantResponseDtos {
    private ParticipantResponseDtos() {}

    public record SaveAnswerRequest(String textValue, Boolean booleanValue, Integer numericValue, List<String> optionValues) {}
    public record AnswerResponse(UUID questionId, String textValue, Boolean booleanValue, Integer numericValue, List<String> optionValues, Instant updatedAt) {}
    public record AnswersResponse(List<AnswerResponse> answers) {}
}
