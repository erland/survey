package info.isaksson.erland.survey.live;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@ApplicationScoped
public class LiveEventService {
    private record Client(SseEventSink sink, Sse sse) {}

    private final Map<UUID, Set<Client>> clients = new ConcurrentHashMap<>();

    @Inject
    TransactionSynchronizationRegistry transactionRegistry;

    public void register(UUID runId, SseEventSink sink, Sse sse) {
        Client client = new Client(sink, sse);
        clients.computeIfAbsent(runId, ignored -> new CopyOnWriteArraySet<>()).add(client);
        send(client, runId, "connected");
    }

    public void publishAfterCommit(UUID runId, String type) {
        if (runId == null || type == null || type.isBlank()) return;
        try {
            transactionRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() {}
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) publish(runId, type);
                }
            });
        } catch (IllegalStateException noTransaction) {
            publish(runId, type);
        }
    }

    public void publish(UUID runId, String type) {
        Set<Client> runClients = clients.get(runId);
        if (runClients == null || runClients.isEmpty()) return;
        for (Client client : runClients) send(client, runId, type);
    }

    private void send(Client client, UUID runId, String type) {
        if (client.sink().isClosed()) {
            remove(runId, client);
            return;
        }
        String json = "{\"type\":\"" + escape(type) + "\"}";
        try {
            client.sink().send(client.sse().newEventBuilder()
                    .name(type)
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(String.class, json)
                    .reconnectDelay(3000)
                    .build())
                    .whenComplete((ignored, error) -> {
                        if (error != null || client.sink().isClosed()) remove(runId, client);
                    });
        } catch (RuntimeException e) {
            remove(runId, client);
        }
    }

    private void remove(UUID runId, Client client) {
        Set<Client> runClients = clients.get(runId);
        if (runClients == null) return;
        runClients.remove(client);
        if (runClients.isEmpty()) clients.remove(runId, runClients);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
