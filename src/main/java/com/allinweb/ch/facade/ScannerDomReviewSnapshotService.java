package com.allinweb.ch.facade;

import java.util.Optional;

public final class ScannerDomReviewSnapshotService {

    public Optional<Snapshot> snapshot(Browser browser) {
        String html = browser.pageSource();
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new Snapshot(html, currentUrlOrUnknown(browser), titleOrEmpty(browser)));
    }

    private static String currentUrlOrUnknown(Browser browser) {
        try {
            return browser.currentUrl();
        } catch (Exception ex) {
            return "(unknown)";
        }
    }

    private static String titleOrEmpty(Browser browser) {
        try {
            return browser.title();
        } catch (Exception ex) {
            return "";
        }
    }

    public interface Browser {
        String pageSource();

        String currentUrl();

        String title();
    }

    public record Snapshot(String html, String currentUrl, String title) {}
}
