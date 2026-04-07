/**
 * bancaStato/cardResolver.js — Heuristic resolver for BancaStato AVQ card components.
 *
 * Detects avq-card, avq-trading-recent-trade-card, avq-portfolio-card, etc.
 * and extracts structured data:
 *
 *   - title:      from avq-card-title or [class*=title]
 *   - subtitle:   from avq-card-subtitle
 *   - testId:     from test-id attribute on the card or title
 *   - keyValues:  from avq-card-content rows (label: value pairs)
 *   - radioValue: from the inner mat-radio-button input value
 *   - radioName:  from mat-radio-group name
 *   - ariaLabel:  from input[aria-label] or button[aria-label]
 *   - actions:    from avq-card-actions buttons
 *   - currency:   from avq-currency
 *
 * Usage:
 *   import { resolveAvqCard } from './bancaStato/cardResolver.js';
 *   const cardData = resolveAvqCard(clickedElement);
 *   if (cardData) { ... }
 */

// ── Card detection ──────────────────────────────────────────────────────────

const AVQ_CARD_TAGS = [
  'avq-card',
  'avq-trading-recent-trade-card',
  'avq-portfolio-card',
  'avq-recent-trade-card-carousel',
  'avq-carousel-item',
];

/**
 * Detect if the element is inside a BancaStato AVQ card component.
 * Returns the card root element, or null.
 */
export function findAvqCard(el) {
  if (!el) return null;

  // Check if the element itself is a card
  const tag = el.tagName?.toLowerCase();
  if (AVQ_CARD_TAGS.includes(tag)) return el;

  // Walk up max 12 levels
  let node = el.parentElement;
  for (let i = 0; i < 12 && node; i++) {
    const t = node.tagName.toLowerCase();
    if (AVQ_CARD_TAGS.includes(t)) return node;
    // Also match any tag starting with avq- that contains 'card'
    if (t.startsWith('avq-') && t.includes('card')) return node;
    node = node.parentElement;
  }
  return null;
}

// ── Full card data extraction ───────────────────────────────────────────────

/**
 * Extract all structured data from an AVQ card.
 *
 * @param {Element} el  — any element inside the card (e.g. the hidden radio input)
 * @returns {object|null} structured card data, or null if not inside a card
 */
export function resolveAvqCard(el) {
  const card = findAvqCard(el);
  if (!card) return null;

  const data = {
    cardTag:    card.tagName.toLowerCase(),
    testId:     '',
    title:      '',
    subtitle:   '',
    ariaLabel:  '',
    radioValue: '',
    radioName:  '',
    radioId:    '',
    keyValues:  [],   // [{ label, value, testId }]
    actions:    [],   // [{ label, testId, ariaLabel }]
    currency:   '',
    performance:'',
    someText:   '',   // computed summary
  };

  // ── test-id on card ─────────────────────────────────────────────────────
  data.testId = findTestId(card);

  // ── Title ───────────────────────────────────────────────────────────────
  const titleEl = card.querySelector(
    'avq-card-title, mat-card-title, [class*="card-title"], [class*="header-title"]'
  );
  if (titleEl) {
    data.title = clean(titleEl.textContent);
    if (!data.testId) data.testId = findTestId(titleEl);
  }

  // ── Subtitle ────────────────────────────────────────────────────────────
  const subtitleEl = card.querySelector(
    'avq-card-subtitle, mat-card-subtitle, [class*="subtitle"]'
  );
  if (subtitleEl) data.subtitle = clean(subtitleEl.textContent);

  // ── Radio button (hidden input) ─────────────────────────────────────────
  const radioInput = card.querySelector('input[type="radio"], input[type="checkbox"]');
  if (radioInput) {
    data.radioValue = radioInput.value || '';
    data.radioName  = radioInput.name || '';
    data.radioId    = radioInput.id || '';
    data.ariaLabel  = radioInput.getAttribute('aria-label') || '';
  }

  // ── Key-value pairs from avq-card-content ───────────────────────────────
  const contentEl = card.querySelector('avq-card-content, mat-card-content, [class*="card-content"]');
  if (contentEl) {
    // Each row is typically a div with two spans: label + value
    const rows = contentEl.querySelectorAll(':scope > div');
    rows.forEach(row => {
      const spans = row.querySelectorAll('span');
      if (spans.length >= 2) {
        const label = clean(spans[0].textContent);
        const value = clean(spans[spans.length - 1].textContent);
        const tid   = findTestId(spans[spans.length - 1]) || findTestId(row);
        if (label || value) {
          data.keyValues.push({ label, value, testId: tid });
        }
      } else if (spans.length === 1) {
        data.keyValues.push({ label: '', value: clean(spans[0].textContent), testId: findTestId(row) });
      }
    });
  }

  // ── Currency ────────────────────────────────────────────────────────────
  const currencyEl = card.querySelector('avq-currency, [class*="currency"]');
  if (currencyEl) {
    const code  = currencyEl.querySelector('[class*="currency-code"]');
    const value = currencyEl.querySelector('[class*="currency-value"]');
    if (code && value) {
      data.currency = clean(code.textContent) + ' ' + clean(value.textContent);
    } else {
      data.currency = clean(currencyEl.textContent);
    }
  }

  // ── Performance ─────────────────────────────────────────────────────────
  const perfEl = card.querySelector('[class*="text-negative"], [class*="text-positive"], [class*="performance"]');
  if (perfEl) data.performance = clean(perfEl.textContent);

  // ── Action buttons ──────────────────────────────────────────────────────
  const actionsEl = card.querySelector('avq-card-actions, [class*="card-actions"]');
  if (actionsEl) {
    actionsEl.querySelectorAll('button, a').forEach(btn => {
      data.actions.push({
        label:     clean(btn.textContent).replace(/^\s*launch\s*$/, 'info-link'),
        testId:    findTestId(btn),
        ariaLabel: btn.getAttribute('aria-label') || '',
      });
    });
  }

  // ── Compute someText summary ────────────────────────────────────────────
  data.someText = computeSomeText(data);

  return data;
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function clean(text) {
  return (text || '').trim().replace(/\s+/g, ' ');
}

function findTestId(el) {
  if (!el) return '';
  return el.getAttribute('test-id') || el.getAttribute('data-testid') || '';
}

/**
 * Build a human-readable someText from the extracted card data.
 * Priority: title > ariaLabel > first key-value > currency
 */
function computeSomeText(data) {
  const parts = [];

  if (data.title) parts.push(data.title);

  // Add key-value summary (compact)
  if (data.keyValues.length > 0) {
    const kvSummary = data.keyValues
      .filter(kv => kv.label && kv.value)
      .map(kv => kv.label + ': ' + kv.value)
      .join(', ');
    if (kvSummary) parts.push(kvSummary);
  }

  if (data.currency) parts.push(data.currency);
  if (data.performance) parts.push(data.performance);

  if (parts.length === 0 && data.ariaLabel) parts.push(data.ariaLabel);

  return parts.join(' | ').slice(0, 300);
}
