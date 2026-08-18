package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerDomReviewSnapshotServiceTest {

    private final ScannerDomReviewSnapshotService service = new ScannerDomReviewSnapshotService();

    @Test
    void returnsSnapshotForPageSource() {
        ScannerDomReviewSnapshotService.Snapshot snapshot =
                service.snapshot(browser("<html></html>", "https://bank.example", "Login")).orElseThrow();

        assertEquals("<html></html>", snapshot.html());
        assertEquals("https://bank.example", snapshot.currentUrl());
        assertEquals("Login", snapshot.title());
    }

    @Test
    void skipsBlankPageSource() {
        assertTrue(service.snapshot(browser(" ", "https://bank.example", "Login")).isEmpty());
    }

    @Test
    void fallsBackWhenBrowserMetadataFails() {
        ScannerDomReviewSnapshotService.Snapshot snapshot = service.snapshot(new ScannerDomReviewSnapshotService.Browser() {
                    @Override
                    public String pageSource() {
                        return "<html></html>";
                    }

                    @Override
                    public String currentUrl() {
                        throw new IllegalStateException("closed");
                    }

                    @Override
                    public String title() {
                        throw new IllegalStateException("closed");
                    }
                })
                .orElseThrow();

        assertEquals("(unknown)", snapshot.currentUrl());
        assertEquals("", snapshot.title());
    }

    private static ScannerDomReviewSnapshotService.Browser browser(String html, String url, String title) {
        return new ScannerDomReviewSnapshotService.Browser() {
            @Override
            public String pageSource() {
                return html;
            }

            @Override
            public String currentUrl() {
                return url;
            }

            @Override
            public String title() {
                return title;
            }
        };
    }
}
