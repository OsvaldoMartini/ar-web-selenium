package com.allinweb.ch.facade;

public final class ScannerBrowserUrlService {
    public String currentUrlOr(String fallback, Browser browser) {
        try {
            return browser.hasCurrentDriver() ? browser.currentUrl() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public interface Browser {
        boolean hasCurrentDriver();

        String currentUrl();
    }
}
