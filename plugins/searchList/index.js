/**
 * searchList — plugin entry point
 *
 * Synchronous element list extraction with WebSocket chunk sender.
 * Scans the page and sends all found elements as chunked JSON payloads
 * over WebSocket (type: UPDATE_LIST_ELEMENTS).
 *
 * Arguments injected by Selenium executeScript():
 *   [0] searchTerms        — string[] filter tags
 *   [1] searchHiddenFields — boolean
 *   [2] port               — WebSocket server port
 *   [3] sessionId          — UUID string
 *   [4] destination        — target session ID for WS routing
 *   [5] operationId        — operation label string
 *   [6] homeBankingId      — int
 *   [7] botJobId           — int
 *
 * Build output: build/searchList.min.js  (produced by esbuild)
 * Do NOT edit the minified file — edit these source modules instead.
 */

import { buildWsClient }     from './sender/wsClient.js';
import { sendChunks }         from './sender/chunkSender.js';
import { collectElements }    from './scanner/domWalker.js';
import { collectIframeElements } from './scanner/iframeInspector.js';
import { getResultMap, processElementsWithXPath, findUniqueAndOneRepeated } from './classifier/deduplicator.js';
import { findMatLabel, changeDivToLabelWithSomeText } from './classifier/nameLabelResolver.js';

(function (
  searchTerms, hiddenFields, socketPort,
  sessionId, destination, operationId,
  homeBankingId, botJobId,
) {
  // ── Cleanup previous injection ────────────────────────────────────────────
  try { if (typeof window.__scannerToolCleanup === 'function') window.__scannerToolCleanup(); } catch (_) {}
  window.__scannerToolCleanup = null;

  // ── Shared state ──────────────────────────────────────────────────────────
  window.elementInfoMap       = new Map();
  window.searchTerms          = searchTerms;
  window.allElementInfo       = [];
  window.destination          = destination;
  window.operationId          = operationId;
  window.homeBankingId        = homeBankingId;
  window.botJobId             = botJobId;
  window.sessionId            = `${sessionId}`;
  window.__slSync_hiddenFields = hiddenFields;

  let pageFullyLoaded = false;

  // ── WebSocket client ──────────────────────────────────────────────────────
  const ws = buildWsClient({
    port:      socketPort,
    sessionId: window.sessionId,
    onReady:   () => runScan(),
  });

  window.cleanupWebSocket     = ws.cleanup;
  window.__scannerToolCleanup = ws.cleanup;
  window.addEventListener('beforeunload', ws.cleanup, { once: true });

  // ── Main scan ─────────────────────────────────────────────────────────────
  function runScan() {
    window.elementInfoMap = new Map();
    let collectionFound = [];

    // 1. Iframes
    collectIframeElements(document, collectionFound, searchTerms);

    // 2. General elements
    collectElements(document, searchTerms, collectionFound);

    // 3. Flatten map
    window.allElementInfo = [];
    collectionFound = getResultMap(window.elementInfoMap);

    // 4. Dedup
    const sameXPath = processElementsWithXPath(collectionFound);
    const noRepeats = findUniqueAndOneRepeated(sameXPath);

    // 5. Sort by tag order
    const order = ['input','textarea','button','a','select','label','span','div'];
    const sortedList = order.reduce((acc, type) => {
      const filtered = noRepeats.filter((item) => {
        if (['label','span','div'].includes(type)) return item.tagName === type && item.someText?.trim() !== '';
        return item.tagName === type;
      });
      return [...acc, ...filtered];
    }, []);

    // 6. Post-processing
    findMatLabel(sortedList);
    changeDivToLabelWithSomeText(sortedList);

    // 7. Build final list with IDs
    let currentId = 1;
    sortedList.forEach((item) => {
      window.allElementInfo.push({ ...item, id: currentId++ });
    });
    window.elementInfoMap.clear();

    // 8. Send via WebSocket in chunks
    if (ws.isOpen()) {
      sendChunks(ws.send, {
        sessionId:    window.sessionId,
        destination,
        operationId,
        homeBankingId,
        botJobId,
        elements:     window.allElementInfo,
      });
    }
  }

  // ── DOM-ready gate ────────────────────────────────────────────────────────
  function init(eventName) {
    if (pageFullyLoaded) {
      if (['DOMContentLoaded','onreadystatechange','load','onload','Direct Execution'].includes(eventName) ||
          ['complete','interactive'].includes(document.readyState)) {
        ws.connect();
      }
    }
    pageFullyLoaded = true;
  }

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

  // Also connect immediately (matches original behaviour)
  ws.connect();

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
