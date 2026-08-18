package com.allinweb.ch.facade.actions;

import java.util.ArrayList;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-window management (cluster N): page refresh with tab restore and window-handle tracking.
 * Window handles and the current tab index stay on the facade and are accessed through the context.
 * Bodies moved verbatim from PerformActions.
 */
public class WindowAndFrameManager {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private final ActionContext ctx;
    public WindowAndFrameManager(ActionContext ctx) {
        this.ctx = ctx;
    }

    public void refreshPage() {
        ctx.justCalledRefreshPage(true);

        // Playwright-first when enabled (single browser); else Selenium refresh + tab restore.
        if (ctx.arWebDriver() != null && ctx.arWebDriver().isPlaywrightEnabled()) {
            try {
                ctx.arWebDriver().getPlaywrightDriver().reload();
                try {
                    ctx.notifyPageRefresh();
                } catch (Exception e) {
                    logOperations.warn("onPageRefresh callback failed: {}", e.getMessage());
                }
                return;
            } catch (Exception e) {
                logOperations.warn("Playwright reload failed, falling back to Selenium: {}", e.getMessage());
            }
        }

        if (ctx.driver() == null) {
            return;
        }

        ctx.driver().navigate().refresh();

        ctx.driver().switchTo().defaultContent();
        if (ctx.driver().getWindowHandles().size() > 1) {
            try {
                ctx.driver().switchTo().window(ctx.windowHandles().get(ctx.tabIndex()));
            } catch (Exception ignore) {

            }
        }

        // Re-inject plugins lost during page reload (actionExecutor, etc.)
        try {
            ctx.notifyPageRefresh();
        } catch (Exception e) {
            logOperations.warn("onPageRefresh callback failed: {}", e.getMessage());
        }
    }

    /**
     * Browser-history back navigation (BACK action). Playwright-first when enabled, else
     * Selenium history back. Plugins injected into the page are lost on history navigation,
     * so the page-refresh callback re-injects them, same as {@link #refreshPage()}.
     */
    public void navigateBack() {
        if (ctx.arWebDriver() != null && ctx.arWebDriver().isPlaywrightEnabled()) {
            try {
                ctx.arWebDriver().getPlaywrightDriver().goBack();
                return;
            } catch (Exception e) {
                logOperations.warn("Playwright goBack failed, falling back to Selenium: {}", e.getMessage());
            }
        }

        if (ctx.driver() == null) {
            return;
        }

        ctx.driver().navigate().back();
        ctx.driver().switchTo().defaultContent();

        try {
            ctx.notifyPageRefresh();
        } catch (Exception e) {
            logOperations.warn("onPageRefresh callback failed after BACK: {}", e.getMessage());
        }
    }

    public void updateWindowHandlesList() {
        // No Selenium window handles in Playwright-only mode.
        if (ctx.driver() == null) {
            ctx.windowHandles(new ArrayList<>());
            return;
        }
        Set<String> windowHandles = ctx.driver().getWindowHandles();
        ctx.windowHandles(new ArrayList<>(windowHandles));
    }

}
