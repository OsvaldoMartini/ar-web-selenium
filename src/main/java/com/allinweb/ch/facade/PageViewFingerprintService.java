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
              const shadowRootLimit = 1024;
              const shadowDepthLimit = 32;
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
              const slotBoundary = element => ({
                slot: clean(element.getAttribute('slot')),
                assigned: element.assignedSlot
                  ? clean(element.assignedSlot.getAttribute('name'))
                  : ''
              });
              const shadowSignature = element => {
                const boundary = slotBoundary(element);
                return `${signature(element)}\u001f${boundary.slot}\u001f${boundary.assigned}`;
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
              const shadowRoots = [];
              const topElements = [];
              let element = walker.currentNode;
              while (element) {
                if (nodeCount >= limit) {
                  truncated = true;
                  break;
                }
                const tag = String(element.tagName || '').toLowerCase();
                if (tag === 'iframe' || tag === 'frame') hasFrames = true;
                if (element.shadowRoot) {
                  hasShadowRoots = true;
                  shadowRoots.push({ root: element.shadowRoot, hostIndex: nodeCount });
                }
                topElements.push({ element, index: nodeCount });
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
              let shadowTraversalComplete = !hasShadowRoots;
              if (hasShadowRoots && !truncated && !attributeOversized) {
                let shadowRootCount = shadowRoots.length;
                if (shadowRootCount > shadowRootLimit) truncated = true;
                const append = value => {
                  if (materialLength + value.length + 1 > materialLimit) {
                    truncated = true;
                    return false;
                  }
                  parts.push(value);
                  materialLength += value.length + 1;
                  return true;
                };
                for (const top of topElements) {
                  const boundary = slotBoundary(top.element);
                  if ((boundary.slot || boundary.assigned)
                      && !append(`\u001cslot:${top.index}\u001f${boundary.slot}\u001f${boundary.assigned}`)) {
                    break;
                  }
                }
                for (const shadow of shadowRoots) {
                  if (truncated) break;
                  if (!append(`\u001cshadow-open:${shadow.hostIndex}`)) break;
                  const first = shadow.root?.firstElementChild;
                  const stack = first ? [{ kind: 'element', node: first, depth: 1 }] : [];
                  while (stack.length && !truncated) {
                    const entry = stack.pop();
                    if (entry.kind === 'marker') {
                      append(entry.value);
                      continue;
                    }
                    if (nodeCount >= limit) {
                      truncated = true;
                      break;
                    }
                    const shadowElement = entry.node;
                    if (!append(shadowSignature(shadowElement))) break;
                    nodeCount += 1;
                    if (shadowElement.nextElementSibling) {
                      stack.push({
                        kind: 'element',
                        node: shadowElement.nextElementSibling,
                        depth: entry.depth
                      });
                    }
                    if (shadowElement.firstElementChild) {
                      stack.push({
                        kind: 'element',
                        node: shadowElement.firstElementChild,
                        depth: entry.depth
                      });
                    }
                    if (shadowElement.shadowRoot) {
                      shadowRootCount += 1;
                      if (shadowRootCount > shadowRootLimit
                          || entry.depth >= shadowDepthLimit) {
                        truncated = true;
                        break;
                      }
                      stack.push({ kind: 'marker', value: '\u001cshadow-close' });
                      if (shadowElement.shadowRoot.firstElementChild) {
                        stack.push({
                          kind: 'element',
                          node: shadowElement.shadowRoot.firstElementChild,
                          depth: entry.depth + 1
                        });
                      }
                      stack.push({ kind: 'marker', value: '\u001cshadow-open' });
                    }
                  }
                  if (!truncated && !append(`\u001cshadow-close:${shadow.hostIndex}`)) break;
                }
                shadowTraversalComplete = !truncated;
              }
              const material = hasShadowRoots
                ? `shadow-v1\u001d${nodeCount}\u001d${truncated ? '1' : '0'}\u001d${parts.join('\u001e')}`
                : `${nodeCount}\u001d${truncated ? '1' : '0'}\u001d${parts.join('\u001e')}`;
              return {
                material,
                nodeCount,
                truncated,
                hasFrames,
                hasShadowRoots,
                shadowTraversalComplete,
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
        boolean hasShadowRoots = booleanValue(result, "hasShadowRoots");
        boolean shadowTraversalComplete = !hasShadowRoots
                || booleanValue(result, "shadowTraversalComplete");
        boolean attributeOversized = booleanValue(result, "attributeOversized");
        ScannedPageIdentity after = ScannedPageIdentity.fromLiveUrl(browser.currentUrl());
        if (!before.pageKey().equals(after.pageKey())) {
            throw new IllegalStateException(
                    "The browser page changed while its structural fingerprint was calculated");
        }
        boolean cacheable = !truncated
                && !attributeOversized
                && shadowTraversalComplete;
        String diagnostic = truncated
                ? "The live DOM exceeds the safe fingerprint limit."
                : attributeOversized
                        ? "A locator attribute exceeds the safe fingerprint limit."
                        : !shadowTraversalComplete
                                ? "The live Shadow DOM could not be fingerprinted safely."
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
