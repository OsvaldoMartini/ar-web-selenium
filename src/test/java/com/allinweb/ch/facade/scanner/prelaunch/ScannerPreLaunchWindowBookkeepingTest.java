package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerPreLaunchWindowBookkeepingTest {

    @Test
    void refreshChangedWindowsSkipsWhenNoSeleniumDriverIsActive() {
        FakeOperations operations = new FakeOperations(null, 1);
        ScannerPreLaunchWindowBookkeeping bookkeeping = new ScannerPreLaunchWindowBookkeeping(operations);

        bookkeeping.refreshChangedWindows();

        assertEquals(0, operations.updateWindowHandlesListCalls);
        assertEquals(0, operations.updateButtonStateCalls);
    }

    @Test
    void refreshChangedWindowsSkipsWhenHandleCountIsUnchanged() {
        FakeOperations operations = new FakeOperations(Integer.valueOf(2), 2);
        ScannerPreLaunchWindowBookkeeping bookkeeping = new ScannerPreLaunchWindowBookkeeping(operations);

        bookkeeping.refreshChangedWindows();

        assertEquals(0, operations.updateWindowHandlesListCalls);
        assertEquals(0, operations.updateButtonStateCalls);
    }

    @Test
    void refreshChangedWindowsUpdatesHandlesAndButtonsWhenHandleCountChanged() {
        FakeOperations operations = new FakeOperations(Integer.valueOf(3), 2);
        ScannerPreLaunchWindowBookkeeping bookkeeping = new ScannerPreLaunchWindowBookkeeping(operations);

        bookkeeping.refreshChangedWindows();

        assertEquals(1, operations.updateWindowHandlesListCalls);
        assertEquals(1, operations.updateButtonStateCalls);
    }

    private static final class FakeOperations implements ScannerPreLaunchWindowBookkeeping.Operations {
        private final Integer currentWindowHandleCount;
        private final int knownWindowHandleCount;
        private int updateWindowHandlesListCalls;
        private int updateButtonStateCalls;

        private FakeOperations(Integer currentWindowHandleCount, int knownWindowHandleCount) {
            this.currentWindowHandleCount = currentWindowHandleCount;
            this.knownWindowHandleCount = knownWindowHandleCount;
        }

        @Override
        public Integer currentWindowHandleCount() {
            return currentWindowHandleCount;
        }

        @Override
        public int knownWindowHandleCount() {
            return knownWindowHandleCount;
        }

        @Override
        public void updateWindowHandlesList() {
            updateWindowHandlesListCalls++;
        }

        @Override
        public void updateButtonState() {
            updateButtonStateCalls++;
        }
    }
}
