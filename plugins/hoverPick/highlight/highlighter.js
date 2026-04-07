/**
 * hoverPick — Coordinate overlay and highlight management
 *
 * Creates the floating X/Y tooltip, manages outline save/restore,
 * and provides the mousemove handler.
 */

import { evaluateXPath } from '../scanner/xpathResolver.js';

// ── Shared state ────────────────────────────────────────────────────────────
const originalStyles  = new Map();   // element → original outline
const hoveredXPathMap = new Set();   // XPaths that were highlighted via WS
let coordinatesElement = null;
let lastHoveredElement = null;
let restoreIntervalId  = null;

// ── Public API ──────────────────────────────────────────────────────────────

/**
 * Create and append the floating coordinate tooltip.
 */
export function createCoordinatesOverlay() {
  coordinatesElement = document.createElement('div');
  coordinatesElement.id = 'coordinates';
  coordinatesElement.style.position        = 'fixed';
  coordinatesElement.style.padding         = '10px';
  coordinatesElement.style.backgroundColor = 'rgba(0, 0, 0, 0.5)';
  coordinatesElement.style.color           = 'white';
  coordinatesElement.style.borderRadius    = '5px';
  coordinatesElement.style.fontSize        = '14px';
  coordinatesElement.style.zIndex          = String(Number.MAX_SAFE_INTEGER);
  coordinatesElement.style.cursor          = 'pointer';
  coordinatesElement.textContent           = 'X: 0\u00a0\u00a0\u00a0\u00a0Y: 0';
  document.body.appendChild(coordinatesElement);
  return coordinatesElement;
}

/**
 * Return the live reference to the coordinates overlay.
 */
export function getCoordinatesElement() {
  return coordinatesElement;
}

/**
 * Return the shared originalStyles Map (element → original outline).
 */
export function getOriginalStyles() {
  return originalStyles;
}

/**
 * Return the shared hoveredXPathMap Set.
 */
export function getHoveredXPathMap() {
  return hoveredXPathMap;
}

/**
 * Mousemove handler — updates tooltip and highlights hovered element.
 */
export function showMartiniTooltip(event) {
  if (!coordinatesElement) return;
  const x = event.clientX;
  const y = event.clientY;
  const w = coordinatesElement.offsetWidth;
  const h = coordinatesElement.offsetHeight;

  coordinatesElement.innerHTML = `X: ${x}&nbsp;&nbsp;&nbsp;&nbsp;Y: ${y}`;
  coordinatesElement.style.left = `${x - w / 2}px`;
  coordinatesElement.style.top  = `${y - h / 2}px`;

  const elementBelow = document.elementFromPoint(x, y);

  if (lastHoveredElement !== elementBelow) {
    if (lastHoveredElement) lastHoveredElement.style.outline = '';
    if (elementBelow && elementBelow !== coordinatesElement) {
      elementBelow.style.outline = '3px solid red';
    }
    lastHoveredElement = elementBelow;
  }
}

/**
 * Restore original outline for all tracked elements.
 */
export function restoreOriginalStyles() {
  if (originalStyles && originalStyles.size > 0) {
    originalStyles.forEach((originalStyle, element) => {
      if (element && element.style) element.style.outline = originalStyle;
    });

    if (hoveredXPathMap && hoveredXPathMap.size > 0) {
      hoveredXPathMap.forEach((xPath) => {
        const orig = originalStyles.get(xPath);
        const el = evaluateXPath(xPath);
        if (el && el.style) el.style.outline = orig;
      });
    }
  }
}

/**
 * Remove the coordinate tooltip and clear hover highlight.
 */
export function removeElements() {
  if (lastHoveredElement) {
    lastHoveredElement.style.outline = '';
    lastHoveredElement = null;
  }
  if (coordinatesElement) {
    coordinatesElement.remove();
    coordinatesElement = null;
  }
}

/**
 * Start a 5-second periodic sweep that restores original outlines.
 */
export function startRevertInterval() {
  restoreIntervalId = setInterval(restoreOriginalStyles, 5000);
}

/**
 * Return the interval ID (for cleanup).
 */
export function getRevertIntervalId() {
  return restoreIntervalId;
}

/**
 * Clear the revert interval.
 */
export function clearRevertInterval() {
  if (restoreIntervalId) {
    clearInterval(restoreIntervalId);
    restoreIntervalId = null;
  }
}

/**
 * Show a small toast notification when an element is picked.
 *
 * @param {Element} el  The picked DOM element
 */
export function showPickedToast(el) {
  if (!el) return;

  // Remove any previous toast
  const prev = document.getElementById('__hoverPickToast');
  if (prev) prev.remove();

  const tag  = el.tagName.toLowerCase();
  const id   = el.id ? '#' + el.id : '';
  const name = el.getAttribute('name') ? '[name="' + el.getAttribute('name') + '"]' : '';
  const type = el.getAttribute('type') ? '[type="' + el.getAttribute('type') + '"]' : '';
  const text = (el.textContent || '').trim().substring(0, 40);
  const label = text ? ' \u2014 "' + text + (text.length >= 40 ? '\u2026' : '') + '"' : '';

  const toast = document.createElement('div');
  toast.id = '__hoverPickToast';
  toast.textContent = '\u2714 Selected: <' + tag + id + name + type + '>' + label;

  Object.assign(toast.style, {
    position:        'fixed',
    bottom:          '20px',
    left:            '50%',
    transform:       'translateX(-50%)',
    padding:         '10px 20px',
    backgroundColor: '#D32F2F',
    color:           '#fff',
    borderRadius:    '8px',
    fontSize:        '13px',
    fontFamily:      'monospace',
    zIndex:          String(Number.MAX_SAFE_INTEGER),
    boxShadow:       '0 4px 12px rgba(0,0,0,0.3)',
    opacity:         '0',
    transition:      'opacity 0.3s ease',
    pointerEvents:   'none',
    whiteSpace:      'nowrap',
    maxWidth:        '90vw',
    overflow:        'hidden',
    textOverflow:    'ellipsis',
  });

  document.body.appendChild(toast);

  // Fade in
  requestAnimationFrame(() => { toast.style.opacity = '1'; });

  // Fade out and remove after 3 seconds
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}
