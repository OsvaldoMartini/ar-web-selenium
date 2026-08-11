package com.allinweb.ch.facade.execution.v2;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Fail-closed process configuration for the Java side of Execution V2 grant signing. */
public final class ExecutionRuntimeGrantConfiguration {
    public static final String SECRET_ENV = "ARWEB_EXECUTION_V2_GRANT_SECRET_BASE64URL";
    public static final String KEY_ID_ENV = "ARWEB_EXECUTION_V2_GRANT_KID";
    public static final String LIFETIME_ENV = "ARWEB_EXECUTION_V2_GRANT_SECONDS";

    private static final Pattern BASE64_URL = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Duration DEFAULT_LIFETIME = Duration.ofSeconds(90);
    private static final Duration MINIMUM_LIFETIME = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_LIFETIME = Duration.ofSeconds(120);

    private final byte[] secret;
    private final String keyId;
    private final Duration lifetime;

    public ExecutionRuntimeGrantConfiguration(byte[] secret, String keyId, Duration lifetime) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("Execution V2 grant secret must contain at least 32 bytes");
        }
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("Execution V2 grant key ID is invalid");
        }
        if (lifetime == null
                || lifetime.compareTo(MINIMUM_LIFETIME) < 0
                || lifetime.compareTo(MAXIMUM_LIFETIME) > 0
                || lifetime.getNano() != 0) {
            throw new IllegalArgumentException("Execution V2 grant lifetime must be 10 to 120 seconds");
        }
        this.secret = secret.clone();
        this.keyId = keyId;
        this.lifetime = lifetime;
    }

    /** Missing secret means disabled/not-ready; malformed configured values fail startup wiring. */
    public static Optional<ExecutionRuntimeGrantConfiguration> fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static Optional<ExecutionRuntimeGrantConfiguration> fromEnvironment(
            Map<String, String> environment) {
        String rawSecret = environment.get(SECRET_ENV);
        if (rawSecret == null || rawSecret.isBlank()) return Optional.empty();
        String canonicalSecret = rawSecret.trim();
        if (!BASE64_URL.matcher(canonicalSecret).matches()) {
            throw new IllegalArgumentException("Execution V2 grant secret is not canonical base64url");
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(canonicalSecret);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Execution V2 grant secret is not canonical base64url");
        }
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(canonicalSecret)) {
            throw new IllegalArgumentException("Execution V2 grant secret is not canonical base64url");
        }

        String keyId = environment.getOrDefault(KEY_ID_ENV, "v1").trim();
        Duration lifetime = DEFAULT_LIFETIME;
        String rawLifetime = environment.get(LIFETIME_ENV);
        if (rawLifetime != null && !rawLifetime.isBlank()) {
            if (!rawLifetime.matches("[0-9]+")) {
                throw new IllegalArgumentException("Execution V2 grant lifetime is invalid");
            }
            try {
                lifetime = Duration.ofSeconds(Long.parseLong(rawLifetime));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Execution V2 grant lifetime is invalid");
            }
        }
        return Optional.of(new ExecutionRuntimeGrantConfiguration(decoded, keyId, lifetime));
    }

    byte[] secretCopy() {
        return secret.clone();
    }

    public String keyId() {
        return keyId;
    }

    public Duration lifetime() {
        return lifetime;
    }
}
