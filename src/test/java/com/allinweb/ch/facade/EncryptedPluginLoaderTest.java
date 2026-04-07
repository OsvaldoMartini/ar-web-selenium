package com.allinweb.ch.facade;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Full pipeline test: UNZIP → DECRYPT → VALIDATE
 *
 * Simulates exactly what happens in production:
 *   1. "Plugin Update" button unzips {plugin}.zip → {plugin}/build/{name}.min.enc
 *   2. EncryptedPluginLoader reads .enc → decrypts with plugins.key → returns JS string
 *   3. Selenium injects the JS into the browser
 *
 * Run:
 *   java com.allinweb.ch.facade.EncryptedPluginLoaderTest
 *   java com.allinweb.ch.facade.EncryptedPluginLoaderTest "D:\path\to\plugins"
 */
public class EncryptedPluginLoaderTest {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int TAG_LENGTH_BITS = 128;

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
        String pluginsDir = args.length > 0 ? args[0] : "D:/Projects/ARWeb-Martini/ARWeb/plugins";

        System.out.println("=== Full Plugin Pipeline Test: UNZIP → DECRYPT → VALIDATE ===\n");
        System.out.println("Plugins dir: " + pluginsDir + "\n");

        try {
            // --1. Load key --───────────────────────────────────────────────
            Path keyFile = Paths.get(pluginsDir, "plugins.key");
            if (!Files.exists(keyFile)) {
                System.out.println("FAIL: plugins.key not found at " + keyFile.toAbsolutePath());
                System.exit(1);
            }
            String keyHex = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
            byte[] key = hexToBytes(keyHex);
            System.out.println("Key loaded: " + keyHex.substring(0, 8) + "... (" + (key.length * 8) + "-bit AES)\n");

            int passed = 0;
            int failed = 0;

            for (String[] plugin : PLUGINS) {
                String pluginId = plugin[0];
                String encFileName = plugin[1];

                System.out.println("--" + pluginId + " --");

                // --2. Check ZIP exists --───────────────────────────────────
                Path zipPath = Paths.get(pluginsDir, pluginId + ".zip");
                if (!Files.exists(zipPath)) {
                    System.out.println("  SKIP: " + pluginId + ".zip not found\n");
                    continue;
                }
                System.out.println("  ZIP:    " + zipPath.getFileName() + " (" + Files.size(zipPath) + " bytes)");

                // --3. Unzip to temp dir (simulates Plugin Update) --────────
                Path tempDir = Files.createTempDirectory("plugin-test-" + pluginId);
                Path buildDir = tempDir.resolve("build");
                Files.createDirectories(buildDir);

                int filesExtracted = 0;
                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory()) continue;
                        // Extract into build/ subfolder (same as Java isPluginInstalledLocally)
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

                // --4. Find .enc file --─────────────────────────────────────
                Path encPath = buildDir.resolve(encFileName);
                if (!Files.exists(encPath)) {
                    System.out.println("  FAIL:   " + encFileName + " not found after unzip");
                    System.out.println("          Files in build/:");
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

                // --5. Decrypt --────────────────────────────────────────────
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

                // Cleanup temp
                deleteDir(tempDir);
                System.out.println();
            }

            // --Summary --───────────────────────────────────────────────────
            System.out.println("=======================================");
            System.out.println("  " + passed + " PASSED, " + failed + " FAILED");
            System.out.println("=======================================");

            if (failed > 0) {
                System.out.println("\nPossible causes:");
                System.out.println("  - Wrong key (plugins.key doesn't match .enc files)");
                System.out.println("  - ZIP contains wrong files (re-run: node encrypt-plugins.js)");
                System.out.println("  - ZIP has subfolder structure (should be flat, .enc at root)");
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

    private static String decrypt(byte[] fileData, byte[] key) throws Exception {
        if (fileData.length < IV_LENGTH + TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("File too short — invalid .enc format");
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

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    private static void deleteDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a)) // files before dirs
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }
}
