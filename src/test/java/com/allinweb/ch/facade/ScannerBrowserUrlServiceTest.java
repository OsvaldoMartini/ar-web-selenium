package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerBrowserUrlServiceTest {
    private final ScannerBrowserUrlService service = new ScannerBrowserUrlService();

    @Test
    void returnsCurrentUrlWhenDriverExists() {
        assertEquals("https://example.test", service.currentUrlOr("(none)", new Browser(true, "https://example.test")));
    }

    @Test
    void returnsFallbackWhenDriverIsMissing() {
        assertEquals("(none)", service.currentUrlOr("(none)", new Browser(false, "https://example.test")));
    }

    @Test
    void returnsFallbackWhenCurrentUrlFails() {
        assertEquals("(none)", service.currentUrlOr("(none)", new FailingBrowser()));
    }

    private record Browser(boolean hasCurrentDriver, String currentUrl) implements ScannerBrowserUrlService.Browser {}

    private static final class FailingBrowser implements ScannerBrowserUrlService.Browser {
        @Override
        public boolean hasCurrentDriver() {
            return true;
        }

        @Override
        public String currentUrl() {
            throw new IllegalStateException("closed");
        }
    }
}
