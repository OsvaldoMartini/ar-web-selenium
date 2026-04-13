package com.allinweb.ch.license;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
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
}
