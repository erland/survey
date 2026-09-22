package info.isaksson.erland.survey.run;

import info.isaksson.erland.survey.domain.QuestionOption;
import info.isaksson.erland.survey.domain.Survey;
import info.isaksson.erland.survey.domain.SurveyQuestion;
import info.isaksson.erland.survey.domain.SurveyRepository;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunOption;
import info.isaksson.erland.survey.domain.SurveyRunQuestion;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.domain.SurveyRunStatus;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SurveyRunSnapshotService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    @Inject SurveyRepository surveyRepository;
    @Inject SurveyRunRepository runRepository;

    @Transactional
    public SurveyRun createDraft(UUID ownerId, UUID surveyId, String title) {
        Survey source = surveyRepository.find("id = ?1 and ownerId = ?2", surveyId, ownerId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "SURVEY_NOT_FOUND", "Enkäten kunde inte hittas."));

        SurveyRun run = new SurveyRun();
        run.id = UUID.randomUUID();
        run.survey = source;
        run.createdBy = ownerId;
        run.publicId = generateUniquePublicId();
        run.joinCode = generateUniqueJoinCode();
        run.title = normalizeTitle(title, source.title);
        run.status = SurveyRunStatus.DRAFT;
        run.createdAt = Instant.now();

        for (SurveyQuestion sourceQuestion : source.questions) {
            SurveyRunQuestion question = snapshotQuestion(run, sourceQuestion);
            run.questions.add(question);
        }

        runRepository.persist(run);
        runRepository.flush();
        return run;
    }

    private SurveyRunQuestion snapshotQuestion(SurveyRun run, SurveyQuestion source) {
        SurveyRunQuestion question = new SurveyRunQuestion();
        question.id = UUID.randomUUID();
        question.run = run;
        question.sourceQuestionId = source.id;
        question.position = source.position;
        question.type = source.type;
        question.text = source.text;
        question.required = source.required;
        question.scaleMin = source.scaleMin;
        question.scaleMax = source.scaleMax;
        question.scaleMinLabel = source.scaleMinLabel;
        question.scaleMaxLabel = source.scaleMaxLabel;

        for (QuestionOption sourceOption : source.options) {
            SurveyRunOption option = new SurveyRunOption();
            option.id = UUID.randomUUID();
            option.question = question;
            option.sourceOptionId = sourceOption.id;
            option.position = sourceOption.position;
            option.value = sourceOption.value;
            option.label = sourceOption.label;
            question.options.add(option);
        }
        return question;
    }

    private String normalizeTitle(String requestedTitle, String surveyTitle) {
        if (requestedTitle == null || requestedTitle.isBlank()) {
            return surveyTitle;
        }
        String trimmed = requestedTitle.trim();
        if (trimmed.length() > 300) {
            throw new ApiException(400, "INVALID_RUN", "Genomförandets titel får vara högst 300 tecken.");
        }
        return trimmed;
    }

    private String generateUniquePublicId() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = UUID.randomUUID().toString().replace("-", "");
            if (runRepository.count("publicId", candidate) == 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("Kunde inte generera ett unikt publicId för enkätgenomförandet.");
    }

    private String generateUniqueJoinCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                code.append(JOIN_CODE_ALPHABET[RANDOM.nextInt(JOIN_CODE_ALPHABET.length)]);
            }
            String candidate = code.toString();
            if (runRepository.count("joinCode", candidate) == 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("Kunde inte generera en unik anslutningskod för enkätgenomförandet.");
    }
}
