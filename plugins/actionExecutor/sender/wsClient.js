/**
 * actionExecutor -- WebSocket client
 *
 * Connects to the Java backend WebSocket, listens for action commands,
 * and sends back results.  Reuses the same protocol as hoverPick/pageScanner.
 */

/**
 * @param {object} opts
 * @param {number} opts.port
 * @param {string} opts.sessionId
 * @param {function} opts.onCommand   called with parsed command object
 * @param {function} opts.onReady     called when WS opens
 * @returns {{ connect, send, isOpen, cleanup }}
 */
export function buildWsClient({ port, sessionId, onCommand, onReady }) {
  let ws             = null;
  let attempts       = 0;
  const maxAttempts  = 100;
  let pingIntervalId = null;

  function connect() {
    if (attempts >= maxAttempts) return;

    try {
      ws = new WebSocket(`ws://localhost:${port}/websocket?sessionId=${sessionId}`);

      ws.onopen = () => {
        attempts = 0;
        console.log('[actionExecutor] WS connected');

        // Subscribe echo
        try {
          const msg = {
            type: 'echo',
            sessionId,
            operationId: 'actionExecutor',
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

          if (window.__actionExecSessionId !== body.sessionId) return;

          if (body.operationId === 'actionExecutor') {
            onCommand(body);
          }
        } catch (_) {
          console.log('[actionExecutor] Non-JSON message:', raw);
        }
      };

      ws.onerror = () => {};

      ws.onclose = () => {
        if (attempts < maxAttempts) {
          attempts++;
          connect();
        }
      };
    } catch (_) {}
  }

  function startPing() {
    pingIntervalId = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        const msg = {
          type: 'ping-action',
          sessionId: window.__actionExecSessionId,
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

  function sendJson(obj) {
    send(btoa(unescape(encodeURIComponent(JSON.stringify(obj)))));
  }

  function isOpen() {
    return ws && ws.readyState === WebSocket.OPEN;
  }

  function cleanup() {
    try {
      if (pingIntervalId) { clearInterval(pingIntervalId); pingIntervalId = null; }
      if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        try { ws.onclose = null; } catch (_) {}
        ws.close(1000, 'cleanup');
      }
    } catch (_) {}
  }

  return { connect, send, sendJson, isOpen, cleanup };
}
