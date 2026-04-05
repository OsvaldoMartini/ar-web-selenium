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
