package info.isaksson.erland.survey.presentation;

import info.isaksson.erland.survey.domain.PresentationToken;
import info.isaksson.erland.survey.domain.PresentationTokenRepository;
import info.isaksson.erland.survey.domain.SurveyRun;
import info.isaksson.erland.survey.domain.SurveyRunRepository;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static info.isaksson.erland.survey.presentation.PresentationDtos.CreatedPresentationToken;

@ApplicationScoped
public class PresentationTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject PresentationTokenRepository tokens;
    @Inject SurveyRunRepository runs;

    @ConfigProperty(name = "app.presentation.token-hours", defaultValue = "12")
    long tokenHours;

    @Transactional
    public CreatedPresentationToken create(UUID ownerId, UUID runId) {
        SurveyRun run = ownedRun(ownerId, runId);
        String rawToken = newToken();
        Instant now = Instant.now();
        PresentationToken token = new PresentationToken();
        token.id = UUID.randomUUID();
        token.run = run;
        token.createdBy = ownerId;
        token.tokenHash = sha256(rawToken);
        token.createdAt = now;
        token.expiresAt = now.plus(Duration.ofHours(tokenHours));
        tokens.persist(token);
        return new CreatedPresentationToken(token.id, rawToken, token.expiresAt, "/present/" + rawToken);
    }

    @Transactional
    public void revoke(UUID ownerId, UUID runId, UUID tokenId) {
        PresentationToken token = tokens.find("id = ?1 and run.id = ?2 and createdBy = ?3", tokenId, runId, ownerId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "PRESENTATION_TOKEN_NOT_FOUND", "Presentationstoken kunde inte hittas."));
        if (token.revokedAt == null) token.revokedAt = Instant.now();
    }

    @Transactional
    public UUID authenticateRunId(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        Instant now = Instant.now();
        PresentationToken token = tokens.find("tokenHash = ?1 and revokedAt is null and expiresAt > ?2", sha256(rawToken), now)
                .firstResultOptional()
                .orElseThrow(this::invalidToken);
        return token.run.id;
    }

    private SurveyRun ownedRun(UUID ownerId, UUID runId) {
        return runs.find("id = ?1 and createdBy = ?2", runId, ownerId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
    }

    private ApiException invalidToken() {
        return new ApiException(401, "PRESENTATION_TOKEN_INVALID", "Presentationslänken är ogiltig eller har gått ut.");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
