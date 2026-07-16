package com.allinweb.ch.facade;

public final class ScannerPageReviewFileService {
    private final ScannerSupportFileService supportFileService;

    public ScannerPageReviewFileService() {
        this(new ScannerSupportFileService());
    }

    ScannerPageReviewFileService(ScannerSupportFileService supportFileService) {
        this.supportFileService = supportFileService;
    }

    public ScannerSupportFileService.SupportFile pageReview(String html, Browser browser) {
        return supportFileService.pageReview(html, currentUrlOrUnknown(browser), titleOrEmpty(browser));
    }

    private static String currentUrlOrUnknown(Browser browser) {
        try {
            return browser != null ? browser.currentUrl() : "(unknown)";
        } catch (Exception ex) {
            return "(unknown)";
        }
    }

    private static String titleOrEmpty(Browser browser) {
        try {
            return browser != null ? browser.title() : "";
        } catch (Exception ex) {
            return "";
        }
    }

    public interface Browser {
        String currentUrl();

        String title();
    }
}
