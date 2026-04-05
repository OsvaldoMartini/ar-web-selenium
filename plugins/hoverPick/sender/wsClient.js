/**
 * hoverPick — WebSocket lifecycle management
 *
 * Handles connection, reconnection (up to 100 retries), ping keep-alive,
 * highlight message dispatch, and cleanup.
 */

import { evaluateXPath, getElementByCoordinates } from '../scanner/xpathResolver.js';
import {
  getOriginalStyles,
  getHoveredXPathMap,
  restoreOriginalStyles,
} from '../highlight/highlighter.js';

/**
 * Build and return a WebSocket client object.
 *
 * @param {object} opts
 * @param {number} opts.port           WebSocket server port
 * @param {string} opts.sessionId      session identifier
 * @param {function} opts.onReady      called when WS opens (triggers sendingData)
 * @returns {{ connect, send, cleanup, isOpen }}
 */
export function buildWsClient({ port, sessionId, onReady }) {
  let ws             = null;
  let attempts       = 0;
  const maxAttempts  = 100;
  let alreadySent    = false;
  let pingIntervalId = null;
  let previousXPath  = null;
  let previousHighlightedElement = null;

  const originalStyles  = getOriginalStyles();
  const hoveredXPathMap = getHoveredXPathMap();

  function connect() {
    if (attempts >= maxAttempts) return;

    try {
      ws = new WebSocket(`ws://localhost:${port}/websocket?sessionId=${sessionId}`);

      ws.onopen = () => {
        attempts = 0;

        // Subscribe echo
        try {
          const msg = {
            type: 'echo',
            sessionId,
            operationId: 'test echo',
            body: 'subscribe',
          };
          ws.send(btoa(unescape(encodeURIComponent(JSON.stringify(msg)))));
        } catch (_) {}

        if (onReady) onReady();
        startPing();
      };

      ws.onmessage = (event) => {
        let raw = event.data;
        if (raw.endsWith('\0')) raw = raw.slice(0, -1);
        if (!raw) return;

        try {
          const parsed = JSON.parse(raw);
          const body = typeof parsed.body === 'string'
            ? JSON.parse(parsed.body)
            : parsed.body;

          if (window.sessionId !== body.sessionId) return;

          if (body.operationId === 'highlight') {
            const details = Array.isArray(body.details) ? body.details : [];
            handleHighlight(details);
          }

          if (
            (parsed.body && parsed.body.includes?.('cannot be processed')) ||
            (parsed.footer && parsed.footer.includes('cannot be processed'))
          ) {
            // silently ignored
          }
        } catch (_) {
          console.log('Non-JSON message received:', raw);
        }
      };

      ws.onerror = () => {};

      ws.onclose = () => {
        if (attempts < maxAttempts) {
          attempts++;
          if (!alreadySent) connect();
        }
      };
    } catch (_) {}
  }

  function handleHighlight(details) {
    let el = evaluateXPath(details[0].xPath);
    if (!el) el = document.querySelector(details[0].cssSelector);
    if (!el) el = getElementByCoordinates(details[0].coordinates);

    if (el) {
      const currentXPath = details[0].xPath;

      if (previousXPath && previousXPath !== currentXPath) {
        const orig = originalStyles.get(previousXPath);
        if (previousHighlightedElement) {
          previousHighlightedElement.style.outline = orig || '';
        }
      }

      if (!originalStyles.has(currentXPath)) {
        originalStyles.set(currentXPath, el.style.outline);
        hoveredXPathMap.add(currentXPath);
      }

      const orig = originalStyles.get(currentXPath) || '';
      if (orig.includes('#2323FF'))      el.style.outline = '3px solid #FF3131';
      else if (orig.includes('#FF3131')) el.style.outline = '3px solid #2323FF';
      else                               el.style.outline = '3px solid #FF3131';

      previousHighlightedElement = el;
      previousXPath = currentXPath;
    } else {
      restoreOriginalStyles();
    }
  }

  function startPing() {
    pingIntervalId = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        const msg = {
          type: 'ping-hover',
          sessionId: window.sessionId,
          timestamp: new Date().toISOString(),
        };
        try {
          ws.send(btoa(unescape(encodeURIComponent(JSON.stringify(msg)))));
        } catch (_) {}
      }
    }, 15000);
  }

  function send(base64) {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(base64);
  }

  function isOpen() {
    return ws && ws.readyState === WebSocket.OPEN;
  }

  function cleanup() {
    try {
      alreadySent = true;
      if (pingIntervalId) { clearInterval(pingIntervalId); pingIntervalId = null; }
      if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        try { ws.onclose = null; } catch (_) {}
        ws.close(1000, 'cleanup');
      }
      try { window.elementInfoMap?.clear(); } catch (_) {}
      window.allElementInfo = [];
    } catch (_) {}
  }

  return { connect, send, isOpen, cleanup };
}
