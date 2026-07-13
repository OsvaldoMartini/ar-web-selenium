package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds the current Bot Job identity and per-surface bootstrap payload used by reused JavaFX WebViews.
 * Load listeners resolve this state when a page finishes loading instead of retaining the first opened job.
 */
final class BotJobDetailsWebViewBootstrap {

    private ActiveJob activeJob;
    private final Map<String, String> payloads = new HashMap<>();

    synchronized void activate(BotJobLoadDTO botJob) {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new IllegalArgumentException("A Bot Job is required for the WebView bootstrap");
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

    synchronized boolean updatePayload(int botJobId, String sessionId, String jsonData) {
        if (activeJob == null || activeJob.botJobId() != botJobId) return false;
        payloads.put(safe(sessionId), safeJson(jsonData));
        return true;
    }

    synchronized boolean deactivate(int botJobId) {
        if (activeJob == null || activeJob.botJobId() != botJobId) return false;
        activeJob = null;
        payloads.clear();
        return true;
    }

    synchronized Context resolve(String sessionId) {
        if (activeJob == null) {
            throw new IllegalStateException("Bot Job WebView bootstrap is not active");
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

    static String initializationScript(Context context, int port, Gson gson) {
        if (context == null || gson == null) {
            throw new IllegalArgumentException("Bot Job WebView context and JSON encoder are required");
        }
        return "window.receiveDataFromJava(JSON.stringify(" + context.jsonData() + "), " + port + ", "
                + gson.toJson(context.sessionId()) + ", " + context.homeBankingId() + ", "
                + gson.toJson(context.organizationName()) + ", " + context.botJobId() + ", "
                + gson.toJson(context.botJobName()) + ")";
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

    record Context(
            String sessionId,
            String jsonData,
            int homeBankingId,
            String organizationName,
            int botJobId,
            String botJobName) {}
}
