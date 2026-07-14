package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerBrowserRuntimeTest {

    @Test
    void currentPageUrlUsesSeleniumBeforePlaywright() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasSeleniumDriver = true;
        operations.seleniumUrl = "https://selenium.example";
        operations.playwrightEnabled = true;
        operations.playwrightUrl = "https://playwright.example";
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        assertEquals("https://selenium.example", runtime.currentPageUrl());
    }

    @Test
    void currentPageUrlFallsBackToPlaywrightWhenSeleniumIsMissing() {
        RecordingOperations operations = new RecordingOperations();
        operations.playwrightEnabled = true;
        operations.playwrightUrl = "https://playwright.example";
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        assertEquals("https://playwright.example", runtime.currentPageUrl());
    }

    @Test
    void currentPageUrlReturnsEmptyAndWarnsWhenDriverReadFails() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasSeleniumDriver = true;
        operations.urlFailure = new IllegalStateException("closed");
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        assertEquals("", runtime.currentPageUrl());
        assertEquals("closed", operations.warnedCurrentPageUrlMessage);
    }

    @Test
    void currentPlaywrightUrlReturnsEmptyWhenDriverIsNotOpen() {
        RecordingOperations operations = new RecordingOperations();
        operations.openPlaywrightDriver = false;
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        assertEquals("", runtime.currentPlaywrightUrl());
    }

    @Test
    void currentPlaywrightUrlReportsUnavailableWhenReadFails() {
        RecordingOperations operations = new RecordingOperations();
        operations.openPlaywrightDriver = true;
        operations.urlFailure = new IllegalStateException("disposed");
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        assertEquals("(url unavailable: disposed)", runtime.currentPlaywrightUrl());
    }

    @Test
    void pauseAfterPlaywrightWebActionLogsNavigationChangedAndAppendStyle() {
        RecordingOperations operations = new RecordingOperations();
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        runtime.pauseAfterPlaywrightWebAction("Submit", "click", true, "https://a.example", "https://b.example");

        assertEquals("click", operations.loggedAction);
        assertEquals("Submit", operations.loggedInstructionName);
        assertEquals(true, operations.loggedSuccess);
        assertEquals(true, operations.loggedNavigationChanged);
        assertEquals("[PW] click - Submit - OK - navigation changed", operations.appendedMessage);
        assertEquals("info", operations.appendedStyle);
    }

    @Test
    void pauseAfterPlaywrightWebActionHandlesNullInstructionAndFailureStyle() {
        RecordingOperations operations = new RecordingOperations();
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        runtime.pauseAfterPlaywrightWebAction(null, "input", false, "https://a.example", "https://a.example");

        assertEquals("(null instruction)", operations.loggedInstructionName);
        assertEquals(false, operations.loggedNavigationChanged);
        assertEquals("[PW] input - (null instruction) - FAILED", operations.appendedMessage);
        assertEquals("warn", operations.appendedStyle);
    }

    @Test
    void navigationTimeSecondsBoundsAndDefaultsInvalidValues() {
        RecordingOperations operations = new RecordingOperations();
        ScannerBrowserRuntime runtime = new ScannerBrowserRuntime(operations);

        operations.navigationTimeProperty = "5";
        assertEquals(5, runtime.navigationTimeSeconds());
        operations.navigationTimeProperty = "-1";
        assertEquals(0, runtime.navigationTimeSeconds());
        operations.navigationTimeProperty = "25";
        assertEquals(10, runtime.navigationTimeSeconds());
        operations.navigationTimeProperty = "bad";
        assertEquals(0, runtime.navigationTimeSeconds());
        operations.navigationTimeProperty = null;
        assertEquals(0, runtime.navigationTimeSeconds());
    }

    private static final class RecordingOperations implements ScannerBrowserRuntime.Operations {
        private boolean hasSeleniumDriver;
        private String seleniumUrl;
        private boolean playwrightEnabled;
        private boolean openPlaywrightDriver = true;
        private String playwrightUrl;
        private RuntimeException urlFailure;
        private String navigationTimeProperty;
        private String warnedCurrentPageUrlMessage;
        private String loggedAction;
        private String loggedInstructionName;
        private boolean loggedSuccess;
        private boolean loggedNavigationChanged;
        private String appendedMessage;
        private String appendedStyle;

        @Override
        public boolean hasSeleniumDriver() {
            return hasSeleniumDriver;
        }

        @Override
        public String currentSeleniumUrl() {
            if (urlFailure != null) {
                throw urlFailure;
            }
            return seleniumUrl;
        }

        @Override
        public boolean isPlaywrightEnabled() {
            return playwrightEnabled;
        }

        @Override
        public boolean hasOpenPlaywrightDriver() {
            return openPlaywrightDriver;
        }

        @Override
        public String currentPlaywrightUrl() {
            if (urlFailure != null) {
                throw urlFailure;
            }
            return playwrightUrl;
        }

        @Override
        public String navigationTimeProperty() {
            return navigationTimeProperty;
        }

        @Override
        public void warnCurrentPageUrlFailed(String message) {
            warnedCurrentPageUrlMessage = message;
        }

        @Override
        public void logPlaywrightStep(
                String action,
                String instructionName,
                boolean success,
                boolean navigationChanged,
                String urlBefore,
                String urlAfter) {
            loggedAction = action;
            loggedInstructionName = instructionName;
            loggedSuccess = success;
            loggedNavigationChanged = navigationChanged;
        }

        @Override
        public void appendLog(String message, String style) {
            appendedMessage = message;
            appendedStyle = style;
        }
    }
}
