package com.allinweb.ch.facade.execution.v2;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Loopback-only configuration for the isolated Execution V2 Node runtime. */
public record ExecutionRuntimeClientConfiguration(URI baseUri, Duration requestTimeout) {
    public static final String PORT_ENV = "ARWEB_EXECUTION_V2_PORT";
    public static final String TIMEOUT_SECONDS_ENV = "ARWEB_EXECUTION_V2_REQUEST_TIMEOUT_SECONDS";

    public ExecutionRuntimeClientConfiguration {
        if (baseUri == null
                || !"http".equalsIgnoreCase(baseUri.getScheme())
                || !"127.0.0.1".equals(baseUri.getHost())
                || baseUri.getPort() < 1
                || baseUri.getPort() > 65_535
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null
                || (baseUri.getPath() != null && !baseUri.getPath().isEmpty())) {
            throw new IllegalArgumentException("Execution V2 runtime must be loopback HTTP");
        }
        if (requestTimeout == null
                || requestTimeout.getNano() != 0
                || requestTimeout.compareTo(Duration.ofSeconds(5)) < 0
                || requestTimeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException(
                    "Execution V2 request timeout must be 5 to 120 seconds");
        }
    }

    public static ExecutionRuntimeClientConfiguration fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static ExecutionRuntimeClientConfiguration fromEnvironment(Map<String, String> environment) {
        int port = integer(environment.get(PORT_ENV), 60_110, 1, 65_535, PORT_ENV);
        int timeout = integer(
                environment.get(TIMEOUT_SECONDS_ENV), 60, 5, 120, TIMEOUT_SECONDS_ENV);
        return new ExecutionRuntimeClientConfiguration(
                URI.create("http://127.0.0.1:" + port), Duration.ofSeconds(timeout));
    }

    URI resolve(String path) {
        if (path == null || !path.startsWith("/") || path.contains("..")) {
            throw new IllegalArgumentException("Execution V2 runtime path is invalid");
        }
        return baseUri.resolve(path);
    }

    private static int integer(
            String raw, int fallback, int minimum, int maximum, String name) {
        if (raw == null || raw.isBlank()) return fallback;
        if (!raw.matches("[0-9]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
