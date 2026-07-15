package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
 * Deterministic headless browser contract for the deployed React Bot Job Details toolbar.
 *
 * <p>The tracked {@code src/main/resources/build} directory is served from an ephemeral loopback
 * HTTP server. A browser-init script replaces {@code WebSocket} before any application JavaScript
 * runs, records requests, returns correlated responses, and publishes authoritative state updates.
 * No application backend, production database, production configuration, or native chooser is
 * opened by this test.
 */
class BotJobDetailsToolbarPlaywrightTest {

    private static final int BOT_JOB_ID = 42;
    private static final int HOME_BANKING_ID = 7;
    private static final int SOCKET_PORT = 54525;
    private static final String SESSION_ID = ScannerWorkspaceSessions.BOT_JOB_TASKS;
    private static final String SELECTED_TRANSFER_PATH = "C:/ARWeb/TestTransfer";
    private static final Path BUILD_ROOT =
            Path.of("src", "main", "resources", "build").toAbsolutePath().normalize();

    private static final String WEBSOCKET_MOCK = """
            (() => {
              const clone = (value) => JSON.parse(JSON.stringify(value));
              window.__arToolbarRequests = [];
              window.__arConfirmMessages = [];
              window.__arBotJobState = {
                revision: 1,
                metadataRevision: 3,
                botJobId: 42,
                name: 'Payments',
                description: 'Deterministic payment flow',
                projectType: 'Web App',
                active: true,
                homeBankingId: 7,
                organizationName: 'Test Bank',
                homeUrlId: 8,
                environmentName: 'TEST',
                environmentUrl: 'https://test.example',
                navigationTimeSeconds: 2,
                transferPathConfigured: true,
                environments: [
                  { id: 8, name: 'TEST', url: 'https://test.example', homeBankingId: 7,
                    organizationName: 'Test Bank' },
                  { id: 9, name: 'QA', url: 'https://qa.example', homeBankingId: 7,
                    organizationName: 'Test Bank' }
                ],
                blocks: [
                  { id: 91, order: 1, name: 'Login', description: 'Authenticate', typeId: 1,
                    active: true, waitSeconds: 0 },
                  { id: 92, order: 2, name: 'Payment', description: 'Create payment', typeId: 1,
                    active: true, waitSeconds: 1 }
                ],
                capabilities: {
                  canUseWorkspaceActions: true,
                  canEditMetadata: true,
                  canUsePreScan: true,
                  canShowComponents: true,
                  canExecute: true,
                  canLaunch: true,
                  canUseFileActions: true,
                  canOpenOrganizations: true
                },
                executionState: 'IDLE',
                activeSurface: 'botJob',
                componentsVisible: false
              };

              window.confirm = (message) => {
                window.__arConfirmMessages.push(String(message));
                return true;
              };

              const parseBody = (envelope) => {
                if (!envelope || envelope.body == null) return {};
                return typeof envelope.body === 'string' ? JSON.parse(envelope.body) : envelope.body;
              };

              const emit = (socket, operationId, body) => {
                const envelope = {
                  sessionId: socket.sessionId,
                  homeBankingId: 7,
                  operationId,
                  body: JSON.stringify(body)
                };
                setTimeout(() => {
                  if (socket.readyState === MockWebSocket.OPEN && typeof socket.onmessage === 'function') {
                    socket.onmessage({ data: JSON.stringify(envelope) });
                  }
                }, 0);
              };

              const publishState = (socket, requestId, message) => {
                emit(socket, 'botJobDetails.state', {
                  ok: true,
                  message,
                  requestId,
                  botJobId: 42,
                  state: clone(window.__arBotJobState)
                });
              };

              class MockWebSocket {
                constructor(url) {
                  this.url = String(url);
                  this.sessionId = new URL(this.url).searchParams.get('sessionId') || '%s';
                  this.readyState = MockWebSocket.CONNECTING;
                  this.onopen = null;
                  this.onmessage = null;
                  this.onerror = null;
                  this.onclose = null;
                  queueMicrotask(() => {
                    if (this.readyState !== MockWebSocket.CONNECTING) return;
                    this.readyState = MockWebSocket.OPEN;
                    if (typeof this.onopen === 'function') this.onopen({ type: 'open' });
                  });
                }

                send(rawMessage) {
                  if (typeof rawMessage === 'string' && rawMessage.startsWith('ping-')) return;
                  const envelope = JSON.parse(String(rawMessage));
                  const body = parseBody(envelope);
                  window.__arToolbarRequests.push(clone(envelope));

                  if (envelope.type === 'botJobDetails.bootstrap') {
                    emit(this, 'botJobDetails.bootstrapResponse', {
                      ok: true,
                      message: 'Bot Job Details loaded',
                      requestId: body.requestId,
                      botJobId: 42,
                      state: clone(window.__arBotJobState),
                      errorCode: null,
                      fieldErrors: {}
                    });
                    return;
                  }

                  if (envelope.type === 'botJobDetails.metadata.update') {
                    window.__arBotJobState.revision += 1;
                    window.__arBotJobState.metadataRevision += 1;
                    window.__arBotJobState.name = body.name;
                    window.__arBotJobState.description = body.description;
                    window.__arBotJobState.homeUrlId = body.homeUrlId;
                    const selected = window.__arBotJobState.environments.find(
                      (environment) => environment.id === body.homeUrlId);
                    window.__arBotJobState.environmentName = selected?.name || '';
                    window.__arBotJobState.environmentUrl = selected?.url || '';
                    emit(this, 'botJobDetails.metadata.updateResponse', {
                      ok: true,
                      message: 'Bot Job metadata updated',
                      requestId: body.requestId,
                      botJobId: 42,
                      state: clone(window.__arBotJobState),
                      errorCode: null,
                      fieldErrors: {}
                    });
                    return;
                  }

                  if (envelope.type !== 'botJobDetails.toolbar.action') return;
                  const action = body.action;
                  const response = {
                    ok: true,
                    action,
                    message: action + ' completed',
                    requestId: body.requestId,
                    botJobId: 42
                  };
                  if (action === 'CHOOSE_TRANSFER_PATH') {
                    response.selectedPath = 'C:/ARWeb/TestTransfer';
                  }
                  emit(this, 'botJobDetails.toolbar.actionResponse', response);

                  window.__arBotJobState.revision += 1;
                  if (action === 'SET_NAVIGATION_TIME') {
                    window.__arBotJobState.navigationTimeSeconds = body.navigationTimeSeconds;
                  } else if (action === 'TEST_RUN') {
                    window.__arBotJobState.executionState = 'RUNNING';
                  } else if (action === 'STOP_TEST_RUN') {
                    window.__arBotJobState.executionState = 'INTERRUPTED';
                  }
                  publishState(this, body.requestId, action + ' state');
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
            """.formatted(SESSION_ID);

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;

    @BeforeEach
    void serveDeployedReactBuild() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serveBuildFile);
        serverExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "bot-job-toolbar-playwright-http");
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
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void rendersAndOperatesTheCompleteBotJobDetailsToolbarContract() throws Exception {
        assertToolbarBuildIsDeployed();
        List<String> pageErrors = new CopyOnWriteArrayList<>();
        List<String> uiContractFailures = new CopyOnWriteArrayList<>();

        Path chromeExecutable = locateChromeExecutable();
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(
                Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")))) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setExecutablePath(chromeExecutable));
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1440, 1100));
            context.addInitScript(WEBSOCKET_MOCK);
            Page page = context.newPage();
            page.setDefaultTimeout(10_000);
            page.onPageError(pageErrors::add);

            try {
                page.navigate(baseUrl + "/");
                page.waitForFunction("() => typeof window.receiveDataFromJava === 'function'");
                page.evaluate(
                        "args => window.receiveDataFromJava(...args)",
                        List.of(
                                "[]",
                                SOCKET_PORT,
                                SESSION_ID,
                                HOME_BANKING_ID,
                                "Test Bank",
                                BOT_JOB_ID,
                                "Payments"));

                page.getByLabel("Starting block").waitFor();
                page.locator("[role='group'][aria-label='Job files']").waitFor();
                button(page, "Export").waitFor();
                button(page, "Import").waitFor();
                page.getByText("IDLE", new Page.GetByTextOptions().setExact(true)).waitFor();

                coverJobFileButtons(page);
                coverNavigationAndExecution(page);
                coverTransferActions(page);
                coverSemanticMetadataForm(page, uiContractFailures);
                coverResponsiveLayout(page);

                @SuppressWarnings("unchecked")
                List<String> blockedControls =
                        (List<String>) page.evaluate("() => window.__arBlockedToolbarControls || []");
                pageErrors.forEach(error -> uiContractFailures.add("Browser page error: " + error));
                blockedControls.forEach(
                        action -> uiContractFailures.add("Control is covered by another layer: " + action));
                assertTrue(
                        uiContractFailures.isEmpty(),
                        "Bot Job Details UI contract failures: " + uiContractFailures);
            } finally {
                context.close();
                browser.close();
            }
        }
    }

    private void coverJobFileButtons(Page page) {
        Locator excel = button(page, "Excel");
        Locator generate = button(page, "Generate");
        Locator report = button(page, "Report");
        assertTrue(excel.isVisible());
        assertTrue(generate.isVisible());
        assertTrue(report.isVisible());

        assertEquals("OPEN_EXCEL", toolbarBodyAfterClick(page, excel, "OPEN_EXCEL").get("action").getAsString());
        awaitToolbarIdle(page);

        JsonObject generateBody = toolbarBodyAfterClick(page, generate, "GENERATE_EXCEL");
        assertTrue(generateBody.get("confirmed").getAsBoolean());
        assertConfirmContains(page, "Generate the Excel file");
        awaitToolbarIdle(page);

        assertEquals("OPEN_REPORT", toolbarBodyAfterClick(page, report, "OPEN_REPORT").get("action").getAsString());
        awaitToolbarIdle(page);

        assertEquals(
                "CREATE_BAT",
                toolbarBodyAfterClick(page, page.locator("[aria-label='Create BAT']"), "CREATE_BAT")
                        .get("action")
                        .getAsString());
        awaitToolbarIdle(page);
    }

    private void coverNavigationAndExecution(Page page) {
        Locator navigation = page.locator("[aria-label='Navigation time: 2 seconds']");
        assertTrue(navigation.isVisible());
        JsonObject navigationBody = toolbarBodyAfterClick(page, navigation, "SET_NAVIGATION_TIME");
        assertEquals(3, navigationBody.get("navigationTimeSeconds").getAsInt());
        awaitToolbarIdle(page);
        page.locator("[aria-label='Navigation time: 3 seconds']").waitFor();

        assertEquals(
                "LAUNCH",
                toolbarBodyAfterClick(page, button(page, "Launch"), "LAUNCH")
                        .get("action")
                        .getAsString());
        awaitToolbarIdle(page);

        assertEquals(
                "REFRESH_BLOCKS",
                toolbarBodyAfterClick(
                                page,
                                page.locator("[aria-label='Reload blocks']"),
                                "REFRESH_BLOCKS")
                        .get("action")
                        .getAsString());
        awaitToolbarIdle(page);

        Locator blockSelect = page.getByLabel("Starting block");
        List<String> optionLabels = blockSelect.locator("option").allTextContents();
        assertFalse(optionLabels.isEmpty());
        assertEquals("Execute All", optionLabels.get(0));
        assertEquals("all", blockSelect.inputValue());

        Locator allMode = page.locator("[aria-label='Execution mode: ALL']");
        assertTrue(allMode.isVisible());
        assertTrue(allMode.isDisabled(), "Execute All must force and lock ALL mode");
        assertEquals("rgb(22, 128, 63)", computedBackground(allMode));

        JsonObject executeAll = toolbarBodyAfterClick(page, button(page, "Test run"), "TEST_RUN");
        assertEquals("ALL", executeAll.get("executionMode").getAsString());
        assertEquals(0, executeAll.get("blockId").getAsInt());
        page.getByText("RUNNING", new Page.GetByTextOptions().setExact(true)).waitFor();

        Locator stop = button(page, "Stop");
        assertFalse(stop.isDisabled());
        toolbarBodyAfterClick(page, stop, "STOP_TEST_RUN");
        awaitToolbarIdle(page);
        page.getByText("INTERRUPTED", new Page.GetByTextOptions().setExact(true)).waitFor();
        assertTrue(stop.isDisabled());

        blockSelect.selectOption("91");
        Locator numberedAll = page.locator("[aria-label='Execution mode: ALL']");
        assertFalse(numberedAll.isDisabled());
        numberedAll.click();
        Locator oneMode = page.locator("[aria-label='Execution mode: ONE']");
        oneMode.waitFor();
        assertEquals("rgb(232, 121, 27)", computedBackground(oneMode));
        assertEquals("true", oneMode.getAttribute("aria-pressed"));

        JsonObject executeOne = toolbarBodyAfterClick(page, button(page, "Test run"), "TEST_RUN");
        assertEquals("ONE", executeOne.get("executionMode").getAsString());
        assertEquals(91, executeOne.get("blockId").getAsInt());
        page.getByText("RUNNING", new Page.GetByTextOptions().setExact(true)).waitFor();
        toolbarBodyAfterClick(page, button(page, "Stop"), "STOP_TEST_RUN");
        awaitToolbarIdle(page);
        page.getByText("INTERRUPTED", new Page.GetByTextOptions().setExact(true)).waitFor();
    }

    private void coverTransferActions(Page page) {
        button(page, "Export").click();
        page.locator("section[aria-label='Export Bot Job']").waitFor();
        Locator transferPath = page.locator("#bot-job-transfer-path");
        assertTrue((Boolean) transferPath.evaluate("element => element.readOnly"));

        JsonObject choose = toolbarBodyAfterClick(
                page, page.locator("[aria-label='Choose transfer folder']"), "CHOOSE_TRANSFER_PATH");
        assertEquals("CHOOSE_TRANSFER_PATH", choose.get("action").getAsString());
        awaitToolbarIdle(page);
        page.waitForFunction(
                "expected => document.querySelector('#bot-job-transfer-path').value === expected",
                SELECTED_TRANSFER_PATH);
        assertEquals(SELECTED_TRANSFER_PATH, transferPath.inputValue());

        JsonObject exportBody = toolbarBodyAfterClick(
                page, page.locator("[aria-label='Confirm export']"), "EXPORT_JOB");
        assertEquals(SELECTED_TRANSFER_PATH, exportBody.get("transferPath").getAsString());
        assertTrue(exportBody.get("confirmed").getAsBoolean());
        awaitToolbarIdle(page);

        button(page, "Import").click();
        page.locator("section[aria-label='Import Bot Job']").waitFor();
        Locator restoreDate = page.getByLabel("Restore date");
        restoreDate.fill("2026-07-12");
        assertEquals(SELECTED_TRANSFER_PATH, page.locator("#bot-job-transfer-path").inputValue());

        JsonObject importBody = toolbarBodyAfterClick(
                page, page.locator("[aria-label='Confirm import']"), "IMPORT_JOB");
        assertEquals(SELECTED_TRANSFER_PATH, importBody.get("transferPath").getAsString());
        assertEquals("2026-07-12", importBody.get("restoreDate").getAsString());
        assertTrue(importBody.get("confirmed").getAsBoolean());
        awaitToolbarIdle(page);
    }

    private void coverSemanticMetadataForm(Page page, List<String> uiContractFailures) {
        Locator edit = button(page, "Edit");
        if (edit.count() == 0 || !edit.isVisible()) {
            uiContractFailures.add("Bot Job metadata editor entry point 'Edit' is missing");
            return;
        }
        edit.click();

        Locator name = page.getByLabel("Bot Job name");
        Locator projectType = page.getByLabel("Project type");
        Locator organization = page.getByLabel("Organization");
        Locator description = page.getByLabel("Description");
        Locator environment = page.getByLabel("Environment");
        Locator selectedUrl = page.getByLabel("Selected URL");
        name.waitFor();

        assertEquals("INPUT", tagName(name));
        assertEquals("Payments", name.inputValue());
        assertTrue((Boolean) name.evaluate("element => Boolean(element.form)"));
        assertEquals("INPUT", tagName(projectType));
        assertTrue((Boolean) projectType.evaluate("element => element.readOnly"));
        assertEquals("Web App", projectType.inputValue());
        assertTrue((Boolean) organization.evaluate("element => element.readOnly"));
        assertEquals("Test Bank", organization.inputValue());
        assertEquals("TEXTAREA", tagName(description));
        assertEquals("Deterministic payment flow", description.inputValue());
        assertEquals("SELECT", tagName(environment));
        assertEquals("8", environment.inputValue());
        assertTrue((Boolean) selectedUrl.evaluate("element => element.readOnly"));
        assertEquals("https://test.example", selectedUrl.inputValue());
        assertEquals("submit", button(page, "Save").getAttribute("type"));

        name.fill("Payments QA");
        description.fill("Updated deterministic flow");
        environment.selectOption("9");
        assertEquals("https://qa.example", selectedUrl.inputValue());
        button(page, "Save").click();
        button(page, "Edit").waitFor();
        Locator metadataPanel = page.locator("section[aria-label='Bot Job metadata']");
        assertTrue(metadataPanel.getByText(
                "Payments QA", new Locator.GetByTextOptions().setExact(true)).isVisible());
        assertTrue(metadataPanel.getByText(
                "Updated deterministic flow", new Locator.GetByTextOptions().setExact(true)).isVisible());
    }

    private void coverResponsiveLayout(Page page) {
        page.setViewportSize(600, 1000);
        for (Locator header : List.of(
                page.locator("header:has([aria-label='Starting block'])"),
                page.locator("header:has([aria-label='Job files'])"))) {
            assertTrue(header.isVisible());
            assertTrue(
                    (Boolean) header.evaluate("element => element.scrollWidth <= element.clientWidth + 1"),
                    "Bot Job header overflows its mobile layout");
        }
        assertTrue(button(page, "Test run").isVisible());
        assertTrue(button(page, "Import").isVisible());
    }

    private static JsonObject toolbarBodyAfterClick(Page page, Locator control, String action) {
        int before = toolbarRequestCount(page, action);
        control.scrollIntoViewIfNeeded();
        Boolean pointerReachable = (Boolean) control.evaluate(
                """
                element => {
                  const bounds = element.getBoundingClientRect();
                  const target = document.elementFromPoint(
                    bounds.left + bounds.width / 2,
                    bounds.top + bounds.height / 2
                  );
                  return target === element || element.contains(target);
                }
                """);
        if (Boolean.TRUE.equals(pointerReachable)) {
            control.click();
        } else {
            page.evaluate(
                    """
                    action => {
                      window.__arBlockedToolbarControls ||= [];
                      window.__arBlockedToolbarControls.push(action);
                    }
                    """,
                    action);
            control.dispatchEvent("click");
        }
        page.waitForFunction(
                """
                expected => window.__arToolbarRequests.filter((request) => {
                  if (request.type !== 'botJobDetails.toolbar.action') return false;
                  const body = typeof request.body === 'string' ? JSON.parse(request.body) : request.body;
                  return body.action === expected.action;
                }).length > expected.before
                """,
                Map.of("action", action, "before", before));
        String requestJson = (String) page.evaluate(
                """
                action => {
                  const requests = window.__arToolbarRequests.filter((request) => {
                    if (request.type !== 'botJobDetails.toolbar.action') return false;
                    const body = typeof request.body === 'string' ? JSON.parse(request.body) : request.body;
                    return body.action === action;
                  });
                  return JSON.stringify(requests[requests.length - 1]);
                }
                """,
                action);
        JsonObject envelope = JsonParser.parseString(requestJson).getAsJsonObject();
        assertEquals("botJobDetails.toolbar.action", envelope.get("type").getAsString());
        assertEquals(SESSION_ID, envelope.get("sessionId").getAsString());
        JsonObject body = JsonParser.parseString(envelope.get("body").getAsString()).getAsJsonObject();
        assertEquals(action, body.get("action").getAsString());
        assertEquals(BOT_JOB_ID, body.get("botJobId").getAsInt());
        assertTrue(body.has("requestId") && !body.get("requestId").getAsString().isBlank());
        return body;
    }

    private static int toolbarRequestCount(Page page, String action) {
        Number count = (Number) page.evaluate(
                """
                action => window.__arToolbarRequests.filter((request) => {
                  if (request.type !== 'botJobDetails.toolbar.action') return false;
                  const body = typeof request.body === 'string' ? JSON.parse(request.body) : request.body;
                  return body.action === action;
                }).length
                """,
                action);
        return count.intValue();
    }

    private static void awaitToolbarIdle(Page page) {
        page.waitForFunction(
                """
                () => [...document.querySelectorAll('button')].some((button) =>
                  button.textContent.includes('Excel') && !button.disabled)
                """);
    }

    private static void assertConfirmContains(Page page, String expectedText) {
        Boolean found = (Boolean) page.evaluate(
                "expected => window.__arConfirmMessages.some((message) => message.includes(expected))",
                expectedText);
        assertTrue(found, "Expected confirmation containing: " + expectedText);
    }

    private static Locator button(Page page, String text) {
        return page.locator("button:has-text(\"" + text + "\")").first();
    }

    private static Locator button(Locator owner, String text) {
        return owner.locator("button:has-text(\"" + text + "\")").first();
    }

    private static String computedBackground(Locator locator) {
        return String.valueOf(locator.evaluate("element => getComputedStyle(element).backgroundColor"));
    }

    private static String tagName(Locator locator) {
        return String.valueOf(locator.evaluate("element => element.tagName"));
    }

    private void assertToolbarBuildIsDeployed() throws IOException {
        Path manifestPath = BUILD_ROOT.resolve("asset-manifest.json");
        assertTrue(Files.isRegularFile(manifestPath), "Missing deployed React asset-manifest.json");
        JsonObject manifest = JsonParser.parseString(Files.readString(manifestPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject files = manifest.getAsJsonObject("files");
        assertNotNull(files, "Deployed React manifest has no files object");
        String mainJs = files.get("main.js").getAsString().replaceFirst("^\\./", "");
        Path bundlePath = BUILD_ROOT.resolve(mainJs.replace('/', java.io.File.separatorChar)).normalize();
        assertTrue(bundlePath.startsWith(BUILD_ROOT) && Files.isRegularFile(bundlePath), "Missing deployed main.js");
        String bundle = Files.readString(bundlePath, StandardCharsets.UTF_8);
        for (String marker : List.of("botJobDetails.toolbar.action", "Execute All", "Job files")) {
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
        if (name.endsWith(".json") || name.endsWith(".map")) return "application/json; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static Path locateChromeExecutable() {
        String override = System.getenv("CHROME_EXECUTABLE_PATH");
        List<Path> candidates = new java.util.ArrayList<>();
        if (override != null && !override.isBlank()) candidates.add(Path.of(override));
        candidates.add(Path.of("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));
        candidates.add(Path.of("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"));
        candidates.add(Path.of("/usr/bin/google-chrome"));
        candidates.add(Path.of("/usr/bin/chromium"));
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Chrome is required for this deterministic Playwright test; set CHROME_EXECUTABLE_PATH"));
    }
}
