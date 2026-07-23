package com.allinweb.ch.driver;

import com.allinweb.ch.facade.PlaywrightActionExecutor;
import com.allinweb.ch.facade.PlaywrightElementScanner;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.ViewportSize;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARPlaywrightDriver {

    private ExecutorService playwrightThread = newPlaywrightThread();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private String activeBrowserType = "";
    /** The page all driver operations currently target. Updated when Playwright opens a new tab. */
    private Page page;
    private final Set<Page> diagnosedPages = Collections.newSetFromMap(new IdentityHashMap<>());
    private final PlaywrightActionExecutor actionExecutor = new PlaywrightActionExecutor();
    private final PlaywrightElementScanner elementScanner = new PlaywrightElementScanner();

    public void open(String browserType, String url, String optionsConfig) {
        run(() -> {
            closeInternal();

            playwright = createPlaywright();
            browser = launchBrowser(browserType, optionsConfig);
            activeBrowserType = canonicalBrowserType(browserType);
            // bypassCSP so injected plugins (hoverPick/actionExecutor) can open their WebSocket back
            // to the Java server on sites with a strict connect-src Content-Security-Policy.
            // bypassCSP so injected plugins can open their WebSocket; null viewport so the page uses
            // the full (maximized) browser window instead of Playwright's default 1280x720 viewport.
            context = browser.newContext(
                    new Browser.NewContextOptions().setBypassCSP(true).setViewportSize(null));
            attachContextTracking();
            page = context.newPage();
            attachDiagnostics(page);
            navigateDomReady(page, url);
            return null;
        });
    }

    public void openOrNavigate(String browserType, String url, String optionsConfig) {
        openOrNavigate(browserType, url, optionsConfig, false);
    }

    public void openOrNavigate(String browserType, String url, String optionsConfig, boolean headless) {
        run(() -> {
            if (page != null && !page.isClosed()) {
                assertBrowserCompatibleInternal(browserType);
                navigateDomReady(page, url);
                return null;
            }

            closeInternal();
            playwright = createPlaywright();
            browser = launchBrowser(browserType, optionsConfig, headless);
            activeBrowserType = canonicalBrowserType(browserType);
            context = browser.newContext(
                    new Browser.NewContextOptions().setBypassCSP(true).setViewportSize(null));
            attachContextTracking();
            page = context.newPage();
            attachDiagnostics(page);
            navigateDomReady(page, url);
            return null;
        });
    }

    public void navigate(String url) {
        run(() -> {
            navigateDomReady(requirePage(), url);
            return null;
        });
    }

    /**
     * Opens a fresh, best-effort read-only diagnostic context for one target origin.
     *
     * <p>No database-derived browser flags are accepted. Service workers and browser-side streaming
     * transports are disabled; only GET/HEAD/OPTIONS requests to the target's exact origin are
     * resumed. Callers must still avoid live clicks because a GET endpoint can itself mutate server
     * state. The context is closed normally through {@link #close()}.
     */
    public void openReadOnlyDiagnostic(String browserType, String url, boolean headless) {
        run(() -> {
            closeInternal();
            URI allowedOrigin = URI.create(url);
            playwright = createPlaywright();
            browser = launchBrowser(browserType, "", headless);
            activeBrowserType = canonicalBrowserType(browserType);
            context = browser.newContext(new Browser.NewContextOptions()
                    .setBypassCSP(false)
                    .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
                    .setViewportSize(null));
            context.addInitScript(
                    """
                    (() => {
                      const blocked = name => class {
                        constructor() { throw new DOMException(name + ' disabled in read-only diagnostics'); }
                      };
                      Object.defineProperty(window, 'WebSocket', { value: blocked('WebSocket'), configurable: false });
                      Object.defineProperty(window, 'EventSource', { value: blocked('EventSource'), configurable: false });
                      if ('WebTransport' in window) {
                        Object.defineProperty(window, 'WebTransport', { value: blocked('WebTransport'), configurable: false });
                      }
                    })();
                    """);
            context.route("**/*", route -> {
                String method = route.request().method().toUpperCase(Locale.ROOT);
                boolean safeMethod = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
                if (safeMethod && sameOrigin(allowedOrigin, route.request().url())) {
                    route.resume();
                } else {
                    log.warn("[pw-diagnostic-guard] blocked {} {}", method, route.request().url());
                    route.abort();
                }
            });
            attachContextTracking();
            page = context.newPage();
            attachDiagnostics(page);
            navigateDomReady(page, url);
            return null;
        });
    }

    private static boolean sameOrigin(URI allowedOrigin, String candidateUrl) {
        try {
            URI candidate = URI.create(candidateUrl);
            return Objects.equals(allowedOrigin.getScheme(), candidate.getScheme())
                    && Objects.equals(allowedOrigin.getHost(), candidate.getHost())
                    && effectivePort(allowedOrigin) == effectivePort(candidate);
        } catch (IllegalArgumentException invalidUrl) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        if ("https".equalsIgnoreCase(uri.getScheme())) return 443;
        if ("http".equalsIgnoreCase(uri.getScheme())) return 80;
        return -1;
    }

    public void goBack() {
        run(() -> {
            requirePage().goBack();
            requirePage().waitForLoadState(LoadState.DOMCONTENTLOADED);
            return null;
        });
    }

    public void setContent(String html) {
        run(() -> {
            requirePage().setContent(html);
            requirePage().waitForLoadState(LoadState.DOMCONTENTLOADED);
            return null;
        });
    }

    public Object evaluate(String script, Object arg) {
        return call(() -> requirePage().evaluate(script, arg));
    }

    public Object evaluate(String script) {
        return call(() -> requirePage().evaluate(script));
    }

    /** Reload the current page (Selenium {@code navigate().refresh()} equivalent). */
    public void reload() {
        run(() -> {
            requirePage().reload();
            requirePage().waitForLoadState(LoadState.DOMCONTENTLOADED);
            return null;
        });
    }

    /** Full serialized HTML of the current page (Selenium {@code getPageSource()} equivalent). */
    public String content() {
        return call(() -> requirePage().content());
    }

    /** Viewport size as {@code [width, height]}, or null if unavailable. */
    public int[] viewportSize() {
        return call(() -> {
            ViewportSize vs = requirePage().viewportSize();
            if (vs != null) return new int[] {vs.width, vs.height};

            // A null Playwright viewport is intentional for visible maximized browsers: it lets
            // the page use the native window size. Still expose the effective dimensions to
            // callers that previously received Selenium's window size.
            Object dimensions = requirePage().evaluate(
                    "() => [window.innerWidth || document.documentElement.clientWidth,"
                            + " window.innerHeight || document.documentElement.clientHeight]");
            if (dimensions instanceof List<?> values && values.size() >= 2
                    && values.get(0) instanceof Number width && values.get(1) instanceof Number height) {
                return new int[] {width.intValue(), height.intValue()};
            }
            return null;
        });
    }

    /**
     * PNG screenshot bytes. {@code fullPage=true} captures the whole scrollable page (replaces the
     * manual scroll-stitch loop), {@code false} captures just the current viewport.
     */
    public byte[] screenshot(boolean fullPage) {
        return call(() -> requirePage().screenshot(new Page.ScreenshotOptions().setFullPage(fullPage)));
    }

    public String currentUrl() {
        return call(() -> requirePage().url());
    }

    public String title() {
        return call(() -> requirePage().title());
    }

    public BrowserElementSnapshot inspectElement(String xPath) {
        if (xPath == null || xPath.isBlank()) {
            return BrowserElementSnapshot.notFound("empty-xpath");
        }
        return call(() -> {
            Object raw = requirePage().evaluate(
                    """
                    xPath => {
                      try {
                        const result = document.evaluate(
                          xPath,
                          document,
                          null,
                          XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
                          null
                        );
                        const element = result.snapshotItem(0);
                        if (!(element instanceof Element)) {
                          return { found: false, matchCount: result.snapshotLength, reason: 'no-match' };
                        }
                        const rect = element.getBoundingClientRect();
                        const style = getComputedStyle(element);
                        return {
                          found: true,
                          matchCount: result.snapshotLength,
                          displayed: rect.width > 0 && rect.height > 0
                            && style.display !== 'none' && style.visibility !== 'hidden',
                          enabled: !('disabled' in element) || !element.disabled,
                          selected: Boolean(element.selected) || Boolean(element.checked)
                            || element.getAttribute('aria-selected') === 'true',
                          x: Math.round(rect.left + window.scrollX),
                          y: Math.round(rect.top + window.scrollY),
                          width: Math.round(rect.width),
                          height: Math.round(rect.height),
                          text: element.innerText || element.textContent || '',
                          outerHtml: element.outerHTML || '',
                          innerHtml: element.innerHTML || '',
                          parentHtml: element.parentElement ? element.parentElement.outerHTML || '' : ''
                        };
                      } catch (error) {
                        return { found: false, matchCount: 0, reason: String(error) };
                      }
                    }
                    """,
                    xPath);
            if (!(raw instanceof Map<?, ?> values)) {
                return BrowserElementSnapshot.notFound("invalid-result");
            }
            return new BrowserElementSnapshot(
                    asBoolean(values.get("found")),
                    asInt(values.get("matchCount")),
                    asBoolean(values.get("displayed")),
                    asBoolean(values.get("enabled")),
                    asBoolean(values.get("selected")),
                    asInt(values.get("x")),
                    asInt(values.get("y")),
                    asInt(values.get("width")),
                    asInt(values.get("height")),
                    asString(values.get("text")),
                    asString(values.get("outerHtml")),
                    asString(values.get("innerHtml")),
                    asString(values.get("parentHtml")),
                    asString(values.get("reason")));
        });
    }

    public List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden) {
        return call(() -> elementScanner.scan(requirePage(), searchTerms, includeHidden));
    }

    /**
     * Best-effort "page settled" wait for JS-heavy sites, so a scan fired right after
     * navigation sees the hydrated DOM instead of the bare DOMContentLoaded tree.
     *
     * <p>Two stages, both bounded by {@code maxWaitMs}:
     *
     * <ol>
     *   <li>Network idle — tolerated on timeout, because sites with long-polling or
     *       analytics beacons never reach idle.
     *   <li>DOM stability probe — samples the total element count every 500 ms until two
     *       consecutive samples match (no mutations for ~1 s), i.e. hydration and lazy
     *       rendering stopped changing the tree.
     * </ol>
     *
     * <p>Cheap when the page is already settled (~1 s for the confirming samples).
     *
     * @return how many milliseconds were actually spent waiting
     */
    public long waitForPageSettled(long maxWaitMs) {
        return call(() -> {
            Page settledPage = requirePage();
            long start = System.currentTimeMillis();
            long deadline = start + Math.max(0, maxWaitMs);

            try {
                double idleBudget = Math.max(1, Math.min(maxWaitMs / 2.0, deadline - System.currentTimeMillis()));
                settledPage.waitForLoadState(
                        LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(idleBudget));
            } catch (TimeoutError ignored) {
                // Long-polling/analytics keep the network busy forever; the DOM probe decides.
            }

            long previousCount = -1;
            int stableSamples = 0;
            while (System.currentTimeMillis() < deadline && stableSamples < 2) {
                long count;
                try {
                    Object result = settledPage.evaluate("() => document.querySelectorAll('*').length");
                    count = result instanceof Number ? ((Number) result).longValue() : -1;
                } catch (RuntimeException evalError) {
                    // Navigation in flight or context destroyed — scan with what we have.
                    break;
                }
                if (count >= 0 && count == previousCount) {
                    stableSamples++;
                } else {
                    stableSamples = 0;
                }
                previousCount = count;
                if (stableSamples < 2 && System.currentTimeMillis() < deadline) {
                    settledPage.waitForTimeout(500);
                }
            }
            return System.currentTimeMillis() - start;
        });
    }

    public boolean click(InstructionLoad instruction) {
        return call(() -> {
            Page actionPage = requirePage();
            List<Page> pagesBefore = openPages();
            boolean clicked = actionExecutor.click(actionPage, instruction);
            if (clicked) adoptPageOpenedByAction(actionPage, pagesBefore, 2000);
            return clicked;
        });
    }

    public boolean clickOnce(InstructionLoad instruction) {
        return call(() -> {
            Page actionPage = requirePage();
            List<Page> pagesBefore = openPages();
            boolean clicked = actionExecutor.clickOnce(actionPage, instruction);
            if (clicked) adoptPageOpenedByAction(actionPage, pagesBefore, 2000);
            return clicked;
        });
    }

    public boolean fill(InstructionLoad instruction, FieldData data) {
        return call(() -> actionExecutor.fill(requirePage(), instruction, data));
    }

    public boolean fillOnce(InstructionLoad instruction, FieldData data) {
        return call(() -> actionExecutor.fillOnce(requirePage(), instruction, data));
    }

    public String text(InstructionLoad instruction) {
        return call(() -> actionExecutor.text(requirePage(), instruction));
    }

    public boolean isOpen() {
        return call(() -> !openPages().isEmpty());
    }

    /** Returns the normalized type of the one live Playwright browser, or an empty value. */
    public String activeBrowserType() {
        return call(() -> openPages().isEmpty() ? "" : activeBrowserType);
    }

    /** Refuses to reuse a browser process launched with a different configured browser type. */
    public void assertBrowserCompatible(String requestedBrowserType) {
        run(() -> {
            assertBrowserCompatibleInternal(requestedBrowserType);
            return null;
        });
    }

    public int pageCount() {
        return call(() -> openPages().size());
    }

    /** Select a zero-based open tab. Intended for future tab controls in the scanner UI. */
    public boolean selectPage(int index) {
        return call(() -> {
            List<Page> pages = openPages();
            if (index < 0 || index >= pages.size()) return false;
            page = pages.get(index);
            page.bringToFront();
            return true;
        });
    }

    /** Selects an adjacent tab, clamped at the first and last open page. */
    public boolean selectPageRelative(int direction) {
        return call(() -> {
            List<Page> pages = openPages();
            if (pages.isEmpty() || direction == 0) return false;
            int currentIndex = pages.indexOf(page);
            if (currentIndex < 0) currentIndex = pages.size() - 1;
            int nextIndex = Math.max(0, Math.min(pages.size() - 1, currentIndex + direction));
            if (nextIndex == currentIndex) return false;
            page = pages.get(nextIndex);
            page.bringToFront();
            return true;
        });
    }

    public boolean selectNewestPage() {
        return call(() -> {
            List<Page> pages = openPages();
            if (pages.isEmpty()) return false;
            page = pages.get(pages.size() - 1);
            page.bringToFront();
            return true;
        });
    }

    private void navigateDomReady(Page targetPage, String url) {
        try {
            targetPage.navigate(
                    url,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60000));
            targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
        } catch (TimeoutError timeout) {
            String current = "";
            try {
                current = targetPage.url();
            } catch (Exception ignored) {
                // Keep the original navigation timeout as the meaningful error.
            }
            if (current != null && !current.isBlank() && !Objects.equals("about:blank", current)) {
                log.warn(
                        "Playwright navigation timed out after reaching '{}'; continuing with current DOM for scanner startup",
                        current);
                return;
            }
            throw timeout;
        }
    }

    public void close() {
        run(() -> {
            closeInternal();
            return null;
        });
    }

    public void shutdown() {
        ExecutorService executor = ensurePlaywrightThread();
        run(() -> {
            closeInternal();
            return null;
        });
        executor.shutdownNow();
    }

    /**
     * Surface the Playwright page's runtime signals into the app log: JS console output, uncaught
     * page errors, and the full WebSocket lifecycle. This is how we diagnose injected plugins
     * (hoverPick/actionExecutor) whose {@code new WebSocket(...)} back to the Java server may fail
     * silently on strict-CSP sites — the socket open/error/close now shows up in the log.
     */
    private void attachDiagnostics(Page p) {
        if (p == null || !diagnosedPages.add(p)) return;
        try {
            p.onConsoleMessage(msg -> log.info("[pw-console] {}: {}", msg.type(), msg.text()));
            p.onPageError(err -> log.warn("[pw-pageerror] {}", err));
            p.onWebSocket(ws -> {
                log.info("[pw-ws] open {}", ws.url());
                ws.onSocketError(e -> log.warn("[pw-ws] error {} : {}", ws.url(), e));
                ws.onClose(w -> log.info("[pw-ws] close {}", ws.url()));
            });
        } catch (Exception e) {
            log.warn("attachDiagnostics failed: {}", e.getMessage());
        }
    }

    private void attachContextTracking() {
        context.onPage(openedPage -> {
            attachDiagnostics(openedPage);
            Page previous = page;
            page = openedPage;
            log.info(
                    "Playwright new tab adopted: previousUrl={} activeUrl={} tabs={}",
                    safeUrl(previous),
                    safeUrl(openedPage),
                    openPages().size());
        });
    }

    private void adoptPageOpenedByAction(Page actionPage, List<Page> pagesBefore, long waitMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, waitMs);
        Page opened = newestPageNotIn(pagesBefore);
        while (opened == null && System.currentTimeMillis() < deadline) {
            try {
                actionPage.waitForTimeout(50);
            } catch (RuntimeException ignored) {
                break;
            }
            opened = newestPageNotIn(pagesBefore);
        }
        if (opened == null) return;

        page = opened;
        attachDiagnostics(opened);
        try {
            opened.waitForLoadState(
                    LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(Math.max(1, waitMs)));
        } catch (TimeoutError timeout) {
            log.warn("New tab did not reach DOMContentLoaded within {} ms: {}", waitMs, safeUrl(opened));
        }
        try {
            opened.bringToFront();
        } catch (RuntimeException frontError) {
            log.debug("Could not bring adopted tab to front: {}", frontError.getMessage());
        }
    }

    private Page newestPageNotIn(List<Page> previousPages) {
        List<Page> pages = openPages();
        for (int index = pages.size() - 1; index >= 0; index--) {
            Page candidate = pages.get(index);
            if (!previousPages.contains(candidate)) return candidate;
        }
        return null;
    }

    private List<Page> openPages() {
        if (context == null) return List.of();
        return context.pages().stream().filter(candidate -> candidate != null && !candidate.isClosed()).toList();
    }

    private static String safeUrl(Page candidate) {
        if (candidate == null || candidate.isClosed()) return "<closed>";
        try {
            return candidate.url();
        } catch (RuntimeException unavailable) {
            return "<unavailable>";
        }
    }

    private Browser launchBrowser(String browserType, String optionsConfig) {
        return launchBrowser(browserType, optionsConfig, false);
    }

    private Browser launchBrowser(String browserType, String optionsConfig, boolean headless) {
        // --start-maximized opens the browser window full-size; combined with a null context viewport
        // (see open()) the page renders at the full window dimensions.
        List<String> launchArgs = new ArrayList<>();
        launchArgs.add("--start-maximized");
        launchArgs.addAll(parseArguments(optionsConfig));
        BrowserType.LaunchOptions options =
                new BrowserType.LaunchOptions().setHeadless(headless).setArgs(launchArgs);

        String normalized = Objects.toString(browserType, "").trim().toLowerCase(Locale.ROOT);
        if (ARConstantsEngine.FIREFOX.toLowerCase(Locale.ROOT).equals(normalized)) {
            return playwright.firefox().launch(options);
        }

        if (ARConstantsEngine.EDGE.toLowerCase(Locale.ROOT).equals(normalized)) {
            String edgePath = findEdgeExecutable();
            if (edgePath != null) {
                options.setExecutablePath(Paths.get(edgePath));
            } else {
                options.setChannel("msedge");
            }
            return playwright.chromium().launch(options);
        }

        String chromePath = findChromeExecutable();
        if (chromePath != null) {
            options.setExecutablePath(Paths.get(chromePath));
        }
        return playwright.chromium().launch(options);
    }

    private void assertBrowserCompatibleInternal(String requestedBrowserType) {
        if (openPages().isEmpty() || activeBrowserType.isEmpty()) return;
        String requested = canonicalBrowserType(requestedBrowserType);
        if (activeBrowserType.equalsIgnoreCase(requested)) return;
        throw new IllegalStateException(
                "The active Playwright browser is "
                        + activeBrowserType
                        + ", but "
                        + requested
                        + " is configured. Use TEMP Browser and confirm replacement first.");
    }

    private static String canonicalBrowserType(String browserType) {
        String normalized = Objects.toString(browserType, "").trim().toLowerCase(Locale.ROOT);
        if (ARConstantsEngine.FIREFOX.equalsIgnoreCase(normalized)) {
            return ARConstantsEngine.FIREFOX;
        }
        if (ARConstantsEngine.EDGE.equalsIgnoreCase(normalized)) {
            return ARConstantsEngine.EDGE;
        }
        return ARConstantsEngine.CHROME;
    }

    private static Playwright createPlaywright() {
        return Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
    }

    private static List<String> parseArguments(String optionsConfig) {
        List<String> args = new ArrayList<>();
        if (optionsConfig == null || optionsConfig.isBlank()) {
            return args;
        }

        String[] lines = optionsConfig.split("\\R|Â£");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split(":", 2);
            if (parts.length == 2
                    && (parts[0].equalsIgnoreCase("argument") || parts[0].equalsIgnoreCase("arg"))
                    && !parts[1].isBlank()) {
                args.add(parts[1].trim());
            }
        }
        return args;
    }

    private static String findEdgeExecutable() {
        String[] candidates = {
            System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
            System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe"
        };

        for (String candidate : candidates) {
            if (candidate != null && Paths.get(candidate).toFile().exists()) {
                return candidate;
            }
        }
        return null;
    }

    private static String findChromeExecutable() {
        String[] candidates = {
            System.getenv("CHROME_EXECUTABLE_PATH"),
            System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
            System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium"
        };

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && Paths.get(candidate).toFile().exists()) {
                return candidate;
            }
        }
        return null;
    }

    private Page requirePage() {
        if (page == null || page.isClosed()) {
            List<Page> pages = openPages();
            if (pages.isEmpty()) throw new ARWebDriverNotStartedException();
            page = pages.get(pages.size() - 1);
            attachDiagnostics(page);
            log.info("Playwright active tab fallback selected: url={} tabs={}", safeUrl(page), pages.size());
        }
        return page;
    }

    private void closeInternal() {
        closeQuietly(page);
        closeQuietly(context);
        closeQuietly(browser);
        closeQuietly(playwright);
        page = null;
        context = null;
        browser = null;
        playwright = null;
        activeBrowserType = "";
        diagnosedPages.clear();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception error) {
            log.warn("Error closing Playwright resource: {}", error.getMessage());
        }
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record BrowserElementSnapshot(
            boolean found,
            int matchCount,
            boolean displayed,
            boolean enabled,
            boolean selected,
            int x,
            int y,
            int width,
            int height,
            String text,
            String outerHtml,
            String innerHtml,
            String parentHtml,
            String reason) {
        public static BrowserElementSnapshot notFound(String reason) {
            return new BrowserElementSnapshot(
                    false, 0, false, false, false, 0, 0, 0, 0, "", "", "", "", reason);
        }
    }

    private static ExecutorService newPlaywrightThread() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ar-playwright-driver");
            thread.setDaemon(true);
            return thread;
        });
    }

    private synchronized ExecutorService ensurePlaywrightThread() {
        if (playwrightThread == null || playwrightThread.isShutdown() || playwrightThread.isTerminated()) {
            playwrightThread = newPlaywrightThread();
        }
        return playwrightThread;
    }

    private <T> T call(Callable<T> callable) {
        Future<T> future = ensurePlaywrightThread().submit(callable);
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Playwright operation was interrupted", interrupted);
        } catch (ExecutionException executionFailure) {
            Throwable cause = executionFailure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error fatalFailure) throw fatalFailure;
            throw new IllegalStateException("Playwright operation failed", cause);
        }
    }

    private void run(Callable<Void> callable) {
        call(callable);
    }
}
