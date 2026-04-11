package com.allinweb.ch.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes a sha256 fingerprint of ARWeb.lic. Used as the bearer credential
 * when the Java client uploads DOM captures to the MultiPlugins portal.
 *
 * This is NOT the orgKey — it's a one-way hash of the license file bytes.
 * Stolen fingerprint === stolen license; same threat model as a stolen .lic.
 */
@Slf4j
public final class LicenseFingerprint {

    private LicenseFingerprint() {}

    /**
     * @return "sha256:<hex>" or null if ARWeb.lic is missing/unreadable
     */
    public static String compute() {
        try {
            String licensePath = ARPropertyManager.getInstance()
                    .getProperty(ARPropertyEnum.PATH_LICENSE);
            if (licensePath == null || licensePath.isBlank()) {
                licensePath = System.getProperty("user.dir");
            }
            Path licFile = Paths.get(licensePath, "ARWeb.lic");
            if (!Files.exists(licFile)) {
                log.warn("LicenseFingerprint — ARWeb.lic not found at {}", licFile);
                return null;
            }
            byte[] bytes = Files.readAllBytes(licFile);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return "sha256:" + hex.toString();
        } catch (Exception e) {
            log.error("LicenseFingerprint — compute failed: {}", e.getMessage());
            return null;
        }
    }
}
