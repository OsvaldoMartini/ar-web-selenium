/**
 * pageScanner — plugin entry point
 *
 * Assembles all sub-modules and exposes the IIFE that Selenium injects.
 * Arguments are injected by PerformPreLoad.dynamicLoadElementsDTO():
 *   [0] searchTerms      — string[] filter tags
 *   [1] searchHiddenFields — boolean
 *   [2] port             — WebSocket server port
 *   [3] sessionId        — UUID string
 *   [4] destination      — target session ID for WS routing
 *   [5] operationId      — operation label string
 *   [6] homeBankingId    — int
 *   [7] botJobId         — int
 *
 * Build output: build/scanner.min.js  (produced by Maven / esbuild)
 * Do NOT edit the minified file — edit these source modules instead.
 */

import { buildWsClient }    from './sender/wsClient.js';
import { sendChunks }       from './sender/chunkSender.js';
import { walkDom }          from './scanner/domWalker.js';
import { inspectIframes }   from './scanner/iframeInspector.js';
import { classifyTag }      from './classifier/tagClassifier.js';
import { resolveNameLabel } from './classifier/nameLabelResolver.js';
import { deduplicate }      from './classifier/deduplicator.js';
import { applyHighlight, revertHighlights, startRevertInterval } from './highlight/highlighter.js';

(function (
  searchTerms, searchHiddenFields, port,
  sessionId, destination, operationId,
  homeBankingId, botJobId
) {
  // ── Cleanup any previous injection ────────────────────────────────────────
  try {
    if (typeof window.__scannerToolCleanup === 'function') {
      window.__scannerToolCleanup();
    }
  } catch (_) {}

  // ── Shared state ──────────────────────────────────────────────────────────
  window.searchTerms    = searchTerms;
  window.sessionId      = `${sessionId}`;
  window.destination    = destination;
  window.operationId    = operationId;
  window.homeBankingId  = homeBankingId;
  window.botJobId       = botJobId;
  window.elementInfoMap = new Map();
  window.allElementInfo = [];

  // ── WebSocket client ──────────────────────────────────────────────────────
  const ws = buildWsClient({
    port,
    sessionId: window.sessionId,
    onHighlight: (elementDetails) => applyHighlight(elementDetails),
    onReady:     ()               => runScan(),
  });

  window.cleanupWebSocket     = ws.cleanup;
  window.__scannerToolCleanup = ws.cleanup;
  window.addEventListener('beforeunload', ws.cleanup, { once: true });

  // Start the 5-second periodic highlight revert sweep
  startRevertInterval();

  // ── Main scan ─────────────────────────────────────────────────────────────
  function runScan() {
    window.elementInfoMap = new Map();

    const rawElements = walkDom(document, searchTerms, searchHiddenFields);
    inspectIframes(document, searchTerms, searchHiddenFields, rawElements);

    const classified  = rawElements.map(el => ({
      ...el,
      tagName:      classifyTag(el.tagName, el.xPath),
      ...resolveNameLabel(el),
    }));

    const deduped = deduplicate(classified);
    let id = 1;
    deduped.forEach(el => window.allElementInfo.push({ ...el, id: id++ }));
    window.elementInfoMap.clear();

    if (ws.isOpen()) {
      sendChunks(ws.send, {
        sessionId:     window.sessionId,
        destination,
        operationId,
        homeBankingId,
        botJobId,
        elements:      window.allElementInfo,
      });
    }
  }

  // ── Revert helper (called by Java after scan) ──────────────────────────────
  window.revertSearchInjections = function () {
    setTimeout(() => {
      revertHighlights();
      window.allElementInfo = [];
    }, 3000);
  };

  // ── DOM-ready gate ────────────────────────────────────────────────────────
  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    setTimeout(() => ws.connect(), 0);
  } else {
    document.addEventListener('DOMContentLoaded', () => setTimeout(() => ws.connect(), 0));
    window.addEventListener('load', () => ws.connect());
  }

}(
  arguments[0], arguments[1], arguments[2],
  arguments[3], arguments[4], arguments[5],
  arguments[6], arguments[7]
));
