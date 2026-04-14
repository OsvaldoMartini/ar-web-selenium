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
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * End-to-end injection test using PerformPreLoad to load
 * pageScanner/build/script-search-in-use.js (the dynamic, non-minified bundle),
 * then injecting it into a real Edge browser pointed at a live page.
 *
 * Unlike ManualSocketInjectionTest (which uses the manual bundle with a hardcoded
 * port 55687 and hardcoded args), this dynamic script reads its 8 parameters from
 * Selenium's executeScript `arguments[0..7]`, so we pick a free WS port and pass
 * everything in.
 *
 * Driver:  D:\Projects\ARWeb-Martini\ARWeb-Scanner\edgedriver-versions\msedgedriver_64-(147.0.3912.60).exe
 * Plugins: D:\Projects\ARWeb-Martini\ARWeb\plugins
 * Page:    https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password
 *
 * Run as a plain main — no JUnit needed.
 */
public class Dynamic_PageScanner_Socket_Injection_Test {

    private static final String EDGE_DRIVER =
            "D:\\Projects\\ARWeb-Martini\\ARWeb-Scanner\\edgedriver-versions\\msedgedriver_64-(147.0.3912.60).exe";

    private static final String PLUGINS_DIR = "D:\\Projects\\ARWeb-Martini\\ARWeb\\plugins";

    private static final String TEST_PAGE = "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

    /**
     * Which scanner bundle to inject. Both end with `})( arguments[0..7] );`
     * so they accept the same 8 executeScript args.
     *   - SCANNER_RELATIVE_PATH_NOT_MIN → readable, easier to debug
     *   - SCANNER_RELATIVE_PATH_MIN     → minified production bundle
     */
    private static final String SCRIPT_PATH = PerformPreLoad.SCANNER_RELATIVE_PATH_MIN;
    //    private static final String SCRIPT_PATH = PerformPreLoad.SCANNER_RELATIVE_PATH_ORIG_MIN;

    public static void main(String[] args) throws Exception {

        // ── 1. Configure ARPropertyManager so PerformPreLoad can resolve plugins ─
        // setProperty() persists to ARWeb.config on disk, which fails in tests
        // (configurationFileName is null). Set the in-memory Properties directly.
        java.lang.reflect.Field propsField = ARPropertyManager.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        java.util.Properties props = (java.util.Properties) propsField.get(ARPropertyManager.getInstance());
        props.setProperty(ARPropertyEnum.PATH_PLUGINS.getValue(), PLUGINS_DIR);
        props.setProperty(ARPropertyEnum.PATH_LOG.getValue(), System.getProperty("java.io.tmpdir"));
        System.out.println("[setup] path_plugins = " + PLUGINS_DIR);

        // ── 2. Pick a free port and start the WS server in a background thread ──
        //       The dynamic script reads the port from executeScript arguments,
        //       so we can use any free port.
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

        // ── 3. Load the dynamic script via PerformPreLoad ───────────────────────
        String script = PerformPreLoad.loadPluginScript(SCRIPT_PATH);
        System.out.println("[setup] loaded script (" + SCRIPT_PATH + "): " + script.length() + " chars");

        // ── 4. Launch Edge via ARWebDriver (the project's own driver wrapper) ──
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

            // ── 5. Inject the dynamic script ────────────────────────────────────
            // The script ends with `})( arguments[0], ..., arguments[7] );` so
            // Selenium's executeScript args become the IIFE parameters directly.
            String sessionId = "scannerTool";
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                    script,
                    java.util.Arrays.asList("button", "textarea", "input", "label", "a", "select"), // searchTerms
                    false, // hiddenFields
                    port, // socketPort
                    sessionId, // sessionId
                    "scannerGrid", // destination
                    "searchTerms", // operationId
                    184L, // homeBankingId
                    310L); // botJobId

            System.out.println("[inject] script injected, session=" + sessionId + ", port=" + port);
            if (result == null) {
                System.out.println("[result] null (page scanner sends elements via WebSocket — watch the WS log)");
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
            System.out.println("[inject] holding browser open for 20s — watch the WS log above");

            // ── 6. Give the script time to scan + send WS frames ────────────────
            Thread.sleep(20_000);

        } finally {
            driver.quit();
            System.out.println("[done] browser closed");
            // give the WS server one last moment to flush its log
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
