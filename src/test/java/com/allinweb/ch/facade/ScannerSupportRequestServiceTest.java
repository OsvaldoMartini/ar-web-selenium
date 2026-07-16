package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerSupportRequestServiceTest {
    private final Gson gson = new Gson();

    @Test
    void sendsSupportRequestWithBrowserUrl() {
        RecordingSender sender = new RecordingSender();
        ScannerSupportRequestService service = service(sender);

        String destination = service.requestSupport(9, browser("https://bank.example"));

        Message message = sender.messages.get(0);
        JsonObject body = gson.fromJson(message.json, JsonObject.class);
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, destination);
        assertEquals(9, message.homeBankingId);
        assertEquals(ScannerSupportRequestPublisher.REQUEST_SUPPORT, message.operationId);
        assertEquals("https://bank.example", body.get("url").getAsString());
    }

    @Test
    void sendsElementsSupportRequestWithFallbackUrl() {
        RecordingSender sender = new RecordingSender();
        ScannerSupportRequestService service = service(sender);

        String destination = service.requestElementsSupport(10, noBrowser());

        Message message = sender.messages.get(0);
        JsonObject body = gson.fromJson(message.json, JsonObject.class);
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, destination);
        assertEquals(10, message.homeBankingId);
        assertEquals(ScannerSupportRequestPublisher.REQUEST_SUPPORT_ELEMENTS, message.operationId);
        assertEquals("(no browser)", body.get("url").getAsString());
    }

    private static ScannerSupportRequestService service(RecordingSender sender) {
        return new ScannerSupportRequestService(
                new ScannerSupportRequestPublisher(sender, new FixedSystemContext()), new ScannerBrowserUrlService());
    }

    private static ScannerBrowserUrlService.Browser browser(String url) {
        return new ScannerBrowserUrlService.Browser() {
            @Override
            public boolean hasCurrentDriver() {
                return true;
            }

            @Override
            public String currentUrl() {
                return url;
            }
        };
    }

    private static ScannerBrowserUrlService.Browser noBrowser() {
        return new ScannerBrowserUrlService.Browser() {
            @Override
            public boolean hasCurrentDriver() {
                return false;
            }

            @Override
            public String currentUrl() {
                return "";
            }
        };
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
