package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/** Produces a value-free structural fingerprint for the active Playwright page. */
public final class PageViewFingerprintService {

    private static final Gson JSON = new Gson();
    private static final int MAX_MATERIAL_LENGTH = 3_000_000;
    private static final String VIEW_SCRIPT = """
            () => {
              const limit = 12000;
              const materialLimit = 2500000;
              const attributeLimit = 8192;
              let attributeOversized = false;
              const clean = value => {
                const raw = String(value || '');
                if (raw.length > attributeLimit) attributeOversized = true;
                return raw.slice(0, attributeLimit).replace(/\\s+/g, ' ').trim();
              };
              const classes = element => clean(element.getAttribute('class'))
                .split(' ')
                .filter(Boolean)
                .sort()
                .join('.');
              const link = element => {
                const raw = clean(element.getAttribute('href'));
                if (!raw) return '';
                try {
                  const parsed = new URL(raw, document.baseURI);
                  return clean(`${parsed.origin}${parsed.pathname}`);
                } catch (_) {
                  return raw;
                }
              };
              const signature = element => {
                const tag = String(element.tagName || '').toLowerCase();
                const id = clean(element.getAttribute('id'));
                const className = classes(element);
                const type = clean(element.getAttribute('type')).toLowerCase();
                const role = clean(element.getAttribute('role')).toLowerCase();
                const name = clean(element.getAttribute('name'));
                const aria = clean(element.getAttribute('aria-label'));
                const testId = clean(element.getAttribute('data-testid'));
                const labelFor = clean(element.getAttribute('for'));
                const placeholder = clean(element.getAttribute('placeholder'));
                const title = clean(element.getAttribute('title'));
                const alt = clean(element.getAttribute('alt'));
                return [tag, id, className, type, role, name, aria, testId, labelFor,
                  placeholder, title, alt, link(element), String(element.childElementCount || 0)]
                  .join('\u001f');
              };
              const root = document.documentElement;
              if (!root) return { material: '', nodeCount: 0, truncated: false,
                hasFrames: false, hasShadowRoots: false };
              const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
              const parts = [];
              let materialLength = 0;
              let nodeCount = 0;
              let truncated = false;
              let hasFrames = false;
              let hasShadowRoots = false;
              let element = walker.currentNode;
              while (element) {
                if (nodeCount >= limit) {
                  truncated = true;
                  break;
                }
                const tag = String(element.tagName || '').toLowerCase();
                if (tag === 'iframe' || tag === 'frame') hasFrames = true;
                if (element.shadowRoot) hasShadowRoots = true;
                const value = signature(element);
                if (materialLength + value.length + 1 > materialLimit) {
                  truncated = true;
                  break;
                }
                parts.push(value);
                materialLength += value.length + 1;
                nodeCount += 1;
                element = walker.nextNode();
              }
              return {
                material: `${nodeCount}\u001d${truncated ? '1' : '0'}\u001d${parts.join('\u001e')}`,
                nodeCount,
                truncated,
                hasFrames,
                hasShadowRoots,
                attributeOversized
              };
            }
            """;

    private PageViewFingerprintService() {}

    public static Observation observe(ARPlaywrightDriver browser) {
        Objects.requireNonNull(browser, "browser");
        ScannedPageIdentity before = ScannedPageIdentity.fromLiveUrl(browser.currentUrl());
        Object raw = browser.evaluate(VIEW_SCRIPT, null);
        JsonObject result = JSON.toJsonTree(raw).isJsonObject()
                ? JSON.toJsonTree(raw).getAsJsonObject()
                : null;
        if (result == null || !result.has("material") || !result.has("nodeCount")) {
            throw new IllegalStateException("The active page did not return a structural fingerprint");
        }
        String material = result.get("material").getAsString();
        if (material.length() > MAX_MATERIAL_LENGTH) {
            throw new IllegalStateException("The active page structure is too large to fingerprint safely");
        }
        int nodeCount = Math.max(0, result.get("nodeCount").getAsInt());
        boolean truncated = booleanValue(result, "truncated");
        boolean hasFrames = booleanValue(result, "hasFrames");
        boolean hasShadowRoots = booleanValue(result, "hasShadowRoots");
        boolean attributeOversized = booleanValue(result, "attributeOversized");
        ScannedPageIdentity after = ScannedPageIdentity.fromLiveUrl(browser.currentUrl());
        if (!before.pageKey().equals(after.pageKey())) {
            throw new IllegalStateException(
                    "The browser page changed while its structural fingerprint was calculated");
        }
        boolean cacheable = !truncated
                && !attributeOversized
                && !hasFrames
                && !hasShadowRoots;
        String diagnostic = truncated
                ? "The live DOM exceeds the safe fingerprint limit."
                : attributeOversized
                        ? "A locator attribute exceeds the safe fingerprint limit."
                        : hasFrames
                        ? "Pages containing frames require a fresh scan."
                        : hasShadowRoots
                                ? "Pages containing Shadow DOM require a fresh scan."
                                : "";
        return new Observation(after, sha256(material), nodeCount, cacheable, diagnostic);
    }

    public static Observation requirePage(
            ARPlaywrightDriver browser, ScannedPageIdentity expectedPage) {
        Observation observation = observe(browser);
        if (expectedPage == null
                || !expectedPage.pageKey().equals(observation.page().pageKey())) {
            throw new IllegalStateException(
                    "The browser page changed while Page Scanner captured its structural fingerprint");
        }
        return observation;
    }

    private static boolean booleanValue(JsonObject object, String field) {
        return object.has(field)
                && object.get(field).isJsonPrimitive()
                && object.getAsJsonPrimitive(field).isBoolean()
                && object.get(field).getAsBoolean();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is required for page fingerprints", unavailable);
        }
    }

    public record Observation(
            ScannedPageIdentity page,
            String fingerprint,
            int nodeCount,
            boolean cacheable,
            String diagnostic) {

        public static Observation unavailable(ScannedPageIdentity page, String reason) {
            return new Observation(
                    Objects.requireNonNull(page, "page"),
                    "",
                    0,
                    false,
                    reason == null || reason.isBlank()
                            ? "The page structure could not be fingerprinted safely."
                            : reason);
        }

        public Observation disableReuse(String reason) {
            return new Observation(
                    page,
                    fingerprint,
                    nodeCount,
                    false,
                    reason == null || reason.isBlank()
                            ? "This scan profile requires a fresh scan."
                            : reason);
        }

        public String persistedFingerprint() {
            return cacheable ? fingerprint : "";
        }
    }
}
