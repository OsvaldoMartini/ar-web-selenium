package com.allinweb.ch.facade;

import com.allinweb.ch.license.SystemDetails;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ConsoleRingBuffer;
import com.allinweb.ch.util.LicenseFingerprint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;

/**
 * Captures the current page DOM via Selenium and uploads it to the MultiPlugins
 * portal support queue. Only invoked on explicit user action (the "Send DOM for
 * Review" button in ARScannedElementPane).
 *
 * Envelope schema v1:
 *   schemaVersion     "1"
 *   capturedAt        ISO-8601
 *   pcName, appVersion, failedPlugin, failureReason, botJobId, operationId
 *   browser           { url, title, viewport{w,h} }
 *   html              base64(gzip(raw page source))
 *   htmlSha256        "sha256:<hex>" of the RAW (non-gzipped) HTML
 *   consoleTail       [ ... last 50 log lines ... ]
 *
 * Endpoint: POST  ${multiplugins.api.url}/support/dom-capture
 * Auth:
 *   X-MP-License-Fingerprint  sha256 of ARWeb.lic bytes
 *   X-MP-Organization         license.organization
 */
@Slf4j
public class SupportCapture {

    private static final String DEFAULT_API = "https://multiplugins.ch/api";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CaptureResult captureAndSend(
            WebDriver driver,
            String failedPlugin,
            String failureReason,
            Long botJobId,
            String operationId) {

        if (driver == null) return CaptureResult.error("No active driver");

        try {
            String rawHtml = driver.getPageSource();
            if (rawHtml == null || rawHtml.isBlank()) {
                return CaptureResult.error("Empty page source");
            }

            String organization = ARPropertyManager.getInstance()
                    .getProperty(ARPropertyEnum.LICENSE_ORGANIZATION.getValue());
            if (organization == null || organization.isBlank()) organization = "UNKNOWN";
            organization = sanitize(organization);

            String appVersion = ARPropertyManager.getInstance()
                    .getProperty(ARPropertyEnum.VERSION);
            String licenseFp = LicenseFingerprint.compute();
            if (licenseFp == null) {
                return CaptureResult.error("Missing ARWeb.lic — cannot authenticate");
            }

            String url = safeString(driver.getCurrentUrl());
            String title = safeString(driver.getTitle());
            Dimension vp = null;
            try { vp = driver.manage().window().getSize(); } catch (Exception ignored) {}

            Map<String, Object> env = new HashMap<>();
            env.put("schemaVersion", "1");
            env.put("capturedAt", Instant.now().toString());
            env.put("pcName", SystemDetails.getSystemComputerName());
            env.put("appVersion", appVersion);
            env.put("failedPlugin", failedPlugin != null ? failedPlugin : "user-initiated");
            env.put("failureReason", failureReason != null ? failureReason : "User-initiated capture");
            env.put("botJobId", botJobId);
            env.put("operationId", operationId);

            Map<String, Object> browser = new HashMap<>();
            browser.put("url", url);
            browser.put("title", title);
            if (vp != null) {
                Map<String, Integer> v = new HashMap<>();
                v.put("w", vp.getWidth());
                v.put("h", vp.getHeight());
                browser.put("viewport", v);
            }
            env.put("browser", browser);

            env.put("html", gzipAndBase64(rawHtml));
            env.put("htmlSha256", "sha256:" + sha256Hex(rawHtml));
            env.put("consoleTail", ConsoleRingBuffer.snapshot(50));

            String json = GSON.toJson(env);

            String apiBase = System.getProperty("multiplugins.api.url", DEFAULT_API);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/support/dom-capture"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-MP-License-Fingerprint", licenseFp)
                    .header("X-MP-Organization", organization)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                log.error("SupportCapture — HTTP {}: {}", resp.statusCode(), resp.body());
                return CaptureResult.error("Server returned HTTP " + resp.statusCode());
            }

            String ticketId = extractJsonString(resp.body(), "ticketId");
            log.info("SupportCapture — upload OK, ticketId={}", ticketId);
            return CaptureResult.ok(ticketId);

        } catch (Exception e) {
            log.error("SupportCapture — failed: {}", e.getMessage(), e);
            return CaptureResult.error(e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String sanitize(String s) {
        if (s == null) return "UNKNOWN";
        String cleaned = s.trim().replaceAll("[\\p{Cntrl}]", "");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static String safeString(String s) { return s != null ? s : ""; }

    private static String gzipAndBase64(String s) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(s.getBytes(StandardCharsets.UTF_8));
            gz.finish();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        }
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int colon = json.indexOf(':', i);
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    // ── Result holder ────────────────────────────────────────────────────────

    public static final class CaptureResult {
        private final boolean ok;
        private final String ticketId;
        private final String error;

        private CaptureResult(boolean ok, String ticketId, String error) {
            this.ok = ok;
            this.ticketId = ticketId;
            this.error = error;
        }
        public static CaptureResult ok(String id) { return new CaptureResult(true, id, null); }
        public static CaptureResult error(String msg) { return new CaptureResult(false, null, msg); }

        public boolean isOk() { return ok; }
        public String ticketId() { return ticketId; }
        public String error() { return error; }
    }
}
