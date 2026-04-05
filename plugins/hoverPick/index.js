/**
 * hoverPick — plugin entry point
 *
 * Assembles all sub-modules and exposes the IIFE that Selenium injects.
 * Arguments are injected by PerformCloneLoad.dynamicPickOneCloneElementsDTO():
 *   [0] hiddenFields    — boolean (search hidden fields)
 *   [1] port            — WebSocket server port
 *   [2] sessionId       — UUID string
 *   [3] destination     — target session ID for WS routing
 *   [4] operationId     — operation label string
 *   [5] homeBankingId   — int
 *   [6] botJobId        — int
 *   [7] targetOriginURL — origin for postMessage
 *   [8] trustedOriginURL — trusted origin for message validation
 *
 * Build output: build/hoverPick.min.js  (produced by esbuild)
 * Do NOT edit the minified file — edit these source modules instead.
 */

import { buildWsClient }     from './sender/wsClient.js';
import { sendData }           from './sender/dataSender.js';
import { buildClickHandler }  from './handler/clickHandler.js';
import {
  createCoordinatesOverlay,
  showMartiniTooltip,
  restoreOriginalStyles,
  removeElements,
  startRevertInterval,
  clearRevertInterval,
} from './highlight/highlighter.js';

(function (
  hiddenFields, socketPort, sessionId,
  destination, operationId, homeBankingId,
  botJobId, targetOriginURL, trustedOriginURL,
) {
  // ── Cleanup any previous injection ────────────────────────────────────────
  try {
    if (typeof window.__scannerToolCleanup === 'function') {
      window.__scannerToolCleanup();
    }
  } catch (_) {}

  window.__scannerToolCleanup = null;

  // ── Coordinate overlay ────────────────────────────────────────────────────
  createCoordinatesOverlay();

  // ── Shared state ──────────────────────────────────────────────────────────
  window.elementInfoMap  = new Map();
  window.allElementInfo  = [];
  window.destination     = destination;
  window.operationId     = operationId;
  window.homeBankingId   = homeBankingId;
  window.botJobId        = botJobId;
  window.sessionId       = `${sessionId}`;

  // ── WebSocket client ──────────────────────────────────────────────────────
  const ws = buildWsClient({
    port:      socketPort,
    sessionId: window.sessionId,
    onReady:   () => sendData(ws),
  });

  // ── Cleanup function ──────────────────────────────────────────────────────
  const cleanup = () => {
    try {
      document.removeEventListener('mousemove', showMartiniTooltip);
      document.removeEventListener('click', clickHandler);
      clearRevertInterval();
      removeElements();
      restoreOriginalStyles();
      ws.cleanup();
    } catch (_) {}
  };

  window.cleanupWebSocket     = cleanup;
  window.__scannerToolCleanup = cleanup;
  window.addEventListener('beforeunload', cleanup, { once: true });

  // ── Highlight revert sweep ────────────────────────────────────────────────
  startRevertInterval();

  // ── Click handler ─────────────────────────────────────────────────────────
  const clickHandler = buildClickHandler(hiddenFields, ws);

  // ── Revert helper (called by Java) ────────────────────────────────────────
  window.revertHoverPickInjections = function () {
    document.removeEventListener('mousemove', showMartiniTooltip);
    document.removeEventListener('click', clickHandler);
    setTimeout(() => {
      removeElements();
      restoreOriginalStyles();
      window.allElementInfo = [];
    }, 1000);
  };

  // ── Wire up event listeners ───────────────────────────────────────────────
  document.addEventListener('mousemove', showMartiniTooltip);
  document.addEventListener('click', clickHandler);

  // ── Cross-origin messaging ────────────────────────────────────────────────
  window.postMessage({ type: 'myMessage', data: 'some data' }, targetOriginURL);
  window.addEventListener('message', function (event) {
    if (event.origin !== trustedOriginURL) return;
  });

  // ── Connect WebSocket ─────────────────────────────────────────────────────
  ws.connect();

}(
  arguments[0], arguments[1], arguments[2],
  arguments[3], arguments[4], arguments[5],
  arguments[6], arguments[7], arguments[8],
));
