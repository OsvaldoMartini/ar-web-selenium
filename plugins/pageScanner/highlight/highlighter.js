/**
 * highlight/highlighter.js
 *
 * Applies and reverts element highlight outlines (red/blue toggle) during
 * an active scan session. Stores original outline values keyed by XPath so
 * they can be restored on cleanup.
 *
 * Extracted from the T(), k() functions and the setInterval(_,5000) block
 * in the original monolithic payload.
 */

import { evaluateXPath } from '../scanner/xpathResolver.js';

// Map<xPath, originalOutline>
const _outlineCache = new Map();
// Set<xPath>  — all highlighted xPaths in current session
const _highlighted  = new Set();

let _lastXPath    = null;
let _lastElement  = null;
let _revertTimer  = null;

const OUTLINE_A = '3px solid #FF3131'; // red
const OUTLINE_B = '3px solid #2323FF'; // blue

/**
 * Apply highlight to the element described by elementDetails[0].
 * Toggles between red and blue if the element was already highlighted.
 *
 * @param {object[]} elementDetails — from the WebSocket highlight message
 */
export function applyHighlight(elementDetails) {
  if (!Array.isArray(elementDetails) || elementDetails.length === 0) return;

  const { xPath, cssSelector, coordinates } = elementDetails[0];

  let el = evaluateXPath(xPath);
  if (!el && cssSelector) el = document.querySelector(cssSelector);
  if (!el && coordinates) el = elementFromCoordinates(coordinates);
  if (!el) { revertHighlights(); return; }

  window.__scannerRawClickedElement = el;

  // Restore the previous element's outline if switching to a new one
  if (_lastXPath && _lastXPath !== xPath) {
    const prev = _outlineCache.get(_lastXPath);
    if (_lastElement?.style) _lastElement.style.outline = prev || '';
  }

  // Store original outline on first highlight
  if (!_outlineCache.has(xPath)) {
    _outlineCache.set(xPath, el.style.outline);
    _highlighted.add(xPath);
  }

  const current = _outlineCache.get(xPath) || '';
  if (current.includes('#2323FF'))       el.style.outline = OUTLINE_A;
  else if (current.includes('#FF3131'))  el.style.outline = OUTLINE_B;
  else                                   el.style.outline = OUTLINE_A;

  _lastElement = el;
  _lastXPath   = xPath;
}

/**
 * Revert all highlighted elements to their original outlines.
 * Called by window.revertSearchInjections() and on cleanup.
 */
export function revertHighlights() {
  _outlineCache.forEach((originalOutline, xPath) => {
    const el = evaluateXPath(xPath);
    if (el?.style) el.style.outline = originalOutline;
  });
  _highlighted.forEach(xPath => {
    const orig = _outlineCache.get(xPath);
    const el   = evaluateXPath(xPath);
    if (el?.style) el.style.outline = orig;
  });
}

/**
 * Start the periodic revert timer (every 5 s) to clean up stale highlights
 * if the highlight message stops arriving.
 */
export function startRevertInterval() {
  if (_revertTimer) return;
  _revertTimer = setInterval(revertHighlights, 5_000);
}

export function stopRevertInterval() {
  clearInterval(_revertTimer);
  _revertTimer = null;
}

// ── Internal helpers ──────────────────────────────────────────────────────────

function elementFromCoordinates(coordinates) {
  const [x, y] = coordinates.split(',').map(parseFloat);
  if (isNaN(x) || isNaN(y)) return null;
  return document.elementFromPoint(x, y);
}
