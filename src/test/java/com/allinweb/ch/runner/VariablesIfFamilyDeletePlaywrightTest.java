package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.driver.PlaywrightTestSupport;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Deployed-React Playwright regression for Variables Page IF-family deletion.
 *
 * <p>The fixture is intentionally Bot Job {@code 1}, Block {@code 1}. It uses a mocked
 * authoritative WebSocket snapshot and never opens or mutates the user's database. Selecting any
 * structural boundary must submit the complete IF-family boundary set while preserving every
 * positional body command.
 */
class VariablesIfFamilyDeletePlaywrightTest {

    private static final int BOT_JOB_ID = 1;
    private static final int BLOCK_ID = 1;
    private static final List<Integer> FAMILY_BOUNDARY_IDS = List.of(101, 103, 105, 107);
    private static final List<Integer> BODY_COMMAND_IDS = List.of(102, 104, 106);
    private static final Path BUILD_ROOT =
            Path.of("src", "main", "resources", "build").toAbsolutePath().normalize();

    private static final String WEBSOCKET_MOCK = """
            (() => {
              const clone = value => JSON.parse(JSON.stringify(value));
              const revision = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
              window.__arIfFamilyRequests = [];
              window.__arIfFamilySnapshot = {
                ok: true,
                message: 'Bot Job 1 IF-family fixture loaded.',
                requestId: '',
                bindingEpoch: 'if-family-binding-1',
                workspaceEpoch: 1,
                graphRevision: revision,
                botJob: {
                  id: 1,
                  name: 'Bot Job 1',
                  homeBankingId: 1,
                  organizationName: 'Playwright Test Organization'
                },
                summary: {
                  variableCount: 0,
                  producerCount: 0,
                  consumerCount: 0,
                  literalAssignmentCount: 0,
                  warningCount: 0,
                  unusedCount: 0
                },
                blocks: [{ id: 1, order: 1, name: 'Block 1', active: true }],
                commands: [
                  command(101, 1, 'IF', 'IF', 101),
                  command(102, 2, 'IF body command', 'CLICK', null),
                  command(103, 3, 'ELSEIF', 'ELSEIF', 101),
                  command(104, 4, 'ELSEIF body command', 'CLICK', null),
                  command(105, 5, 'ELSE', 'ELSE', 101),
                  command(106, 6, 'ELSE body command', 'CLICK', null),
                  command(107, 7, 'ENDIF', 'ENDIF', 101)
                ],
                variables: [],
                edges: [],
                diagnostics: [],
                runtimeMemory: { revision: 0, variables: [] },
                mutationCapability: {
                  enabled: true,
                  contractVersion: 3,
                  profile: 'VARIABLES_INDIVIDUAL_ROW_V1',
                  crossBlockProfile: null,
                  reactAuthoredProfile: 'VARIABLES_REACT_AUTHORED_V1',
                  graphVersion: 1,
                  graphRevision: revision,
                  ownerAssertion: {
                    workspaceKind: 'BOT_JOB',
                    homeBankingId: 1,
                    botJobId: 1
                  },
                  layoutRows: [
                    layout(101, 1), layout(102, 2), layout(103, 3), layout(104, 4),
                    layout(105, 5), layout(106, 6), layout(107, 7)
                  ],
                  instructionFacts: [
                    fact(101, 1, 'IF', 101),
                    fact(102, 2, 'CLICK', null),
                    fact(103, 3, 'ELSEIF', 101),
                    fact(104, 4, 'CLICK', null),
                    fact(105, 5, 'ELSE', 101),
                    fact(106, 6, 'CLICK', null),
                    fact(107, 7, 'ENDIF', 101)
                  ]
                }
              };

              function command(id, order, name, action, parentId) {
                return {
                  id,
                  name,
                  command: action,
                  operation: action,
                  blockId: 1,
                  blockName: 'Block 1',
                  blockOrder: 1,
                  instructionOrder: order,
                  parentId,
                  parentBlockId: parentId == null ? null : 1,
                  variableId: null,
                  active: true,
                  blockActive: true
                };
              }

              function layout(instructionId, instructionOrderNumber) {
                return {
                  instructionId,
                  blockId: 1,
                  blockOrderNumber: 1,
                  instructionOrderNumber
                };
              }

              function fact(instructionId, instructionOrderNumber, action, parentId) {
                return {
                  ...layout(instructionId, instructionOrderNumber),
                  action,
                  relationKind: action === 'CLICK' ? 'ELEMENT_TARGET' : 'CONDITIONAL_ROOT',
                  parentId,
                  parentBlockId: parentId == null ? null : 1,
                  variableId: null
                };
              }

              const parseBody = envelope => {
                if (!envelope || envelope.body == null) return {};
                return typeof envelope.body === 'string'
                  ? JSON.parse(envelope.body)
                  : envelope.body;
              };

              const emit = (socket, operationId, body) => {
                const envelope = {
                  sessionId: 'variablesManager',
                  homeBankingId: 1,
                  operationId,
                  body: JSON.stringify(body)
                };
                setTimeout(() => {
                  if (socket.readyState === MockWebSocket.OPEN
                    && typeof socket.onmessage === 'function') {
                    socket.onmessage({ data: JSON.stringify(envelope) });
                  }
                }, 0);
              };

              class MockWebSocket {
                static CONNECTING = 0;
                static OPEN = 1;
                static CLOSING = 2;
                static CLOSED = 3;

                constructor(url) {
                  this.url = String(url);
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
                  window.__arIfFamilyRequests.push(clone(envelope));
                  if (envelope.type === 'variablesWorkspace.bootstrap'
                    || envelope.type === 'variablesWorkspace.refresh') {
                    emit(this,
                      envelope.type === 'variablesWorkspace.bootstrap'
                        ? 'variablesWorkspace.bootstrapResponse'
                        : 'variablesWorkspace.refreshResponse',
                      {
                        ...clone(window.__arIfFamilySnapshot),
                        requestId: body.requestId
                      });
                  }
                }

                close(code = 1000, reason = '') {
                  if (this.readyState === MockWebSocket.CLOSED) return;
                  this.readyState = MockWebSocket.CLOSED;
                  if (typeof this.onclose === 'function') this.onclose({ code, reason });
                }

                addEventListener(type, listener) {
                  this['on' + type] = listener;
                }

                removeEventListener(type, listener) {
                  if (this['on' + type] === listener) this['on' + type] = null;
                }
              }

              window.WebSocket = MockWebSocket;
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
            Thread thread = new Thread(runnable, "variables-if-family-playwright-http");
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
    void deletingAnyBoundarySubmitsTheCompleteIfFamilyAndPreservesBodyCommands() throws Exception {
        assertDeployedVariablesBuild();
        Path chromeExecutable = PlaywrightTestSupport.locateBrowserExecutable()
                .orElseThrow(() -> new IllegalStateException(
                        "Chrome is required; set CHROME_EXECUTABLE_PATH."));

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(
                Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setExecutablePath(chromeExecutable))) {
            for (int selectedBoundaryId : FAMILY_BOUNDARY_IDS) {
                verifyBoundaryDeletionRequest(browser, selectedBoundaryId);
            }
        }
    }

    private void verifyBoundaryDeletionRequest(Browser browser, int selectedBoundaryId) {
        List<String> pageErrors = new ArrayList<>();
        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
            context.addInitScript(WEBSOCKET_MOCK);
            Page page = context.newPage();
            page.setDefaultTimeout(10_000);
            page.onPageError(pageErrors::add);
            page.navigate(baseUrl + "/?desktopShell=1&openWorkspace=variablesManager");

            page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
                            new Page.GetByRoleOptions().setName("Variables").setExact(true))
                    .waitFor();
            page.getByText(
                            "Bot Job ID " + BOT_JOB_ID)
                    .first()
                    .waitFor();
            page.getByText(
                            "Block " + BLOCK_ID,
                            new Page.GetByTextOptions().setExact(true))
                    .first()
                    .waitFor();

            String selectedName = switch (selectedBoundaryId) {
                case 101 -> "IF";
                case 103 -> "ELSEIF";
                case 105 -> "ELSE";
                case 107 -> "ENDIF";
                default -> throw new IllegalArgumentException("Unexpected boundary ID");
            };
            page.getByLabel("Delete command " + selectedName + " ID " + selectedBoundaryId).click();
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("Confirm"))
                    .click();

            page.waitForFunction(
                    """
                    () => window.__arIfFamilyRequests.some(
                      request => request.type === 'variablesWorkspace.commands.delete')
                    """);
            String requestJson = (String) page.evaluate(
                    """
                    () => JSON.stringify(window.__arIfFamilyRequests
                      .filter(request => request.type === 'variablesWorkspace.commands.delete')
                      .at(-1))
                    """);
            JsonObject envelope = JsonParser.parseString(requestJson).getAsJsonObject();
            JsonObject body = JsonParser.parseString(
                            envelope.get("body").getAsString())
                    .getAsJsonObject();

            assertEquals(BLOCK_ID, body.get("expectedBlockId").getAsInt());
            assertEquals(selectedBoundaryId, body.get("instructionId").getAsInt());
            assertTrue(
                    body.has("familyDeleteInstructionIds"),
                    "Variables Page must submit the complete IF-family delete set, not one instruction.");

            JsonArray submittedIds = body.getAsJsonArray("familyDeleteInstructionIds");
            List<Integer> companionIds = new ArrayList<>();
            submittedIds.forEach(value -> companionIds.add(value.getAsInt()));
            List<Integer> expectedCompanionIds = FAMILY_BOUNDARY_IDS.stream()
                    .filter(id -> id != selectedBoundaryId)
                    .toList();
            assertEquals(
                    expectedCompanionIds,
                    companionIds,
                    "The family field must contain every boundary except the selected source.");
            List<Integer> completeDeleteIds = new ArrayList<>();
            completeDeleteIds.add(selectedBoundaryId);
            completeDeleteIds.addAll(companionIds);
            completeDeleteIds.sort(Integer::compareTo);
            assertEquals(
                    FAMILY_BOUNDARY_IDS,
                    completeDeleteIds,
                    "Source plus companions must select IF, every ELSEIF, ELSE, and ENDIF.");
            for (Integer bodyCommandId : BODY_COMMAND_IDS) {
                assertFalse(
                        completeDeleteIds.contains(bodyCommandId),
                        "Positional IF-family body command " + bodyCommandId + " must survive.");
            }
            assertTrue(pageErrors.isEmpty(), "Unexpected browser errors: " + pageErrors);
        }
    }

    private static void assertDeployedVariablesBuild() throws IOException {
        Path manifestPath = BUILD_ROOT.resolve("asset-manifest.json");
        assertTrue(Files.isRegularFile(manifestPath), "Missing deployed React asset manifest.");
        JsonObject manifest = JsonParser.parseString(
                        Files.readString(manifestPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject files = manifest.getAsJsonObject("files");
        assertNotNull(files, "Deployed React manifest has no files object.");
        String mainJs = files.get("main.js").getAsString().replaceFirst("^\\./", "");
        Path bundlePath = BUILD_ROOT.resolve(
                        mainJs.replace('/', java.io.File.separatorChar))
                .normalize();
        assertTrue(bundlePath.startsWith(BUILD_ROOT) && Files.isRegularFile(bundlePath));
        String bundle = Files.readString(bundlePath, StandardCharsets.UTF_8);
        for (String marker : List.of(
                "variablesWorkspace.commands.delete",
                "All Bot Job commands by block")) {
            assertTrue(
                    bundle.contains(marker),
                    "The deployed React build is stale (missing '" + marker + "').");
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
}
