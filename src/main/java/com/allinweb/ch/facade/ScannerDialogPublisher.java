package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
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

    private boolean publish(String operationId, DialogEvent event) {
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
}
