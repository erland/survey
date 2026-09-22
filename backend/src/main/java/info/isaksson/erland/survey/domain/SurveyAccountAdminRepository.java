package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SurveyAccountAdminRepository implements PanacheRepositoryBase<SurveyAccountAdmin, SurveyAccountAdminId> {
    public Optional<UUID> firstAccountIdForAdmin(UUID adminUserId) {
        return find("id.adminUserId = ?1 order by createdAt", adminUserId)
                .firstResultOptional()
                .map(membership -> membership.id.surveyAccountId);
    }
}
