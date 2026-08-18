package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.function.BooleanSupplier;

/** UI-agnostic receiver for scanner TEST RUN lifecycle commands. */
public interface ScannerTestRunHandler {
    long startTestRun(
            BotJobLoadDTO botJob,
            int blockOrderNumber,
            String endpointUrl,
            boolean runSingleBlock,
            BooleanSupplier cancellationRequested);

    void cancelTestRunStartup();

    boolean stopTestRun(long executionId);

    boolean isTestRunComplete(long executionId);

    String testRunTerminalOutcome(long executionId);
}
