package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class ScannerSupportFileServiceTest {

    @Test
    void pageReviewBuildsSupportEnvelope() throws Exception {
        ScannerSupportFileService service = new ScannerSupportFileService(
                new TestSupportContext(),
                () -> Instant.parse("2026-07-15T11:00:00Z"),
                () -> LocalDateTime.of(2026, 7, 15, 13, 5, 6));

        ScannerSupportFileService.SupportFile file =
                service.pageReview("<html><body>login</body></html>", "https://example.test/login", "Login");

        JsonObject root = JsonParser.parseString(file.json()).getAsJsonObject();

        assertEquals("2026-07-15_13-05-06_page_review.support", file.suggestedFileName());
        assertEquals("1", root.get("schemaVersion").getAsString());
        assertEquals("2026-07-15T11:00:00Z", root.get("capturedAt").getAsString());
        assertEquals("TEST-PC", root.get("pcName").getAsString());
        assertEquals("test@example.test", root.get("email").getAsString());
        assertEquals("4.6-test", root.get("appVersion").getAsString());
        assertEquals("pageScanner", root.get("failedPlugin").getAsString());
        assertEquals("User-initiated local capture", root.get("failureReason").getAsString());
        assertEquals("https://example.test/login", root.getAsJsonObject("browser").get("url").getAsString());
        assertEquals("Login", root.getAsJsonObject("browser").get("title").getAsString());
        assertEquals("<html><body>login</body></html>", gunzipBase64(root.get("html").getAsString()));
        assertEquals("sha256:" + sha256Hex("<html><body>login</body></html>"), root.get("htmlSha256").getAsString());
    }

    @Test
    void pageReviewNormalizesNullFields() throws Exception {
        ScannerSupportFileService service = new ScannerSupportFileService(
                new EmptySupportContext(),
                () -> Instant.parse("2026-07-15T11:00:00Z"),
                () -> LocalDateTime.of(2026, 7, 15, 13, 5, 6));

        ScannerSupportFileService.SupportFile file = service.pageReview(null, null, null);
        JsonObject root = JsonParser.parseString(file.json()).getAsJsonObject();

        assertEquals("", root.get("pcName").getAsString());
        assertEquals("", root.get("email").getAsString());
        assertEquals("", root.get("appVersion").getAsString());
        assertEquals("", root.getAsJsonObject("browser").get("url").getAsString());
        assertEquals("", root.getAsJsonObject("browser").get("title").getAsString());
        assertEquals("", gunzipBase64(root.get("html").getAsString()));
        assertTrue(root.get("htmlSha256").getAsString().startsWith("sha256:"));
    }

    private static String gunzipBase64(String value) throws Exception {
        byte[] compressed = Base64.getDecoder().decode(value);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private static final class TestSupportContext implements ScannerSupportFileService.SupportContext {
        @Override
        public String computerName() {
            return "TEST-PC";
        }

        @Override
        public String licenseEmail() {
            return "test@example.test";
        }

        @Override
        public String appVersion() {
            return "4.6-test";
        }
    }

    private static final class EmptySupportContext implements ScannerSupportFileService.SupportContext {
        @Override
        public String computerName() {
            return null;
        }

        @Override
        public String licenseEmail() {
            return null;
        }

        @Override
        public String appVersion() {
            return null;
        }
    }
}
