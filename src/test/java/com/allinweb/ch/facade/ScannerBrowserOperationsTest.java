package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceState;
import org.junit.jupiter.api.Test;

class ScannerBrowserOperationsTest {

    @Test
    void browserStateUsesPlaywright() {
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.playwrightOpen = true;
        runtime.playwrightCurrentUrl = "https://playwright.example";
        runtime.playwrightTitle = "Playwright page";
        runtime.playwrightPageCount = 3;

        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(runtime).browserState();

        assertEquals("OPEN", browser.state());
        assertEquals("https://playwright.example", browser.activeUrl());
        assertEquals("Playwright page", browser.activeTitle());
        assertEquals(3, browser.openTabs());
        assertTrue(browser.scannable());
    }

    @Test
    void browserStateIsClosedWhenNoBrowserIsAvailable() {
        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(new RecordingRuntime()).browserState();

        assertEquals("CLOSED", browser.state());
        assertEquals("", browser.activeUrl());
        assertEquals("", browser.activeTitle());
        assertEquals(0, browser.openTabs());
        assertFalse(browser.scannable());
    }

    @Test
    void browserStateKeepsPlaywrightOpenWhenOptionalDetailsFail() {
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.playwrightOpen = true;
        runtime.failPlaywrightDetails = true;

        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(runtime).browserState();

        assertEquals("OPEN", browser.state());
        assertEquals("", browser.activeUrl());
        assertEquals("", browser.activeTitle());
        assertEquals(1, browser.openTabs());
        assertTrue(browser.scannable());
    }

    @Test
    void switchTabSelectsAnAdjacentPlaywrightPage() {
        RecordingRuntime runtime = new RecordingRuntime();
        ScannerBrowserOperations operations = new ScannerBrowserOperations(runtime);

        operations.switchTab(-1);

        assertEquals(-1, runtime.selectedDirection);
    }

    @Test
    void switchTabIgnoresZeroDirection() {
        RecordingRuntime runtime = new RecordingRuntime();

        new ScannerBrowserOperations(runtime).switchTab(0);

        assertEquals(0, runtime.selectedDirection);
    }

    private static final class RecordingRuntime implements ScannerBrowserOperations.Runtime {
        private boolean playwrightOpen;
        private String playwrightCurrentUrl = "";
        private String playwrightTitle = "";
        private int playwrightPageCount;
        private boolean failPlaywrightDetails;
        private int selectedDirection;

        @Override
        public boolean playwrightOpen() {
            return playwrightOpen;
        }

        @Override
        public String playwrightCurrentUrl() {
            if (failPlaywrightDetails) {
                throw new IllegalStateException("url failed");
            }
            return playwrightCurrentUrl;
        }

        @Override
        public String playwrightTitle() {
            if (failPlaywrightDetails) {
                throw new IllegalStateException("title failed");
            }
            return playwrightTitle;
        }

        @Override
        public int playwrightPageCount() {
            if (failPlaywrightDetails) {
                throw new IllegalStateException("count failed");
            }
            return playwrightPageCount;
        }

        @Override
        public boolean selectPlaywrightPageRelative(int direction) {
            selectedDirection = direction;
            return true;
        }
    }
}
