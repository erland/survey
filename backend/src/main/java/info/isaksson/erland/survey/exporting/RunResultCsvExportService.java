package info.isaksson.erland.survey.exporting;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static info.isaksson.erland.survey.exporting.RunResultExportDtos.*;

@ApplicationScoped
public class RunResultCsvExportService {
    @Inject RunResultExportService jsonExportService;

    public byte[] export(UUID ownerId, UUID runId) {
        ResultExportDocument document = jsonExportService.export(ownerId, runId);
        StringBuilder csv = new StringBuilder();
        csv.append("participant_id,status,started_at,submitted_at");
        for (QuestionExport question : document.questions()) {
            csv.append(',').append(quote(question.key() + "__" + sanitizeHeader(question.text())));
        }
        csv.append("\r\n");

        for (ParticipantExport participant : document.responses()) {
            Map<String, AnswerExport> answers = new HashMap<>();
            for (AnswerExport answer : participant.answers()) answers.put(answer.questionKey(), answer);

            csv.append(quote(participant.participantId())).append(',')
               .append(quote(participant.status().name())).append(',')
               .append(quote(participant.startedAt() == null ? "" : participant.startedAt().toString())).append(',')
               .append(quote(participant.submittedAt() == null ? "" : participant.submittedAt().toString()));

            for (QuestionExport question : document.questions()) {
                csv.append(',').append(quote(renderAnswer(answers.get(question.key()))));
            }
            csv.append("\r\n");
        }
        // UTF-8 BOM improves Excel auto-detection while remaining valid UTF-8 text.
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
        byte[] out = Arrays.copyOf(bom, bom.length + body.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }

    private String renderAnswer(AnswerExport answer) {
        if (answer == null) return "";
        if (answer.textValue() != null) return answer.textValue();
        if (answer.booleanValue() != null) return answer.booleanValue() ? "Ja" : "Nej";
        if (answer.numericValue() != null) return answer.numericValue().toString();
        if (answer.optionValues() != null && !answer.optionValues().isEmpty()) return String.join(";", answer.optionValues());
        return "";
    }

    private String sanitizeHeader(String text) {
        if (text == null || text.isBlank()) return "question";
        String compact = text.trim().replaceAll("\\s+", " ");
        return compact.length() <= 80 ? compact : compact.substring(0, 80);
    }

    static String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
