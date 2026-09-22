package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public class ResponseRepository implements PanacheRepositoryBase<Response, UUID> {
    public Optional<Response> findBySessionAndQuestion(UUID sessionId, UUID questionId) {
        return find("participantSession.id = ?1 and question.id = ?2", sessionId, questionId).firstResultOptional();
    }
    public List<Response> listBySession(UUID sessionId) {
        return list("participantSession.id", sessionId);
    }
}
