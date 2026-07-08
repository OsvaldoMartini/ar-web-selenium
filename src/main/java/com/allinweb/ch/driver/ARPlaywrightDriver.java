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
import com.microsoft.playwright.options.ViewportSize;
import com.microsoft.playwright.options.WaitUntilState;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARPlaywrightDriver {

    private final ExecutorService playwrightThread = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ar-playwright-driver");
        thread.setDaemon(true);
        return thread;
    });

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private final PlaywrightActionExecutor actionExecutor = new PlaywrightActionExecutor();
    private final PlaywrightElementScanner elementScanner = new PlaywrightElementScanner();

    public void open(String browserType, String url, String optionsConfig) {
        run(() -> {
            closeInternal();

            playwright = Playwright.create();
            browser = launchBrowser(browserType, optionsConfig);
            // bypassCSP so injected plugins (hoverPick/actionExecutor) can open their WebSocket back
            // to the Java server on sites with a strict connect-src Content-Security-Policy.
            // bypassCSP so injected plugins can open their WebSocket; null viewport so the page uses
            // the full (maximized) browser window instead of Playwright's default 1280x720 viewport.
            context = browser.newContext(
                    new Browser.NewContextOptions().setBypassCSP(true).setViewportSize(null));
            page = context.newPage();
            attachDiagnostics(page);
            navigateDomReady(page, url);
            return null;
        });
    }

    public void openOrNavigate(String browserType, String url, String optionsConfig) {
        run(() -> {
            if (page != null && !page.isClosed()) {
                navigateDomReady(page, url);
                return null;
            }

            closeInternal();
            playwright = Playwright.create();
            browser = launchBrowser(browserType, optionsConfig);
            context = browser.newContext(
                    new Browser.NewContextOptions().setBypassCSP(true).setViewportSize(null));
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
            return vs == null ? null : new int[] {vs.width, vs.height};
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

    public List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden) {
        return call(() -> elementScanner.scan(requirePage(), searchTerms, includeHidden));
    }

    public boolean click(InstructionLoad instruction) {
        return call(() -> actionExecutor.click(requirePage(), instruction));
    }

    public boolean fill(InstructionLoad instruction, FieldData data) {
        return call(() -> actionExecutor.fill(requirePage(), instruction, data));
    }

    public String text(InstructionLoad instruction) {
        return call(() -> actionExecutor.text(requirePage(), instruction));
    }

    public boolean isOpen() {
        return call(() -> page != null && !page.isClosed());
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
        playwrightThread.shutdownNow();
    }

    /**
     * Surface the Playwright page's runtime signals into the app log: JS console output, uncaught
     * page errors, and the full WebSocket lifecycle. This is how we diagnose injected plugins
     * (hoverPick/actionExecutor) whose {@code new WebSocket(...)} back to the Java server may fail
     * silently on strict-CSP sites — the socket open/error/close now shows up in the log.
     */
    private void attachDiagnostics(Page p) {
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

    private Browser launchBrowser(String browserType, String optionsConfig) {
        // --start-maximized opens the browser window full-size; combined with a null context viewport
        // (see open()) the page renders at the full window dimensions.
        List<String> launchArgs = new ArrayList<>();
        launchArgs.add("--start-maximized");
        launchArgs.addAll(parseArguments(optionsConfig));
        BrowserType.LaunchOptions options =
                new BrowserType.LaunchOptions().setHeadless(false).setArgs(launchArgs);

        String normalized = Objects.toString(browserType, "").toLowerCase(Locale.ROOT);
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

        return playwright.chromium().launch(options);
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

    private Page requirePage() {
        if (page == null || page.isClosed()) {
            throw new ARWebDriverNotStartedException();
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

    private <T> T call(Callable<T> callable) {
        Future<T> future = playwrightThread.submit(callable);
        try {
            return future.get();
        } catch (Exception error) {
            throw new IllegalStateException("Playwright operation failed", error);
        }
    }

    private void run(Callable<Void> callable) {
        call(callable);
    }
}
