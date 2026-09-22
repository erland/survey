package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "participant_session", uniqueConstraints = {
        @UniqueConstraint(name = "uq_participant_session_token", columnNames = {"run_id", "client_token_hash"})
})
public class ParticipantSession extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "run_id") public SurveyRun run;
    @Column(name = "client_token_hash", nullable = false, length = 64) public String clientTokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) public ParticipantSessionStatus status = ParticipantSessionStatus.ACTIVE;
    @Column(name = "started_at", nullable = false) public Instant startedAt;
    @Column(name = "last_activity_at", nullable = false) public Instant lastActivityAt;
    @Column(name = "submitted_at") public Instant submittedAt;
    @Version @Column(nullable = false) public long version;
}
