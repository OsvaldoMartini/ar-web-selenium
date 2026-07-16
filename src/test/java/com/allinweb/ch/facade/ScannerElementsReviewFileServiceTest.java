package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScannerElementsReviewFileServiceTest {

    @Test
    void buildsElementsReviewFile() {
        ScannerElementsReviewFileService service = new ScannerElementsReviewFileService(new ScannerSupportFileService(
                new FixedSupportContext(),
                () -> Instant.parse("2026-01-01T00:00:00Z"),
                () -> LocalDateTime.of(2026, 1, 1, 0, 0)));

        ScannerSupportFileService.SupportFile file =
                service.elementsReview(null, "[{\"id\":7,\"tagName\":\"button\",\"xPath\":\"//button\"}]", "Check it");

        JsonObject json = JsonParser.parseString(file.json()).getAsJsonObject();
        assertEquals("elements-review", json.get("kind").getAsString());
        assertEquals("Check it", json.get("message").getAsString());
        assertEquals(1, json.get("elementCount").getAsInt());
        assertEquals("2026-01-01_00-00-00_elements_review.support", file.suggestedFileName());
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
