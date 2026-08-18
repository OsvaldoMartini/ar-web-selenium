package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Production-derived, locally executed Playwright diagnostics.
 *
 * <p>The real BancaStato config/database are read only long enough to create a sanitized config and
 * consistent SQLite image. All browser actions run against a deterministic loopback page, so fill,
 * click, popup, iframe, shadow-DOM, navigation, scanning, and blocked-mutation behavior can be
 * exercised without sending data to the bank. Enable explicitly with:
 *
 * <pre>
 * mvn -Dtest=BancaStatoLocalhostPlaywrightIT -DbancastatoLocalIT=true test
 * </pre>
 *
 * Add {@code -DbancastatoKeepLocalhostOpen=true} to leave the loopback page available until Enter is
 * pressed. The active URL is printed and written to {@code target/bancastato-localhost-url.txt}.
 */
@EnabledIfSystemProperty(named = "bancastatoLocalIT", matches = "true")
@Isolated("Mutates ARPropertyManager and ARWebDriver singletons")
class BancaStatoLocalhostPlaywrightIT {

    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 5;
    private static final int BLOCK_ORDER_NUMBER = 1;
    private static final Path URL_FILE = Path.of("target", "bancastato-localhost-url.txt");

    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private final AtomicInteger serverMutationRequests = new AtomicInteger();

    private BancaStatoIsolatedFixture fixture;
    private HttpServer server;
    private ARPlaywrightDriver driver;
    private String baseUrl;
    private boolean browserSurfaceStarted;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void createIsolatedFixtureAndLocalhost() throws Exception {
        fixture = BancaStatoIsolatedFixture.create(tempDirectory);
        fixture.activate(properties);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        Files.createDirectories(URL_FILE.getParent());
        Files.writeString(URL_FILE, baseUrl + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("BancaStato isolated Playwright page: " + baseUrl);
    }

    @AfterEach
    void closeBrowserServerAndFixture() {
        try {
            holdLocalhostOpenWhenRequested();
        } finally {
            try {
                ARWebDriver.getInstance().closeBrowser();
            } catch (Exception ignored) {
                // Best-effort Playwright cleanup.
            }
            if (server != null) {
                server.stop(0);
            }
            try {
                Files.deleteIfExists(URL_FILE);
            } catch (IOException ignored) {
                // Stale URL file is diagnostic-only and points to a stopped loopback server.
            }
            if (fixture != null) {
                fixture.close();
            }
        }
    }

    @Test
    void productionSnapshotAndSanitizedConfigMatchExpectedContract() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM home_banking WHERE id = ?", HOME_BANKING_ID));
        assertEquals(1, scalar("SELECT COUNT(*) FROM bot_job WHERE id = ?", BOT_JOB_ID));
        assertEquals(5, scalar("SELECT COUNT(*) FROM block WHERE bot_job_id = ?", BOT_JOB_ID));
        assertEquals(
                6,
                scalar(
                        "SELECT COUNT(*) FROM instruction i JOIN block b ON b.id = i.block_id "
                                + "WHERE b.bot_job_id = ? AND b.block_order_number = ?",
                        BOT_JOB_ID,
                        BLOCK_ORDER_NUMBER));

        Properties isolated = new Properties();
        try (var input = Files.newInputStream(fixture.configFile())) {
            isolated.load(input);
        }
        assertEquals("TEXT", isolated.getProperty(ARPropertyEnum.DATABASE_TYPE.getValue()));
        assertEquals("", isolated.getProperty(ARPropertyEnum.DB_PWD.getValue()));
        assertEquals("", isolated.getProperty(ARPropertyEnum.AI_API_KEY.getValue()));
        assertTrue(Path.of(isolated.getProperty(ARPropertyEnum.PATH_DB.getValue())).startsWith(fixture.fixtureRoot()));
    }

    @Test
    void localhostCoversPlaywrightActionsFramesShadowDomPopupNavigationAndNetworkGuard() {
        driver = ARWebDriver.getInstance().getPlaywrightDriver();
        browserSurfaceStarted = true;
        String browser = properties.getProperty(ARPropertyEnum.BROWSER);
        boolean headless = Boolean.parseBoolean(System.getProperty("bancastatoLocalHeadless", "true"));
        driver.openReadOnlyDiagnostic(browser, baseUrl + "/", headless);

        List<ElementDTO> scanned = driver.scanElements(
                new String[] {"input", "button", "select", "label", "div", "iframe"}, false);
        assertFalse(scanned.isEmpty(), "Expected localhost banking controls to be scanned");
        assertTrue(scanned.stream().anyMatch(element -> "clientName".equals(element.getAttribId())));
        assertTrue(scanned.stream().anyMatch(element -> "createClient".equals(element.getAttribId())));

        assertTrue(driver.fill(byCss("clientName", "#clientName"), new FieldData("clientName", "Local Test")));
        assertTrue(driver.click(byCss("createClient", "#createClient")));
        assertEquals("Client Local Test created", driver.text(byCss("status", "#status")));

        assertTrue(driver.fill(byCss("shadowName", "#shadowName"), new FieldData("shadowName", "Shadow Test")));
        assertTrue(driver.click(byCss("shadowSave", "#shadowSave")));
        assertEquals(
                "Shadow Shadow Test saved",
                String.valueOf(driver.evaluate("() => document.querySelector('#shadowHost').shadowRoot.querySelector('#shadowStatus').textContent")));

        InstructionLoad iframeInput = byCss("iban", "#iban");
        iframeInput.setIFrameXPath("//iframe[@id='paymentFrame']");
        assertTrue(driver.fill(iframeInput, new FieldData("iban", "CH00-LOCAL")));
        assertEquals(
                "CH00-LOCAL",
                String.valueOf(driver.evaluate(
                        "() => document.querySelector('#paymentFrame').contentDocument.querySelector('#iban').value")));

        assertTrue(driver.click(byCss("scheduleDelayed", "#scheduleDelayed")));
        assertEquals(
                "ready",
                String.valueOf(driver.evaluate(
                        """
                        () => new Promise((resolve, reject) => {
                          if (document.querySelector('#delayedAction')) return resolve('ready');
                          const observer = new MutationObserver(() => {
                            if (document.querySelector('#delayedAction')) {
                              observer.disconnect();
                              resolve('ready');
                            }
                          });
                          observer.observe(document.querySelector('#delayedContainer'), {childList: true});
                          setTimeout(() => { observer.disconnect(); reject(new Error('delayed control timeout')); }, 3000);
                        })
                        """)));
        assertTrue(driver.click(byCss("delayedAction", "#delayedAction")));
        assertEquals("Delayed ready", driver.text(byCss("delayedStatus", "#delayedStatus")));

        assertEquals(
                "blocked",
                String.valueOf(driver.evaluate(
                        "async () => { try { await fetch('/mutation', {method:'POST', body:'never-send'}); return 'sent'; } catch (e) { return 'blocked'; } }")));
        assertEquals(0, serverMutationRequests.get(), "Read-only Playwright guard must block POST before localhost receives it");

        assertTrue(driver.click(byCss("openPopup", "#openPopup")));
        assertEquals("Mock Popup", driver.title());
        assertTrue(driver.currentUrl().endsWith("/popup"));
        assertTrue(driver.selectPage(0));

        driver.navigate(baseUrl + "/second");
        assertEquals("Second Page", driver.title());
        driver.goBack();
        assertEquals("Local Banking Playwright", driver.title());
        assertTrue(driver.screenshot(false).length > 100, "Expected non-empty Playwright screenshot bytes");
    }

    private int scalar(String sql, int... parameters) throws Exception {
        String databasePath = fixture.databaseFile().toString().replace('\\', '/');
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:file:" + databasePath + "?mode=ro")) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setInt(i + 1, parameters[i]);
                }
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next(), "Expected scalar query result");
                    return result.getInt(1);
                }
            }
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            serverMutationRequests.incrementAndGet();
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String html = switch (path) {
            case "/frame" -> framePage();
            case "/popup" -> popupPage();
            case "/second" -> secondPage();
            default -> mainPage();
        };
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static InstructionLoad byCss(String name, String css) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setName(name);
        instruction.setCssSelector(css);
        return instruction;
    }

    private static String mainPage() {
        return """
                <!doctype html>
                <html>
                  <head><meta charset="utf-8"><title>Local Banking Playwright</title></head>
                  <body>
                    <main>
                      <h1>BancaStato isolated Playwright surface</h1>
                      <label for="clientName">Client name</label>
                      <input id="clientName" name="clientName" data-testid="client-name" />
                      <label for="accountType">Account type</label>
                      <select id="accountType" name="accountType">
                        <option value="checking">Checking</option>
                        <option value="savings">Savings</option>
                      </select>
                      <button id="createClient" type="button">Create client</button>
                      <div id="status">Waiting</div>
                      <div id="shadowHost"></div>
                      <iframe id="paymentFrame" src="/frame" title="Payment frame"></iframe>
                      <button id="scheduleDelayed" type="button">Schedule delayed action</button>
                      <div id="delayedContainer"></div>
                      <div id="delayedStatus">Delayed pending</div>
                      <button id="openPopup" type="button">Open popup</button>
                      <a id="secondPage" href="/second">Second page</a>
                    </main>
                    <script>
                      document.querySelector('#createClient').addEventListener('click', () => {
                        document.querySelector('#status').textContent =
                          'Client ' + document.querySelector('#clientName').value + ' created';
                      });
                      const root = document.querySelector('#shadowHost').attachShadow({mode: 'open'});
                      root.innerHTML = `<label for="shadowName">Shadow name</label>
                        <input id="shadowName"/><button id="shadowSave" type="button">Save shadow</button>
                        <div id="shadowStatus">Shadow waiting</div>`;
                      root.querySelector('#shadowSave').addEventListener('click', () => {
                        root.querySelector('#shadowStatus').textContent =
                          'Shadow ' + root.querySelector('#shadowName').value + ' saved';
                      });
                      document.querySelector('#scheduleDelayed').addEventListener('click', () => {
                        setTimeout(() => {
                          const button = document.createElement('button');
                          button.id = 'delayedAction';
                          button.type = 'button';
                          button.textContent = 'Delayed action';
                          button.addEventListener('click', () => {
                            document.querySelector('#delayedStatus').textContent = 'Delayed ready';
                          });
                          document.querySelector('#delayedContainer').appendChild(button);
                        }, 500);
                      });
                      document.querySelector('#openPopup').addEventListener('click', () => window.open('/popup', '_blank'));
                    </script>
                  </body>
                </html>
                """;
    }

    private static String framePage() {
        return """
                <!doctype html><html><head><title>Payment Frame</title></head>
                <body><label for="iban">IBAN</label><input id="iban" name="iban" /></body></html>
                """;
    }

    private static String popupPage() {
        return "<!doctype html><html><head><title>Mock Popup</title></head><body>Popup ready</body></html>";
    }

    private static String secondPage() {
        return "<!doctype html><html><head><title>Second Page</title></head><body>Second page ready</body></html>";
    }

    private void holdLocalhostOpenWhenRequested() {
        if (!browserSurfaceStarted
                || !Boolean.parseBoolean(System.getProperty("bancastatoKeepLocalhostOpen", "false"))) {
            return;
        }
        System.out.println("Localhost test page remains available at " + baseUrl + ". Press Enter to close it.");
        try {
            new InputStreamReader(System.in, StandardCharsets.UTF_8).read();
        } catch (IOException error) {
            System.out.println("Localhost hold ended: " + error.getMessage());
        }
    }
}
