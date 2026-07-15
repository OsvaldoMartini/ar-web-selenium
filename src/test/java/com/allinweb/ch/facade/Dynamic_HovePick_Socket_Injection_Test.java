package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.net.ServerSocket;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * End-to-end injection test for the legacy hoverPick plugin using its
 * relative script path. Loads the script, launches Edge via ARWebDriver,
 * navigates to a live page, and injects via executeScript with the 9 IIFE args.
 *
 * The hoverPick script (script-hover-pick-in-use.js / hoverPick.min.js) ends with:
 *   })( arguments[0], arguments[1], ..., arguments[8] );
 *
 * Argument order (9, not 8 like the page scanner):
 *   0 hiddenFields       boolean
 *   1 socketPort         int
 *   2 sessionId          string
 *   3 destination        string
 *   4 operationId        string
 *   5 homeBankingId      int
 *   6 botJobId           int
 *   7 targetOriginURL    string
 *   8 trustedOriginURL   string
 *
 * Driver:  D:\Projects\ARWeb-Martini\ARWeb-Scanner\edgedriver-versions\msedgedriver_64-(147.0.3912.60).exe
 * Plugins: D:\Projects\ARWeb-Martini\ARWeb\plugins
 * Page:    https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password
 *
 * Run as a plain main — no JUnit needed.
 */
public class Dynamic_HovePick_Socket_Injection_Test {

    private static final String EDGE_DRIVER =
            "C:\\ARWeb-Martini\\ARWeb-Scanner\\edgedriver-versions\\msedgedriver_64-(147.0.3912.60).exe";

    private static final String PLUGINS_DIR = "C:\\ARWeb-Martini\\ARWeb\\plugins";

    private static final String TEST_PAGE = "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

    /**
     * Which hoverPick bundle to inject. Both end with `})( arguments[0..8] );`
     * so they accept the same 9 executeScript args.
     *   - HOVER_RELATIVE_PATH_NOT_MIN → readable, easier to debug (lives under pageScanner/build)
     *   - HOVER_PICK_RELATIVE_PATH_MIN → minified production bundle (lives under hoverPick/build)
     */
    private static final String SCRIPT_PATH = "hoverPick/build/hoverPick.min.js";

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

        // ── 3. Load the hoverPick script via PerformPreLoad ─────────────────────
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

            // ── 5. Inject the hoverPick script ──────────────────────────────────
            // 9 positional args matching the trailing `})( arguments[0..8] );`.
            String sessionId = ScannerWorkspaceSessions.SCANNER_TOOL;
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    script,
                    false, // hiddenFields
                    port, // socketPort
                    sessionId, // sessionId
                    ScannerWorkspaceSessions.SCANNER_GRID, // destination
                    ScannerWorkspaceOperations.ADD_PICK_ONE, // operationId
                    2L, // homeBankingId
                    66L, // botJobId
                    TEST_PAGE, // targetOriginURL
                    TEST_PAGE); // trustedOriginURL

            System.out.println("[inject] script injected, session=" + sessionId + ", port=" + port);
            System.out.println("[inject] holding browser open for 30s — hover over elements to trigger picks");

            // ── 6. Give the user time to hover-pick + observe WS frames ─────────
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
