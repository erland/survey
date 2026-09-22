package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "presentation_token", uniqueConstraints =
        @UniqueConstraint(name = "uq_presentation_token_hash", columnNames = "token_hash"))
public class PresentationToken extends PanacheEntityBase {
    @Id public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    public SurveyRun run;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "token_hash", nullable = false, length = 128)
    public String tokenHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;
}
