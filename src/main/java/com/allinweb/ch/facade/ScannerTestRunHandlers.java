package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/** Process-wide scanner TEST RUN handler registry, kept free of presentation dependencies. */
@Slf4j
public final class ScannerTestRunHandlers {
    private static final ScannerTestRunHandlers INSTANCE = new ScannerTestRunHandlers();

    private final AtomicReference<ScannerTestRunHandler> activeHandler = new AtomicReference<>();

    private ScannerTestRunHandlers() {}

    public static ScannerTestRunHandlers getInstance() {
        return INSTANCE;
    }

    public void register(ScannerTestRunHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Scanner TEST RUN handler is required");
        }
        activeHandler.set(handler);
    }

    public void unregister(ScannerTestRunHandler handler) {
        if (handler != null) {
            activeHandler.compareAndSet(handler, null);
        }
    }

    public long startTestRun(
            BotJobLoadDTO botJob,
            int blockOrderNumber,
            String endpointUrl,
            boolean runSingleBlock,
            BooleanSupplier cancellationRequested) {
        ScannerTestRunHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Rejecting scanner TEST RUN because no scanner test-run handler is registered");
            return 0L;
        }
        return handler.startTestRun(botJob, blockOrderNumber, endpointUrl, runSingleBlock, cancellationRequested);
    }

    public void cancelTestRunStartup() {
        ScannerTestRunHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Ignoring TEST RUN startup cancellation because no scanner test-run handler is registered");
            return;
        }
        handler.cancelTestRunStartup();
    }

    public boolean stopTestRun(long executionId) {
        ScannerTestRunHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Rejecting TEST RUN stop because no scanner test-run handler is registered");
            return false;
        }
        return handler.stopTestRun(executionId);
    }

    public boolean isTestRunComplete(long executionId) {
        ScannerTestRunHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Treating TEST RUN as complete because no scanner test-run handler is registered");
            return true;
        }
        return handler.isTestRunComplete(executionId);
    }

    public String testRunTerminalOutcome(long executionId) {
        ScannerTestRunHandler handler = activeHandler.get();
        if (handler == null) {
            log.warn("Returning missing TEST RUN outcome because no scanner test-run handler is registered");
            return "UNAVAILABLE";
        }
        return handler.testRunTerminalOutcome(executionId);
    }
}
