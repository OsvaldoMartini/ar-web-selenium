package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.net.ServerSocket;
import java.time.Duration;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * End-to-end injection test for the searchListAsync plugin via PerformListElements.
 *
 * Unlike the page-scanner and hover-pick scripts (which use {@code executeScript}),
 * the searchListAsync bundle is an ASYNC Selenium script: it reads the Selenium
 * callback from {@code arguments[arguments.length-1]} and invokes it with a JSON
 * string when it's done. We must call {@link JavascriptExecutor#executeAsyncScript}
 * and set a script timeout that's longer than the script's internal 20-second guard.
 *
 * Argument order (8 positional args, matching the IIFE signature):
 *   0 searchTerms        Array<String>
 *   1 hiddenFields       boolean
 *   2 socketPort         int
 *   3 sessionId          string
 *   4 destination        string
 *   5 operationId        string
 *   6 homeBankingId      int
 *   7 botJobId           int
 *
 * Driver:  D:\Projects\ARWeb-Martini\ARWeb-Scanner\edgedriver-versions\msedgedriver_64-(147.0.3912.60).exe
 * Plugins: D:\Projects\ARWeb-Martini\ARWeb\plugins
 * Page:    https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password
 *
 * Run as a plain main — no JUnit needed.
 */
public class Dynamic_SearchListAsync_Socket_Injection_Test {

    private static final String EDGE_DRIVER =
            "D:\\Projects\\ARWeb-Martini\\ARWeb-Scanner\\edgedriver-versions\\msedgedriver_64-(147.0.3912.60).exe";

    private static final String PLUGINS_DIR = "D:\\Projects\\ARWeb-Martini\\ARWeb\\plugins";

    private static final String TEST_PAGE = "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

    /**
     * Which searchListAsync bundle to inject.
     *   - SEARCH_LIST_ASYNC_RELATIVE_PATH_MIN      → minified production bundle
     *   - SEARCH_LIST_ASYNC_RELATIVE_PATH_ORIG_MIN → original minified script-search-in-use-list-async.min.js
     *   - SEARCH_LIST_ASYNC_RELATIVE_PATH_NOT_MIN  → readable, easier to debug (if present)
     */
    private static final String SCRIPT_PATH = PerformListElements.SEARCH_LIST_ASYNC_RELATIVE_PATH_MIN;

    public static void main(String[] args) throws Exception {

        // ── 1. Configure ARPropertyManager so PerformPreLoad can resolve plugins ─
        java.lang.reflect.Field propsField = ARPropertyManager.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(ARPropertyManager.getInstance());
        props.setProperty(ARPropertyEnum.PATH_PLUGINS.getValue(), PLUGINS_DIR);
        props.setProperty(ARPropertyEnum.PATH_LOG.getValue(), System.getProperty("java.io.tmpdir"));
        System.out.println("[setup] path_plugins = " + PLUGINS_DIR);

        // ── 2. Pick a free port and start the WS server in a background thread ──
        int port = pickFreePort();
        Thread wsServer = new Thread(
                () -> {
                    try {
                        ManualScriptWebSocketTest.main(new String[] {String.valueOf(port)});
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                "ws-server");
        wsServer.setDaemon(true);
        wsServer.start();
        Thread.sleep(300);
        System.out.println("[setup] WS server listening on port " + port);

        // ── 3. Load the searchListAsync script via PerformPreLoad ───────────────
        String script = PerformPreLoad.loadPluginScript(SCRIPT_PATH);
        System.out.println("[setup] loaded script (" + SCRIPT_PATH + "): " + script.length() + " chars");

        // ── 4. Launch Edge via ARWebDriver ──────────────────────────────────────
        WebDriver driver = ARWebDriver.getInstance()
                .openDriver(
                        ARConstantsEngine.EDGE,
                        EDGE_DRIVER,
                        TEST_PAGE,
                        "", // optionsConfig (no extra config lines)
                        new String[] {"button", "input", "a", "select"},
                        false,
                        port);

        if (driver == null) {
            System.out.println("[error] ARWebDriver.openDriver returned null — aborting");
            System.exit(1);
        }

        try {
            Thread.sleep(500);

            // ── 5. Inject the searchListAsync script (executeAsyncScript) ───────
            // The script reads its callback from arguments[arguments.length-1],
            // so we must pass our 8 args first; Selenium appends the callback.
            // Set a script timeout > 20s (the script's internal timeout fallback).
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

            String sessionId = "scannerTool";
            JavascriptExecutor js = (JavascriptExecutor) driver;

            long t0 = System.currentTimeMillis();
            Object result = js.executeAsyncScript(
                    script,
                    java.util.Arrays.asList("button", "textarea", "input", "label", "a", "select"), // searchTerms
                    false, // hiddenFields
                    port, // socketPort
                    sessionId, // sessionId
                    "scannerGrid", // destination
                    "searchTerms", // operationId
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

            // Hold open briefly so the WS server has a chance to flush any frames
            Thread.sleep(3_000);

        } finally {
            driver.quit();
            System.out.println("[done] browser closed");
            Thread.sleep(200);
            System.exit(0);
        }
    }

    private static int pickFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
