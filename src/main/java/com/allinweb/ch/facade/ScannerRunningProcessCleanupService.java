package com.allinweb.ch.facade;

/** Resets scanner runtime state after a browser/job run without depending on JavaFX controls. */
public final class ScannerRunningProcessCleanupService {
    public void cleanup(Operations operations) {
        operations.clearCloneSelection();
        operations.enableLaunchAction();
        operations.revertCloneInjections();
        operations.revertHoverPickInjections();
        if (operations.isJobRunning()) {
            operations.interceptBotJob();
        }
    }

    public interface Operations {
        void clearCloneSelection();

        void enableLaunchAction();

        void revertCloneInjections();

        void revertHoverPickInjections();

        boolean isJobRunning();

        void interceptBotJob();
    }
}
