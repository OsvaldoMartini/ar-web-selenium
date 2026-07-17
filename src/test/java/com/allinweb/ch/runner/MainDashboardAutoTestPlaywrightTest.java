package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.driver.PlaywrightTestSupport;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Headless navigation contract for the deployed Main Dashboard and Auto Test inventory.
 *
 * <p>The React build and packaged automation catalog are served/read locally. WebSocket and
 * new-tab APIs are replaced before application JavaScript executes, so every dashboard control
 * can be exercised without a backend, database, browser engine run, or destructive side effect.
 */
class MainDashboardAutoTestPlaywrightTest {

    private static final String DASHBOARD_SESSION = "mainDashboard";
    private static final int SOCKET_PORT = 54525;
    private static final Path BUILD_ROOT =
            Path.of("src", "main", "resources", "build").toAbsolutePath().normalize();
    private static final Path CATALOG_PATH =
            Path.of("src", "main", "resources", "automation-tests.json")
                    .toAbsolutePath()
                    .normalize();

    private static final String WEBSOCKET_MOCK_TEMPLATE = """
            (() => {
              const clone = (value) => JSON.parse(JSON.stringify(value));
              window.__arCatalog = __AR_AUTOMATION_CATALOG__;
              window.__arDashboardRequests = [];
              window.__arOpenedTabs = [];
              window.__arDashboardJobs = [
                {
                  id: 101,
                  name: 'Payments',
                  description: 'Deterministic payment flow',
                  priority: 'WEB',
                  active: true,
                  homeBankingId: 7,
                  homeUrlId: 8,
                  organizationName: 'Test Bank',
                  environmentName: 'TEST',
                  environmentUrl: 'https://test.example',
                  blockCount: 12,
                  launchable: true
                },
                {
                  id: 202,
                  name: 'Mobile onboarding',
                  description: 'Mobile-only flow',
                  priority: 'MOBILE',
                  active: false,
                  homeBankingId: 9,
                  homeUrlId: 10,
                  organizationName: 'Mobile Bank',
                  environmentName: 'QA',
                  environmentUrl: 'https://qa.example',
                  blockCount: 4,
                  launchable: false
                }
              ];

              window.open = (url, target) => {
                window.__arOpenedTabs.push({ url: String(url), target: String(target) });
                return { closed: false };
              };

              const parseBody = (envelope) => {
                if (!envelope || envelope.body == null) return {};
                return typeof envelope.body === 'string' ? JSON.parse(envelope.body) : envelope.body;
              };

              const emit = (socket, operationId, body) => {
                const envelope = {
                  sessionId: socket.sessionId,
                  operationId,
                  body: JSON.stringify(body)
                };
                setTimeout(() => {
                  if (socket.readyState === MockWebSocket.OPEN && typeof socket.onmessage === 'function') {
                    socket.onmessage({ data: JSON.stringify(envelope) });
                  }
                }, 0);
              };

              class MockWebSocket {
                constructor(url) {
                  this.url = String(url);
                  this.sessionId = new URL(this.url).searchParams.get('sessionId') || '';
                  this.readyState = MockWebSocket.CONNECTING;
                  this.onopen = null;
                  this.onmessage = null;
                  this.onerror = null;
                  this.onclose = null;
                  queueMicrotask(() => {
                    if (this.readyState !== MockWebSocket.CONNECTING) return;
                    this.readyState = MockWebSocket.OPEN;
                    if (typeof this.onopen === 'function') this.onopen({ type: 'open' });
                    if (this.sessionId === 'mainDashboardBootstrap') {
                      emit(this, 'react.session.open', {
                        targetSession: 'mainDashboard',
                        port: 54525,
                        botJobId: -9999
                      });
                    }
                  });
                }

                send(rawMessage) {
                  if (typeof rawMessage === 'string' && rawMessage.startsWith('ping-')) return;
                  const envelope = JSON.parse(String(rawMessage));
                  const body = parseBody(envelope);
                  window.__arDashboardRequests.push(clone(envelope));

                  if (envelope.type === 'mainDashboard.list') {
                    emit(this, 'mainDashboard.listResponse', {
                      ok: true,
                      botJobs: clone(window.__arDashboardJobs)
                    });
                    return;
                  }

                  if (envelope.type === 'license.bootstrap') {
                    emit(this, 'license.bootstrapResponse', {
                      active: true,
                      status: 'ACTIVE',
                      statusCode: 'ACTIVE',
                      organization: 'Test Bank',
                      owner: 'Ada Lovelace',
                      licensedUser: 'ci-licensed-runner'
                    });
                    return;
                  }

                  if (envelope.type === 'automationTests.list') {
                    emit(this, 'automationTests.listResponse', clone(window.__arCatalog));
                    return;
                  }

                  if (String(envelope.type).startsWith('mainDashboard.')) {
                    emit(this, 'mainDashboard.actionResponse', {
                      ok: true,
                      message: envelope.type + ' completed',
                      selectedBotJobId: typeof body.botJobId === 'number' ? body.botJobId : undefined
                    });
                  }
                }

                close() {
                  if (this.readyState === MockWebSocket.CLOSED) return;
                  this.readyState = MockWebSocket.CLOSED;
                  if (typeof this.onclose === 'function') this.onclose({ type: 'close' });
                }

                addEventListener(type, listener) {
                  this['on' + type] = listener;
                }

                removeEventListener(type, listener) {
                  if (this['on' + type] === listener) this['on' + type] = null;
                }
              }
              MockWebSocket.CONNECTING = 0;
              MockWebSocket.OPEN = 1;
              MockWebSocket.CLOSING = 2;
              MockWebSocket.CLOSED = 3;
              Object.defineProperty(window, 'WebSocket', {
                configurable: true,
                writable: true,
                value: MockWebSocket
              });
            })();
            """;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;

    @BeforeEach
    void serveDeployedReactBuild() throws IOException {
        PlaywrightTestSupport.assumeBrowserLaunchAvailable();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serveBuildFile);
        serverExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "main-dashboard-auto-test-playwright-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(serverExecutor);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopLocalhostServer() {
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void navigatesEveryDashboardControlAndBrowsesTheCompleteTestCatalog() throws Exception {
        JsonObject catalog = readCatalog();
        int catalogEntryCount = catalog.getAsJsonArray("tests").size();
        int automatedCodeCases =
                catalog.getAsJsonObject("summary").get("automatedCodeCases").getAsInt();
        assertDashboardBuildIsDeployed();
        List<String> pageErrors = new CopyOnWriteArrayList<>();

        Path chromeExecutable = locateChromeExecutable();
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(
                Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")))) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setExecutablePath(chromeExecutable));
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1440, 1000));
            context.addInitScript(WEBSOCKET_MOCK_TEMPLATE.replace(
                    "__AR_AUTOMATION_CATALOG__", catalog.toString()));
            Page page = context.newPage();
            page.setDefaultTimeout(15_000);
            page.onPageError(pageErrors::add);

            try {
                page.navigate(baseUrl + "/");
                page.getByText("Main Dashboard", new Page.GetByTextOptions().setExact(true)).waitFor();
                page.getByText("Ada Lovelace", new Page.GetByTextOptions().setExact(true)).first().waitFor();
                awaitCatalogRequestPrerequisites(page);

                coverFindSortAndSelectionControls(page);
                coverDashboardCommandButtons(page);
                coverDeleteControls(page);
                coverUserIconAndAutomationCatalog(page, catalogEntryCount, automatedCodeCases);

                assertTrue(pageErrors.isEmpty(), "Browser page errors: " + pageErrors);
            } finally {
                context.close();
                browser.close();
            }
        }
    }

    private static void awaitCatalogRequestPrerequisites(Page page) {
        page.waitForFunction(
                """
                () => window.__arDashboardRequests.some(request => request.type === 'mainDashboard.list')
                  && window.__arDashboardRequests.some(request => request.type === 'license.bootstrap')
                """);
        page.locator("table tbody tr").first().waitFor();
        assertEquals(2, page.locator("table tbody tr").count());
    }

    private static void coverFindSortAndSelectionControls(Page page) {
        Locator clone = button(page, "Clone Job");
        Locator launch = button(page, "Launch");
        Locator openJob = button(page, "Open Job");
        assertTrue(clone.isDisabled());
        assertTrue(launch.isDisabled());
        assertTrue(openJob.isDisabled());

        Locator sortableHeaders = page.locator("th[title='Click to sort']");
        assertEquals(8, sortableHeaders.count());
        for (int index = 0; index < sortableHeaders.count(); index++) {
            sortableHeaders.nth(index).click();
        }

        Locator find = page.locator("#main-dashboard-find");
        find.fill("Mobile Bank");
        assertEquals(1, page.locator("table tbody tr").count());
        assertTrue(page.locator("table tbody tr").first().textContent().contains("Mobile onboarding"));
        page.locator("button[title='Clear Find']").click();
        assertEquals("", find.inputValue());
        assertEquals(2, page.locator("table tbody tr").count());

        Locator mobileRow = dashboardRow(page, 202);
        mobileRow.click();
        assertFalse(clone.isDisabled());
        assertTrue(launch.isDisabled(), "Mobile-only Bot Jobs must remain non-launchable");

        dashboardRow(page, 101).click();
        assertFalse(clone.isDisabled());
        assertFalse(launch.isDisabled());
        assertFalse(openJob.isDisabled());
    }

    private static void coverDashboardCommandButtons(Page page) {
        requestBodyAfterClick(page, button(page, "Organizations"), "mainDashboard.openOrganizations");
        requestBodyAfterClick(page, button(page, "New Bot Job"), "mainDashboard.newBotJob");
        assertEquals(
                101,
                requestBodyAfterClick(page, button(page, "Clone Job"), "mainDashboard.cloneBotJob")
                        .get("botJobId")
                        .getAsInt());
        requestBodyAfterClick(page, button(page, "Config"), "mainDashboard.openConfig");
        requestBodyAfterClick(page, button(page, "Info"), "mainDashboard.openInfo");
        assertEquals(
                101,
                requestBodyAfterClick(page, button(page, "Launch"), "mainDashboard.launchBotJob")
                        .get("botJobId")
                        .getAsInt());
        assertEquals(
                101,
                requestBodyAfterClick(page, button(page, "Open Job"), "mainDashboard.openBotJob")
                        .get("botJobId")
                        .getAsInt());
        page.waitForFunction("() => window.__arOpenedTabs.length === 1");
        @SuppressWarnings("unchecked")
        Map<String, String> openedTab = (Map<String, String>) page.evaluate(
                "() => window.__arOpenedTabs[0]");
        assertTrue(openedTab.get("url").endsWith("/?openBotJob=101"));
        assertEquals("_blank", openedTab.get("target"));

        int listRequests = requestCount(page, "mainDashboard.list");
        button(page, "Refresh").click();
        awaitRequestCount(page, "mainDashboard.list", listRequests + 1);
        requestBodyAfterClick(page, button(page, "Exit"), "mainDashboard.exit");

        int beforeDoubleClick = requestCount(page, "mainDashboard.openBotJob");
        dashboardRow(page, 101).dblclick();
        awaitRequestCount(page, "mainDashboard.openBotJob", beforeDoubleClick + 1);
        page.waitForFunction("() => window.__arOpenedTabs.length === 2");
    }

    private static void coverDeleteControls(Page page) {
        Locator deleteRow = dashboardRow(page, 101).locator("button[title='Delete Bot Job']");
        deleteRow.click();
        page.getByText("Bot Job Deletion", new Page.GetByTextOptions().setExact(true)).waitFor();
        button(page, "Cancel").click();
        assertEquals(0, page.getByText(
                        "Bot Job Deletion", new Page.GetByTextOptions().setExact(true))
                .count());

        deleteRow.click();
        JsonObject body = requestBodyAfterClick(
                page, button(page, "Delete"), "mainDashboard.deleteBotJob");
        assertEquals(101, body.get("botJobId").getAsInt());
        assertEquals(0, page.getByText(
                        "Bot Job Deletion", new Page.GetByTextOptions().setExact(true))
                .count());
    }

    private static void coverUserIconAndAutomationCatalog(
            Page page, int catalogEntryCount, int automatedCodeCases) {
        Locator trigger = page.getByLabel("Open user menu");
        Locator suppliedIcon = trigger.locator("svg.lucide-user[width='15'][height='15']");
        assertEquals(1, suppliedIcon.count());
        assertEquals("0 0 24 24", suppliedIcon.getAttribute("viewBox"));
        assertEquals("none", suppliedIcon.getAttribute("fill"));
        assertEquals("currentColor", suppliedIcon.getAttribute("stroke"));
        assertEquals("2", suppliedIcon.getAttribute("stroke-width"));
        assertEquals(
                1,
                suppliedIcon.locator(
                                "path[d='M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2']")
                        .count());
        assertEquals(1, suppliedIcon.locator("circle[cx='12'][cy='7'][r='4']").count());

        trigger.click();
        Locator userMenu = page.locator("[role='menu'][aria-label='User menu']");
        userMenu.waitFor();
        assertTrue(userMenu.textContent().contains("Licensed user"));
        assertTrue(userMenu.textContent().contains("Ada Lovelace"));
        assertTrue(userMenu.textContent().contains("ci-licensed-runner"));
        assertTrue(userMenu.textContent().contains("ACTIVE"));

        int beforeCatalogRequest = requestCount(page, "automationTests.list");
        userMenu.locator("button[role='menuitem']:has-text('Auto Test')").click();
        awaitRequestCount(page, "automationTests.list", beforeCatalogRequest + 1);

        Locator panel = page.locator("section[aria-label='Auto Test automation catalog']");
        panel.waitFor();
        assertEquals("SECTION", panel.evaluate("element => element.tagName"));
        assertEquals(null, panel.getAttribute("aria-modal"));
        assertEquals(0, page.locator("[role='dialog']").count());
        awaitCatalogRows(page, catalogEntryCount);
        assertTrue(panel.getByText(
                        "Code test cases", new Locator.GetByTextOptions().setExact(true))
                .isVisible());
        assertEquals(
                automatedCodeCases,
                ((Number) page.evaluate(
                                """
                                () => Number(document.querySelector(
                                  "[aria-label='Automation totals'] strong"
                                ).textContent.replace(/[^0-9]/g, ''))
                                """))
                        .intValue());

        coverCatalogFilters(page, catalogEntryCount);
        coverCatalogDraggingAndResponsiveLayout(page, panel);

        int refreshCount = requestCount(page, "automationTests.list");
        page.getByLabel("Refresh test catalog").click();
        awaitRequestCount(page, "automationTests.list", refreshCount + 1);
        awaitCatalogRows(page, catalogEntryCount);

        page.getByLabel("Close Auto Test").click();
        assertEquals(0, page.locator("section[aria-label='Auto Test automation catalog']").count());
    }

    private static void coverCatalogFilters(Page page, int catalogEntryCount) {
        Locator repository = page.getByLabel("Filter by repository");
        Locator type = page.getByLabel("Filter by test type");
        Locator safety = page.getByLabel("Filter by safety");
        Locator find = page.getByLabel("Find tests");

        repository.selectOption("AR Web Scanner");
        awaitRowsMatchingCatalogFilter(page);
        type.selectOption("PLAYWRIGHT");
        awaitRowsMatchingCatalogFilter(page);
        safety.selectOption("LOCAL_REQUIREMENT");
        awaitRowsMatchingCatalogFilter(page);
        find.fill("Playwright");
        awaitRowsMatchingCatalogFilter(page);
        assertTrue(page.locator("[data-testid='auto-test-workspace'] tbody tr").count() > 0);

        button(page.locator("[data-testid='auto-test-workspace']"), "Clear").click();
        awaitCatalogRows(page, catalogEntryCount);
        assertEquals("ALL", repository.inputValue());
        assertEquals("ALL", type.inputValue());
        assertEquals("ALL", safety.inputValue());
        assertEquals("", find.inputValue());
    }

    private static void coverCatalogDraggingAndResponsiveLayout(Page page, Locator panel) {
        double leftBefore = ((Number) panel.evaluate(
                        "element => element.getBoundingClientRect().left"))
                .doubleValue();
        double topBefore = ((Number) panel.evaluate(
                        "element => element.getBoundingClientRect().top"))
                .doubleValue();
        Locator dragHandle = page.locator("[data-testid='auto-test-drag-handle']");
        BoundingBox handleBox = dragHandle.boundingBox();
        assertNotNull(handleBox);
        page.mouse().move(handleBox.x + 24, handleBox.y + handleBox.height / 2);
        page.mouse().down();
        page.mouse().move(handleBox.x + 84, handleBox.y + handleBox.height / 2 + 36);
        page.mouse().up();
        page.waitForFunction(
                """
                before => {
                  const bounds = document.querySelector('[data-testid="auto-test-workspace"]')
                    .getBoundingClientRect();
                  return Math.abs(bounds.left - before.left) > 20
                    && Math.abs(bounds.top - before.top) > 20;
                }
                """,
                Map.of("left", leftBefore, "top", topBefore));

        page.setViewportSize(700, 900);
        page.waitForFunction(
                """
                () => {
                  const bounds = document.querySelector('[data-testid="auto-test-workspace"]')
                    .getBoundingClientRect();
                  return bounds.left >= 0 && bounds.right <= window.innerWidth + 1
                    && bounds.top >= 0 && bounds.top < window.innerHeight;
                }
                """);
        assertTrue(panel.isVisible());
    }

    private static void awaitRowsMatchingCatalogFilter(Page page) {
        page.waitForFunction(
                """
                () => {
                  const root = document.querySelector('[data-testid="auto-test-workspace"]');
                  const project = root.querySelector('[aria-label="Filter by repository"]').value;
                  const kind = root.querySelector('[aria-label="Filter by test type"]').value;
                  const safety = root.querySelector('[aria-label="Filter by safety"]').value;
                  const query = root.querySelector('[aria-label="Find tests"]').value.trim().toLowerCase();
                  const expected = window.__arCatalog.tests.filter(entry => {
                    if (project !== 'ALL' && entry.project !== project) return false;
                    if (kind !== 'ALL' && entry.kind !== kind) return false;
                    if (safety !== 'ALL' && entry.safety !== safety) return false;
                    if (!query) return true;
                    return [entry.displayName, entry.name, entry.suite, entry.framework, entry.runtime,
                      entry.sourcePath, ...entry.tags]
                      .some(value => String(value).toLowerCase().includes(query));
                  }).length;
                  return root.querySelectorAll('tbody tr').length === expected;
                }
                """);
    }

    private static void awaitCatalogRows(Page page, int expected) {
        page.waitForFunction(
                """
                expected => document.querySelectorAll(
                  '[data-testid="auto-test-workspace"] tbody tr'
                ).length === expected
                """,
                expected);
    }

    private static JsonObject requestBodyAfterClick(Page page, Locator control, String type) {
        int before = requestCount(page, type);
        control.scrollIntoViewIfNeeded();
        control.click();
        awaitRequestCount(page, type, before + 1);
        String requestJson = (String) page.evaluate(
                """
                type => JSON.stringify(window.__arDashboardRequests
                  .filter(request => request.type === type).at(-1))
                """,
                type);
        JsonObject envelope = JsonParser.parseString(requestJson).getAsJsonObject();
        assertEquals(type, envelope.get("type").getAsString());
        assertEquals(DASHBOARD_SESSION, envelope.get("sessionId").getAsString());
        return JsonParser.parseString(envelope.get("body").getAsString()).getAsJsonObject();
    }

    private static int requestCount(Page page, String type) {
        Number count = (Number) page.evaluate(
                "type => window.__arDashboardRequests.filter(request => request.type === type).length",
                type);
        return count.intValue();
    }

    private static void awaitRequestCount(Page page, String type, int expected) {
        page.waitForFunction(
                """
                expected => window.__arDashboardRequests
                  .filter(request => request.type === expected.type).length >= expected.count
                """,
                Map.of("type", type, "count", expected));
    }

    private static Locator dashboardRow(Page page, int id) {
        return page.locator("table tbody tr:has(td[title='" + id + "'])").first();
    }

    private static Locator button(Page page, String text) {
        return page.locator("button:has-text(\"" + text + "\")").first();
    }

    private static Locator button(Locator owner, String text) {
        return owner.locator("button:has-text(\"" + text + "\")").first();
    }

    private static JsonObject readCatalog() throws IOException {
        assertTrue(Files.isRegularFile(CATALOG_PATH), "Missing packaged automation-tests.json");
        JsonObject catalog = JsonParser.parseString(
                        Files.readString(CATALOG_PATH, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertTrue(catalog.getAsJsonArray("tests").size() > 0, "Automation catalog is empty");
        return catalog;
    }

    private static void assertDashboardBuildIsDeployed() throws IOException {
        Path manifestPath = BUILD_ROOT.resolve("asset-manifest.json");
        assertTrue(Files.isRegularFile(manifestPath), "Missing deployed React asset-manifest.json");
        JsonObject manifest = JsonParser.parseString(
                        Files.readString(manifestPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject files = manifest.getAsJsonObject("files");
        assertNotNull(files, "Deployed React manifest has no files object");
        String mainJs = files.get("main.js").getAsString().replaceFirst("^\\./", "");
        Path bundlePath = BUILD_ROOT.resolve(
                        mainJs.replace('/', java.io.File.separatorChar))
                .normalize();
        assertTrue(bundlePath.startsWith(BUILD_ROOT) && Files.isRegularFile(bundlePath));
        String bundle = Files.readString(bundlePath, StandardCharsets.UTF_8);
        for (String marker : List.of(
                "Open user menu", "automationTests.list", "Auto Test automation catalog")) {
            assertTrue(
                    bundle.contains(marker),
                    "The deployed React build is stale (missing '" + marker
                            + "'). Build abr-react-ts-grid and clean-deploy it before running this test.");
        }
    }

    private void serveBuildFile(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String requestPath = exchange.getRequestURI().getPath();
            if (requestPath == null || requestPath.isBlank() || "/".equals(requestPath)) {
                requestPath = "/index.html";
            }
            Path file = BUILD_ROOT.resolve(requestPath.substring(1)).normalize();
            if (!file.startsWith(BUILD_ROOT) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(file));
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        } finally {
            exchange.close();
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json") || name.endsWith(".map")) {
            return "application/json; charset=utf-8";
        }
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static Path locateChromeExecutable() {
        return PlaywrightTestSupport.locateBrowserExecutable()
                .orElseThrow(() -> new IllegalStateException(
                        "Chrome is required for this deterministic Playwright test; "
                                + "set CHROME_EXECUTABLE_PATH"));
    }
}
