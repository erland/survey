package info.isaksson.erland.survey.participant;

import info.isaksson.erland.survey.domain.ParticipantSessionStatus;
import java.time.Instant;
import java.util.UUID;

public final class ParticipantDtos {
    private ParticipantDtos() {}

    public record ParticipantHeartbeatResponse(Instant lastActivityAt) {}

    public record ParticipantSessionResponse(
            UUID sessionId,
            String participantToken,
            ParticipantSessionStatus status,
            Instant startedAt,
            Instant lastActivityAt,
            boolean resumed
    ) {}
}
