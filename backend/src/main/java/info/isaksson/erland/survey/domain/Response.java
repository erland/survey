package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "response", uniqueConstraints = @UniqueConstraint(name = "uq_response_session_question", columnNames = {"participant_session_id", "run_question_id"}))
public class Response extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "participant_session_id") public ParticipantSession participantSession;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "run_question_id") public SurveyRunQuestion question;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ResponseValue> values = new ArrayList<>();
}
