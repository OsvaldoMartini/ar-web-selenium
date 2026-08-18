package com.allinweb.ch.license;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Standalone replica of what the main app does with ARWeb.lic:
 *
 *  1. Read raw file bytes.
 *  2. AES-128-ECB decrypt with the shared key (matches LicenseManager + crypto.cjs).
 *  3. Split the plaintext by '|' and show every part.
 *  4. Simulate LicenseManager.validateLicense() storing parts[4]=orgKey,
 *     parts[5]=email, parts[6]=version into a local map.
 *  5. Compute SHA-256 fingerprint the way LicenseFingerprint.compute() does.
 *
 * Run from IntelliJ or CLI:
 *   mvn -pl . -Dtest=com.allinweb.ch.license.LicenseReadTest test
 * Or standalone:
 *   javac -d out src/test/java/com/allinweb/ch/license/LicenseReadTest.java
 *   java -cp out com.allinweb.ch.license.LicenseReadTest "C:\Martini\ARWeb.lic"
 */
public class LicenseReadTest {

    private static final String KEY = "0123456789abcdef"; // 16 bytes, AES-128

    public static void main(String[] args) throws Exception {
        //        String path = args.length > 0 ? args[0] : "C:\\Martini\\ARWeb.lic";
        String path = args.length > 0 ? args[0] : "C:\\ARWeb-Martini\\ARWeb-Scanner\\ARWeb.lic";
        Path licFile = Paths.get(path);

        System.out.println("License path : " + licFile.toAbsolutePath());
        System.out.println("Exists       : " + Files.exists(licFile));
        if (!Files.exists(licFile)) {
            System.err.println("FATAL -- file not found");
            System.exit(1);
        }

        byte[] raw = Files.readAllBytes(licFile);
        System.out.println("Size (bytes) : " + raw.length);

        // SHA-256 fingerprint -- matches LicenseFingerprint.compute()
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

        System.out.println();
        System.out.println("Plaintext    : " + plaintext);

        String[] parts = plaintext.split("\\|");
        System.out.println();
        System.out.println("Parts (" + parts.length + ")");
        for (int i = 0; i < parts.length; i++) {
            System.out.println("  [" + i + "] \"" + parts[i] + "\"");
        }

        // Simulate LicenseManager.validateLicense
        System.out.println();
        System.out.println("Simulated ARPropertyManager values after validateLicense():");
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
        System.out.println();
        String emailGuess = parts.length >= 6 ? parts[5] : "";
        boolean looksLikeEmail = emailGuess.contains("@");
        boolean looksLikeVersion = emailGuess.matches("^\\d+(\\.\\d+)*$");
        if (parts.length < 6) {
            System.out.println("VERDICT: FAIL .lic has fewer than 6 parts -- email not set.");
        } else if (looksLikeVersion) {
            System.out.println("VERDICT: FAIL parts[5] = \"" + emailGuess + "\" looks like a version number.");
            System.out.println("         Server-side crypto.cjs fix is NOT live yet.");
            System.out.println("         Rebuild the API (restart.bat), then refresh the .lic from the portal.");
        } else if (looksLikeEmail) {
            System.out.println("VERDICT: OK parts[5] = \"" + emailGuess + "\" -- valid email.");
            System.out.println("         Java will send X-MP-Email: " + emailGuess);
        } else {
            System.out.println("VERDICT: WARN parts[5] = \"" + emailGuess + "\" -- not an email. Check the .lic.");
        }
    }
}
