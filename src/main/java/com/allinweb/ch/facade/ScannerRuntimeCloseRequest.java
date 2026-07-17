package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScannerRuntimeCloseRequest implements ScannerCloseRequestService.CloseRequest {
    private final ARWebDriver arWebDriver;
    private final Runnable closeWebDrivers;
    private final ExecutorService executorWebSocket;
    private final ExecutorService executorServicePreLaunch;

    public ScannerRuntimeCloseRequest(
            ARWebDriver arWebDriver,
            Runnable closeWebDrivers,
            ExecutorService executorWebSocket,
            ExecutorService executorServicePreLaunch) {
        this.arWebDriver = arWebDriver;
        this.closeWebDrivers = closeWebDrivers;
        this.executorWebSocket = executorWebSocket;
        this.executorServicePreLaunch = executorServicePreLaunch;
    }

    @Override
    public void interruptThreads() {
        // ARScannedElementScene no longer owns UI threads directly; executor shutdown is handled below.
    }

    @Override
    public boolean hasWebDriver() {
        return arWebDriver != null;
    }

    @Override
    public void closeWebDrivers() {
        closeWebDrivers.run();
    }

    @Override
    public void quitCurrentDriver() {
        arWebDriver.getCurrentDriver().quit();
    }

    @Override
    public void clearCurrentDriver() {
        arWebDriver.setCurrentDriver(null);
    }

    @Override
    public void shutdownExecutors() {
        shutDownExecutorService(executorWebSocket);
        shutDownExecutorService(executorServicePreLaunch);
        log.info("WebDriver quit successfully.");
    }

    @Override
    public void closeFailed(Exception error) {
        log.error("Error closing WebDriver: " + error.getMessage());
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
