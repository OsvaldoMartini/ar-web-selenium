package com.allinweb.ch.socket;

import com.allinweb.ch.facade.execution.MultiExecutionDatasetLoader.PreparedDataset;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.websocket.Session;

/** Short-lived exact-transport registry for immutable Main Dashboard multi-run preparation. */
public final class MainDashboardMultiExecutionRegistry {
    private static final MainDashboardMultiExecutionRegistry INSTANCE =
            new MainDashboardMultiExecutionRegistry();
    private static final int MAX_BATCHES = 16;
    private static final Duration MAX_AGE = Duration.ofMinutes(10);
    private final LinkedHashMap<String, Batch> batches = new LinkedHashMap<>();

    public static MainDashboardMultiExecutionRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized String create(Session transport, Map<Integer, PreparedJob> jobs) {
        requireDashboardTransport(transport);
        purgeExpired();
        String batchId = UUID.randomUUID().toString();
        batches.put(batchId, new Batch(batchId, transport, Instant.now(), Map.copyOf(jobs)));
        while (batches.size() > MAX_BATCHES) {
            batches.remove(batches.keySet().iterator().next());
        }
        return batchId;
    }

    public synchronized PreparedJob require(
            String batchId, int homeBankingId, int botJobId, String excelMode, Session transport) {
        purgeExpired();
        Batch batch = batches.get(batchId);
        if (batch == null || batch.transport() != transport) {
            throw new IllegalArgumentException("The multi-run preparation is unavailable or expired.");
        }
        requireDashboardTransport(transport);
        PreparedJob job = batch.jobs().get(botJobId);
        if (job == null
                || job.plan().owner().homeBankingId() != homeBankingId
                || !job.dataset().integration().mode().equals(excelMode)) {
            throw new IllegalArgumentException("The multi-run row does not match its frozen preparation.");
        }
        return job;
    }

    public synchronized void release(String batchId, Session transport) {
        Batch batch = batches.get(batchId);
        if (batch != null && batch.transport() == transport) batches.remove(batchId);
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(MAX_AGE);
        batches.values().removeIf(batch -> batch.createdAt().isBefore(cutoff)
                || batch.transport() == null
                || !batch.transport().isOpen());
    }

    private static void requireDashboardTransport(Session transport) {
        if (transport == null
                || !transport.isOpen()
                || WebSocketSessionManager.getSession("mainDashboard") != transport) {
            throw new IllegalArgumentException("The Main Dashboard requester is not authoritative.");
        }
    }

    public record PreparedJob(Plan plan, PreparedDataset dataset, com.google.gson.JsonObject workspaceSnapshot) {
        public PreparedJob {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(dataset, "dataset");
            workspaceSnapshot = Objects.requireNonNull(workspaceSnapshot, "workspaceSnapshot").deepCopy();
        }

        @Override
        public com.google.gson.JsonObject workspaceSnapshot() {
            return workspaceSnapshot.deepCopy();
        }
    }

    private record Batch(String id, Session transport, Instant createdAt, Map<Integer, PreparedJob> jobs) {}
}
