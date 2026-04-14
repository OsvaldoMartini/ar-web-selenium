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
 * End-to-end injection test for the actionExecutor plugin via PerformActionExecutorLoad.
 *
 * The actionExecutor differs from the page scanner / hover-pick / search-list bundles:
 * it does NOT scan the DOM and return data. Instead it stays alive as a long-lived
 * WebSocket listener that waits for ACTION commands from Java (click, type, select,
 * scroll, …) and executes them on the page, sending back result frames.
 *
 * The IIFE signature (6 positional args, matching the trailing
 *   })( arguments[0..5] );
 * call):
 *   0 socketPort      int
 *   1 sessionId       string
 *   2 destination     string  (target session for routing)
 *   3 operationId     string  (fixed "actionExecutor")
 *   4 homeBankingId   int
 *   5 botJobId        int
 *
 * Driver:  D:\Projects\ARWeb-Martini\ARWeb-Scanner\edgedriver-versions\msedgedriver_64-(147.0.3912.60).exe
 * Plugins: D:\Projects\ARWeb-Martini\ARWeb\plugins
 * Page:    https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password
 *
 * Run as a plain main — no JUnit needed.
 */
public class Dynamic_ActionExecutor_Socket_Injection_Test {

    private static final String EDGE_DRIVER =
            "D:\\Projects\\ARWeb-Martini\\ARWeb-Scanner\\edgedriver-versions\\msedgedriver_64-(147.0.3912.60).exe";

    private static final String PLUGINS_DIR = "D:\\Projects\\ARWeb-Martini\\ARWeb\\plugins";

    private static final String TEST_PAGE = "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

    /**
     * Which actionExecutor bundle to inject.
     *   - ACTION_EXECUTION_RELATIVE_PATH_MIN      → production minified bundle
     *   - ACTION_EXECUTION_RELATIVE_PATH_ORIG_MIN → action-executor-in-use.min.js
     *   - ACTION_EXECUTION_RELATIVE_PATH_NOT_MIN  → action-executor-in-use.js (readable)
     */
    private static final String SCRIPT_PATH = PerformActionExecutorLoad.ACTION_EXECUTION_RELATIVE_PATH_MIN;

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

        // ── 3. Load the actionExecutor script via PerformPreLoad ────────────────
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

            // ── 5. Inject the actionExecutor (executeScript, 6 positional args) ─
            String sessionId = "actionExec-test-1";
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(
                    script,
                    port, // socketPort
                    sessionId, // sessionId
                    "scannerGrid", // destination
                    "actionExecutor", // operationId
                    184L, // homeBankingId
                    310L); // botJobId

            System.out.println("[inject] script injected, session=" + sessionId + ", port=" + port);
            if (result == null) {
                System.out.println(
                        "[result] null (actionExecutor stays alive — sends/receives via WebSocket, watch the WS log)");
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

            System.out.println("[inject] holding browser open for 30s — actionExecutor will:");
            System.out.println("           - connect to ws://localhost:" + port);
            System.out.println("           - send `subscribe` echo");
            System.out.println("           - emit ping-action every 15s");
            System.out.println("           - wait for ACTION commands (none sent here, so it just stays connected)");

            // ── 6. Hold so we can observe the WS handshake + ping frames ────────
            Thread.sleep(30_000);

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
