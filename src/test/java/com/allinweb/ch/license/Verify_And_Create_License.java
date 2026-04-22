package com.allinweb.ch.license;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verify a .request file and produce a matching ARWeb.lic (v4.2 format).
 *
 * Request plaintext (8 parts):
 *   organization | owner | pcName | domainName | userName | requestDate | email_client:<email> | version
 *
 * License plaintext (7 parts, parsed by LicenseManager.validateLicense):
 *   pcName | domainName | userName | expiryDate | orgKey | email | version
 *
 * Both use AES-128-ECB / PKCS5 with key "0123456789abcdef", Base64-wrapped.
 *
 * Run standalone:
 *   javac -d out src/test/java/com/allinweb/ch/license/Verify_And_Create_License.java
 *   java  -cp out com.allinweb.ch.license.Verify_And_Create_License
 *
 * Optional args:
 *   args[0] = path to .request file
 *   args[1] = output .lic path
 *   args[2] = validity days (default 365)
 *   args[3] = orgKey (64-char hex). If omitted, a random one is generated.
 *            NOTE: orgKey IS the AES-256 key that EncryptedPluginLoaderTest uses to
 *            decrypt plugin .zip contents. Must match the key used by encrypt-bank.js.
 */
public class Verify_And_Create_License {

    private static final String KEY = "0123456789abcdef";

    //    private static final String DEFAULT_REQUEST =
    //            "C:\\Users\\osval\\OneDrive\\\u00c1rea de Trabalho\\Avaloq-O Martini.request";

    private static final String DEFAULT_REQUEST = "D:\\ARWeb-Licenses-Backup\\Avaloq-Rahul Amrutkar.request";

    private static final String DEFAULT_OUTPUT = "D:\\Projects\\ARWebAvaloq\\ARWeb-Scanner\\ARWeb.lic";
    private static final int DEFAULT_DAYS = 365;

    /**
     * Optional pinned orgKey. Set this to the SAME 64-hex key passed to
     *   node encrypt-bank.js --key <hex>
     * so EncryptedPluginLoaderTest can decrypt the plugin zips.
     * Leave empty to generate a random one (will NOT decrypt pre-encrypted plugins).
     */
    private static final String PINNED_ORG_KEY = ""; // IT MUST BE THE SAME ORG

    public static void main(String[] args) throws Exception {
        String requestPath = args.length > 0 ? args[0] : DEFAULT_REQUEST;
        String outputPath = args.length > 1 ? args[1] : DEFAULT_OUTPUT;
        int days = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_DAYS;
        String cliOrgKey = args.length > 3 ? args[3] : null;

        System.out.println("=== Verify_And_Create_License ===\n");

        // 1. Read + verify request
        System.out.println("--- Verifying request ---");
        String[] parts = verifyRequest(requestPath);
        if (parts == null) {
            System.err.println("ABORT: request verification failed.");
            System.exit(1);
        }

        // 2. Extract fields
        String organization = parts[0];
        String owner = parts[1];
        String pcName = parts[2];
        String domainName = parts[3];
        String userName = parts[4];
        String requestDate = parts[5];
        String email = stripEmailTag(parts[6]);
        String version = parts.length > 7 ? parts[7] : "4.2";

        System.out.println();
        System.out.println("Extracted fields:");
        System.out.printf("  organization : %s%n", organization);
        System.out.printf("  owner        : %s%n", owner);
        System.out.printf("  pcName       : %s%n", pcName);
        System.out.printf("  domainName   : %s%n", domainName);
        System.out.printf("  userName     : %s%n", userName);
        System.out.printf("  requestDate  : %s%n", requestDate);
        System.out.printf("  email        : %s%n", email);
        System.out.printf("  version      : %s%n", version);

        // 3. Build license plaintext
        String expiryDate = LocalDate.now().plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String orgKey = resolveOrgKey(cliOrgKey);
        System.out.printf(
                "  orgKey src   : %s%n",
                cliOrgKey != null ? "CLI arg" : (!PINNED_ORG_KEY.isEmpty() ? "PINNED_ORG_KEY" : "random"));

        String licPlain = String.join("|", pcName, domainName, userName, expiryDate, orgKey, email, version);

        System.out.println();
        System.out.println("--- Creating license ---");
        System.out.println("License plaintext:");
        System.out.println("  " + licPlain);

        // 4. Encrypt + write
        String encrypted = encrypt(licPlain, KEY);
        Path out = Paths.get(outputPath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, encrypted);

        System.out.println();
        System.out.printf("License written : %s%n", out.toAbsolutePath());
        System.out.printf("Size (bytes)    : %d%n", Files.size(out));
        System.out.println("SHA-256         : sha256:" + sha256Hex(Files.readAllBytes(out)));

        // 5. Round-trip verification
        System.out.println();
        System.out.println("--- Round-trip verification ---");
        String roundTrip = decrypt(encrypted, KEY);
        boolean ok = licPlain.equals(roundTrip);
        System.out.println("Decrypted       : " + roundTrip);
        System.out.println(ok ? "VERDICT: OK (round-trip match)" : "VERDICT: FAIL (round-trip mismatch)");
        if (!ok) System.exit(2);
    }

    private static String[] verifyRequest(String path) throws Exception {
        Path reqFile = Paths.get(path);
        System.out.println("Request path : " + reqFile.toAbsolutePath());
        System.out.println("Exists       : " + Files.exists(reqFile));
        if (!Files.exists(reqFile)) {
            System.err.println("FAIL -- request file not found.");
            return null;
        }

        byte[] raw = Files.readAllBytes(reqFile);
        System.out.println("Size (bytes) : " + raw.length);
        System.out.println("SHA-256      : sha256:" + sha256Hex(raw));

        String plaintext = decrypt(new String(raw).trim(), KEY);
        if (plaintext == null) {
            System.err.println("FAIL -- decryption returned null.");
            return null;
        }
        System.out.println("Plaintext    : " + plaintext);

        String[] parts = plaintext.split("\\|");
        System.out.println("Parts count  : " + parts.length);
        for (int i = 0; i < parts.length; i++) {
            System.out.println("  [" + i + "] \"" + parts[i] + "\"");
        }

        if (parts.length < 7) {
            System.err.println("FAIL -- expected at least 7 parts, got " + parts.length);
            return null;
        }
        System.out.println("VERDICT: request looks valid.");
        return parts;
    }

    private static String stripEmailTag(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if (trimmed.startsWith("email_client:")) {
            String v = trimmed.substring("email_client:".length()).trim();
            return "empty".equalsIgnoreCase(v) ? "" : v;
        }
        return trimmed;
    }

    private static String resolveOrgKey(String cliOrgKey) {
        if (cliOrgKey != null && !cliOrgKey.isBlank()) return validateHex64(cliOrgKey);
        if (!PINNED_ORG_KEY.isBlank()) return validateHex64(PINNED_ORG_KEY);
        return generateOrgKey();
    }

    private static String validateHex64(String s) {
        String v = s.trim().toLowerCase();
        if (!v.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("orgKey must be exactly 64 hex chars, got: " + s);
        }
        return v;
    }

    private static String generateOrgKey() {
        byte[] buf = new byte[32]; // 64 hex chars
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String encrypt(String data, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(), "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
    }

    private static String decrypt(String encryptedBase64, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes(), "AES"));
        byte[] decoded = Base64.getDecoder().decode(encryptedBase64.trim());
        return new String(cipher.doFinal(decoded));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
