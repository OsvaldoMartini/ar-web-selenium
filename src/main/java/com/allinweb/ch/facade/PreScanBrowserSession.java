package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-owning view of the process-wide Playwright browser plus the Page Scanner scan lease. */
public final class PreScanBrowserSession {

    private final DriverFactory driverFactory;
    private final boolean ownsDriver;
    private final AtomicBoolean scanRunning = new AtomicBoolean();
    private DriverPort driver;

    public PreScanBrowserSession() {
        this(new SharedPlaywrightDriverPort(ARWebDriver.getInstance()));
    }

    PreScanBrowserSession(DriverFactory driverFactory) {
        this.driverFactory = driverFactory;
        this.ownsDriver = true;
    }

    /** Testable constructor for the non-owning, process-wide Playwright adapter. */
    PreScanBrowserSession(DriverPort sharedDriver) {
        this.driverFactory = () -> sharedDriver;
        this.driver = sharedDriver;
        this.ownsDriver = false;
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

    /** Verifies that a live shared browser matches the current configured browser selection. */
    public synchronized void assertBrowserCompatible(String browserType) {
        if (driver != null && driver.isOpen()) {
            driver.assertBrowserCompatible(browserType);
        }
    }

    /** Opens a new driver when needed and returns true only when a driver was created. */
    public synchronized boolean ensureOpen(String browserType, String endpointUrl, String optionsConfig) {
        if (driver != null && driver.isOpen()) {
            driver.assertBrowserCompatible(browserType);
            return false;
        }
        if (ownsDriver) {
            closeDriver();
            driver = driverFactory.create();
        } else if (driver == null) {
            driver = driverFactory.create();
        }
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

    public synchronized boolean clickOnce(InstructionLoad instruction) {
        return requireOpen().clickOnce(instruction);
    }

    public synchronized boolean fill(InstructionLoad instruction, FieldData data) {
        return requireOpen().fill(instruction, data);
    }

    public synchronized boolean fillOnce(InstructionLoad instruction, FieldData data) {
        return requireOpen().fillOnce(instruction, data);
    }

    /** Narrow compatibility access for existing OCR/diagnostic utilities during their extraction. */
    public synchronized ARPlaywrightDriver playwrightDriver() {
        return requireOpen().playwrightDriver();
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
        if (driver == null || !ownsDriver) return;
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

        default void assertBrowserCompatible(String browserType) {}

        void openOrNavigate(String browserType, String endpointUrl, String optionsConfig);

        void reload();

        String currentUrl();

        long waitForPageSettled(long maxWaitMs);

        List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden);

        boolean click(InstructionLoad instruction);

        boolean clickOnce(InstructionLoad instruction);

        boolean fill(InstructionLoad instruction, FieldData data);

        boolean fillOnce(InstructionLoad instruction, FieldData data);

        default ARPlaywrightDriver playwrightDriver() {
            throw new IllegalStateException("The Pre Scan driver is not a Playwright driver");
        }

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
        public void assertBrowserCompatible(String browserType) {
            delegate.assertBrowserCompatible(browserType);
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
        public boolean clickOnce(InstructionLoad instruction) {
            return delegate.clickOnce(instruction);
        }

        @Override
        public boolean fill(InstructionLoad instruction, FieldData data) {
            return delegate.fill(instruction, data);
        }

        @Override
        public boolean fillOnce(InstructionLoad instruction, FieldData data) {
            return delegate.fillOnce(instruction, data);
        }

        @Override
        public ARPlaywrightDriver playwrightDriver() {
            return delegate;
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }
    }

    /** Non-owning adapter: TEST RUN and Page Scanner always see the same ARWebDriver instance. */
    private static final class SharedPlaywrightDriverPort implements DriverPort {
        private final ARWebDriver owner;

        private SharedPlaywrightDriverPort(ARWebDriver owner) {
            this.owner = owner;
        }

        @Override
        public boolean isOpen() {
            ARPlaywrightDriver active = owner.currentPlaywrightDriver();
            return active != null && active.isOpen();
        }

        @Override
        public void assertBrowserCompatible(String browserType) {
            ARPlaywrightDriver active = owner.currentPlaywrightDriver();
            if (active != null) active.assertBrowserCompatible(browserType);
        }

        @Override
        public void openOrNavigate(String browserType, String endpointUrl, String optionsConfig) {
            if (!owner.openBrowser(browserType, endpointUrl, optionsConfig)) {
                throw new IllegalStateException("Unable to open the shared Playwright browser");
            }
        }

        @Override
        public void reload() {
            playwrightDriver().reload();
        }

        @Override
        public String currentUrl() {
            return playwrightDriver().currentUrl();
        }

        @Override
        public long waitForPageSettled(long maxWaitMs) {
            return playwrightDriver().waitForPageSettled(maxWaitMs);
        }

        @Override
        public List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden) {
            return playwrightDriver().scanElements(searchTerms, includeHidden);
        }

        @Override
        public boolean click(InstructionLoad instruction) {
            return playwrightDriver().click(instruction);
        }

        @Override
        public boolean clickOnce(InstructionLoad instruction) {
            return playwrightDriver().clickOnce(instruction);
        }

        @Override
        public boolean fill(InstructionLoad instruction, FieldData data) {
            return playwrightDriver().fill(instruction, data);
        }

        @Override
        public boolean fillOnce(InstructionLoad instruction, FieldData data) {
            return playwrightDriver().fillOnce(instruction, data);
        }

        @Override
        public ARPlaywrightDriver playwrightDriver() {
            ARPlaywrightDriver active = owner.currentPlaywrightDriver();
            if (active == null || !active.isOpen()) {
                throw new IllegalStateException("No shared Playwright browser is open");
            }
            return active;
        }

        @Override
        public void shutdown() {
            // The application runtime owns this browser. Closing Page Scanner only releases its
            // scan lease; TEST RUN, subsequent scans, and live progress keep the same page/context.
        }
    }
}
