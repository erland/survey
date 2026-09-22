package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "survey")
public class Survey extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name="survey_account_id", nullable=false) public UUID surveyAccountId;
    @Column(name="created_by_admin_user_id") public UUID createdByAdminUserId;
    @Column(nullable=false, length=300) public String title;
    @Column(columnDefinition="text") public String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) public SurveyStatus status = SurveyStatus.DRAFT;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
    @Version @Column(nullable=false) public long version;
    @OneToMany(mappedBy="survey", cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderBy("position ASC") public List<SurveyQuestion> questions = new ArrayList<>();
}
