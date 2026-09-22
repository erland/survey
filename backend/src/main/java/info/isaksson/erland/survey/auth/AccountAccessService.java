package info.isaksson.erland.survey.auth;

import info.isaksson.erland.survey.domain.*;
import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AccountAccessService {
    @Inject SurveyAccountAdminRepository memberships;
    @Inject SurveyRepository surveys;
    @Inject SurveyRunRepository runs;

    public UUID requireSingleAccount(UUID userId) {
        List<SurveyAccountAdmin> rows = memberships.find("id.adminUserId = ?1 order by createdAt, id.surveyAccountId", userId).list();
        if (rows.isEmpty()) {
            throw new ApiException(403, "NO_SURVEY_ACCOUNT", "Administratören är inte kopplad till något enkätkonto.");
        }
        if (rows.size() > 1) {
            throw new ApiException(409, "ACCOUNT_SELECTION_REQUIRED", "Välj vilket enkätkonto du vill arbeta i.");
        }
        return rows.getFirst().id.surveyAccountId;
    }

    public void requireMembership(UUID userId, UUID accountId) {
        if (memberships.findByIdOptional(new SurveyAccountAdminId(accountId, userId)).isEmpty()) {
            throw new ApiException(403, "ACCOUNT_ACCESS_DENIED", "Du har inte behörighet till enkätkontot.");
        }
    }

    public Survey requireSurvey(UUID userId, UUID accountId, UUID surveyId) {
        requireMembership(userId, accountId);
        return surveys.find("id = ?1 and surveyAccountId = ?2", surveyId, accountId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "SURVEY_NOT_FOUND", "Enkäten kunde inte hittas."));
    }

    public SurveyRun requireRun(UUID userId, UUID accountId, UUID runId) {
        requireMembership(userId, accountId);
        return runs.find("id = ?1 and survey.surveyAccountId = ?2", runId, accountId)
                .firstResultOptional()
                .orElseThrow(() -> new ApiException(404, "RUN_NOT_FOUND", "Enkätgenomförandet kunde inte hittas."));
    }
}
