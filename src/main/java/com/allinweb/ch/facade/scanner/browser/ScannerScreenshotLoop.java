package com.allinweb.ch.facade.scanner.browser;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ScannerScreenshotLoop {
    private final ScheduledExecutorService scheduler;
    private final Operations operations;
    private ScheduledFuture<?> screenshotFuture;

    public ScannerScreenshotLoop(ScheduledExecutorService scheduler, Operations operations) {
        this.scheduler = scheduler;
        this.operations = operations;
    }

    public void start() {
        if (screenshotFuture != null && !screenshotFuture.isCancelled() && !screenshotFuture.isDone()) {
            return;
        }

        screenshotFuture = scheduler.scheduleAtFixedRate(this::tick, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stop() {
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

    public interface Operations {
        boolean isJobRunning();

        void sendScreenshotIfAvailable();

        void reportScreenshotLoopError(Exception error);
    }
}
