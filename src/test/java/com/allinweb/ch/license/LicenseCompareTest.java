package com.allinweb.ch.license;

import java.io.Console;
import java.io.OutputStream;
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
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Compare the OLD and NEW license files side by side.
 *
 *  OLD: C:\Users\osval\Downloads\files\ARWeb.lic        (previous encryption)
 *  NEW: C:\Users\osval\Downloads\files\ARWeb-v4-2-1.lic (new encryption)
 *
 * For each file:
 *   1. Read raw bytes and compute SHA-256 fingerprint.
 *   2. AES-128-ECB decrypt with the shared key.
 *   3. Split plaintext by '|' and display every part.
 *   4. Simulate LicenseManager.validateLicense() fields.
 *
 * Run:
 *   mvn -pl . -Dtest=com.allinweb.ch.license.LicenseCompareTest test
 * Or standalone:
 *   javac -d out src/test/java/com/allinweb/ch/license/LicenseCompareTest.java
 *   java -cp out com.allinweb.ch.license.LicenseCompareTest
 */
public class LicenseCompareTest {

    private static final String KEY = "0123456789abcdef"; // 16 bytes, AES-128

    private static final String OLD_LICENSE = "C:\\Users\\osval\\Downloads\\files\\ARWeb.lic";
    private static final String NEW_LICENSE = "C:\\Users\\osval\\Downloads\\files\\ARWeb-v4-2-1.lic";

    public static void main(String[] args) throws Exception {
        String oldPath = args.length > 0 ? args[0] : OLD_LICENSE;
        String newPath = args.length > 1 ? args[1] : NEW_LICENSE;

        System.out.println("=== License Compare Test ===\n");

        System.out.println("========================================");
        System.out.println("  OLD LICENSE (previous encryption)");
        System.out.println("========================================");
        String[] oldParts = processLicense(oldPath);

        System.out.println();
        System.out.println("========================================");
        System.out.println("  NEW LICENSE (v4.2.1 encryption)");
        System.out.println("========================================");
        String[] newParts = processLicense(newPath);

        // Compare
        System.out.println();
        System.out.println("========================================");
        System.out.println("  COMPARISON");
        System.out.println("========================================");

        if (oldParts == null || newParts == null) {
            System.out.println("Cannot compare -- one or both licenses failed to decrypt.");
            System.exit(1);
        }

        int maxParts = Math.max(oldParts.length, newParts.length);
        String[] labels = {"pcName", "domainName", "userName", "expiryDate", "orgKey", "email", "version"};

        for (int i = 0; i < maxParts; i++) {
            String label = i < labels.length ? labels[i] : "part[" + i + "]";
            String oldVal = i < oldParts.length ? oldParts[i] : "<missing>";
            String newVal = i < newParts.length ? newParts[i] : "<missing>";
            boolean same = oldVal.equals(newVal);
            System.out.printf("  %-12s : %s%n", label, same ? "SAME" : "CHANGED");
            if (!same) {
                System.out.printf("    OLD: %s%n", truncate(oldVal, 80));
                System.out.printf("    NEW: %s%n", truncate(newVal, 80));
            }
        }

        System.out.println();
        System.out.println("Old parts count: " + oldParts.length);
        System.out.println("New parts count: " + newParts.length);
    }

    private static String[] processLicense(String path) throws Exception {
        Path licFile = Paths.get(path);

        System.out.println("License path : " + licFile.toAbsolutePath());
        System.out.println("Exists       : " + Files.exists(licFile));
        if (!Files.exists(licFile)) {
            System.err.println("WARN -- file not found: " + path);
            return null;
        }

        byte[] raw = Files.readAllBytes(licFile);
        System.out.println("Size (bytes) : " + raw.length);

        // SHA-256 fingerprint
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha.digest(raw);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b));
        System.out.println("Fingerprint  : sha256:" + hex);

        // AES-128-ECB decrypt
        byte[] b64Decoded = Base64.getDecoder().decode(new String(raw).trim());
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY.getBytes(), "AES"));
        String plaintext = new String(cipher.doFinal(b64Decoded));

        System.out.println("Plaintext    : " + plaintext);

        String[] parts = plaintext.split("\\|");
        System.out.println("Parts (" + parts.length + ")");
        for (int i = 0; i < parts.length; i++) {
            System.out.println("  [" + i + "] \"" + parts[i] + "\"");
        }

        // Simulate LicenseManager.validateLicense
        System.out.println();
        System.out.println("Simulated ARPropertyManager values:");
        if (parts.length >= 5) {
            System.out.println("  license.orgKey       = " + parts[4]);
        } else {
            System.out.println("  license.orgKey       = <unset>");
        }
        if (parts.length >= 6) {
            System.out.println("  license.email        = " + parts[5]);
        } else {
            System.out.println("  license.email        = <unset>");
        }
        if (parts.length >= 7) {
            System.out.println("  license.version      = " + parts[6]);
        } else {
            System.out.println("  license.version      = <unset>");
        }

        // Verdict
        String emailGuess = parts.length >= 6 ? parts[5] : "";
        boolean looksLikeEmail = emailGuess.contains("@");
        boolean looksLikeVersion = emailGuess.matches("^\\d+(\\.\\d+)*$");
        if (parts.length < 6) {
            System.out.println("VERDICT: FAIL .lic has fewer than 6 parts -- email not set.");
        } else if (looksLikeVersion) {
            System.out.println("VERDICT: FAIL parts[5] = \"" + emailGuess + "\" looks like a version number.");
        } else if (looksLikeEmail) {
            System.out.println("VERDICT: OK parts[5] = \"" + emailGuess + "\" -- valid email.");
        } else {
            System.out.println("VERDICT: WARN parts[5] = \"" + emailGuess + "\" -- not an email. Check the .lic.");
        }

        return parts;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * Full pipeline test: ACTIVATE → UNZIP → DECRYPT → VALIDATE
     *
     * Handles four scenarios:
     *   A. --activate     → Online activation via Supabase (no password needed)
     *   B. ACTIVATED:...  → Machine-bound key, unwrap locally
     *   C. PROTECTED:...  → Legacy password key, prompts password
     *   D. Plain hex key  → Backward-compatible, no auth needed
     *
     * Run:
     *   java EncryptedPluginLoaderTest                                        (auto-detect)
     *   java EncryptedPluginLoaderTest "D:\path\to\plugins"                   (custom path)
     *   java EncryptedPluginLoaderTest "D:\path\to\plugins" --activate        (force online activation)
     *   java EncryptedPluginLoaderTest "D:\path\to\plugins" --license "D:\path\to\ARWeb.lic"
     */
    public static class EncryptedPluginLoaderTest {

        private static final String ALGORITHM = "AES/GCM/NoPadding";
        private static final int IV_LENGTH = 12;
        private static final int TAG_LENGTH_BYTES = 16;
        private static final int TAG_LENGTH_BITS = 128;

        // Key wrapping constants (must match PluginKeyManager)
        private static final String ACTIVATED_PREFIX = "ACTIVATED:";
        private static final String PROTECTED_PREFIX = "PROTECTED:";
        private static final int SALT_LENGTH = 16;
        private static final int PBKDF2_ITERATIONS = 100_000;
        private static final int KEY_LENGTH_BITS = 256;

        // MultiPlugins API (local PostgreSQL backend)
        private static final String API_URL = "https://multiplugins.ch/api";

        private static final String DEFAULT_LICENSE = "D:/Projects/ARWeb-Martini/ARWeb-Scanner/ARWeb.lic";
        //    private static final String DEFAULT_LICENSE = "C:/ARWeb-ANDROMEDA/ARWeb-Scanner/ARWeb.lic";
        //    private static final String DEFAULT_LICENSE = "C:/Users/osval/Downloads/files/ARWeb-v4-2-1.lic";
        /** Plugin ID → expected .enc filename inside the zip */
        private static final String[][] PLUGINS = {
            {"pageScanner", "scanner.min.enc"},
            {"hoverPick", "hoverPick.min.enc"},
            {"actionExecutor", "actionExecutor.min.enc"},
            {"searchListAsync", "searchListAsync.min.enc"},
            {"searchList", "searchList.min.enc"},
            {"pluginTest", "pluginTest.min.enc"},
        };

        public static void main(String[] args) {
            //        String pluginsDir =
            //                args.length > 0 && !args[0].startsWith("--") ? args[0] :
            // "D:/Projects/ARWeb-Martini/ARWeb/plugins";
            //        String pluginsDir = args.length > 0 && !args[0].startsWith("--") ? args[0] :
            // "C:/ARWeb-ANDROMEDA/ARWeb/plugins";

            String pluginsDir =
                    args.length > 0 && !args[0].startsWith("--") ? args[0] : "C:\\ARWeb-Martini\\ARWeb\\plugins-Avaloq";

            boolean forceActivate = Arrays.asList(args).contains("--activate");

            System.out.println("=== Full Plugin Pipeline Test ===\n");
            System.out.println("Plugins dir: " + pluginsDir + "\n");

            try {
                // ── Resolve license path ──
                String licensePath = DEFAULT_LICENSE;
                for (int i = 0; i < args.length - 1; i++) {
                    if ("--license".equals(args[i])) {
                        licensePath = args[i + 1];
                        break;
                    }
                }

                String licenseFingerprint = getLicenseFingerprint(licensePath);
                if (licenseFingerprint == null) {
                    System.out.println("FAIL: Could not read license at " + licensePath);
                    System.exit(1);
                }
                System.out.println("License:     " + licensePath);
                System.out.println("Fingerprint: " + licenseFingerprint.substring(0, 16) + "...");

                String machineId = getMachineId();
                System.out.println("Machine ID:  " + machineId.substring(0, 16) + "...\n");

                // ── Load key: try ARWeb.lic org key first, then plugins.key, then online activation ──
                byte[] key = null;

                // Priority 1: Extract org key embedded in ARWeb.lic
                String orgKeyHex = extractOrgKeyFromLicense(licensePath);
                if (orgKeyHex != null && !orgKeyHex.isEmpty()) {
                    key = hexToBytes(orgKeyHex);
                    System.out.println("Key from ARWeb.lic (org key): " + orgKeyHex.substring(0, 8) + "... ("
                            + (key.length * 8) + "-bit AES)\n");
                }

                // Priority 2: plugins.key file (legacy / fallback)
                Path keyFile = Paths.get(pluginsDir, "plugins.key");
                if (key == null && Files.exists(keyFile) && !forceActivate) {
                    String content =
                            Files.readString(keyFile, StandardCharsets.UTF_8).trim();

                    if (content.startsWith(ACTIVATED_PREFIX)) {
                        System.out.println("plugins.key is MACHINE-BOUND (ACTIVATED)\n");
                        String encoded = content.substring(ACTIVATED_PREFIX.length());
                        key = unwrapKey(encoded, machineId, licenseFingerprint);
                        System.out.println("Key UNLOCKED (" + (key.length * 8) + "-bit AES)\n");
                    } else if (content.startsWith(PROTECTED_PREFIX)) {
                        System.out.println("plugins.key is PASSWORD-PROTECTED (legacy)\n");
                        String encoded = content.substring(PROTECTED_PREFIX.length());
                        String password = askPassword("Enter plugin password: ");
                        key = unwrapKey(encoded, password, licenseFingerprint);
                        System.out.println("Key UNLOCKED (" + (key.length * 8) + "-bit AES)\n");
                    } else {
                        key = hexToBytes(content);
                        System.out.println("Key loaded (plain hex): " + content.substring(0, 8) + "... (" + (key.length * 8)
                                + "-bit AES)\n");
                    }
                }

                // Priority 3: Online activation
                if (key == null || forceActivate) {
                    System.out.println("=== ONLINE ACTIVATION ===\n");
                    key = activateOnline(keyFile, licenseFingerprint, machineId);
                    if (key == null) {
                        System.out.println("\nActivation FAILED. Exiting.");
                        System.exit(1);
                    }
                    System.out.println("\nActivation SUCCEEDED.\n");
                }

                // ── Test decrypt all plugins ──
                int passed = 0;
                int failed = 0;

                for (String[] plugin : PLUGINS) {
                    String pluginId = plugin[0];
                    String encFileName = plugin[1];

                    System.out.println("── " + pluginId + " ──");

                    Path zipPath = Paths.get(pluginsDir, pluginId + ".zip");
                    if (!Files.exists(zipPath)) {
                        System.out.println("  SKIP: " + pluginId + ".zip not found\n");
                        continue;
                    }
                    System.out.println("  ZIP:    " + zipPath.getFileName() + " (" + Files.size(zipPath) + " bytes)");

                    Path tempDir = Files.createTempDirectory("plugin-test-" + pluginId);
                    Path buildDir = tempDir.resolve("build");
                    Files.createDirectories(buildDir);

                    int filesExtracted = 0;
                    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            if (entry.isDirectory()) continue;
                            Path target = buildDir.resolve(entry.getName()).normalize();
                            if (!target.startsWith(buildDir)) {
                                System.out.println("  WARN:   zip-slip blocked: " + entry.getName());
                                continue;
                            }
                            try (OutputStream out = Files.newOutputStream(target)) {
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = zis.read(buf)) != -1) out.write(buf, 0, len);
                            }
                            filesExtracted++;
                            System.out.println("  UNZIP:  " + entry.getName() + " → build/" + target.getFileName());
                            zis.closeEntry();
                        }
                    }

                    if (filesExtracted == 0) {
                        System.out.println("  FAIL:   zip is empty\n");
                        failed++;
                        deleteDir(tempDir);
                        continue;
                    }

                    Path encPath = buildDir.resolve(encFileName);
                    if (!Files.exists(encPath)) {
                        System.out.println("  FAIL:   " + encFileName + " not found after unzip");
                        try (var list = Files.list(buildDir)) {
                            list.forEach(f -> System.out.println("            " + f.getFileName()));
                        }
                        failed++;
                        deleteDir(tempDir);
                        System.out.println();
                        continue;
                    }

                    byte[] fileData = Files.readAllBytes(encPath);
                    System.out.println("  ENC:    " + encFileName + " (" + fileData.length + " bytes)");

                    try {
                        String js = decrypt(fileData, key);
                        boolean validJs =
                                js.contains("function") || js.contains("var ") || js.contains("const ") || js.contains("(");

                        if (validJs) {
                            System.out.println("  DECRYPT: OK (" + js.length() + " chars JavaScript)");
                            System.out.println("  PREVIEW: " + js.substring(0, Math.min(70, js.length())) + "...");
                            System.out.println("  RESULT: PASS");
                            passed++;
                        } else {
                            System.out.println("  DECRYPT: output doesn't look like JavaScript");
                            System.out.println("  RESULT: FAIL");
                            failed++;
                        }
                    } catch (Exception e) {
                        System.out.println("  DECRYPT: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        System.out.println("  RESULT: FAIL");
                        failed++;
                    }

                    deleteDir(tempDir);
                    System.out.println();
                }

                System.out.println("=======================================");
                System.out.println("  " + passed + " PASSED, " + failed + " FAILED");
                System.out.println("=======================================");

                if (failed > 0) {
                    System.out.println("\nPossible causes:");
                    System.out.println("  - Wrong key (plugins.key doesn't match .enc files)");
                    System.out.println("  - Different machine (key is bound to the activating machine)");
                    System.out.println("  - ZIP contains wrong files (re-run: node encrypt-plugins.js)");
                    System.exit(1);
                } else {
                    System.out.println("\nPipeline OK. Ready for production.");
                }

            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }

        // ── Online activation via Supabase ──────────────────────────────────────

        private static byte[] activateOnline(Path keyFile, String licenseFingerprint, String machineId) throws Exception {
            String hostname = getHostname();
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String javaVersion = System.getProperty("java.version");

            System.out.println("Connecting to activation server...");
            System.out.println("  Hostname:    " + hostname);
            System.out.println("  OS:          " + osInfo);
            System.out.println("  Java:        " + javaVersion);

            String json = String.format(
                    "{\"license_hash\":\"%s\",\"machine_id\":\"%s\",\"hostname\":\"%s\","
                            + "\"os_info\":\"%s\",\"java_version\":\"%s\"}",
                    escapeJson(licenseFingerprint),
                    escapeJson(machineId),
                    escapeJson(hostname),
                    escapeJson(osInfo),
                    escapeJson(javaVersion));

            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/client/activate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("  HTTP Status: " + response.statusCode());

            if (response.statusCode() != 200) {
                System.out.println("  ERROR: " + response.body());
                return null;
            }

            String body = response.body();
            boolean ok = body.contains("\"ok\":true") || body.contains("\"ok\": true");

            if (!ok) {
                String error = extractJsonString(body, "error");
                System.out.println("  REJECTED: " + error);
                System.out.println("  Full response: " + body);
                return null;
            }

            String pluginKeyHex = extractJsonString(body, "plugin_key");
            if (pluginKeyHex == null || pluginKeyHex.isEmpty() || pluginKeyHex.equals("YOUR_HEX_KEY_HERE")) {
                System.out.println("  ERROR: Plugin key not configured on server");
                System.out.println("  Update the 'plugin_key_hex' in Supabase config table");
                return null;
            }

            String clientName = extractJsonString(body, "client_name");
            System.out.println("  Client:      " + clientName);
            System.out.println("  Key received: " + pluginKeyHex.substring(0, 8) + "...");

            byte[] key = hexToBytes(pluginKeyHex);

            // Wrap with machine_id + license and save
            String wrapped = wrapKeyActivated(key, machineId, licenseFingerprint);
            Files.writeString(keyFile, wrapped, StandardCharsets.UTF_8);

            return key;
        }

        // ── Plugin decryption ───────────────────────────────────────────────────

        private static String decrypt(byte[] fileData, byte[] key) throws Exception {
            if (fileData.length < IV_LENGTH + TAG_LENGTH_BYTES) {
                throw new IllegalArgumentException("File too short - invalid .enc format");
            }

            byte[] iv = Arrays.copyOfRange(fileData, 0, IV_LENGTH);
            byte[] tag = Arrays.copyOfRange(fileData, IV_LENGTH, IV_LENGTH + TAG_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(fileData, IV_LENGTH + TAG_LENGTH_BYTES, fileData.length);

            byte[] cipherWithTag = new byte[encrypted.length + tag.length];
            System.arraycopy(encrypted, 0, cipherWithTag, 0, encrypted.length);
            System.arraycopy(tag, 0, cipherWithTag, encrypted.length, tag.length);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(cipherWithTag);
            return new String(decrypted, StandardCharsets.UTF_8);
        }

        // ── Password prompt (for legacy PROTECTED keys) ─────────────────────────

        private static String askPassword(String prompt) {
            Console console = System.console();
            if (console != null) {
                char[] pw = console.readPassword(prompt);
                return new String(pw);
            }
            System.out.print(prompt);
            Scanner scanner = new Scanner(System.in);
            return scanner.nextLine().trim();
        }

        // ── Machine ID ──────────────────────────────────────────────────────────

        private static String getMachineId() {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(getHostname()).append("|");

                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                while (nets.hasMoreElements()) {
                    NetworkInterface ni = nets.nextElement();
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        for (byte b : mac) sb.append(String.format("%02x", b));
                        sb.append(",");
                    }
                }

                sb.append(System.getProperty("os.name")).append("|");
                sb.append(System.getProperty("user.name"));

                return bytesToHex(
                        MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                // Fallback
                String fallback = getHostname() + "|" + System.getProperty("user.name");
                try {
                    return bytesToHex(
                            MessageDigest.getInstance("SHA-256").digest(fallback.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        private static String getHostname() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                String cn = System.getenv("COMPUTERNAME");
                return cn != null ? cn : "unknown";
            }
        }

        // ── Extract org key from ARWeb.lic ────────────────────────────────────────

        private static final String LIC_KEY = "0123456789abcdef";
        private static final String LIC_ALGORITHM = "AES/ECB/PKCS5Padding";

        /**
         * Decrypt ARWeb.lic and extract the org key from part[4].
         * Format: pcName|domainName|userName|expiryDate|orgKey
         * Returns null if no org key embedded (legacy 4-part format).
         */
        private static String extractOrgKeyFromLicense(String licensePath) {
            try {
                String content = Files.readString(Paths.get(licensePath), StandardCharsets.UTF_8)
                        .trim();
                SecretKeySpec keySpec = new SecretKeySpec(LIC_KEY.getBytes(StandardCharsets.UTF_8), "AES");
                Cipher cipher = Cipher.getInstance(LIC_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, keySpec);
                byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(content));
                String plain = new String(decrypted, StandardCharsets.UTF_8);
                String[] parts = plain.split("\\|");
                if (parts.length >= 5) {
                    System.out.println("  License format: " + parts.length + " parts");
                    System.out.println("  PC: " + parts[0] + "  Domain: " + parts[1] + "  User: " + parts[2]);
                    System.out.println("  Expiry: " + parts[3]);
                    System.out.println("  Org Key: " + parts[4].substring(0, Math.min(16, parts[4].length())) + "...");
                    if (parts.length >= 6) System.out.println("  Version: " + parts[5]);
                    return parts[4]; // org key hex
                }
                return null; // legacy format, no org key
            } catch (Exception e) {
                System.err.println("[LIC] Failed to extract org key: " + e.getMessage());
                return null;
            }
        }

        // ── License fingerprint ─────────────────────────────────────────────────

        private static String getLicenseFingerprint(String licensePath) {
            try {
                Path licFile = Paths.get(licensePath);
                if (!Files.exists(licFile)) return null;
                byte[] content = Files.readAllBytes(licFile);
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
                return bytesToHex(hash);
            } catch (Exception e) {
                return null;
            }
        }

        // ── Key wrapping (ACTIVATED - machine-bound) ────────────────────────────

        private static String wrapKeyActivated(byte[] pluginKey, String machineId, String fingerprint) throws Exception {
            byte[] salt = new byte[SALT_LENGTH];
            new SecureRandom().nextBytes(salt);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            byte[] wrapperKey = deriveKey(machineId, fingerprint, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE, new SecretKeySpec(wrapperKey, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(pluginKey);

            byte[] combined = new byte[salt.length + iv.length + encrypted.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(encrypted, 0, combined, salt.length + iv.length, encrypted.length);

            return ACTIVATED_PREFIX + Base64.getEncoder().encodeToString(combined);
        }

        // ── Key unwrapping (both ACTIVATED and legacy PROTECTED) ────────────────

        private static byte[] unwrapKey(String encoded, String secret, String fingerprint) throws Exception {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] salt = Arrays.copyOfRange(combined, 0, SALT_LENGTH);
            byte[] iv = Arrays.copyOfRange(combined, SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, SALT_LENGTH + IV_LENGTH, combined.length);

            byte[] wrapperKey = deriveKey(secret, fingerprint, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE, new SecretKeySpec(wrapperKey, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(encrypted);
        }

        private static byte[] deriveKey(String secret, String fingerprint, byte[] salt) throws Exception {
            String combined = secret + "|" + fingerprint;
            KeySpec spec = new PBEKeySpec(combined.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        }

        // ── JSON helpers ────────────────────────────────────────────────────────

        private static String extractJsonString(String json, String key) {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start < 0) {
                search = "\"" + key + "\":";
                start = json.indexOf(search);
                if (start < 0) return null;
                start += search.length();
                while (start < json.length() && json.charAt(start) == ' ') start++;
                if (start >= json.length() || json.charAt(start) == 'n') return null;
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

        // ── Hex utilities ───────────────────────────────────────────────────────

        private static byte[] hexToBytes(String hex) {
            int len = hex.length();
            byte[] bytes = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
            }
            return bytes;
        }

        private static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        }

        private static void deleteDir(Path dir) {
            try {
                Files.walk(dir).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }
}
