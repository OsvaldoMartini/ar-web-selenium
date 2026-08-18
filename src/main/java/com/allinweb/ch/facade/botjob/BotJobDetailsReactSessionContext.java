package com.allinweb.ch.facade.botjob;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds the current Bot Job identity and per-surface bootstrap payload used by React sessions.
 */
public final class BotJobDetailsReactSessionContext {

    private ActiveJob activeJob;
    private final Map<String, String> payloads = new HashMap<>();

    public synchronized void activate(BotJobLoadDTO botJob) {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new IllegalArgumentException("A Bot Job is required for the React session context");
        }
        String organizationName = botJob.getHomeBankingLoadDTO() == null
                ? ""
                : safe(botJob.getHomeBankingLoadDTO().getName());
        activeJob = new ActiveJob(
                botJob.getId(),
                value(botJob.getHomeBankingId()),
                organizationName,
                safe(botJob.getName()));
        payloads.clear();
    }

    public synchronized boolean updatePayload(int botJobId, String sessionId, String jsonData) {
        if (activeJob == null || activeJob.botJobId() != botJobId) return false;
        payloads.put(safe(sessionId), safeJson(jsonData));
        return true;
    }

    public synchronized boolean deactivate(int botJobId) {
        if (activeJob == null || activeJob.botJobId() != botJobId) return false;
        activeJob = null;
        payloads.clear();
        return true;
    }

    public synchronized Context resolve(String sessionId) {
        if (activeJob == null) {
            throw new IllegalStateException("Bot Job React session context is not active");
        }
        String normalizedSessionId = safe(sessionId);
        return new Context(
                normalizedSessionId,
                payloads.getOrDefault(normalizedSessionId, "[]"),
                activeJob.homeBankingId(),
                activeJob.organizationName(),
                activeJob.botJobId(),
                activeJob.botJobName());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeJson(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }

    private record ActiveJob(int botJobId, int homeBankingId, String organizationName, String botJobName) {}

    public record Context(
            String sessionId,
            String jsonData,
            int homeBankingId,
            String organizationName,
            int botJobId,
            String botJobName) {}
}
