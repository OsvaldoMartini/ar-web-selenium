package com.allinweb.ch.facade;

import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/** Process-wide scanner support handler registry, kept free of presentation dependencies. */
@Slf4j
public final class ScannerSupportRequestHandlers {
    private static final ScannerSupportRequestHandlers INSTANCE = new ScannerSupportRequestHandlers();

    private final AtomicReference<ScannerSupportRequestHandler> activeHandler = new AtomicReference<>();

    private ScannerSupportRequestHandlers() {}

    public static ScannerSupportRequestHandlers getInstance() {
        return INSTANCE;
    }

    public void register(ScannerSupportRequestHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Scanner support request handler is required");
        }
        activeHandler.set(handler);
    }

    public void unregister(ScannerSupportRequestHandler handler) {
        if (handler != null) {
            activeHandler.compareAndSet(handler, null);
        }
    }

    public void handleDomReviewResponse(String action) {
        ScannerSupportRequestHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Ignoring DOM review response because no scanner support handler is registered");
            return;
        }
        handler.handleDomReviewResponse(action);
    }

    public void handleSupportRequestResponse(String action, String message) {
        ScannerSupportRequestHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Ignoring support request response because no scanner support handler is registered");
            return;
        }
        handler.handleSupportRequestResponse(action, message);
    }

    public void requestSupportElements() {
        ScannerSupportRequestHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Ignoring support elements request because no scanner support handler is registered");
            return;
        }
        handler.requestSupportElements();
    }

    public void handleSupportRequestElementsResponse(String action, String message, String elementDetailsJson) {
        ScannerSupportRequestHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Ignoring support elements response because no scanner support handler is registered");
            return;
        }
        handler.handleSupportRequestElementsResponse(action, message, elementDetailsJson);
    }
}
