package com.allinweb.ch.component.pane;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class ScannerScreenshotLoop {
    private final ScheduledExecutorService scheduler;
    private final Operations operations;
    private ScheduledFuture<?> screenshotFuture;

    ScannerScreenshotLoop(ScheduledExecutorService scheduler, Operations operations) {
        this.scheduler = scheduler;
        this.operations = operations;
    }

    void start() {
        if (screenshotFuture != null && !screenshotFuture.isCancelled() && !screenshotFuture.isDone()) {
            return;
        }

        screenshotFuture = scheduler.scheduleAtFixedRate(this::tick, 0, 500, TimeUnit.MILLISECONDS);
    }

    void stop() {
        if (screenshotFuture != null) {
            screenshotFuture.cancel(true);
            screenshotFuture = null;
        }
    }

    private void tick() {
        try {
            if (!operations.isJobRunning()) {
                stop();
                return;
            }
            operations.sendScreenshotIfAvailable();
        } catch (Exception error) {
            operations.reportScreenshotLoopError(error);
        }
    }

    interface Operations {
        boolean isJobRunning();

        void sendScreenshotIfAvailable();

        void reportScreenshotLoopError(Exception error);
    }
}
