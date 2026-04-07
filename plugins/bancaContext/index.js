/**
 * bancaContext/index.js — Bank pattern detection and dispatch.
 *
 * Central registry for bank-specific heuristics. Each bank defines
 * detection patterns (URL, DOM markers, custom tags) and resolvers.
 *
 * Plugins (hoverPick, pageScanner) call:
 *   resolveElement(el)   → structured data or null
 *   extractSomeText(el)  → best text label or ''
 *   detectBank()         → { id, name } or null
 *
 * To add a new bank:
 *   1. Create a file in banks/ (e.g. banks/ubsResolver.js)
 *   2. Export: patterns, resolveElement, extractSomeText
 *   3. Register it in the BANKS array below
 */

import * as bancaStato from './banks/bancaStato.js';

// ── Bank registry ───────────────────────────────────────────────────────────
// Each bank must export:
//   patterns:        { urls: [], domMarkers: [], tags: [] }
//   resolveElement:  (el) => { someText, ...metadata } | null
//   extractSomeText: (el, attributes) => string

const BANKS = [
  bancaStato,
  // Add more banks here:
  // ubsBank,
  // creditSuisse,
  // postFinance,
  // raiffeisen,
];

// ── Cached detection ────────────────────────────────────────────────────────
let _detectedBank = undefined; // undefined = not checked, null = no match

/**
 * Detect which bank the current page belongs to.
 * Checks URL patterns, DOM markers, and custom tags.
 * Result is cached for the page lifetime.
 *
 * @returns {{ id: string, name: string, bank: object } | null}
 */
export function detectBank() {
  if (_detectedBank !== undefined) return _detectedBank;

  const url = window.location?.href || '';
  const hostname = window.location?.hostname || '';

  for (const bank of BANKS) {
    const p = bank.patterns;
    if (!p) continue;

    // URL match
    if (p.urls?.some(u => hostname.includes(u) || url.includes(u))) {
      _detectedBank = { id: bank.id, name: bank.name, bank };
      return _detectedBank;
    }

    // DOM marker match (elements that identify this bank's framework)
    if (p.domMarkers?.some(sel => {
      try { return !!document.querySelector(sel); } catch (_) { return false; }
    })) {
      _detectedBank = { id: bank.id, name: bank.name, bank };
      return _detectedBank;
    }

    // Custom tag prefix match
    if (p.tagPrefixes?.some(prefix => {
      try { return !!document.querySelector(prefix + '*'); } catch (_) {
        // Fallback: scan first 50 elements for matching tag prefix
        const els = document.querySelectorAll('body *');
        for (let i = 0; i < Math.min(els.length, 50); i++) {
          if (els[i].tagName.toLowerCase().startsWith(prefix)) return true;
        }
        return false;
      }
    })) {
      _detectedBank = { id: bank.id, name: bank.name, bank };
      return _detectedBank;
    }
  }

  _detectedBank = null;
  return null;
}

/**
 * Reset detection cache. Call after page navigation.
 */
export function resetDetection() {
  _detectedBank = undefined;
}

/**
 * Resolve structured data from an element using the detected bank's heuristics.
 *
 * @param {Element} el
 * @returns {object|null} — { someText, iamType/cardType, ...metadata } or null
 */
export function resolveElement(el) {
  const detected = detectBank();
  if (!detected) return null;

  try {
    return detected.bank.resolveElement(el);
  } catch (_) {
    return null;
  }
}

/**
 * Extract the best text label for an element using bank-specific heuristics.
 *
 * @param {Element} el
 * @param {Array} attributes — [{ name, value }]
 * @returns {string}
 */
export function extractSomeText(el, attributes) {
  const detected = detectBank();
  if (!detected) return '';

  try {
    return detected.bank.extractSomeText(el, attributes) || '';
  } catch (_) {
    return '';
  }
}
