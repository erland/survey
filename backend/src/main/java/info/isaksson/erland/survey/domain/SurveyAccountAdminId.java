package info.isaksson.erland.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SurveyAccountAdminId implements Serializable {
    @Column(name = "survey_account_id")
    public UUID surveyAccountId;

    @Column(name = "admin_user_id")
    public UUID adminUserId;

    public SurveyAccountAdminId() { }

    public SurveyAccountAdminId(UUID surveyAccountId, UUID adminUserId) {
        this.surveyAccountId = surveyAccountId;
        this.adminUserId = adminUserId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SurveyAccountAdminId that)) return false;
        return Objects.equals(surveyAccountId, that.surveyAccountId)
                && Objects.equals(adminUserId, that.adminUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(surveyAccountId, adminUserId);
    }
}
