package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the plugin encryption key, bound to the license file and a user password.
 *
 * <p>Security model:
 * <ul>
 *   <li>The actual AES-256 plugin key is stored in {@code plugins.key}, encrypted</li>
 *   <li>The wrapper key is derived from: {@code PBKDF2(password + license_fingerprint)}</li>
 *   <li>If the license changes → fingerprint changes → can't unwrap → BLOCKED</li>
 *   <li>If plugins.key is tampered → decryption fails → BLOCKED</li>
 *   <li>If wrong password → can't derive wrapper key → BLOCKED</li>
 * </ul>
 *
 * <p>File format of plugins.key (when password-protected):
 * <pre>
 *   PROTECTED:base64([salt(16)] [iv(12)] [tag(16)] [encrypted_plugin_key])
 * </pre>
 * If the file does NOT start with "PROTECTED:", it's treated as a plain hex key
 * (backward compatibility).
 *
 * <p>First launch flow:
 * <ol>
 *   <li>No plugins.key → prompt "Create plugin password"</li>
 *   <li>Generate random AES-256 key for plugins</li>
 *   <li>Wrap it with PBKDF2(password + license) → save as PROTECTED:...</li>
 * </ol>
 *
 * <p>Normal launch flow:
 * <ol>
 *   <li>plugins.key starts with PROTECTED: → prompt "Enter plugin password"</li>
 *   <li>Derive wrapper key from password + license → unwrap → return plugin key</li>
 * </ol>
 */
@Slf4j
public class PluginKeyManager {

    private static volatile PluginKeyManager instance;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final String PROTECTED_PREFIX = "PROTECTED:";

    /** The unwrapped plugin AES key — null until successfully loaded */
    private byte[] pluginKey;

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
     * Get the plugin encryption key. Prompts for password if needed.
     * Returns null if the user cancels or authentication fails.
     */
    public byte[] getPluginKey() {
        if (pluginKey != null) return pluginKey;

        synchronized (this) {
            if (pluginKey != null) return pluginKey;

            try {
                String pluginsDir = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_PLUGINS);
                if (pluginsDir == null) {
                    log.error("PluginKeyManager — path_plugins not configured");
                    return null;
                }

                Path keyFile = Paths.get(pluginsDir, "plugins.key");
                String licenseFingerprint = getLicenseFingerprint();

                if (licenseFingerprint == null) {
                    log.error("PluginKeyManager — could not read license fingerprint");
                    return null;
                }

                if (!Files.exists(keyFile)) {
                    // First launch — create new key with password
                    pluginKey = firstTimeSetup(keyFile, licenseFingerprint);
                } else {
                    String content =
                            Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                    if (content.startsWith(PROTECTED_PREFIX)) {
                        // Password-protected key
                        pluginKey = unlockWithPassword(content, licenseFingerprint);
                    } else {
                        // Plain hex key (backward compatibility)
                        pluginKey = hexToBytes(content);
                        log.info("PluginKeyManager — loaded plain key (not password-protected)");
                    }
                }
            } catch (Exception e) {
                log.error("PluginKeyManager — failed: {}", e.getMessage(), e);
                pluginKey = null;
            }
        }

        return pluginKey;
    }

    /** Clear cached key (for re-authentication). */
    public void clearKey() {
        if (pluginKey != null) Arrays.fill(pluginKey, (byte) 0);
        pluginKey = null;
    }

    // ── First-time setup ────────────────────────────────────────────────────

    private byte[] firstTimeSetup(Path keyFile, String licenseFingerprint) throws Exception {
        log.info("PluginKeyManager — first-time setup, prompting for new password");

        String password = promptNewPassword();
        if (password == null) {
            log.warn("PluginKeyManager — user cancelled password creation");
            return null;
        }

        // Generate random plugin key
        byte[] newPluginKey = new byte[32]; // AES-256
        new SecureRandom().nextBytes(newPluginKey);

        // Wrap and save
        String wrapped = wrapKey(newPluginKey, password, licenseFingerprint);
        Files.writeString(keyFile, wrapped, StandardCharsets.UTF_8);

        log.info("PluginKeyManager — new key created and saved (password-protected)");
        return newPluginKey;
    }

    // ── Unlock with password ────────────────────────────────────────────────

    private byte[] unlockWithPassword(String fileContent, String licenseFingerprint) throws Exception {
        String encoded = fileContent.substring(PROTECTED_PREFIX.length());

        for (int attempt = 1; attempt <= 3; attempt++) {
            String password = promptPassword(attempt);
            if (password == null) {
                log.warn("PluginKeyManager — user cancelled password entry");
                return null;
            }

            try {
                byte[] unwrapped = unwrapKey(encoded, password, licenseFingerprint);
                log.info("PluginKeyManager — key unlocked successfully");
                return unwrapped;
            } catch (Exception e) {
                log.warn("PluginKeyManager — attempt {}/3 failed: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    showError("Wrong password (attempt " + attempt + "/3). Try again.");
                }
            }
        }

        log.error("PluginKeyManager — 3 failed attempts, blocking");
        showError("Plugin access blocked. Too many wrong password attempts.\nRestart the application to try again.");
        return null;
    }

    // ── Key wrapping (PBKDF2 + AES-GCM) ────────────────────────────────────

    private String wrapKey(byte[] pluginKey, String password, String licenseFingerprint) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);

        byte[] wrapperKey = deriveKey(password, licenseFingerprint, salt);
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

        return PROTECTED_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    private byte[] unwrapKey(String encoded, String password, String licenseFingerprint) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encoded);

        byte[] salt = Arrays.copyOfRange(combined, 0, SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(combined, SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(combined, SALT_LENGTH + IV_LENGTH, combined.length);

        byte[] wrapperKey = deriveKey(password, licenseFingerprint, salt);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(wrapperKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));

        return cipher.doFinal(encrypted); // the original plugin AES key
    }

    private byte[] deriveKey(String password, String licenseFingerprint, byte[] salt) throws Exception {
        // Combine password + license fingerprint as the secret
        String combined = password + "|" + licenseFingerprint;
        KeySpec spec = new PBEKeySpec(combined.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    // ── License fingerprint ─────────────────────────────────────────────────

    /**
     * Read the license file and compute a SHA-256 fingerprint.
     * The fingerprint binds the plugin key to this specific license.
     */
    private String getLicenseFingerprint() {
        try {
            String licensePath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_LICENSE);
            if (licensePath == null) licensePath = System.getProperty("user.dir");

            Path licFile = Paths.get(licensePath, "ARWeb.lic");
            if (!Files.exists(licFile)) {
                log.error("PluginKeyManager — license file not found: {}", licFile);
                return null;
            }

            byte[] licContent = Files.readAllBytes(licFile);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(licContent);
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("PluginKeyManager — failed to read license: {}", e.getMessage());
            return null;
        }
    }

    // ── UI dialogs (JavaFX) ─────────────────────────────────────────────────

    private String promptNewPassword() {
        return runOnFxThread(() -> {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Plugin Security Setup");
            dialog.setHeaderText("Create a password to protect your plugins.\n"
                    + "This password is bound to your license — keep it safe.");

            ButtonType createButton = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createButton, ButtonType.CANCEL);

            PasswordField pw1 = new PasswordField();
            pw1.setPromptText("Password");
            PasswordField pw2 = new PasswordField();
            pw2.setPromptText("Confirm password");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 80, 10, 10));
            grid.add(new Label("Password:"), 0, 0);
            grid.add(pw1, 1, 0);
            grid.add(new Label("Confirm:"), 0, 1);
            grid.add(pw2, 1, 1);
            dialog.getDialogPane().setContent(grid);

            Platform.runLater(pw1::requestFocus);

            dialog.setResultConverter(btn -> {
                if (btn == createButton) {
                    if (pw1.getText().length() < 4) return null;
                    if (!pw1.getText().equals(pw2.getText())) return null;
                    return pw1.getText();
                }
                return null;
            });

            Optional<String> result = dialog.showAndWait();
            return result.orElse(null);
        });
    }

    private String promptPassword(int attempt) {
        return runOnFxThread(() -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Plugin Authentication");
            dialog.setHeaderText("Enter plugin password" + (attempt > 1 ? " (attempt " + attempt + "/3)" : ""));
            dialog.setContentText("Password:");

            // Replace TextField with PasswordField
            PasswordField pwField = new PasswordField();
            pwField.setPromptText("Password");
            dialog.getEditor().setVisible(false);
            dialog.getEditor().setManaged(false);
            dialog.getDialogPane().setContent(pwField);
            Platform.runLater(pwField::requestFocus);

            dialog.setResultConverter(btn -> {
                if (btn == ButtonType.OK) return pwField.getText();
                return null;
            });

            Optional<String> result = dialog.showAndWait();
            return result.orElse(null);
        });
    }

    private void showError(String message) {
        runOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Plugin Security");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
            return null;
        });
    }

    /** Run a UI action on the JavaFX thread and wait for the result. */
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

    // ── Hex utilities ───────────────────────────────────────────────────────

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
