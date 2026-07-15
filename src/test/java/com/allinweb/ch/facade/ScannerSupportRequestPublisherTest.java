package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerSupportRequestPublisherTest {
    private final Gson gson = new Gson();

    @Test
    void publishesDomReviewContext() {
        RecordingSender sender = new RecordingSender();
        ScannerSupportRequestPublisher publisher =
                new ScannerSupportRequestPublisher(sender, new FixedSystemContext());

        publisher.publishDomReview(2, "https://bank.example", "Login", "1234567890");

        Message message = sender.messages.get(0);
        JsonObject body = gson.fromJson(message.json, JsonObject.class);
        assertEquals(2, message.homeBankingId);
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, message.sessionId);
        assertEquals(ScannerSupportRequestPublisher.SEND_DOM_REVIEW, message.operationId);
        assertEquals("https://bank.example", body.get("url").getAsString());
        assertEquals("Login", body.get("title").getAsString());
        assertEquals("pc-1", body.get("pcName").getAsString());
        assertEquals("user@example.test", body.get("email").getAsString());
        assertEquals(0, body.get("htmlSizeKb").getAsInt());
    }

    @Test
    void publishesSupportRequestContext() {
        RecordingSender sender = new RecordingSender();
        ScannerSupportRequestPublisher publisher =
                new ScannerSupportRequestPublisher(sender, new FixedSystemContext());

        publisher.publishSupportRequest(3, "(no browser)");

        Message message = sender.messages.get(0);
        JsonObject body = gson.fromJson(message.json, JsonObject.class);
        assertEquals(ScannerSupportRequestPublisher.REQUEST_SUPPORT, message.operationId);
        assertEquals("(no browser)", body.get("url").getAsString());
        assertEquals("pc-1", body.get("pcName").getAsString());
    }

    @Test
    void publishesElementsSupportRequestContext() {
        RecordingSender sender = new RecordingSender();
        ScannerSupportRequestPublisher publisher =
                new ScannerSupportRequestPublisher(sender, new FixedSystemContext());

        publisher.publishElementsSupportRequest(4, "https://bank.example/elements");

        Message message = sender.messages.get(0);
        assertEquals(ScannerSupportRequestPublisher.REQUEST_SUPPORT_ELEMENTS, message.operationId);
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, message.sessionId);
    }

    @Test
    void exposesDestinationSessionId() {
        ScannerSupportRequestPublisher publisher =
                new ScannerSupportRequestPublisher(new RecordingSender(), new FixedSystemContext());

        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, publisher.destinationSessionId());
    }

    @Test
    void exposesSupportResponseOperationIds() {
        assertEquals("DOM_REVIEW_RESPONSE", ScannerSupportRequestPublisher.DOM_REVIEW_RESPONSE);
        assertEquals("SUPPORT_REQUEST_RESPONSE", ScannerSupportRequestPublisher.SUPPORT_REQUEST_RESPONSE);
        assertEquals(
                "SUPPORT_REQUEST_ELEMENTS_RESPONSE",
                ScannerSupportRequestPublisher.SUPPORT_REQUEST_ELEMENTS_RESPONSE);
    }

    private static final class RecordingSender implements ScannerSupportRequestPublisher.Sender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            messages.add(new Message(homeBankingId, sessionId, json, operationId));
        }
    }

    private static final class FixedSystemContext implements ScannerSupportRequestPublisher.SystemContext {
        @Override
        public String computerName() {
            return "pc-1";
        }

        @Override
        public String licenseEmail() {
            return "user@example.test";
        }
    }

    private record Message(int homeBankingId, String sessionId, String json, String operationId) {}
}
