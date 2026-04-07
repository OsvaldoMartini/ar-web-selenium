/**
 * bancaContext/banks/bancaStato.js — BancaStato (Banca dello Stato del Cantone Ticino)
 *
 * Frameworks detected:
 *   - IAM (Identity Access Management): iam-* tags, login pages, InLinea auth
 *   - AVQ (Avaloq Web Banking): avq-* tags, trading, portfolio cards
 *
 * Detection patterns:
 *   - URLs: bancastato.ch, inlinea.ch
 *   - DOM: iam-loginapp, avq-web-banking-portal, [data-iam-flow-id]
 *   - Tags: iam-*, avq-*, assl-*
 */

// ── Re-export from dedicated resolvers ──────────────────────────────────────
import { resolveIamElement, isIamElement, isIamPage } from './bancaStato/iamResolver.js';
import { resolveAvqCard, findAvqCard }                from './bancaStato/cardResolver.js';

// ── Bank identity ───────────────────────────────────────────────────────────
export const id   = 'bancaStato';
export const name = 'BancaStato';

// ── Detection patterns ──────────────────────────────────────────────────────
export const patterns = {
  urls: [
    'bancastato.ch',
    'inlinea.ch',
    'bancastato',
  ],
  domMarkers: [
    'iam-loginapp',
    'iam-page',
    '[data-iam-flow-id]',
    '[data-iam-domain]',
    'avq-web-banking-portal',
    'assl-root',
  ],
  tagPrefixes: [
    'iam-',
    'avq-',
    'assl-',
  ],
};

// ── Element resolver ────────────────────────────────────────────────────────

/**
 * Resolve structured data from an element.
 * Tries IAM first (login/forms), then AVQ (cards/trading).
 *
 * @param {Element} el
 * @returns {object|null}
 */
export function resolveElement(el) {
  // IAM framework (login, forms, modals)
  const iamData = resolveIamElement(el);
  if (iamData) {
    iamData.bank = id;
    return iamData;
  }

  // AVQ card components (trading, portfolio)
  const cardData = resolveAvqCard(el);
  if (cardData) {
    cardData.bank = id;
    return cardData;
  }

  return null;
}

// ── Text extraction ─────────────────────────────────────────────────────────

/**
 * Extract the best text label for an element.
 *
 * @param {Element} el
 * @param {Array} attributes — [{ name, value }]
 * @returns {string}
 */
export function extractSomeText(el, attributes) {
  // IAM: form labels, button text, card headers
  const iamData = resolveIamElement(el);
  if (iamData && iamData.someText) return iamData.someText;

  // AVQ: card titles, key-value pairs
  const cardData = resolveAvqCard(el);
  if (cardData && cardData.someText) return cardData.someText;

  return '';
}
