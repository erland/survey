package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ParticipantSessionRepository implements PanacheRepositoryBase<ParticipantSession, UUID> {
    public Optional<ParticipantSession> findByRunAndTokenHash(UUID runId, String tokenHash) {
        return find("run.id = ?1 and clientTokenHash = ?2", runId, tokenHash).firstResultOptional();
    }

    public long countActiveForRun(UUID runId, Instant activeSince) {
        return count("run.id = ?1 and status = ?2 and lastActivityAt >= ?3",
                runId, ParticipantSessionStatus.ACTIVE, activeSince);
    }
}
