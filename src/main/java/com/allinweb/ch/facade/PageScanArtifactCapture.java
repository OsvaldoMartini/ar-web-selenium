package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.vision.RasterImage;
import com.allinweb.ch.vision.RasterImageIO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Captures geometry and a screenshot directly into one scan-owned staging folder. */
final class PageScanArtifactCapture {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String GEOMETRY_SCRIPT = """
            (targets) => {
              const number = (value, fallback = 0) => Number.isFinite(Number(value)) ? Number(value) : fallback;
              const resolve = (doc, xpath) => {
                try {
                  return doc.evaluate(xpath, doc, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
                } catch (_) {
                  return null;
                }
              };
              const topScrollX = number(window.scrollX || window.pageXOffset);
              const topScrollY = number(window.scrollY || window.pageYOffset);
              const rects = [];
              for (const target of (Array.isArray(targets) ? targets : [])) {
                const elementIndex = Math.trunc(number(target?.elementIndex, -1));
                const xPath = String(target?.xPath || '');
                const iframeXPath = String(target?.iframeXPath || '');
                let doc = document;
                let offsetX = 0;
                let offsetY = 0;
                if (iframeXPath) {
                  const frame = resolve(document, iframeXPath);
                  if (!frame || frame.tagName !== 'IFRAME') {
                    rects.push({elementIndex, xPath, iframeXPath, found: false, bounds: null, error: 'iframe-not-found'});
                    continue;
                  }
                  try {
                    doc = frame.contentDocument;
                  } catch (_) {
                    doc = null;
                  }
                  if (!doc) {
                    rects.push({elementIndex, xPath, iframeXPath, found: false, bounds: null, error: 'cross-origin-iframe'});
                    continue;
                  }
                  const frameRect = frame.getBoundingClientRect();
                  offsetX = number(frameRect.left) + number(frame.clientLeft);
                  offsetY = number(frameRect.top) + number(frame.clientTop);
                }
                const node = resolve(doc, xPath);
                if (!node || typeof node.getBoundingClientRect !== 'function') {
                  rects.push({elementIndex, xPath, iframeXPath, found: false, bounds: null});
                  continue;
                }
                const rect = node.getBoundingClientRect();
                const x = offsetX + number(rect.left);
                const y = offsetY + number(rect.top);
                rects.push({
                  elementIndex,
                  xPath,
                  iframeXPath,
                  found: true,
                  bounds: {
                    x,
                    y,
                    width: Math.max(0, number(rect.width)),
                    height: Math.max(0, number(rect.height)),
                    pageX: x + topScrollX,
                    pageY: y + topScrollY
                  }
                });
              }
              const root = document.documentElement;
              const body = document.body;
              return {
                meta: {
                  devicePixelRatio: Math.max(0.01, number(window.devicePixelRatio, 1)),
                  viewportWidth: Math.max(0, number(window.innerWidth)),
                  viewportHeight: Math.max(0, number(window.innerHeight)),
                  documentWidth: Math.max(
                    number(root?.scrollWidth), number(root?.offsetWidth), number(body?.scrollWidth), number(body?.offsetWidth)),
                  documentHeight: Math.max(
                    number(root?.scrollHeight), number(root?.offsetHeight), number(body?.scrollHeight), number(body?.offsetHeight)),
                  scrollX: topScrollX,
                  scrollY: topScrollY
                },
                rects
              };
            }
            """;

    private PageScanArtifactCapture() {}

    static PageScanSnapshotStore.CaptureMetadata capture(
            ARPlaywrightDriver browser,
            ScannedPageIdentity expectedPage,
            List<ElementDTO> elements,
            Path staging,
            String requestedScope)
            throws Exception {
        return capture(browser, expectedPage, elements, staging, requestedScope, null);
    }

    static PageScanSnapshotStore.CaptureMetadata capture(
            ARPlaywrightDriver browser,
            ScannedPageIdentity expectedPage,
            List<ElementDTO> elements,
            Path staging,
            String requestedScope,
            PageViewFingerprintService.Observation scannedView)
            throws Exception {
        if (browser == null) throw new IllegalArgumentException("The active Playwright page is required");
        if (expectedPage == null) throw new IllegalArgumentException("The scanned page identity is required");
        requireExpectedPage(browser, expectedPage);
        PageViewFingerprintService.Observation fingerprintBefore = null;
        PageViewFingerprintService.Observation authoritativeView = null;
        if (scannedView != null) {
            fingerprintBefore = PageViewFingerprintService.requirePage(browser, expectedPage);
            authoritativeView = scannedView.fingerprint().isBlank()
                    ? fingerprintBefore
                    : scannedView;
            if (!expectedPage.pageKey().equals(authoritativeView.page().pageKey())
                    || !authoritativeView.fingerprint().equals(fingerprintBefore.fingerprint())) {
                throw new IllegalStateException(
                        "The page structure changed after Page Scanner collected its elements. "
                                + "Scan the current page again.");
            }
        }
        String scope = "full_page".equalsIgnoreCase(requestedScope) ? "full_page" : "viewport";
        List<Map<String, Object>> targets = targets(elements);
        Object rawGeometry = browser.evaluate(GEOMETRY_SCRIPT, targets);
        JsonObject geometry = JSON.toJsonTree(rawGeometry).isJsonObject()
                ? JSON.toJsonTree(rawGeometry).getAsJsonObject()
                : null;
        if (geometry == null || !geometry.has("meta") || !geometry.get("meta").isJsonObject()) {
            throw new IllegalStateException("The active page did not return scan geometry metadata");
        }
        JsonArray rects = geometry.has("rects") && geometry.get("rects").isJsonArray()
                ? geometry.getAsJsonArray("rects")
                : new JsonArray();

        byte[] screenshot = browser.screenshot("full_page".equals(scope));
        PageScanArtifactPolicy.requireWritableSize("screenshot.png", screenshot.length);
        RasterImage image = RasterImageIO.readPng(screenshot);
        if (image == null || image.width() <= 0 || image.height() <= 0) {
            throw new IllegalStateException("The active page screenshot could not be decoded");
        }
        Files.write(
                staging.resolve("screenshot.png"),
                screenshot,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        byte[] rectangleBytes = JSON.toJson(rects).getBytes(StandardCharsets.UTF_8);
        PageScanArtifactPolicy.requireWritableSize("rects.json", rectangleBytes.length);
        Files.write(
                staging.resolve("rects.json"),
                rectangleBytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        JsonObject meta = geometry.getAsJsonObject("meta");
        double dpr = positive(meta, "devicePixelRatio", 1.0d);
        double viewportWidth = nonNegative(meta, "viewportWidth");
        double viewportHeight = nonNegative(meta, "viewportHeight");
        double documentWidth = nonNegative(meta, "documentWidth");
        double documentHeight = nonNegative(meta, "documentHeight");
        double cssWidth = "full_page".equals(scope) ? documentWidth : viewportWidth;
        double cssHeight = "full_page".equals(scope) ? documentHeight : viewportHeight;
        if (cssWidth <= 0) cssWidth = image.width() / dpr;
        if (cssHeight <= 0) cssHeight = image.height() / dpr;
        requireExpectedPage(browser, expectedPage);
        PageViewFingerprintService.Observation fingerprintAfter = null;
        if (scannedView != null) {
            fingerprintAfter = PageViewFingerprintService.requirePage(browser, expectedPage);
            if (!authoritativeView.fingerprint().equals(fingerprintAfter.fingerprint())) {
                throw new IllegalStateException(
                        "The page structure changed while Page Scanner captured its immutable artifacts. "
                                + "Scan the current page again.");
            }
        }
        String reusableFingerprint = scannedView != null
                        && scannedView.cacheable()
                        && fingerprintBefore != null
                        && fingerprintBefore.cacheable()
                        && fingerprintAfter != null
                        && fingerprintAfter.cacheable()
                        && containsOnlyTopDocumentElements(elements)
                ? scannedView.fingerprint()
                : "";
        return new PageScanSnapshotStore.CaptureMetadata(
                scope,
                dpr,
                cssWidth,
                cssHeight,
                image.width(),
                image.height(),
                nonNegative(meta, "scrollX"),
                nonNegative(meta, "scrollY"),
                reusableFingerprint,
                scannedView == null ? 0 : scannedView.nodeCount());
    }

    /**
     * The structural material covers the top document, including iframe host nodes, but not frame
     * or Shadow DOM contents. Persist it for reuse only when every captured locator has that same
     * top-document scope. This keeps future nested-context scanner support fail-closed.
     */
    private static boolean containsOnlyTopDocumentElements(List<ElementDTO> elements) {
        if (elements == null || elements.isEmpty()) return true;
        for (ElementDTO element : elements) {
            if (element == null) continue;
            if (hasText(element.getIFrameXPath())
                    || hasText(element.getShadowHost())
                    || hasText(element.getShadowRoot())
                    || Boolean.parseBoolean(element.getNestedShadow())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireExpectedPage(
            ARPlaywrightDriver browser, ScannedPageIdentity expectedPage) {
        ScannedPageIdentity activePage = ScannedPageIdentity.fromLiveUrl(browser.currentUrl());
        if (!expectedPage.pageKey().equals(activePage.pageKey())) {
            throw new IllegalStateException(
                    "The browser page changed while Page Scanner captured its immutable artifacts. "
                            + "Scan the current page again.");
        }
    }

    private static List<Map<String, Object>> targets(List<ElementDTO> elements) {
        List<Map<String, Object>> targets = new ArrayList<>();
        if (elements == null) return targets;
        for (int index = 0; index < elements.size(); index++) {
            ElementDTO element = elements.get(index);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("elementIndex", index);
            target.put("xPath", element == null ? "" : value(element.getXPath()));
            target.put("iframeXPath", element == null ? "" : value(element.getIFrameXPath()));
            targets.add(target);
        }
        return targets;
    }

    private static double positive(JsonObject object, String field, double fallback) {
        double value = number(object, field, fallback);
        return value > 0 ? value : fallback;
    }

    private static double nonNegative(JsonObject object, String field) {
        return Math.max(0.0d, number(object, field, 0.0d));
    }

    private static double number(JsonObject object, String field, double fallback) {
        try {
            return object.has(field) && !object.get(field).isJsonNull()
                    ? object.get(field).getAsDouble()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }
}
