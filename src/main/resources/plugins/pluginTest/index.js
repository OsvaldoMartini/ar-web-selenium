/**
 * pluginTest — plugin entry point
 *
 * A minimal smoke-test plugin that confirms the plugin folder structure
 * and classpath loading mechanism are working correctly.
 *
 * Injected by ARScannedElementPane.runPluginTest() via Selenium JavascriptExecutor.
 * No arguments required.
 *
 * Validation behaviour:
 *   ✅ Success: console.log("plugin test") + green floating card (4 s)
 *   ⚠  Failure: handled on the Java side (Alert dialog)
 *
 * Build output: build/pluginTest.min.js  (produced by esbuild or Maven)
 *   npx esbuild index.js --bundle --minify --outfile=build/pluginTest.min.js
 */

(function () {
  // ── Console confirmation (required by plugin test spec) ──────────────
  console.log('plugin test');

  // ── Clean any previous test overlay ──────────────────────────────────
  const existing = document.getElementById('__ar-plugin-test-overlay');
  if (existing) existing.remove();

  // ── Inject a floating status card ────────────────────────────────────
  const overlay = document.createElement('div');
  overlay.id = '__ar-plugin-test-overlay';
  overlay.style.cssText = [
    'position: fixed',
    'top: 24px',
    'right: 24px',
    'z-index: 2147483647',
    'display: flex',
    'align-items: center',
    'gap: 12px',
    'padding: 14px 20px',
    'border-radius: 10px',
    'background: #1a1a2e',
    'border: 1.5px solid #22c55e',
    'box-shadow: 0 4px 24px rgba(0,0,0,0.45)',
    'font-family: system-ui, -apple-system, sans-serif',
    'font-size: 14px',
    'font-weight: 500',
    'color: #f0fdf4',
    'cursor: pointer',
    'user-select: none',
    'transition: opacity 0.3s ease',
  ].join(';');

  const dot = document.createElement('span');
  dot.style.cssText = [
    'display: inline-block',
    'width: 10px',
    'height: 10px',
    'border-radius: 50%',
    'background: #22c55e',
    'box-shadow: 0 0 8px #22c55e',
    'flex-shrink: 0',
  ].join(';');

  const label = document.createElement('span');
  label.textContent = 'Plugin loaded \u2713';

  const sub = document.createElement('span');
  sub.style.cssText = 'font-size:11px; color:#86efac; margin-left:4px;';
  sub.textContent = '(pluginTest)';

  const close = document.createElement('span');
  close.textContent = '\u00D7';
  close.style.cssText = [
    'margin-left: 12px',
    'font-size: 16px',
    'color: #6b7280',
    'line-height: 1',
  ].join(';');

  overlay.appendChild(dot);
  overlay.appendChild(label);
  overlay.appendChild(sub);
  overlay.appendChild(close);
  document.body.appendChild(overlay);

  // Auto-dismiss after 4 s
  const timer = setTimeout(() => {
    overlay.style.opacity = '0';
    setTimeout(() => overlay.remove(), 300);
  }, 4000);

  // Manual close
  overlay.addEventListener('click', () => {
    clearTimeout(timer);
    overlay.style.opacity = '0';
    setTimeout(() => overlay.remove(), 300);
  });
}());
