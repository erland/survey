package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "survey_run", uniqueConstraints = {
        @UniqueConstraint(name = "uq_survey_run_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uq_survey_run_join_code", columnNames = "join_code")
})
public class SurveyRun extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "survey_id") public Survey survey;
    @Column(name = "created_by", nullable = false) public UUID createdBy;
    @Column(name = "public_id", nullable = false, length = 64) public String publicId;
    @Column(name = "join_code", nullable = false, length = 12) public String joinCode;
    @Column(nullable = false, length = 300) public String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) public SurveyRunStatus status = SurveyRunStatus.DRAFT;
    @Column(name = "opens_at") public Instant opensAt;
    @Column(name = "closes_at") public Instant closesAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "opened_at") public Instant openedAt;
    @Column(name = "closed_at") public Instant closedAt;
    @Version @Column(nullable = false) public long version;
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") public List<SurveyRunQuestion> questions = new ArrayList<>();
}
