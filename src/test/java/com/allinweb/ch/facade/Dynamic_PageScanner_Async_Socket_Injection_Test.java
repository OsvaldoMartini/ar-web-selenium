package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.time.Duration;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Async test variant of the page scanner. Uses
 * pageScanner/build/script-search-in-use-async.js which is the regular
 * page-scanner script with a WebSocket MOCK in front: every chunk the script
 * tries to send is captured in-page and forwarded to Selenium's
 * executeAsyncScript callback as a single JSON payload.
 *
 * No external WS server, no port, no manual cleanup — Selenium gets the
 * scanned elements as the script's return value, just like the searchListAsync
 * test pattern but using the page-scanner DOM walker.
 *
 * Driver:  C:\Projects\ARWeb-Martini\ARWeb-Scanner\edgedriver-versions\msedgedriver_64-(147.0.3912.60).exe
 * Plugins: C:\Projects\ARWeb-Martini\ARWeb\plugins
 * Page:    https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password
 *
 * Run as a plain main — no JUnit needed.
 */
public class Dynamic_PageScanner_Async_Socket_Injection_Test {

    private static final String EDGE_DRIVER =
            "C:\\ARWeb-Martini\\ARWeb-Scanner\\edgedriver-versions\\msedgedriver_64-(147.0.3912.60).exe";

    private static final String PLUGINS_DIR = "C:\\ARWeb-Martini\\ARWeb\\plugins";

    private static final String TEST_PAGE = "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

    /** Async page-scanner variant with WebSocket mock prelude. */
    private static final String SCRIPT_PATH = PerformPreLoad.SCANNER_RELATIVE_PATH_ASYNC;

    public static void main(String[] args) throws Exception {

        // ── 1. Configure ARPropertyManager so PerformPreLoad can resolve plugins ─
        java.lang.reflect.Field propsField = ARPropertyManager.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(ARPropertyManager.getInstance());
        props.setProperty(ARPropertyEnum.PATH_PLUGINS.getValue(), PLUGINS_DIR);
        props.setProperty(ARPropertyEnum.PATH_LOG.getValue(), System.getProperty("java.io.tmpdir"));
        System.out.println("[setup] path_plugins = " + PLUGINS_DIR);

        // No WS server needed — script returns via Selenium callback.

        // ── 2. Load the async page-scanner script via PerformPreLoad ────────────
        String script = PerformPreLoad.loadPluginScript(SCRIPT_PATH);
        System.out.println("[setup] loaded script (" + SCRIPT_PATH + "): " + script.length() + " chars");

        // ── 3. Launch Edge via ARWebDriver ──────────────────────────────────────
        // Pass dummy port (0) — the script doesn't use it, the WS is mocked.
        WebDriver driver = ARWebDriver.getInstance()
                .openDriver(
                        ARConstantsEngine.EDGE,
                        EDGE_DRIVER,
                        TEST_PAGE,
                        "", // optionsConfig
                        new String[] {"button", "input", "a", "select"},
                        false,
                        0);

        if (driver == null) {
            System.out.println("[error] ARWebDriver.openDriver returned null — aborting");
            System.exit(1);
        }

        try {
            Thread.sleep(500);

            // ── 4. Inject (executeAsyncScript) ──────────────────────────────────
            // Script timeout > 20s (matches the in-script safety net).
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

            ScannerSearchRoute route = ScannerSearchRoute.standardPageScanner();
            String sessionId = route.sourceSessionId();
            JavascriptExecutor js = (JavascriptExecutor) driver;

            long t0 = System.currentTimeMillis();
            Object result = js.executeAsyncScript(
                    script,
                    java.util.Arrays.asList("button", "textarea", "input", "label", "a", "select"), // searchTerms
                    false, // hiddenFields
                    0, // socketPort (unused — WebSocket is mocked)
                    sessionId, // sessionId
                    route.destinationSessionId(), // destination
                    route.operationId(), // operationId
                    184L, // homeBankingId
                    310L); // botJobId
            long elapsed = System.currentTimeMillis() - t0;

            System.out.println("[inject] async script returned in " + elapsed + " ms, session=" + sessionId);
            if (result == null) {
                System.out.println("[result] null");
            } else {
                String s = String.valueOf(result);
                System.out.println("[result] type=" + result.getClass().getSimpleName() + ", length=" + s.length());
                try {
                    Gson pretty = new GsonBuilder()
                            .setPrettyPrinting()
                            .disableHtmlEscaping()
                            .create();
                    JsonElement parsed = JsonParser.parseString(s);
                    System.out.println("[result] pretty JSON:");
                    System.out.println(pretty.toJson(parsed));
                } catch (Exception parseErr) {
                    System.out.println("[result] (not JSON) preview: " + s.substring(0, Math.min(500, s.length())));
                }
            }

            // Brief hold so any console logs in the page have time to flush
            Thread.sleep(1_000);

        } finally {
            driver.quit();
            System.out.println("[done] browser closed");
            Thread.sleep(200);
            System.exit(0);
        }
    }
}
