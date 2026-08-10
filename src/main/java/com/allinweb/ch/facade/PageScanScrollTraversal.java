package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import java.util.Map;
import java.util.Objects;

/** Bounded top-window traversal used to expose lazy page content before a mapping scan. */
final class PageScanScrollTraversal {

    private static final int MAX_STEPS = 40;
    private static final int MAX_DURATION_MS = 20_000;
    private static final int MAX_DOCUMENT_HEIGHT_CSS_PX = 60_000;
    private static final int STEP_DELAY_MS = 200;
    private static final String TRAVERSE_SCRIPT = """
            async (limits) => {
              const number = (value, fallback = 0) =>
                Number.isFinite(Number(value)) ? Number(value) : fallback;
              const height = () => {
                const root = document.documentElement;
                const body = document.body;
                return Math.max(
                  number(root?.scrollHeight),
                  number(root?.offsetHeight),
                  number(body?.scrollHeight),
                  number(body?.offsetHeight),
                  number(window.innerHeight));
              };
              const wait = (delay) => new Promise(resolve => setTimeout(resolve, delay));
              const root = document.documentElement;
              const previousScrollBehavior = root?.style?.scrollBehavior || '';
              const initialUrl = String(window.location.href);
              const originX = number(window.scrollX || window.pageXOffset);
              const originY = number(window.scrollY || window.pageYOffset);
              const startedAt = Date.now();
              let steps = 0;
              let stableBottomSamples = 0;
              let previousHeight = -1;
              let maximumHeight = height();
              let completed = false;
              let reason = '';
              try {
                if (root?.style) root.style.scrollBehavior = 'auto';
                if (maximumHeight > limits.maxDocumentHeight) {
                  reason = 'document-height-limit';
                  return {completed, reason, steps, maximumHeight};
                }
                window.scrollTo(originX, 0);
                await wait(limits.stepDelayMs);
                while (steps < limits.maxSteps && Date.now() - startedAt < limits.maxDurationMs) {
                  if (String(window.location.href) !== initialUrl) {
                    reason = 'page-changed';
                    break;
                  }
                  const viewportHeight = Math.max(1, number(window.innerHeight, 1));
                  const currentHeight = height();
                  maximumHeight = Math.max(maximumHeight, currentHeight);
                  if (maximumHeight > limits.maxDocumentHeight) {
                    reason = 'document-height-limit';
                    break;
                  }
                  const bottom = Math.max(0, currentHeight - viewportHeight);
                  const currentY = Math.max(0, number(window.scrollY || window.pageYOffset));
                  const atBottom = currentY >= bottom - 2;
                  if (atBottom && currentHeight === previousHeight) {
                    stableBottomSamples += 1;
                  } else {
                    stableBottomSamples = 0;
                  }
                  if (stableBottomSamples >= 4) {
                    completed = true;
                    break;
                  }
                  previousHeight = currentHeight;
                  const stepSize = Math.max(250, Math.floor(viewportHeight * 0.85));
                  const nextY = atBottom ? bottom : Math.min(bottom, currentY + stepSize);
                  window.scrollTo(originX, nextY);
                  steps += 1;
                  await wait(limits.stepDelayMs);
                }
                if (!completed && !reason) {
                  reason = steps >= limits.maxSteps ? 'step-limit' : 'time-limit';
                }
                return {completed, reason, steps, maximumHeight};
              } finally {
                window.scrollTo(originX, originY);
                if (root?.style) root.style.scrollBehavior = previousScrollBehavior;
              }
            }
            """;

    private PageScanScrollTraversal() {}

    static void traverse(ARPlaywrightDriver browser, ScannedPageIdentity expectedPage) {
        Objects.requireNonNull(browser, "The active Playwright page is required");
        Objects.requireNonNull(expectedPage, "The active page identity is required");
        requireUnchangedPage(browser, expectedPage);
        Object raw = browser.evaluate(
                TRAVERSE_SCRIPT,
                Map.of(
                        "maxSteps", MAX_STEPS,
                        "maxDurationMs", MAX_DURATION_MS,
                        "maxDocumentHeight", MAX_DOCUMENT_HEIGHT_CSS_PX,
                        "stepDelayMs", STEP_DELAY_MS));
        requireUnchangedPage(browser, expectedPage);
        if (!(raw instanceof Map<?, ?> result)) {
            throw new IllegalStateException(
                    "Automatic page scrolling stopped safely (invalid-result). "
                            + "Scan without SCROLL PAGE.");
        }
        if (!Boolean.TRUE.equals(result.get("completed"))) {
            String reason = Objects.toString(result.get("reason"), "unknown");
            throw new IllegalStateException(
                    "Automatic page scrolling stopped safely (" + reason
                            + "). Reduce the page size or scan without SCROLL PAGE.");
        }
    }

    private static void requireUnchangedPage(
            ARPlaywrightDriver browser, ScannedPageIdentity expectedPage) {
        ScannedPageIdentity current = ScannedPageIdentity.fromLiveUrl(browser.currentUrl());
        if (!expectedPage.pageKey().equals(current.pageKey())
                || !expectedPage.actualUrl().equals(current.actualUrl())) {
            throw new IllegalStateException(
                    "The browser page changed during automatic scrolling. Scan the current page again.");
        }
    }
}
