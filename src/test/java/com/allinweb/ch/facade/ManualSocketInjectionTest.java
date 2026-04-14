package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.net.ServerSocket;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * End-to-end injection test using PerformPreLoad to load
 * pageScanner/build/script-search-in-use-manual.js, then injecting it
 * into a real Edge browser pointed at a local test page. The manual script
 * connects to a WebSocket server started in this same JVM
 * (ManualScriptWebSocketTest), so every frame the script sends is logged
 * to the console.
 *
 * Driver:  D:\Projects\ARWeb-Martini\ARWeb-Scanner\edgedriver-versions\msedgedriver_64-(147.0.3912.60).exe
 * Plugins: D:\Projects\ARWeb-Martini\ARWeb\plugins
 * Page:    src/main/resources/build/input_page.html (file:// URL)
 *
 * Run as a plain main — no JUnit needed.
 */
public class ManualSocketInjectionTest {

    private static final String EDGE_DRIVER =
            "D:\\Projects\\ARWeb-Martini\\ARWeb-Scanner\\edgedriver-versions\\msedgedriver_64-(147.0.3912.60).exe";

    private static final String PLUGINS_DIR = "D:\\Projects\\ARWeb-Martini\\ARWeb\\plugins";

    private static final String TEST_PAGE = "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

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

        // ── 2. Start the WS server on the port hardcoded inside the manual script.
        //       script-search-in-use-manual.js ends with `})( ..., 55687, ... );`
        //       so the script will only connect to ws://localhost:55687.
        int port = 55687;
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

        // ── 3. Load the manual script via PerformPreLoad (the whole point) ──────
        String script = PerformPreLoad.loadPluginScript(PerformPreLoad.SCANNER_RELATIVE_PATH_MANUAL);
        System.out.println("[setup] loaded manual script: " + script.length() + " chars");

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

            // ── 5. Inject the manual script ─────────────────────────────────────
            // The script is a self-invoking IIFE with hardcoded args (port 55687,
            // sessionId "scannerTool", etc.) — inject it as-is, no wrapping.
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(script);

            System.out.println("[inject] script injected (uses hardcoded port " + port + ")");
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
