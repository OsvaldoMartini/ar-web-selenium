package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the plugin encryption key with online activation via Supabase.
 *
 * <p>Security model:
 * <ul>
 *   <li>First launch: Java collects machine fingerprint + license hash</li>
 *   <li>Calls MultiPlugins API {@code /api/client/activate} to validate the license</li>
 *   <li>On success: receives the plugin AES key, wraps it with machine_id + license</li>
 *   <li>Saves as {@code ACTIVATED:...} in plugins.key - works offline from now on</li>
 *   <li>Periodic {@code /api/client/validate} call checks expiry/revocation</li>
 * </ul>
 *
 * <p>No password prompt needed - the server validates the license, and the key
 * is bound to the specific machine (can't be copied to another PC).
 *
 * <p>File format of plugins.key (when activated):
 * <pre>
 *   ACTIVATED:base64([salt(16)] [iv(12)] [encrypted_plugin_key + gcm_tag])
 * </pre>
 * If the file starts with "PROTECTED:" (legacy password-based), it falls back
 * to password prompt. Plain hex keys are also supported (backward compat).
 */
@Slf4j
public class PluginKeyManager {

    private static volatile PluginKeyManager instance;

    // Crypto constants
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int KEY_LENGTH_BITS = 256;

    // Key file prefixes
    private static final String ACTIVATED_PREFIX = "ACTIVATED:";
    private static final String PROTECTED_PREFIX = "PROTECTED:"; // legacy

    // License file decryption (AES-128-ECB for ARWeb.lic)
    private static final String LIC_KEY = "0123456789abcdef";
    private static final String LIC_ALGORITHM = "AES/ECB/PKCS5Padding";

    // MultiPlugins API
    private static final String API_URL = System.getProperty("arweb.api.url", "https://multiplugins.ch/api");

    // Validation interval (days)
    private static final int VALIDATE_INTERVAL_DAYS = 7;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /** The unwrapped plugin AES key - null until successfully loaded */
    private byte[] pluginKey;

    /** Cached machine ID */
    private String machineId;

    private PluginKeyManager() {}

    public static PluginKeyManager getInstance() {
        if (instance == null) {
            synchronized (PluginKeyManager.class) {
                if (instance == null) {
                    instance = new PluginKeyManager();
                }
            }
        }
        return instance;
    }

    /**
     * Get the plugin encryption key. Activates online if needed.
     * Returns null if activation fails.
     */
    public byte[] getPluginKey() {
        if (pluginKey != null) return pluginKey;

        synchronized (this) {
            if (pluginKey != null) return pluginKey;

            try {
                String pluginsDir = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_PLUGINS);
                if (pluginsDir == null) {
                    log.error("PluginKeyManager - path_plugins not configured");
                    return null;
                }

                String licenseFingerprint = getLicenseFingerprint();
                String machId = getMachineId();

                if (licenseFingerprint == null) {
                    log.error("PluginKeyManager - could not read license fingerprint");
                    showError("License file not found.\nMake sure ARWeb.lic is in the correct location.");
                    return null;
                }

                // Priority 1: Extract org key embedded in ARWeb.lic
                String orgKeyHex = extractOrgKeyFromLicense();
                if (orgKeyHex != null && !orgKeyHex.isEmpty()) {
                    pluginKey = hexToBytes(orgKeyHex);
                    log.info("PluginKeyManager - using org key from ARWeb.lic ({}-bit)", pluginKey.length * 8);
                    scheduleValidation(licenseFingerprint, machId);
                }

                // Priority 2: plugins.key file (legacy fallback)
                Path keyFile = Paths.get(pluginsDir, "plugins.key");
                if (pluginKey == null && Files.exists(keyFile)) {
                    String content =
                            Files.readString(keyFile, StandardCharsets.UTF_8).trim();

                    if (content.startsWith(ACTIVATED_PREFIX)) {
                        String encoded = content.substring(ACTIVATED_PREFIX.length());
                        pluginKey = unwrapKey(encoded, machId, licenseFingerprint);
                        log.info("PluginKeyManager - key unlocked (machine-bound)");
                        scheduleValidation(licenseFingerprint, machId);
                    } else if (content.startsWith(PROTECTED_PREFIX)) {
                        log.info("PluginKeyManager - legacy PROTECTED key, re-activating online");
                        pluginKey = activateOnline(keyFile, licenseFingerprint, machId);
                    } else {
                        pluginKey = hexToBytes(content);
                        log.info("PluginKeyManager - loaded plain key (not machine-bound)");
                    }
                }

                // Priority 3: Online activation
                if (pluginKey == null) {
                    pluginKey = activateOnline(keyFile, licenseFingerprint, machId);
                }
            } catch (Exception e) {
                log.error("PluginKeyManager - failed: {}", e.getMessage(), e);
                showError("Plugin activation failed:\n" + e.getMessage());
                pluginKey = null;
            }
        }

        return pluginKey;
    }

    /** Clear cached key (for re-activation). */
    public void clearKey() {
        if (pluginKey != null) Arrays.fill(pluginKey, (byte) 0);
        pluginKey = null;
    }

    // ── Online activation ───────────────────────────────────────────────────

    private byte[] activateOnline(Path keyFile, String licenseFingerprint, String machId) throws Exception {
        log.info("PluginKeyManager - activating online...");

        String hostname = getHostname();
        String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String javaVersion = System.getProperty("java.version");

        // Build JSON body for MultiPlugins API
        String json = String.format(
                "{\"license_hash\":\"%s\",\"machine_id\":\"%s\",\"hostname\":\"%s\","
                        + "\"os_info\":\"%s\",\"java_version\":\"%s\"}",
                escapeJson(licenseFingerprint),
                escapeJson(machId),
                escapeJson(hostname),
                escapeJson(osInfo),
                escapeJson(javaVersion));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/client/activate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("PluginKeyManager - activation HTTP error: {} {}", response.statusCode(), response.body());
            showError(
                    "Activation server returned error " + response.statusCode() + "\nCheck your internet connection.");
            return null;
        }

        String body = response.body();
        log.debug("PluginKeyManager - activation response: {}", body);

        // Parse JSON response (minimal parsing, no dependency needed)
        boolean ok = body.contains("\"ok\":true") || body.contains("\"ok\": true");
        if (!ok) {
            String error = extractJsonString(body, "error");
            log.error("PluginKeyManager - activation rejected: {}", error);
            String userMsg =
                    switch (error) {
                        case "LICENSE_NOT_FOUND" -> "License not recognized.\nContact your administrator.";
                        case "LICENSE_REVOKED" -> "Your license has been revoked.\nContact your administrator.";
                        case "LICENSE_EXPIRED" -> "Your license has expired.\nContact your administrator to renew.";
                        case "MAX_ACTIVATIONS_REACHED" -> "Maximum activations reached for this license.\n"
                                + "Contact your administrator to increase the limit.";
                        default -> "Activation failed: " + error;
                    };
            showError(userMsg);
            return null;
        }

        // Extract the plugin key from response
        String pluginKeyHex = extractJsonString(body, "plugin_key");
        if (pluginKeyHex == null || pluginKeyHex.isEmpty() || pluginKeyHex.equals("YOUR_HEX_KEY_HERE")) {
            log.error("PluginKeyManager - server returned empty/placeholder plugin key");
            showError(
                    "Activation succeeded but plugin key not configured on server.\n" + "Contact your administrator.");
            return null;
        }

        byte[] key = hexToBytes(pluginKeyHex);

        // Wrap with machine_id + license and save
        String wrapped = wrapKey(key, machId, licenseFingerprint);
        Files.writeString(keyFile, wrapped, StandardCharsets.UTF_8);

        String clientName = extractJsonString(body, "client_name");
        log.info("PluginKeyManager - activated successfully for client: {}", clientName);

        // Download encrypted plugins from Supabase Storage
        downloadPlugins(body, keyFile.getParent());

        return key;
    }

    // ── Plugin download from Supabase Storage ───────────────────────────────

    /**
     * Downloads encrypted plugin files (.enc) from Supabase Storage.
     * Called after successful activation. Parses the plugins array from
     * the activation response and downloads each file.
     */
    private void downloadPlugins(String activationResponse, Path pluginsDir) {
        new Thread(() -> {
            try {
                // Extract plugins array from response
                // Format: "plugins":[{"name":"...","storage_path":"...","version":"..."}, ...]
                int pluginsStart = activationResponse.indexOf("\"plugins\":[");
                if (pluginsStart < 0) {
                    log.info("PluginKeyManager - no plugins to download");
                    return;
                }

                int arrayStart = activationResponse.indexOf('[', pluginsStart);
                int arrayEnd = findMatchingBracket(activationResponse, arrayStart);
                if (arrayEnd < 0) return;

                String pluginsJson = activationResponse.substring(arrayStart, arrayEnd + 1);

                // Parse each plugin entry and download
                int pos = 0;
                int downloaded = 0;
                int skipped = 0;

                while (true) {
                    int objStart = pluginsJson.indexOf('{', pos);
                    if (objStart < 0) break;
                    int objEnd = pluginsJson.indexOf('}', objStart);
                    if (objEnd < 0) break;

                    String obj = pluginsJson.substring(objStart, objEnd + 1);
                    pos = objEnd + 1;

                    String storagePath = extractJsonString(obj, "storage_path");
                    String fileName = extractJsonString(obj, "file_name");
                    String version = extractJsonString(obj, "version");
                    String name = extractJsonString(obj, "name");
                    String checksum = extractJsonString(obj, "checksum");

                    if (storagePath == null || storagePath.isEmpty()) continue;

                    // Determine local path: plugins/{pluginName}/build/{name}.min.enc
                    // The storage_path is like "pageScanner/pageScanner.min.enc"
                    Path localDir = pluginsDir.resolve(storagePath).getParent();
                    Path localFile = pluginsDir.resolve(storagePath);

                    // Check if already up to date (by checksum or just existence)
                    if (Files.exists(localFile) && checksum != null) {
                        String localChecksum = bytesToHex(sha256(Files.readAllBytes(localFile)));
                        if (localChecksum.equals(checksum)) {
                            log.debug("PluginKeyManager - {} v{} already up to date", name, version);
                            skipped++;
                            continue;
                        }
                    }

                    // Download from MultiPlugins server
                    String downloadUrl = API_URL.replace("/api", "") + "/data/plugins/" + storagePath;

                    log.info("PluginKeyManager - downloading {} v{} from {}", name, version, downloadUrl);

                    HttpRequest dlRequest = HttpRequest.newBuilder()
                            .uri(URI.create(downloadUrl))
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build();

                    HttpResponse<byte[]> dlResponse =
                            httpClient.send(dlRequest, HttpResponse.BodyHandlers.ofByteArray());

                    if (dlResponse.statusCode() == 200) {
                        Files.createDirectories(localDir);
                        Files.write(localFile, dlResponse.body());
                        log.info(
                                "PluginKeyManager - saved {} ({} KB)",
                                localFile.getFileName(),
                                dlResponse.body().length / 1024);
                        downloaded++;
                    } else {
                        log.warn("PluginKeyManager - download failed for {}: HTTP {}", name, dlResponse.statusCode());
                    }
                }

                log.info("PluginKeyManager - download complete: {} new, {} up-to-date", downloaded, skipped);

            } catch (Exception e) {
                log.warn("PluginKeyManager - plugin download error: {}", e.getMessage());
                // Non-fatal - plugins may already exist locally from .zip distribution
            }
        });
    }

    private static int findMatchingBracket(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ── Periodic validation ─────────────────────────────────────────────────

    private void scheduleValidation(String licenseFingerprint, String machId) {
        // Check last validation timestamp
        try {
            String pluginsDir = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_PLUGINS);
            Path stampFile = Paths.get(pluginsDir, ".last-validation");

            if (Files.exists(stampFile)) {
                long lastValidation = Long.parseLong(
                        Files.readString(stampFile, StandardCharsets.UTF_8).trim());
                long daysSince = (System.currentTimeMillis() - lastValidation) / (1000L * 60 * 60 * 24);
                if (daysSince < VALIDATE_INTERVAL_DAYS) {
                    log.debug("PluginKeyManager - last validation {} days ago, skipping", daysSince);
                    return;
                }
            }

            // Run validation in background
            new Thread(() -> {
                try {
                    boolean valid = validateOnline(licenseFingerprint, machId);
                    if (valid) {
                        Files.writeString(
                                Paths.get(pluginsDir, ".last-validation"),
                                String.valueOf(System.currentTimeMillis()),
                                StandardCharsets.UTF_8);
                        log.info("PluginKeyManager - validation passed");
                    } else {
                        log.warn("PluginKeyManager - validation failed, clearing key");
                        clearKey();
                        // Delete plugins.key so next launch re-activates
                        Files.deleteIfExists(Paths.get(pluginsDir, "plugins.key"));
                        showError("Your license is no longer valid.\n"
                                + "The application will need to re-activate on next launch.");
                    }
                } catch (Exception e) {
                    // Network error - don't block, try next time
                    log.warn("PluginKeyManager - validation network error: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("PluginKeyManager - could not schedule validation: {}", e.getMessage());
        }
    }

    private boolean validateOnline(String licenseFingerprint, String machId) throws Exception {
        String json = String.format(
                "{\"license_hash\":\"%s\",\"machine_id\":\"%s\"}", escapeJson(licenseFingerprint), escapeJson(machId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/client/validate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("PluginKeyManager - validate HTTP error: {}", response.statusCode());
            return true; // network error → don't block the user
        }

        String body = response.body();
        return body.contains("\"ok\":true") || body.contains("\"ok\": true");
    }

    // ── Key wrapping (machine-bound, PBKDF2 + AES-GCM) ─────────────────────

    private String wrapKey(byte[] pluginKey, String machId, String licenseFingerprint) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);

        byte[] wrapperKey = deriveKey(machId, licenseFingerprint, salt);
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrapperKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(pluginKey);

        // Format: salt + iv + encrypted (which includes GCM tag)
        byte[] combined = new byte[salt.length + iv.length + encrypted.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(iv, 0, combined, salt.length, iv.length);
        System.arraycopy(encrypted, 0, combined, salt.length + iv.length, encrypted.length);

        return ACTIVATED_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    private byte[] unwrapKey(String encoded, String machId, String licenseFingerprint) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encoded);

        byte[] salt = Arrays.copyOfRange(combined, 0, SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(combined, SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(combined, SALT_LENGTH + IV_LENGTH, combined.length);

        byte[] wrapperKey = deriveKey(machId, licenseFingerprint, salt);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(wrapperKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));

        return cipher.doFinal(encrypted);
    }

    private byte[] deriveKey(String machId, String licenseFingerprint, byte[] salt) throws Exception {
        String combined = machId + "|" + licenseFingerprint;
        KeySpec spec = new PBEKeySpec(combined.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    // ── Machine fingerprint ─────────────────────────────────────────────────

    /**
     * Generate a stable machine ID from hostname + MAC addresses.
     * The ID is a SHA-256 hash, so it's consistent across launches.
     */
    public String getMachineId() {
        if (machineId != null) return machineId;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append(getHostname()).append("|");

            // Collect all MAC addresses
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface ni = nets.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    for (byte b : mac) sb.append(String.format("%02x", b));
                    sb.append(",");
                }
            }

            // Add OS + user
            sb.append(System.getProperty("os.name")).append("|");
            sb.append(System.getProperty("user.name"));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            machineId = bytesToHex(hash);
        } catch (Exception e) {
            log.warn("PluginKeyManager - machine ID fallback: {}", e.getMessage());
            // Fallback: just hostname + user
            machineId = bytesToHex(sha256(getHostname() + "|" + System.getProperty("user.name")));
        }

        return machineId;
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getenv("COMPUTERNAME") != null ? System.getenv("COMPUTERNAME") : "unknown";
        }
    }

    // ── Extract org key from ARWeb.lic ────────────────────────────────────────

    /**
     * Decrypt ARWeb.lic and extract the org key from part[4].
     * Format: pcName|domainName|userName|expiryDate|orgKey
     * Returns null if no org key embedded (legacy 4-part format).
     */
    private String extractOrgKeyFromLicense() {
        try {
            String licensePath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_LICENSE);
            if (licensePath == null) licensePath = System.getProperty("user.dir");

            Path licFile = Paths.get(licensePath, "ARWeb.lic");
            if (!Files.exists(licFile)) return null;

            String content = Files.readString(licFile, StandardCharsets.UTF_8).trim();
            SecretKeySpec keySpec = new SecretKeySpec(LIC_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(LIC_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(content));
            String plain = new String(decrypted, StandardCharsets.UTF_8);
            String[] parts = plain.split("\\|");
            if (parts.length >= 5) {
                log.info("PluginKeyManager - org key found in ARWeb.lic");
                return parts[4];
            }
            return null;
        } catch (Exception e) {
            log.debug("PluginKeyManager - no org key in ARWeb.lic: {}", e.getMessage());
            return null;
        }
    }

    // ── License fingerprint ─────────────────────────────────────────────────

    private String getLicenseFingerprint() {
        try {
            String licensePath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_LICENSE);
            if (licensePath == null) licensePath = System.getProperty("user.dir");

            Path licFile = Paths.get(licensePath, "ARWeb.lic");
            if (!Files.exists(licFile)) {
                log.error("PluginKeyManager - license file not found: {}", licFile);
                return null;
            }

            byte[] licContent = Files.readAllBytes(licFile);
            return bytesToHex(sha256(licContent));
        } catch (Exception e) {
            log.error("PluginKeyManager - failed to read license: {}", e.getMessage());
            return null;
        }
    }

    // ── UI (error dialogs only) ─────────────────────────────────────────────

    private void showError(String message) {
        try {
            runOnFxThread(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Plugin Activation");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
                return null;
            });
        } catch (Exception e) {
            // If no JavaFX available, just log
            log.error("PluginKeyManager - {}", message);
        }
    }

    private <T> T runOnFxThread(java.util.function.Supplier<T> action) {
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    // ── JSON helpers (minimal, no external dependency) ──────────────────────

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            // Try without quotes (for null values)
            search = "\"" + key + "\":";
            start = json.indexOf(search);
            if (start < 0) return null;
            start += search.length();
            // Skip whitespace
            while (start < json.length() && json.charAt(start) == ' ') start++;
            if (start >= json.length() || json.charAt(start) == 'n') return null; // null
            if (json.charAt(start) == '"') {
                start++;
                int end = json.indexOf('"', start);
                return end > start ? json.substring(start, end) : null;
            }
            return null;
        }
        start += search.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ── Crypto utilities ────────────────────────────────────────────────────

    private static byte[] sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        return bytes;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
