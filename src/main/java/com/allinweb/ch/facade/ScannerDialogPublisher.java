package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.PluginManifestDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScannerDialogPublisher {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    private static final String ALERT_OPERATION = "scanner.dialog.alert";
    private static final String TOAST_OPERATION = "scanner.dialog.toast";
    private static final String PROGRESS_OPERATION = "scanner.dialog.progress";
    private static final String PLUGIN_UPDATE_OPERATION = "scanner.dialog.pluginUpdate";
    private static final String PLUGIN_LIST_OPERATION = "scanner.dialog.pluginList";
    private static final ScannerDialogPublisher INSTANCE =
            new ScannerDialogPublisher(WebSocketSessionManager.getInstance(), new Gson());

    private final WebSocketSessionManager sessions;
    private final Gson gson;

    ScannerDialogPublisher(WebSocketSessionManager sessions, Gson gson) {
        this.sessions = sessions;
        this.gson = gson;
    }

    public static ScannerDialogPublisher getInstance() {
        return INSTANCE;
    }

    public boolean alert(Severity severity, String title, String header, String body) {
        return publish(
                ALERT_OPERATION,
                new DialogEvent("alert", severity.name().toLowerCase(), title, header, body, 0));
    }

    public boolean toast(Severity severity, String message, double seconds) {
        return publish(
                TOAST_OPERATION,
                new DialogEvent("toast", severity.name().toLowerCase(), "Scanner", message, message, seconds));
    }

    public boolean progress(String id, String title, String message, double progress, int current, int total) {
        return publish(
                PROGRESS_OPERATION,
                new ProgressEvent("progress", id, title, message, progress, current, total, false));
    }

    public boolean closeProgress(String id) {
        return publish(
                PROGRESS_OPERATION,
                new ProgressEvent("progress", id, "", "", 1.0, 0, 0, true));
    }

    public boolean pluginUpdate(String pluginsDir, boolean serverConfigured, java.util.List<String[]> rows) {
        return publish(
                PLUGIN_UPDATE_OPERATION,
                new PluginUpdateEvent("pluginUpdate", pluginsDir, serverConfigured, rows));
    }

    public boolean pluginList(PluginManifestDTO manifest, String serverBase, String pathPlugins) {
        return publish(
                PLUGIN_LIST_OPERATION,
                new PluginListEvent("pluginList", manifest, serverBase, pathPlugins));
    }

    private boolean publish(String operationId, Object event) {
        String body = gson.toJson(event);
        boolean sent =
                sessions.sendMessageJson(-1, ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE, body, operationId) != null;
        if (!sent) {
            sent = sessions.sendMessageJson(-1, ScannerWorkspaceSessions.SCANNER_GRID, body, operationId) != null;
        }
        if (!sent) {
            log.debug("No React scanner session available for {}", operationId);
        }
        return sent;
    }

    public record DialogEvent(
            String kind,
            String severity,
            String title,
            String header,
            String body,
            double seconds) {}

    public record ProgressEvent(
            String kind,
            String id,
            String title,
            String message,
            double progress,
            int current,
            int total,
            boolean close) {}

    public record PluginUpdateEvent(
            String kind,
            String pluginsDir,
            boolean serverConfigured,
            java.util.List<String[]> rows) {}

    public record PluginListEvent(
            String kind,
            PluginManifestDTO manifest,
            String serverBase,
            String pathPlugins) {}
}
