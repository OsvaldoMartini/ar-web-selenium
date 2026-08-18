package com.allinweb.ch.facade;

public final class ScannerSearchCleanupService {
    public void beforeSearch(Operations operations) {
        if (operations.hasCurrentDriver()) {
            operations.switchToDefaultContent();
        }
        operations.clearPreviousXPath();
        operations.revertCloneInjections();
        operations.revertPickInjections();
    }

    public void afterSearchDelay(Operations operations, long delayMillis) {
        try {
            operations.sleep(delayMillis);
            operations.revertSearchTermsInjections();
        } catch (Exception ignored) {
            // Keeps legacy behavior: cleanup failures after a search are ignored.
        }
    }

    public interface Operations {
        boolean hasCurrentDriver();

        void switchToDefaultContent();

        void clearPreviousXPath();

        void revertCloneInjections();

        void revertPickInjections();

        void sleep(long millis) throws InterruptedException;

        void revertSearchTermsInjections();
    }
}
