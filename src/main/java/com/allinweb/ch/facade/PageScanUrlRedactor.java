package com.allinweb.ch.facade;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;

/** Removes credentials and request-specific values before a page URL enters scan history. */
public final class PageScanUrlRedactor {

    private static final String REDACTED_PAGE = "arweb://redacted-page";

    private PageScanUrlRedactor() {}

    public static String redact(String input) {
        if (input == null || input.isBlank()) return REDACTED_PAGE;
        try {
            URI uri = URI.create(input.trim());
            String scheme = value(uri.getScheme()).toLowerCase(Locale.ROOT);
            String rawHost = uri.getHost();
            if ((!"http".equals(scheme) && !"https".equals(scheme))
                    || rawHost == null
                    || rawHost.isBlank()) {
                return REDACTED_PAGE;
            }
            String host = rawHost.contains(":")
                    ? rawHost.toLowerCase(Locale.ROOT)
                    : IDN.toASCII(rawHost).toLowerCase(Locale.ROOT);
            if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
            int port = uri.getPort();
            boolean defaultPort = ("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443);
            String path = value(uri.getRawPath());
            if (path.isBlank()) path = "/";
            return scheme + "://" + host + (port >= 0 && !defaultPort ? ":" + port : "") + path;
        } catch (RuntimeException invalid) {
            return REDACTED_PAGE;
        }
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }
}
