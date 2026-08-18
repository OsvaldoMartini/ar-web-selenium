package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PreScanBrowserSessionTest {

    @Test
    void opensOnceReusesTheLiveBrowserAndDelegatesOperations() {
        FakeDriver driver = new FakeDriver();
        PreScanBrowserSession session = new PreScanBrowserSession(() -> driver);

        assertTrue(session.ensureOpen("chromium", "https://example.test", "--safe"));
        assertFalse(session.ensureOpen("chromium", "https://ignored.test", ""));
        session.reload();

        assertEquals(1, driver.openCalls);
        assertEquals(1, driver.reloadCalls);
        assertEquals("https://example.test", session.currentUrl());
        assertEquals(250L, session.waitForPageSettled(1_000));
        assertEquals(1, session.scanElements(new String[] {"input"}, false).size());
    }

    @Test
    void scanLeaseRejectsOverlapAndIsReleasedExplicitly() {
        PreScanBrowserSession session = new PreScanBrowserSession(FakeDriver::new);

        assertTrue(session.tryBeginScan());
        assertTrue(session.isScanRunning());
        assertFalse(session.tryBeginScan());
        session.finishScan();
        assertFalse(session.isScanRunning());
        assertTrue(session.tryBeginScan());
    }

    @Test
    void failedOpenClosesAndDiscardsTheDriver() {
        FakeDriver driver = new FakeDriver();
        driver.openFailure = new IllegalStateException("browser failed");
        PreScanBrowserSession session = new PreScanBrowserSession(() -> driver);

        assertThrows(
                IllegalStateException.class,
                () -> session.ensureOpen("chromium", "https://example.test", ""));

        assertFalse(session.isOpen());
        assertEquals(1, driver.shutdownCalls);
    }

    @Test
    void shutdownIsIdempotentAndReleasesBrowserAndScanLease() {
        AtomicInteger creations = new AtomicInteger();
        FakeDriver driver = new FakeDriver();
        PreScanBrowserSession session = new PreScanBrowserSession(() -> {
            creations.incrementAndGet();
            return driver;
        });
        session.ensureOpen("chromium", "https://example.test", "");
        session.tryBeginScan();

        session.shutdown();
        session.shutdown();

        assertFalse(session.isOpen());
        assertFalse(session.isScanRunning());
        assertEquals(1, creations.get());
        assertEquals(1, driver.shutdownCalls);
    }

    @Test
    void shutdownFailureStillDiscardsDriverAndReleasesScanLease() {
        FakeDriver driver = new FakeDriver();
        PreScanBrowserSession session = new PreScanBrowserSession(() -> driver);
        session.ensureOpen("chromium", "https://example.test", "");
        session.tryBeginScan();
        driver.shutdownFailure = new IllegalStateException("close failed");

        assertThrows(IllegalStateException.class, session::shutdown);

        assertFalse(session.isOpen());
        assertFalse(session.isScanRunning());
    }

    @Test
    void sharedSessionAdoptsAnExistingBrowserAndNeverClosesTheRuntimeOwner() {
        FakeDriver sharedDriver = new FakeDriver();
        sharedDriver.open = true;
        sharedDriver.url = "https://current.test/step-two";
        PreScanBrowserSession session = new PreScanBrowserSession(sharedDriver);

        assertFalse(session.ensureOpen("chromium", "https://home.test", ""));
        assertEquals("https://current.test/step-two", session.currentUrl());
        assertEquals(0, sharedDriver.openCalls, "Page Scanner must not navigate an existing TEST RUN page");

        session.tryBeginScan();
        session.shutdown();

        assertTrue(session.isOpen(), "Page Scanner does not own the shared Playwright browser");
        assertFalse(session.isScanRunning());
        assertEquals(0, sharedDriver.shutdownCalls);
    }

    private static final class FakeDriver implements PreScanBrowserSession.DriverPort {
        private boolean open;
        private int openCalls;
        private int reloadCalls;
        private int shutdownCalls;
        private RuntimeException openFailure;
        private RuntimeException shutdownFailure;
        private String url;

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void openOrNavigate(String browserType, String endpointUrl, String optionsConfig) {
            openCalls++;
            if (openFailure != null) throw openFailure;
            open = true;
            url = endpointUrl;
        }

        @Override
        public void reload() {
            reloadCalls++;
        }

        @Override
        public String currentUrl() {
            return url;
        }

        @Override
        public long waitForPageSettled(long maxWaitMs) {
            return 250;
        }

        @Override
        public List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden) {
            return List.of(new ElementDTO());
        }

        @Override
        public boolean click(InstructionLoad instruction) {
            return true;
        }

        @Override
        public boolean clickOnce(InstructionLoad instruction) {
            return true;
        }

        @Override
        public boolean fill(InstructionLoad instruction, FieldData data) {
            return true;
        }

        @Override
        public boolean fillOnce(InstructionLoad instruction, FieldData data) {
            return true;
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
            open = false;
            if (shutdownFailure != null) throw shutdownFailure;
        }
    }
}
