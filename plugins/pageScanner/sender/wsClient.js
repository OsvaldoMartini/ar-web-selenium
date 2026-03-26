/**
 * sender/wsClient.js
 *
 * WebSocket connection lifecycle for the pageScanner plugin.
 * Handles: connect, reconnect (up to 100 retries), 15-second ping,
 * highlight message dispatch, and cleanup on tab unload.
 *
 * Extracted from the b() and g() functions in the original monolithic payload.
 */

const MAX_RETRIES    = 100;
const PING_INTERVAL  = 15_000; // ms
const RECONNECT_WAIT = 0;      // immediate retry (mirrors original)

/**
 * @param {object} opts
 * @param {number}   opts.port
 * @param {string}   opts.sessionId
 * @param {function} opts.onHighlight  — called with elementDetails array
 * @param {function} opts.onReady      — called when WS is open and subscribed
 * @returns {{ connect, cleanup, send, isOpen }}
 */
export function buildWsClient({ port, sessionId, onHighlight, onReady }) {
  let ws         = null;
  let retries    = 0;
  let pingTimer  = null;
  let destroyed  = false;

  function connect() {
    if (retries >= MAX_RETRIES) return;
    try {
      ws = new WebSocket(`ws://localhost:${port}/websocket?sessionId=${sessionId}`);

      ws.onopen = () => {
        retries = 0;
        _send({ type: 'echo', sessionId, operationId: 'test echo', body: 'subscribe' });
        onReady?.();
        pingTimer = setInterval(() => {
          if (ws?.readyState === WebSocket.OPEN) {
            _send({ type: 'ping-search', sessionId, timestamp: new Date().toISOString() });
          }
        }, PING_INTERVAL);
      };

      ws.onmessage = (event) => {
        let data = event.data;
        if (data.endsWith('\0')) data = data.slice(0, -1);
        if (!data) return;
        try {
          const msg  = JSON.parse(data);
          const body = typeof msg.body === 'string' ? JSON.parse(msg.body) : msg.body;
          if (body?.sessionId !== sessionId) return;

          if (body.operationId === 'highlight') {
            const details = Array.isArray(body.elementDetails) ? body.elementDetails : [];
            onHighlight?.(details);
          }
        } catch (_) {}
      };

      ws.onerror = () => {};

      ws.onclose = () => {
        clearInterval(pingTimer);
        pingTimer = null;
        if (!destroyed && retries < MAX_RETRIES) {
          retries++;
          connect();
        }
      };
    } catch (_) {}
  }

  function cleanup() {
    try {
      destroyed = true;
      clearInterval(pingTimer);
      pingTimer = null;
      if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        try { ws.onclose = null; } catch (_) {}
        ws.close(1000, 'cleanup');
      }
    } catch (_) {}
  }

  function _send(payload) {
    if (ws?.readyState !== WebSocket.OPEN) return;
    try {
      ws.send(btoa(unescape(encodeURIComponent(JSON.stringify(payload)))));
    } catch (_) {}
  }

  return {
    connect,
    cleanup,
    send:   _send,
    isOpen: () => ws?.readyState === WebSocket.OPEN,
  };
}
