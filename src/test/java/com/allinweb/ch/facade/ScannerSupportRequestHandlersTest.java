package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScannerSupportRequestHandlersTest {
    private final ScannerSupportRequestHandlers registry = ScannerSupportRequestHandlers.getInstance();
    private RecordingHandler handler;

    @AfterEach
    void cleanup() {
        registry.unregister(handler);
    }

    @Test
    void routesSupportResponsesToRegisteredHandler() {
        handler = new RecordingHandler();
        registry.register(handler);

        registry.handleDomReviewResponse("send");
        registry.handleSupportRequestResponse("cancel", "message");
        registry.requestSupportElements();
        registry.handleSupportRequestElementsResponse("save", "details", "[{}]");

        assertEquals(
                List.of(
                        "dom:send",
                        "support:cancel:message",
                        "request-elements",
                        "support-elements:save:details:[{}]"),
                handler.calls);
    }

    @Test
    void ignoresMessagesWhenNoHandlerIsRegistered() {
        registry.handleDomReviewResponse("send");
        registry.handleSupportRequestResponse("send", "message");
        registry.requestSupportElements();
        registry.handleSupportRequestElementsResponse("send", "message", "[]");
    }

    private static final class RecordingHandler implements ScannerSupportRequestHandler {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void handleDomReviewResponse(String action) {
            calls.add("dom:" + action);
        }

        @Override
        public void handleSupportRequestResponse(String action, String message) {
            calls.add("support:" + action + ":" + message);
        }

        @Override
        public void requestSupportElements() {
            calls.add("request-elements");
        }

        @Override
        public void handleSupportRequestElementsResponse(String action, String message, String elementDetailsJson) {
            calls.add("support-elements:" + action + ":" + message + ":" + elementDetailsJson);
        }
    }
}
