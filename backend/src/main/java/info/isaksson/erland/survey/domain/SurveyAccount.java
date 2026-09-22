package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "survey_account")
public class SurveyAccount extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false, length = 300) public String name;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}
