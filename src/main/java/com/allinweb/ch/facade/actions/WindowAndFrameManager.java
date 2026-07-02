package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.IframeInputLocator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-window / iframe management (cluster N): page refresh with tab restore, window-handle
 * tracking and the iframe→elements map. Owns {@code iframeElementsMap}; window handles and the
 * current tab index stay on the facade (public fields) and are accessed through the context.
 * Bodies moved verbatim from PerformActions.
 */
public class WindowAndFrameManager {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private final ActionContext ctx;
    private Map<WebElement, List<WebElement>> iframeElementsMap;

    public WindowAndFrameManager(ActionContext ctx) {
        this.ctx = ctx;
    }

    public void refreshPage() {
        ctx.justCalledRefreshPage(true);

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

    public void updateWindowHandlesList() {
        Set<String> windowHandles = ctx.driver().getWindowHandles();
        ctx.windowHandles(new ArrayList<>(windowHandles));
    }

    public String getSessionId() {
        if (ctx.driver() instanceof RemoteWebDriver) {
            return ((RemoteWebDriver) ctx.driver()).getSessionId().toString();
        } else {
            throw new IllegalStateException("Driver is not an instance of RemoteWebDriver");
        }
    }

    public Map<WebElement, List<WebElement>> getIframeElementsMap() {
        iframeElementsMap = new HashMap<>();

        if (ctx.driver() != null) {
            // Get all iframe elements on the page
            List<WebElement> iframeList = ctx.driver().findElements(By.tagName("iframe"));
            logOperations.info("Number of iframes found: " + iframeList.size());

            for (WebElement iframe : iframeList) {
                try {
                    // Switch to the iframe
                    ctx.driver().switchTo().frame(iframe);

                    // Get all elements inside the iframe
                    List<WebElement> elementsInsideIframe = ctx.driver().findElements(By.xpath("//*"));
                    iframeElementsMap.put(iframe, elementsInsideIframe);

                    logOperations.info("Iframe contains " + elementsInsideIframe.size() + " elements");
                } catch (Exception e) {
                    logOperations.warn("Could not access iframe: " + e.getMessage());
                } finally {
                    // Switch back to the main page
                    ctx.driver().switchTo().defaultContent();
                }
            }

            IframeInputLocator.getInstance().initializeIframeInputLocator(iframeElementsMap, ctx.driver());
        }
        return iframeElementsMap;
    }
}
