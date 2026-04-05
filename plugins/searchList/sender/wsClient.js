/**
 * searchList — WebSocket lifecycle management
 *
 * Handles connection, reconnection (up to 100 retries), ping keep-alive,
 * and cleanup. No highlight handling (unlike hoverPick).
 */

export function buildWsClient({ port, sessionId, onReady }) {
  let ws             = null;
  let attempts       = 0;
  const maxAttempts  = 100;
  let alreadySent    = false;
  let pingIntervalId = null;

  function connect() {
    if (attempts >= maxAttempts) return;

    try {
      ws = new WebSocket(`ws://localhost:${port}/websocket?sessionId=${sessionId}`);

      ws.onopen = () => {
        attempts = 0;
        try {
          const msg = { type: 'echo', sessionId, operationId: 'test echo', body: 'subscribe' };
          ws.send(btoa(unescape(encodeURIComponent(JSON.stringify(msg)))));
        } catch (_) {}

        if (onReady) onReady();
        startPing();
      };

      ws.onmessage = (event) => {
        let raw = event.data;
        if (raw.endsWith('\0')) raw = raw.slice(0, -1);
        if (!raw) return;
        try { JSON.parse(raw); } catch (_) {}
      };

      ws.onerror = () => {};

      ws.onclose = () => {
        if (attempts < maxAttempts) { attempts++; if (!alreadySent) connect(); }
      };
    } catch (_) {}
  }

  function startPing() {
    pingIntervalId = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        const msg = { type: 'ping-search', sessionId: window.sessionId, timestamp: new Date().toISOString() };
        try { ws.send(btoa(unescape(encodeURIComponent(JSON.stringify(msg))))); } catch (_) {}
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
      if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        try { ws.onclose = null; } catch (_) {}
        ws.close(1000, 'cleanup');
      }
      if (pingIntervalId) { clearInterval(pingIntervalId); pingIntervalId = null; }
    } catch (_) {}
  }

  return { connect, send, isOpen, cleanup };
}
