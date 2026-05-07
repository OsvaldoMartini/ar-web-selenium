package com.allinweb.ch.facade;

import com.allinweb.ch.license.SystemDetails;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ConsoleRingBuffer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

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
 *   X-MP-Email                license.email (requester email from ARWeb.lic parts[5])
 */
@Slf4j
public class SupportCapture {

    private static final String DEFAULT_API = "https://multiplugins.ch/api";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CaptureResult captureAndSend(
            WebDriver driver, String failedPlugin, String failureReason, Long botJobId, String operationId) {
        // MultiPlugins network traffic disabled — UI is gated; this is a hard stop.
        log.info("captureAndSend disabled — no MultiPlugins call performed");
        return CaptureResult.error("Support upload disabled");
    }

    /**
     * Same Page-Review envelope as {@link #captureAndSend} but carries the scanned
     * {@code elementDetails} array (plus best-effort live WebDriver enrichment)
     * instead of the raw HTML. Posts to the same {@code /support/dom-capture}
     * endpoint so it lands in the Page-Review queue, classified as elements.
     */
    /**
     * Build the elements-review envelope as a {@link JsonObject}. Shared by
     * {@link #captureElementsAndSend} and the local-save path in
     * {@code ARScannedElementPane} so both the portal upload and the
     * {@code .support} file carry identical structure.
     */
    public JsonObject buildElementsReviewEnvelope(
            WebDriver driver, String elementDetailsJson, String message, String failureReason) {

        String appVersion = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.VERSION);

        String url = "";
        String title = "";
        Dimension vp = null;
        if (driver != null) {
            try {
                url = safeString(driver.getCurrentUrl());
            } catch (Exception ignored) {
            }
            try {
                title = safeString(driver.getTitle());
            } catch (Exception ignored) {
            }
            try {
                vp = driver.manage().window().getSize();
            } catch (Exception ignored) {
            }
        }

        JsonArray elements;
        try {
            JsonElement parsed = JsonParser.parseString(
                    elementDetailsJson == null || elementDetailsJson.isBlank() ? "[]" : elementDetailsJson);
            elements = parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (Exception ex) {
            elements = new JsonArray();
        }

        // Only ever one clicked element. Locate it live and build a tiny HTML page
        // with the 3 views most useful for triage: outerHTML, innerHTML, and
        // parent outerHTML (context). Everything else (attributes, xPath, coords)
        // stays in elementDetails so the portal has structured data too.
        JsonObject clicked = elements.size() > 0 && elements.get(0).isJsonObject()
                ? elements.get(0).getAsJsonObject()
                : null;
        String clickedHtmlPage = buildClickedElementHtml(driver, clicked, url, title);

        JsonArray elementsLive = enrichElementsWithLiveData(driver, elements);

        JsonObject env = new JsonObject();
        env.addProperty("schemaVersion", "1");
        env.addProperty("kind", "elements-review");
        env.addProperty("capturedAt", Instant.now().toString());
        env.addProperty("pcName", SystemDetails.getSystemComputerName());
        env.addProperty("appVersion", appVersion != null ? appVersion : "");
        env.addProperty("failedPlugin", "pageScanner");
        env.addProperty("failureReason", failureReason != null ? failureReason : "User-initiated elements review");
        env.addProperty("message", message != null ? message : "");

        JsonObject browser = new JsonObject();
        browser.addProperty("url", url);
        browser.addProperty("title", title);
        if (vp != null) {
            JsonObject v = new JsonObject();
            v.addProperty("w", vp.getWidth());
            v.addProperty("h", vp.getHeight());
            browser.add("viewport", v);
        }
        env.add("browser", browser);

        env.add("elementDetails", elements);
        env.add("elementsLive", elementsLive);
        env.addProperty("elementCount", elements.size());
        try {
            env.addProperty("html", gzipAndBase64(clickedHtmlPage));
            env.addProperty("htmlSha256", "sha256:" + sha256Hex(clickedHtmlPage));
        } catch (Exception ignored) {
        }

        JsonArray consoleTail = new JsonArray();
        try {
            for (String line : ConsoleRingBuffer.snapshot(50)) consoleTail.add(line);
        } catch (Exception ignored) {
        }
        env.add("consoleTail", consoleTail);

        return env;
    }

    public CaptureResult captureElementsAndSend(
            WebDriver driver, String elementDetailsJson, String message, String failureReason) {
        // MultiPlugins network traffic disabled — UI is gated; this is a hard stop.
        log.info("captureElementsAndSend disabled — no MultiPlugins call performed");
        return CaptureResult.error("Support upload disabled");
    }

    /**
     * Builds a small standalone HTML document with 3 views of the clicked element:
     *   1. outerHTML   — the element itself
     *   2. innerHTML   — only its contents
     *   3. parentHTML  — the immediate parent's outerHTML (useful for DOM context)
     * Each view is rendered both as a rendered preview and as an escaped code block.
     * Safe to call with {@code clicked == null} or when the XPath doesn't resolve —
     * in those cases the page still includes the DTO metadata with an error note.
     */
    private String buildClickedElementHtml(WebDriver driver, JsonObject clicked, String pageUrl, String pageTitle) {
        String xPath =
                clicked != null && clicked.has("xPath") && !clicked.get("xPath").isJsonNull()
                        ? clicked.get("xPath").getAsString()
                        : "";
        String tagName = clicked != null
                        && clicked.has("tagName")
                        && !clicked.get("tagName").isJsonNull()
                ? clicked.get("tagName").getAsString()
                : "";
        String definedName = clicked != null
                        && clicked.has("definedName")
                        && !clicked.get("definedName").isJsonNull()
                ? clicked.get("definedName").getAsString()
                : "";

        String outerHtml = null;
        String innerHtml = null;
        String parentHtml = null;
        String locateError = null;
        boolean displayed = false;
        boolean enabled = false;

        if (driver != null && !xPath.isBlank()) {
            try {
                List<WebElement> matches = driver.findElements(By.xpath(xPath));
                if (matches.isEmpty()) {
                    locateError = "No element matched xPath";
                } else {
                    WebElement we = matches.get(0);
                    try {
                        displayed = we.isDisplayed();
                    } catch (Exception ignored) {
                    }
                    try {
                        enabled = we.isEnabled();
                    } catch (Exception ignored) {
                    }
                    try {
                        outerHtml = we.getAttribute("outerHTML");
                    } catch (Exception ignored) {
                    }
                    try {
                        innerHtml = we.getAttribute("innerHTML");
                    } catch (Exception ignored) {
                    }
                    try {
                        WebElement parent = we.findElement(By.xpath(".."));
                        if (parent != null) parentHtml = parent.getAttribute("outerHTML");
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ex) {
                locateError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
        } else if (xPath.isBlank()) {
            locateError = "No xPath available on the clicked element";
        } else {
            locateError = "No active WebDriver";
        }

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">");
        sb.append("<title>Element Review — ")
                .append(htmlEscape(definedName.isBlank() ? tagName : definedName))
                .append("</title>");
        sb.append("<style>body{font-family:system-ui,Segoe UI,Arial,sans-serif;margin:20px;color:#222}"
                + "h1{font-size:18px;margin:0 0 6px}"
                + "h2{font-size:14px;color:#0b5394;margin:18px 0 6px;border-bottom:1px solid #eee;padding-bottom:4px}"
                + ".meta{font-size:12px;color:#555;margin-bottom:14px}"
                + ".meta span{display:inline-block;margin-right:14px}"
                + ".preview{border:1px dashed #ccc;padding:10px;margin:6px 0;background:#fafafa;border-radius:4px}"
                + "pre{background:#f4f4f4;border:1px solid #ddd;padding:8px;overflow:auto;font-size:12px;border-radius:4px;white-space:pre-wrap;word-break:break-word}"
                + ".err{color:#c00;font-weight:600}"
                + "</style></head><body>");

        sb.append("<h1>Element Review</h1>");
        sb.append("<div class=\"meta\">")
                .append("<span><b>Page:</b> ")
                .append(htmlEscape(pageTitle))
                .append("</span>")
                .append("<span><b>URL:</b> ")
                .append(htmlEscape(pageUrl))
                .append("</span>")
                .append("<span><b>Captured:</b> ")
                .append(htmlEscape(Instant.now().toString()))
                .append("</span>")
                .append("</div>");

        sb.append("<h2>Target DTO</h2><pre>")
                .append(htmlEscape(
                        clicked != null
                                ? new GsonBuilder().setPrettyPrinting().create().toJson(clicked)
                                : "(none)"))
                .append("</pre>");

        sb.append("<h2>Live Locate</h2><div class=\"meta\">")
                .append("<span><b>xPath:</b> ")
                .append(htmlEscape(xPath))
                .append("</span>")
                .append("<span><b>displayed:</b> ")
                .append(displayed)
                .append("</span>")
                .append("<span><b>enabled:</b> ")
                .append(enabled)
                .append("</span>")
                .append("</div>");
        if (locateError != null) {
            sb.append("<p class=\"err\">Locate failed: ")
                    .append(htmlEscape(locateError))
                    .append("</p>");
        }

        appendSection(sb, "outerHTML (rendered)", outerHtml, true);
        appendSection(sb, "outerHTML (source)", outerHtml, false);
        appendSection(sb, "innerHTML (rendered)", innerHtml, true);
        appendSection(sb, "innerHTML (source)", innerHtml, false);
        appendSection(sb, "parent outerHTML (context, source)", parentHtml, false);

        sb.append("</body></html>");
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String heading, String html, boolean rendered) {
        sb.append("<h2>").append(htmlEscape(heading)).append("</h2>");
        if (html == null || html.isBlank()) {
            sb.append("<p class=\"err\">(unavailable)</p>");
            return;
        }
        if (rendered) {
            sb.append("<div class=\"preview\">").append(html).append("</div>");
        } else {
            sb.append("<pre>").append(htmlEscape(html)).append("</pre>");
        }
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Best-effort enrichment: for each element in the scanned list, try to
     * locate it via its xPath in the live DOM and capture runtime state
     * (displayed/enabled/location/size). Capped at 200 elements so a huge
     * scan can't stall the upload; failures are recorded per element.
     */
    private JsonArray enrichElementsWithLiveData(WebDriver driver, JsonArray elements) {
        JsonArray result = new JsonArray();
        if (driver == null || elements == null || elements.size() == 0) return result;

        int limit = Math.min(elements.size(), 200);
        for (int i = 0; i < limit; i++) {
            JsonElement raw = elements.get(i);
            if (!raw.isJsonObject()) continue;
            JsonObject el = raw.getAsJsonObject();

            JsonObject live = new JsonObject();
            String xPath = el.has("xPath") && !el.get("xPath").isJsonNull()
                    ? el.get("xPath").getAsString()
                    : "";
            String tag = el.has("tagName") && !el.get("tagName").isJsonNull()
                    ? el.get("tagName").getAsString()
                    : "";
            Integer id =
                    el.has("id") && !el.get("id").isJsonNull() ? el.get("id").getAsInt() : null;
            if (id != null) live.addProperty("id", id);
            live.addProperty("tagName", tag);
            live.addProperty("xPath", xPath);

            if (xPath.isEmpty()) {
                live.addProperty("found", false);
                live.addProperty("reason", "empty-xpath");
                result.add(live);
                continue;
            }

            try {
                List<WebElement> matches = driver.findElements(By.xpath(xPath));
                if (matches.isEmpty()) {
                    live.addProperty("found", false);
                    live.addProperty("reason", "no-match");
                } else {
                    WebElement we = matches.get(0);
                    live.addProperty("found", true);
                    live.addProperty("matchCount", matches.size());
                    try {
                        live.addProperty("displayed", we.isDisplayed());
                    } catch (Exception ignored) {
                    }
                    try {
                        live.addProperty("enabled", we.isEnabled());
                    } catch (Exception ignored) {
                    }
                    try {
                        live.addProperty("selected", we.isSelected());
                    } catch (Exception ignored) {
                    }
                    try {
                        Point p = we.getLocation();
                        JsonObject loc = new JsonObject();
                        loc.addProperty("x", p.getX());
                        loc.addProperty("y", p.getY());
                        live.add("location", loc);
                    } catch (Exception ignored) {
                    }
                    try {
                        Dimension d = we.getSize();
                        JsonObject sz = new JsonObject();
                        sz.addProperty("w", d.getWidth());
                        sz.addProperty("h", d.getHeight());
                        live.add("size", sz);
                    } catch (Exception ignored) {
                    }
                    try {
                        String text = we.getText();
                        if (text != null) live.addProperty("text", sanitize(text));
                    } catch (Exception ignored) {
                    }
                    try {
                        String outer = we.getAttribute("outerHTML");
                        if (outer != null) live.addProperty("outerHTML", outer);
                    } catch (Exception ignored) {
                    }
                    try {
                        String inner = we.getAttribute("innerHTML");
                        if (inner != null) live.addProperty("innerHTML", inner);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ex) {
                live.addProperty("found", false);
                live.addProperty("reason", "error:" + ex.getClass().getSimpleName());
            }
            result.add(live);
        }

        if (elements.size() > limit) {
            JsonObject truncated = new JsonObject();
            truncated.addProperty("truncated", true);
            truncated.addProperty("skipped", elements.size() - limit);
            result.add(truncated);
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String sanitize(String s) {
        if (s == null) return "UNKNOWN";
        String cleaned = s.trim().replaceAll("[\\p{Cntrl}]", "");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static String safeString(String s) {
        return s != null ? s : "";
    }

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

        public static CaptureResult ok(String id) {
            return new CaptureResult(true, id, null);
        }

        public static CaptureResult error(String msg) {
            return new CaptureResult(false, null, msg);
        }

        public boolean isOk() {
            return ok;
        }

        public String ticketId() {
            return ticketId;
        }

        public String error() {
            return error;
        }
    }
}
