package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** JavaFX-free owner of the isolated Pre Scan browser and its single-scan lease. */
public final class PreScanBrowserSession {

    private final DriverFactory driverFactory;
    private final AtomicBoolean scanRunning = new AtomicBoolean();
    private DriverPort driver;

    public PreScanBrowserSession() {
        this(() -> new PlaywrightDriverPort(new ARPlaywrightDriver()));
    }

    PreScanBrowserSession(DriverFactory driverFactory) {
        this.driverFactory = driverFactory;
    }

    public boolean tryBeginScan() {
        return scanRunning.compareAndSet(false, true);
    }

    public void finishScan() {
        scanRunning.set(false);
    }

    public boolean isScanRunning() {
        return scanRunning.get();
    }

    public synchronized boolean isOpen() {
        return driver != null && driver.isOpen();
    }

    /** Opens a new driver when needed and returns true only when a driver was created. */
    public synchronized boolean ensureOpen(String browserType, String endpointUrl, String optionsConfig) {
        if (driver != null && driver.isOpen()) return false;
        closeDriver();
        driver = driverFactory.create();
        try {
            driver.openOrNavigate(browserType, endpointUrl, optionsConfig);
            return true;
        } catch (RuntimeException failure) {
            try {
                closeDriver();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public synchronized void reload() {
        requireOpen().reload();
    }

    public synchronized String currentUrl() {
        return requireOpen().currentUrl();
    }

    public synchronized long waitForPageSettled(long maxWaitMs) {
        return requireOpen().waitForPageSettled(maxWaitMs);
    }

    public synchronized List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden) {
        return requireOpen().scanElements(searchTerms, includeHidden);
    }

    public synchronized boolean click(InstructionLoad instruction) {
        return requireOpen().click(instruction);
    }

    public synchronized boolean fill(InstructionLoad instruction, FieldData data) {
        return requireOpen().fill(instruction, data);
    }

    /** Narrow compatibility access for existing OCR/diagnostic utilities during their extraction. */
    public synchronized ARPlaywrightDriver playwrightDriver() {
        DriverPort active = requireOpen();
        if (active instanceof PlaywrightDriverPort playwright) return playwright.delegate;
        throw new IllegalStateException("The Pre Scan driver is not a Playwright driver");
    }

    public synchronized void shutdown() {
        try {
            closeDriver();
        } finally {
            scanRunning.set(false);
        }
    }

    private DriverPort requireOpen() {
        if (driver == null || !driver.isOpen()) {
            throw new IllegalStateException("No pre-scan browser is open");
        }
        return driver;
    }

    private void closeDriver() {
        if (driver == null) return;
        DriverPort closing = driver;
        driver = null;
        closing.shutdown();
    }

    @FunctionalInterface
    interface DriverFactory {
        DriverPort create();
    }

    interface DriverPort {
        boolean isOpen();

        void openOrNavigate(String browserType, String endpointUrl, String optionsConfig);

        void reload();

        String currentUrl();

        long waitForPageSettled(long maxWaitMs);

        List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden);

        boolean click(InstructionLoad instruction);

        boolean fill(InstructionLoad instruction, FieldData data);

        void shutdown();
    }

    private static final class PlaywrightDriverPort implements DriverPort {
        private final ARPlaywrightDriver delegate;

        private PlaywrightDriverPort(ARPlaywrightDriver delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void openOrNavigate(String browserType, String endpointUrl, String optionsConfig) {
            delegate.openOrNavigate(browserType, endpointUrl, optionsConfig, false);
        }

        @Override
        public void reload() {
            delegate.reload();
        }

        @Override
        public String currentUrl() {
            return delegate.currentUrl();
        }

        @Override
        public long waitForPageSettled(long maxWaitMs) {
            return delegate.waitForPageSettled(maxWaitMs);
        }

        @Override
        public List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden) {
            return delegate.scanElements(searchTerms, includeHidden);
        }

        @Override
        public boolean click(InstructionLoad instruction) {
            return delegate.click(instruction);
        }

        @Override
        public boolean fill(InstructionLoad instruction, FieldData data) {
            return delegate.fill(instruction, data);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }
    }
}
