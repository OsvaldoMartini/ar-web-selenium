package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScannerPageReviewFileServiceTest {

    @Test
    void buildsPageReviewWithBrowserMetadata() {
        ScannerPageReviewFileService service = service();

        ScannerSupportFileService.SupportFile file =
                service.pageReview("<html>ok</html>", browser("https://bank.example", "Login"));

        JsonObject browser = browser(file);
        assertTrue(browser.get("url").getAsString().equals("https://bank.example"));
        assertTrue(browser.get("title").getAsString().equals("Login"));
    }

    @Test
    void fallsBackWhenBrowserMetadataFails() {
        ScannerPageReviewFileService service = service();

        ScannerSupportFileService.SupportFile file = service.pageReview("<html>ok</html>", new ScannerPageReviewFileService.Browser() {
            @Override
            public String currentUrl() {
                throw new IllegalStateException("closed");
            }

            @Override
            public String title() {
                throw new IllegalStateException("closed");
            }
        });

        JsonObject browser = browser(file);
        assertTrue(browser.get("url").getAsString().equals("(unknown)"));
        assertTrue(browser.get("title").getAsString().equals(""));
    }

    private static JsonObject browser(ScannerSupportFileService.SupportFile file) {
        return JsonParser.parseString(file.json()).getAsJsonObject().getAsJsonObject("browser");
    }

    private static ScannerPageReviewFileService service() {
        return new ScannerPageReviewFileService(new ScannerSupportFileService(
                new FixedSupportContext(),
                () -> Instant.parse("2026-01-01T00:00:00Z"),
                () -> LocalDateTime.of(2026, 1, 1, 0, 0)));
    }

    private static ScannerPageReviewFileService.Browser browser(String url, String title) {
        return new ScannerPageReviewFileService.Browser() {
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

    private static final class FixedSupportContext implements ScannerSupportFileService.SupportContext {
        @Override
        public String computerName() {
            return "pc-1";
        }

        @Override
        public String licenseEmail() {
            return "user@example.test";
        }

        @Override
        public String appVersion() {
            return "4.2";
        }

        @Override
        public String licenseOwner() {
            return "Owner";
        }

        @Override
        public String organizationName() {
            return "Org";
        }

        @Override
        public String systemUserName() {
            return "tester";
        }
    }
}
