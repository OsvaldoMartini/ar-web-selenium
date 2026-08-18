package com.allinweb.ch.facade;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScannerRuntimeCloseRequest implements ScannerCloseRequestService.CloseRequest {
    private final Runnable closeBrowserRuntime;
    private final ExecutorService executorWebSocket;
    private final ExecutorService executorServicePreLaunch;

    public ScannerRuntimeCloseRequest(
            Runnable closeBrowserRuntime,
            ExecutorService executorWebSocket,
            ExecutorService executorServicePreLaunch) {
        this.closeBrowserRuntime = closeBrowserRuntime;
        this.executorWebSocket = executorWebSocket;
        this.executorServicePreLaunch = executorServicePreLaunch;
    }

    @Override
    public void interruptThreads() {
        // ScannerRuntime no longer owns UI threads directly; executor shutdown is handled below.
    }

    @Override
    public void closeBrowserRuntime() {
        closeBrowserRuntime.run();
    }

    @Override
    public void shutdownExecutors() {
        shutDownExecutorService(executorWebSocket);
        shutDownExecutorService(executorServicePreLaunch);
        log.info("Browser runtime closed successfully.");
    }

    @Override
    public void closeFailed(Exception error) {
        log.error("Error closing browser runtime: " + error.getMessage());
    }

    private void shutDownExecutorService(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("ExecutorService did not terminate" + e.getMessage());
        }
    }
}
