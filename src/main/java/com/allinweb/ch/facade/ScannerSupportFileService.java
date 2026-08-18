package com.allinweb.ch.facade;

import com.allinweb.ch.license.SystemDetails;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

public final class ScannerSupportFileService {

    private static final DateTimeFormatter SUPPORT_FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final SupportContext context;
    private final Supplier<Instant> capturedAt;
    private final Supplier<LocalDateTime> fileTime;
    private final SupportCapture supportCapture;
    private final Gson gson;

    public ScannerSupportFileService() {
        this(new DefaultSupportContext(), Instant::now, LocalDateTime::now, new SupportCapture());
    }

    ScannerSupportFileService(
            SupportContext context, Supplier<Instant> capturedAt, Supplier<LocalDateTime> fileTime) {
        this(context, capturedAt, fileTime, new SupportCapture());
    }

    ScannerSupportFileService(
            SupportContext context,
            Supplier<Instant> capturedAt,
            Supplier<LocalDateTime> fileTime,
            SupportCapture supportCapture) {
        this.context = Objects.requireNonNull(context, "context");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        this.fileTime = Objects.requireNonNull(fileTime, "fileTime");
        this.supportCapture = Objects.requireNonNull(supportCapture, "supportCapture");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public SupportFile pageReview(String html, String url, String title) {
        String safeHtml = safe(html);
        byte[] htmlBytes = safeHtml.getBytes(StandardCharsets.UTF_8);

        JsonObject support = new JsonObject();
        support.addProperty("schemaVersion", "1");
        support.addProperty("capturedAt", capturedAt.get().toString());
        support.addProperty("pcName", safe(context.computerName()));
        support.addProperty("email", safe(context.licenseEmail()));
        support.addProperty("appVersion", safe(context.appVersion()));
        support.addProperty("failedPlugin", "pageScanner");
        support.addProperty("failureReason", "User-initiated local capture");

        JsonObject browser = new JsonObject();
        browser.addProperty("url", safe(url));
        browser.addProperty("title", safe(title));
        support.add("browser", browser);

        support.addProperty("html", gzipAndBase64(htmlBytes));
        support.addProperty("htmlSha256", "sha256:" + sha256Hex(htmlBytes));

        String suggestedName = fileTime.get().format(SUPPORT_FILE_TIMESTAMP) + "_page_review.support";
        return new SupportFile(suggestedName, gson.toJson(support));
    }

    public SupportFile elementsReview(SupportCapture.Browser browser, String elementDetailsJson, String message) {
        JsonObject support = supportCapture.buildElementsReviewEnvelope(browser, elementDetailsJson, message, null);
        support.addProperty("email", safe(context.licenseEmail()));
        support.addProperty("requesterName", safe(context.licenseOwner()));
        support.addProperty("userName", safe(context.systemUserName()));
        support.addProperty("organizationName", safe(context.organizationName()));
        maskBrowserUrl(support);

        String suggestedName = fileTime.get().format(SUPPORT_FILE_TIMESTAMP) + "_elements_review.support";
        return new SupportFile(suggestedName, gson.toJson(support));
    }

    private static void maskBrowserUrl(JsonObject support) {
        if (!support.has("browser") || !support.get("browser").isJsonObject()) {
            return;
        }
        JsonObject browser = support.getAsJsonObject("browser");
        browser.addProperty("url", "www.example.com");
        if (browser.has("title")) {
            String title = browser.get("title").getAsString();
            browser.addProperty("title", title.replaceAll("https?://[^\\s/]+", "www.example.com"));
        }
    }

    private static String gzipAndBase64(byte[] bytes) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(bytes);
            }
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compress support HTML", ex);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder hexHash = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hexHash.append(String.format("%02x", b));
            }
            return hexHash.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash support HTML", ex);
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    public record SupportFile(String suggestedFileName, String json) {}

    interface SupportContext {
        String computerName();

        String licenseEmail();

        String appVersion();

        String licenseOwner();

        String organizationName();

        String systemUserName();
    }

    private static final class DefaultSupportContext implements SupportContext {
        @Override
        public String computerName() {
            return SystemDetails.getSystemComputerName();
        }

        @Override
        public String licenseEmail() {
            return ARPropertyManager.getInstance().getProperty(ARPropertyEnum.LICENSE_EMAIL);
        }

        @Override
        public String appVersion() {
            return ARPropertyManager.getInstance().getProperty(ARPropertyEnum.VERSION);
        }

        @Override
        public String licenseOwner() {
            return ARPropertyManager.getInstance().getProperty(ARPropertyEnum.LICENSE_OWNER);
        }

        @Override
        public String organizationName() {
            return ARPropertyManager.getInstance().getProperty(ARPropertyEnum.LICENSE_ORG_NAME);
        }

        @Override
        public String systemUserName() {
            return SystemDetails.getSystemUserName();
        }
    }
}
