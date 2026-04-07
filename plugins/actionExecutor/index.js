/**
 * actionExecutor -- plugin entry point
 *
 * Injected once per page by PerformActionExecutorLoad.  Stays alive
 * and listens for action commands (click, type, select, ...) from
 * the Java backend over WebSocket.  Executes them directly in the
 * browser DOM — bypasses all Selenium visibility / pointer-events checks.
 *
 * Arguments (injected by Selenium executeScript):
 *   [0] port            — WebSocket server port
 *   [1] sessionId       — UUID string
 *   [2] destination     — target session ID for WS routing
 *   [3] operationId     — fixed: 'actionExecutor'
 *   [4] homeBankingId   — int
 *   [5] botJobId        — int
 *
 * Build:
 *   cd plugins/actionExecutor
 *   npx esbuild index.js --bundle --minify --outfile=build/actionExecutor.min.js
 */

import { buildWsClient } from './sender/wsClient.js';
import { executeCommand } from './executor/actionExecutor.js';

(function (socketPort, sessionId, destination, operationId, homeBankingId, botJobId) {

  // ── Clean up previous injection if still alive (page soft-reload) ────────
  if (window.__actionExecutorActive && typeof window.__actionExecutorCleanup === 'function') {
    console.log('[actionExecutor] Cleaning up previous instance before re-injection');
    try { window.__actionExecutorCleanup(); } catch (_) {}
  }
  window.__actionExecutorActive = true;

  // ── Shared state ──────────────────────────────────────────────────────────
  window.__actionExecSessionId = `${sessionId}`;

  console.log(`[actionExecutor] Initializing — port=${socketPort}, session=${sessionId}`);

  // ── WebSocket client ──────────────────────────────────────────────────────
  const ws = buildWsClient({
    port:      socketPort,
    sessionId: window.__actionExecSessionId,
    onReady:   () => {
      console.log('[actionExecutor] Ready — listening for commands');
      // Notify Java that the executor is ready
      ws.sendJson({
        type:          'ACTION_EXECUTOR',
        sessionId:     destination,
        operationId:   'actionExecutorReady',
        homeBankingId: homeBankingId,
        botJobId:      botJobId,
        result:        { success: true, message: 'actionExecutor ready' },
      });
    },
    onCommand: (cmd) => {
      executeCommand(cmd, (result) => {
        // Send result back to Java
        if (ws.isOpen()) {
          ws.sendJson({
            type:          'ACTION_EXECUTOR',
            sessionId:     destination,
            operationId:   'actionExecutorResult',
            homeBankingId: homeBankingId,
            botJobId:      botJobId,
            commandId:     result.commandId || cmd.commandId,
            result:        result,
          });
        }
      });
    },
  });

  // ── Cleanup ───────────────────────────────────────────────────────────────
  const cleanup = () => {
    try {
      ws.cleanup();
      window.__actionExecutorActive = false;
    } catch (_) {}
  };

  window.__actionExecutorCleanup = cleanup;
  window.addEventListener('beforeunload', cleanup, { once: true });

  // ── Connect ───────────────────────────────────────────────────────────────
  ws.connect();

}(
  arguments[0], arguments[1], arguments[2],
  arguments[3], arguments[4], arguments[5],
));
