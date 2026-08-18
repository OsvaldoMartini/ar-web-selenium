package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import java.util.Map;
import java.util.Objects;

/** Bounded top-window traversal used to expose lazy page content before a mapping scan. */
final class PageScanScrollTraversal {

    private static final int MAX_STEPS = PreScanWorkflowService.MAX_SCROLL_PAGES;
    private static final int MAX_DURATION_MS = 45_000;
    private static final int MAX_DOCUMENT_HEIGHT_CSS_PX = 60_000;
    private static final int MIN_VIEWPORT_SETTLE_MS = 1_000;
    private static final int MAX_VIEWPORT_SETTLE_MS = 2_500;
    private static final int DOM_QUIET_MS = 750;
    private static final int POLL_MS = 100;
    private static final int PAINT_TIMEOUT_MS = 300;
    private static final int RESTORE_SETTLE_MS = 3_000;
    private static final int STABLE_BOTTOM_SAMPLES = 2;
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
              const paint = () => new Promise(resolve => {
                let finished = false;
                const finish = () => {
                  if (finished) return;
                  finished = true;
                  clearTimeout(timeout);
                  resolve();
                };
                const timeout = setTimeout(finish, limits.paintTimeoutMs);
                if (typeof requestAnimationFrame === 'function') {
                  window.requestAnimationFrame(() => window.requestAnimationFrame(finish));
                } else {
                  setTimeout(finish, 32);
                }
              });
              const nearViewport = (node) => {
                const element = node?.nodeType === Node.ELEMENT_NODE
                  ? node
                  : node?.parentElement;
                if (!element?.getBoundingClientRect) return false;
                const rect = element.getBoundingClientRect();
                const margin = Math.max(200, number(window.innerHeight) * 0.25);
                return rect.width > 0
                  && rect.height > 0
                  && rect.bottom >= -margin
                  && rect.top <= number(window.innerHeight) + margin;
              };
              const visibleImages = () => Array.from(document.images || []).filter(image =>
                nearViewport(image) && Boolean(image.currentSrc || image.getAttribute('src')));
              const activeFiniteAnimations = () => {
                if (typeof document.getAnimations !== 'function') return [];
                return document.getAnimations().filter(animation => {
                  if (animation.playState !== 'running' && animation.playState !== 'pending') {
                    return false;
                  }
                  const target = animation.effect?.target;
                  if (target && !nearViewport(target)) return false;
                  try {
                    return Number.isFinite(Number(animation.effect?.getComputedTiming()?.endTime));
                  } catch (_) {
                    return false;
                  }
                });
              };
              const visualState = () => {
                const images = visibleImages();
                const pendingImages = images.filter(image => !image.complete);
                const finiteAnimations = activeFiniteAnimations();
                const fontsReady = !document.fonts || document.fonts.status !== 'loading';
                return {
                  images,
                  pendingImages,
                  finiteAnimations,
                  fontsReady,
                  signature: [
                    height(),
                    number(window.scrollY || window.pageYOffset),
                    document.querySelectorAll('*').length,
                    images.length,
                    pendingImages.length,
                    finiteAnimations.length,
                    fontsReady ? 'fonts-ready' : 'fonts-loading'
                  ].join('|')
                };
              };
              const root = document.documentElement;
              const previousScrollBehavior = root?.style?.scrollBehavior || '';
              const initialUrl = String(window.location.href);
              const originX = number(window.scrollX || window.pageXOffset);
              const originY = number(window.scrollY || window.pageYOffset);
              const startedAt = Date.now();
              const traversalDeadline = startedAt + limits.maxDurationMs;
              let steps = 0;
              let stableBottomSamples = 0;
              let previousHeight = -1;
              let maximumHeight = height();
              let stalledScrollAttempts = 0;
              let completed = false;
              let reason = '';
              const settleViewport = async (maximumWait, deadline) => {
                const settleStartedAt = Date.now();
                const settleDeadline = Math.min(settleStartedAt + maximumWait, deadline);
                let lastRelevantMutation = settleStartedAt;
                let lastSignatureChange = settleStartedAt;
                let previousSignature = '';
                const observer = new MutationObserver(mutations => {
                  if (mutations.some(mutation => nearViewport(mutation.target))) {
                    lastRelevantMutation = Date.now();
                  }
                });
                if (root) {
                  observer.observe(root, {
                    subtree: true,
                    childList: true,
                    characterData: true,
                    attributes: true,
                    attributeFilter: [
                      'src', 'srcset', 'sizes', 'loading', 'hidden', 'class', 'style'
                    ]
                  });
                }
                try {
                  await paint();
                  while (Date.now() < settleDeadline) {
                    if (String(window.location.href) !== initialUrl) {
                      return {ok: false, reason: 'page-changed'};
                    }
                    const state = visualState();
                    if (state.signature !== previousSignature) {
                      previousSignature = state.signature;
                      lastSignatureChange = Date.now();
                    }
                    const now = Date.now();
                    const quietSince = Math.max(lastRelevantMutation, lastSignatureChange);
                    if (now - settleStartedAt >= limits.minViewportSettleMs
                        && now - quietSince >= limits.domQuietMs
                        && state.pendingImages.length === 0
                        && state.finiteAnimations.length === 0
                        && state.fontsReady) {
                      const decodes = state.images
                        .filter(image => image.complete && image.naturalWidth > 0
                          && typeof image.decode === 'function')
                        .map(image => {
                          try {
                            return Promise.resolve(image.decode()).catch(() => undefined);
                          } catch (_) {
                            return Promise.resolve();
                          }
                        });
                      let decoded = decodes.length === 0;
                      if (!decoded) {
                        await Promise.race([
                          Promise.allSettled(decodes).then(() => { decoded = true; }),
                          wait(Math.max(0, settleDeadline - Date.now()))
                        ]);
                      }
                      if (decoded) {
                        await paint();
                        const painted = visualState();
                        const paintedNow = Date.now();
                        if (painted.pendingImages.length === 0
                            && painted.finiteAnimations.length === 0
                            && painted.fontsReady
                            && painted.signature === previousSignature
                            && paintedNow - Math.max(
                              lastRelevantMutation, lastSignatureChange) >= limits.domQuietMs) {
                          return {ok: true, reason: ''};
                        }
                      }
                    }
                    await wait(Math.min(limits.pollMs, Math.max(0, settleDeadline - Date.now())));
                  }
                  return {ok: false, reason: 'visual-settle-timeout'};
                } finally {
                  observer.disconnect();
                }
              };
              try {
                if (root?.style) root.style.scrollBehavior = 'auto';
                if (maximumHeight > limits.maxDocumentHeight) {
                  reason = 'document-height-limit';
                } else {
                  window.scrollTo(originX, 0);
                  const initialSettle = await settleViewport(
                    limits.maxViewportSettleMs, traversalDeadline);
                  if (!initialSettle.ok) reason = initialSettle.reason;
                  while (!reason
                      && steps < limits.requestedSteps
                      && steps < limits.maxSteps
                      && Date.now() < traversalDeadline) {
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
                    if (atBottom) {
                      if (currentHeight === previousHeight) {
                        stableBottomSamples += 1;
                      } else {
                        stableBottomSamples = 0;
                      }
                      previousHeight = currentHeight;
                      if (stableBottomSamples >= limits.stableBottomSamples) {
                        completed = true;
                        break;
                      }
                      const bottomSettled = await settleViewport(
                        limits.maxViewportSettleMs, traversalDeadline);
                      if (!bottomSettled.ok) reason = bottomSettled.reason;
                      continue;
                    }
                    stableBottomSamples = 0;
                    previousHeight = currentHeight;
                    const stepSize = Math.max(250, Math.floor(viewportHeight * 0.75));
                    const nextY = Math.min(bottom, currentY + stepSize);
                    window.scrollTo(originX, nextY);
                    const movedY = Math.max(
                      0, number(window.scrollY || window.pageYOffset));
                    if (movedY > currentY + 1) {
                      steps += 1;
                      stalledScrollAttempts = 0;
                    } else {
                      stalledScrollAttempts += 1;
                    }
                    const settled = await settleViewport(
                      limits.maxViewportSettleMs, traversalDeadline);
                    if (!settled.ok) {
                      reason = settled.reason;
                    } else if (stalledScrollAttempts >= 2) {
                      reason = 'scroll-stalled';
                    } else {
                      const settledHeight = height();
                      maximumHeight = Math.max(maximumHeight, settledHeight);
                      if (maximumHeight > limits.maxDocumentHeight) {
                        reason = 'document-height-limit';
                      } else if (steps >= limits.requestedSteps) {
                        completed = true;
                      }
                    }
                  }
                  if (!completed && !reason) {
                    if (steps >= limits.requestedSteps) {
                      completed = true;
                    } else {
                      reason = steps >= limits.maxSteps ? 'step-limit' : 'time-limit';
                    }
                  }
                }
              } finally {
                window.scrollTo(originX, originY);
                const restored = await settleViewport(
                  limits.restoreSettleMs, Date.now() + limits.restoreSettleMs);
                if (!restored.ok && !reason) {
                  completed = false;
                  reason = 'restore-' + restored.reason;
                }
                if (root?.style) root.style.scrollBehavior = previousScrollBehavior;
              }
              return {completed, reason, steps, maximumHeight};
            }
            """;

    private PageScanScrollTraversal() {}

    static void traverse(ARPlaywrightDriver browser, ScannedPageIdentity expectedPage) {
        traverse(browser, expectedPage, PreScanWorkflowService.DEFAULT_SCROLL_PAGES);
    }

    static void traverse(
            ARPlaywrightDriver browser,
            ScannedPageIdentity expectedPage,
            int scrollPages) {
        Objects.requireNonNull(browser, "The active Playwright page is required");
        Objects.requireNonNull(expectedPage, "The active page identity is required");
        if (scrollPages < PreScanWorkflowService.MIN_SCROLL_PAGES
                || scrollPages > MAX_STEPS) {
            throw new IllegalArgumentException(
                    "SCROLL PAGE count must be between "
                            + PreScanWorkflowService.MIN_SCROLL_PAGES
                            + " and " + MAX_STEPS + '.');
        }
        requireUnchangedPage(browser, expectedPage);
        Object raw = browser.evaluate(
                TRAVERSE_SCRIPT,
                Map.ofEntries(
                        Map.entry("requestedSteps", scrollPages),
                        Map.entry("maxSteps", MAX_STEPS),
                        Map.entry("maxDurationMs", MAX_DURATION_MS),
                        Map.entry("maxDocumentHeight", MAX_DOCUMENT_HEIGHT_CSS_PX),
                        Map.entry("minViewportSettleMs", MIN_VIEWPORT_SETTLE_MS),
                        Map.entry("maxViewportSettleMs", MAX_VIEWPORT_SETTLE_MS),
                        Map.entry("domQuietMs", DOM_QUIET_MS),
                        Map.entry("pollMs", POLL_MS),
                        Map.entry("paintTimeoutMs", PAINT_TIMEOUT_MS),
                        Map.entry("restoreSettleMs", RESTORE_SETTLE_MS),
                        Map.entry("stableBottomSamples", STABLE_BOTTOM_SAMPLES)));
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
