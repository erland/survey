package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "survey_account_admin")
public class SurveyAccountAdmin extends PanacheEntityBase {
    @EmbeddedId
    public SurveyAccountAdminId id;

    @Column(nullable = false, length = 32)
    public String role = "ADMIN";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
