package com.allinweb.ch.facade;

/** Coordinates scanner shell shutdown without depending on JavaFX event types. */
public final class ScannerCloseRequestService {
    public void close(CloseRequest request) {
        request.interruptThreads();
        if (!request.hasWebDriver()) {
            return;
        }

        try {
            request.closeWebDrivers();
            request.quitCurrentDriver();
            request.clearCurrentDriver();
            request.shutdownExecutors();
        } catch (Exception error) {
            request.closeFailed(error);
        }
    }

    public interface CloseRequest {
        void interruptThreads();

        boolean hasWebDriver();

        void closeWebDrivers();

        void quitCurrentDriver();

        void clearCurrentDriver();

        void shutdownExecutors();

        void closeFailed(Exception error);
    }
}
