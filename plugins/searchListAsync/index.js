/**
 * searchListAsync — plugin entry point
 *
 * Async element extraction with Selenium executeAsyncScript callback.
 * Used by PerformListElements for in-process scanning.
 *
 * Arguments are injected by PerformListElements.dynamicLoadElementsDTO():
 *   [0] searchTerms        — string[] filter tags
 *   [1] searchHiddenFields — boolean
 *   [2] port               — (unused, kept for arg alignment)
 *   [3] sessionId          — UUID string
 *   [4] destination        — target session ID
 *   [5] operationId        — operation label string
 *   [6] homeBankingId      — int
 *   [7] botJobId           — int
 *
 * Build output: build/searchListAsync.min.js  (produced by esbuild)
 * Do NOT edit the minified file — edit these source modules instead.
 */

import { collectElements, collectShadowElements } from './scanner/domWalker.js';
import { collectIframeElements }                    from './scanner/iframeInspector.js';
import { getResultMap, processElementsWithXPath, findUniqueAndOneRepeated } from './classifier/deduplicator.js';
import { findMatLabel, changeDivToLabelWithSomeText, normalizeSomeTextForTables } from './classifier/nameLabelResolver.js';

// ── Selenium async callback ─────────────────────────────────────────────────
const __done = arguments[arguments.length - 1];

(function (
  searchTerms, hiddenFields, socketPort,
  sessionId, destination, operationId,
  homeBankingId, botJobId,
) {
  // ── Async callback guard ──────────────────────────────────────────────────
  let __doneCalled = false;
  function doneOnce(payload) {
    if (__doneCalled) return;
    __doneCalled = true;
    __done(JSON.stringify(payload));
  }

  // Safety timeout (20 s)
  setTimeout(() => doneOnce({ ok: false, error: 'timeout waiting for JS completion' }), 20000);

  // ── Cleanup previous injection ────────────────────────────────────────────
  try { if (typeof window.__scannerToolCleanup === 'function') window.__scannerToolCleanup(); } catch (_) {}

  window.__scannerToolCleanup = null;
  const cleanup = () => {};
  window.__scannerToolCleanup = cleanup;
  window.addEventListener('beforeunload', cleanup, { once: true });

  // ── Shared state ──────────────────────────────────────────────────────────
  window.elementInfoMap    = new Map();
  window.searchTerms       = searchTerms;
  window.allElementInfo    = [];
  window.destination       = destination;
  window.operationId       = operationId;
  window.homeBankingId     = homeBankingId;
  window.botJobId          = botJobId;
  window.sessionId         = `${sessionId}`;
  // Expose hiddenFields for sub-modules
  window.__slAsync_hiddenFields = hiddenFields;

  let started = false;

  function init(eventName) {
    if (started) return;

    const ready =
      ['DOMContentLoaded','onreadystatechange','load','onload','Direct Execution'].includes(eventName) ||
      ['complete','interactive'].includes(document.readyState);

    if (!ready) return;
    started = true;

    try {
      runScan(searchTerms);
    } catch (e) {
      doneOnce({
        ok: false,
        error: 'startCollectingElements failed',
        message: String(e?.message || e),
        stack: String(e?.stack || ''),
      });
    }
  }

  // ── Main scan ─────────────────────────────────────────────────────────────
  function runScan(searchTerms) {
    window.elementInfoMap = new Map();
    let collectionFound = [];

    // 1. Iframes
    collectIframeElements(document, collectionFound, searchTerms);

    // 2. Shadow DOM
    collectShadowElements(document, searchTerms);

    // 3. General elements
    collectElements(document, searchTerms, collectionFound);

    // 4. Flatten map
    window.allElementInfo = [];
    collectionFound = getResultMap(window.elementInfoMap);

    // 5. Dedup
    const sameXPath    = processElementsWithXPath(collectionFound);
    const noRepeats    = findUniqueAndOneRepeated(sameXPath);

    // 6. Sort by tag order
    const order = ['input','textarea','button','a','select','label','span','div'];
    const sortedList = order.reduce((acc, type) => {
      const filtered = noRepeats.filter((item) => {
        if (['label','span','div'].includes(type)) return item.tagName === type && item.someText?.trim() !== '';
        return item.tagName === type;
      });
      return [...acc, ...filtered];
    }, []);

    // 7. Post-processing
    findMatLabel(sortedList);
    changeDivToLabelWithSomeText(sortedList);
    normalizeSomeTextForTables(sortedList);

    // 8. Build final list with IDs
    let currentId = 1;
    sortedList.forEach((item) => {
      window.allElementInfo.push({ ...item, id: currentId++ });
    });

    window.elementInfoMap.clear();

    // 9. Return to Java
    doneOnce({
      ok: true,
      elements: window.allElementInfo,
      totalElements: window.allElementInfo.length,
    });
  }

  // ── DOM-ready gate ────────────────────────────────────────────────────────
  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    setTimeout(() => init('Direct Execution'), 0);
  } else {
    document.addEventListener('DOMContentLoaded', () => setTimeout(() => init('DOMContentLoaded'), 0));
    window.addEventListener('load', () => init('load'));
    document.attachEvent?.('onreadystatechange', function () {
      if (document.readyState === 'complete') setTimeout(() => init('onreadystatechange'), 0);
    });
    window.attachEvent?.('onload', () => init('onload'));
  }

  // ── Message listener (kept for parity) ────────────────────────────────────
  window.addEventListener('message', function (event) {
    if (event.origin !== window.trustedOriginURL) return;
    if (event.data.type === 'elementsData') { event.data.data; }
  });

}(
  arguments[0], arguments[1], arguments[2],
  arguments[3], arguments[4], arguments[5],
  arguments[6], arguments[7],
));
