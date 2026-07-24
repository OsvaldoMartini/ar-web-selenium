package com.allinweb.ch.db;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Stable identity for the exact browser page observed by Page Scanner.
 *
 * <p>The readable URL is retained in {@code scanned_element.page_url}; the SHA-256 key is used in
 * database identity and indexes so long SPA URLs do not exceed database index limits.
 */
public record ScannedPageIdentity(String actualUrl, String normalizedUrl, String pageKey) {

    private static final String UNKNOWN_PAGE = "arweb://unknown-page";
    private static final String PAGE_KEY_VERSION = "url-v1:";

    /**
     * Build identity for a new live Playwright observation.
     *
     * <p>New observations must never share an "unknown" page bucket: doing so would reintroduce
     * cross-page collisions when the browser URL cannot be read.
     */
    public static ScannedPageIdentity fromLiveUrl(String pageUrl) {
        String actual = pageUrl == null ? "" : pageUrl.trim();
        if (actual.isEmpty()) {
            throw new IllegalArgumentException("The live Playwright page URL is unavailable");
        }
        String normalized = normalizeAbsoluteHttpUrl(actual);
        return identity(actual, normalized);
    }

    /**
     * Build identity for rows created before page-scoped persistence existed.
     *
     * <p>Blank legacy URLs share a clearly-labelled sentinel because their original page cannot be
     * recovered. Malformed-but-distinct values remain distinct.
     */
    public static ScannedPageIdentity fromStoredUrl(String pageUrl) {
        String raw = pageUrl == null ? "" : pageUrl.trim();
        if (raw.isEmpty()) {
            return identity("", UNKNOWN_PAGE);
        }

        try {
            return identity(raw, normalizeAbsoluteHttpUrl(raw));
        } catch (IllegalArgumentException invalidUrl) {
            // Preserve malformed-but-distinct browser values rather than merging them.
            return identity(raw, raw);
        }
    }

    private static ScannedPageIdentity identity(String actual, String normalized) {
        return new ScannedPageIdentity(actual, normalized, PAGE_KEY_VERSION + sha256(normalized));
    }

    private static String normalizeAbsoluteHttpUrl(String raw) {
        try {
            URI uri = new URI(raw);
            String scheme = lower(uri.getScheme());
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("The live Playwright page URL must use HTTP or HTTPS");
            }
            String rawHost = uri.getHost();
            if (rawHost == null || rawHost.isBlank()) {
                throw new IllegalArgumentException("The live Playwright page URL has no host");
            }
            String host = rawHost.contains(":")
                    ? rawHost.toLowerCase(Locale.ROOT)
                    : IDN.toASCII(rawHost).toLowerCase(Locale.ROOT);
            if (host.contains(":") && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
            int port = normalizedPort(scheme, uri.getPort());
            String path = normalizePath(uri.getRawPath());

            StringBuilder normalized = new StringBuilder();
            normalized.append(scheme).append("://");
            if (uri.getRawUserInfo() != null && !uri.getRawUserInfo().isEmpty()) {
                normalized.append(uri.getRawUserInfo()).append('@');
            }
            normalized.append(host);
            if (port >= 0) normalized.append(':').append(port);
            normalized.append(path);
            if (uri.getRawQuery() != null) normalized.append('?').append(uri.getRawQuery());
            if (uri.getRawFragment() != null) normalized.append('#').append(uri.getRawFragment());
            return normalized.toString();
        } catch (URISyntaxException invalidUrl) {
            throw new IllegalArgumentException("The live Playwright page URL is invalid", invalidUrl);
        }
    }

    private static String normalizePath(String rawPath) {
        String path = value(rawPath);
        if (path.isEmpty()) return "/";
        try {
            path = new URI(path).normalize().getRawPath();
        } catch (URISyntaxException ignored) {
            // Keep the browser-provided path when URI path-only normalization cannot parse it.
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    private static int normalizedPort(String scheme, int port) {
        if (port < 0) return -1;
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }

    private static String lower(String value) {
        return value(value).toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : bytes) {
                result.append(Character.forDigit((b >> 4) & 0xF, 16));
                result.append(Character.forDigit(b & 0xF, 16));
            }
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is required for scanned page identity", unavailable);
        }
    }
}
