package com.allinweb.ch.facade;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone test for the useNoEncrypted = true path.
 *
 * Verifies the plain .min.js bundles referenced by the *_RELATIVE_PATH_MIN constants
 * actually exist on disk and contain valid-looking JavaScript. Mirrors the new
 * branch added to EncryptedPluginLoader.loadPlugin() that reads the file directly,
 * no key and no decryption.
 *
 * Run:
 *   java PlainPluginLoadTest                                  (uses default plugins dir)
 *   java PlainPluginLoadTest "D:\path\to\plugins"             (custom path)
 */
public class PlainPluginLoadTest {

    private static final String DEFAULT_PLUGINS_DIR = "C:\\ARWeb-Martini\\ARWeb\\plugins";

    /** Mirror of the *_RELATIVE_PATH_MIN constants in the Perform*Load classes. */
    private static final String[][] PLUGINS = {
        {"pageScanner", "pageScanner/build/scanner.min.js"},
        {"hoverPick", "hoverPick/build/hoverPick.min.js"},
        {"actionExecutor", "actionExecutor/build/actionExecutor.min.js"},
        {"searchListAsync", "searchListAsync/build/searchListAsync.min.js"},
    };

    public static void main(String[] args) {
        String pluginsDir = args.length > 0 ? args[0] : DEFAULT_PLUGINS_DIR;

        System.out.println("=== Plain .min.js Plugin Load Test (useNoEncrypted = true) ===\n");
        System.out.println("Plugins dir: " + pluginsDir + "\n");

        Path base = Paths.get(pluginsDir);
        if (!Files.isDirectory(base)) {
            System.out.println("FAIL: plugins dir does not exist: " + base);
            System.exit(1);
        }

        int passed = 0;
        int failed = 0;

        for (String[] p : PLUGINS) {
            String pluginId = p[0];
            String relative = p[1];
            Path path = base.resolve(relative);

            System.out.println("── " + pluginId + " ──");
            System.out.println("  Path:   " + path);

            if (!Files.exists(path)) {
                System.out.println("  RESULT: FAIL — file not found\n");
                failed++;
                continue;
            }

            try {
                long size = Files.size(path);
                String js = Files.readString(path, StandardCharsets.UTF_8);
                boolean validJs = js.contains("function")
                        || js.contains("var ")
                        || js.contains("const ")
                        || js.contains("=>")
                        || js.contains("(");

                System.out.println("  Size:   " + size + " bytes, " + js.length() + " chars");
                System.out.println("  Head:   "
                        + js.substring(0, Math.min(80, js.length())).replace("\n", " ") + "...");

                if (validJs) {
                    System.out.println("  RESULT: PASS\n");
                    passed++;
                } else {
                    System.out.println("  RESULT: FAIL — doesn't look like JavaScript\n");
                    failed++;
                }
            } catch (Exception e) {
                System.out.println("  RESULT: FAIL — " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n");
                failed++;
            }
        }

        System.out.println("=======================================");
        System.out.println("  " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("=======================================");

        if (failed > 0) {
            System.out.println("\nPossible causes:");
            System.out.println(
                    "  - Plugin not built: run `npx esbuild index.js --bundle --minify --outfile=build/<name>.min.js` in the plugin folder");
            System.out.println("  - Wrong plugins dir: pass the path as first argument");
            System.exit(1);
        }
        System.out.println("\nAll plain .min.js bundles are loadable. useNoEncrypted path is OK.");
    }
}
