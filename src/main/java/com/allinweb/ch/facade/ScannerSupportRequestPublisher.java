package com.allinweb.ch.facade;

import com.allinweb.ch.license.SystemDetails;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;

public final class ScannerSupportRequestPublisher {
    public static final String SEND_DOM_REVIEW = "SEND_DOM_REVIEW";
    public static final String REQUEST_SUPPORT = "REQUEST_SUPPORT";
    public static final String REQUEST_SUPPORT_ELEMENTS = "REQUEST_SUPPORT_ELEMENTS";

    private final Sender sender;
    private final SystemContext systemContext;
    private final Gson gson = new Gson();

    public ScannerSupportRequestPublisher() {
        this(new WebSocketSender(), new DefaultSystemContext());
    }

    ScannerSupportRequestPublisher(Sender sender, SystemContext systemContext) {
        this.sender = sender;
        this.systemContext = systemContext;
    }

    public void publishDomReview(int homeBankingId, String currentUrl, String pageTitle, String rawHtml) {
        JsonObject body = baseBody(currentUrl);
        body.addProperty("title", pageTitle != null ? pageTitle : "");
        int htmlSizeKb = rawHtml == null ? 0 : rawHtml.getBytes(StandardCharsets.UTF_8).length / 1024;
        body.addProperty("htmlSizeKb", htmlSizeKb);
        send(homeBankingId, body, SEND_DOM_REVIEW);
    }

    public void publishSupportRequest(int homeBankingId, String currentUrl) {
        send(homeBankingId, baseBody(currentUrl), REQUEST_SUPPORT);
    }

    public void publishElementsSupportRequest(int homeBankingId, String currentUrl) {
        send(homeBankingId, baseBody(currentUrl), REQUEST_SUPPORT_ELEMENTS);
    }

    private JsonObject baseBody(String currentUrl) {
        JsonObject body = new JsonObject();
        body.addProperty("url", currentUrl);
        body.addProperty("pcName", systemContext.computerName());
        String email = systemContext.licenseEmail();
        body.addProperty("email", email != null ? email : "");
        return body;
    }

    private void send(int homeBankingId, JsonObject body, String operationId) {
        sender.sendMessageJson(homeBankingId, ScannerWorkspaceSessions.SCANNER_GRID, gson.toJson(body), operationId);
    }

    interface Sender {
        void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId);
    }

    interface SystemContext {
        String computerName();

        String licenseEmail();
    }

    private static final class WebSocketSender implements Sender {
        private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            sessions.sendMessageJson(homeBankingId, sessionId, json, operationId);
        }
    }

    private static final class DefaultSystemContext implements SystemContext {
        private final ARPropertyManager properties = ARPropertyManager.getInstance();

        @Override
        public String computerName() {
            return SystemDetails.getSystemComputerName();
        }

        @Override
        public String licenseEmail() {
            return properties.getProperty(ARPropertyEnum.LICENSE_EMAIL);
        }
    }
}
