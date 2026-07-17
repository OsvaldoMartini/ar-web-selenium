package com.allinweb.ch.facade.scanner.browser;

import java.util.Objects;

public final class ScannerBrowserRuntime {
    private final Operations operations;

    public ScannerBrowserRuntime(Operations operations) {
        this.operations = operations;
    }

    public String currentPageUrl() {
        try {
            if (operations.hasSeleniumDriver()) {
                return operations.currentSeleniumUrl();
            }
            if (operations.isPlaywrightEnabled()) {
                return operations.currentPlaywrightUrl();
            }
        } catch (Exception error) {
            operations.warnCurrentPageUrlFailed(error.getMessage());
        }
        return "";
    }

    public String currentPlaywrightUrl() {
        try {
            if (!operations.hasOpenPlaywrightDriver()) {
                return "";
            }
            return operations.currentPlaywrightUrl();
        } catch (Exception error) {
            return "(url unavailable: " + error.getMessage() + ")";
        }
    }

    public void pauseAfterPlaywrightWebAction(
            String instructionName, String action, boolean success, String urlBefore, String urlAfter) {
        String safeInstructionName = instructionName == null ? "(null instruction)" : instructionName;
        boolean navigationChanged = !Objects.equals(urlBefore, urlAfter);
        operations.logPlaywrightStep(action, safeInstructionName, success, navigationChanged, urlBefore, urlAfter);
        operations.appendLog(
                "[PW] "
                        + action
                        + " - "
                        + safeInstructionName
                        + " - "
                        + (success ? "OK" : "FAILED")
                        + (navigationChanged ? " - navigation changed" : ""),
                success ? "info" : "warn");
    }

    public int navigationTimeSeconds() {
        String value = operations.navigationTimeProperty();
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 0) return 0;
            if (seconds > 10) return 10;
            return seconds;
        } catch (Exception ignore) {
            return 0;
        }
    }

    public interface Operations {
        boolean hasSeleniumDriver();

        String currentSeleniumUrl();

        boolean isPlaywrightEnabled();

        boolean hasOpenPlaywrightDriver();

        String currentPlaywrightUrl();

        String navigationTimeProperty();

        void warnCurrentPageUrlFailed(String message);

        void logPlaywrightStep(
                String action,
                String instructionName,
                boolean success,
                boolean navigationChanged,
                String urlBefore,
                String urlAfter);

        void appendLog(String message, String style);
    }
}
