/**
 * bancaStato/iamResolver.js — Heuristic resolver for BancaStato IAM/InLinea components.
 *
 * Detects iam-* custom elements (login page, forms, modals) and extracts
 * structured data: labels, inputs, buttons, cards, messages.
 *
 * IAM page structure:
 *   iam-loginapp > iam-page > iam-card > iam-form > iam-input-row > iam-row-with-label
 *
 * Form row pattern:
 *   <iam-row-with-label>
 *     <div class="iam-form-group iam-row">
 *       <label class="iam-col-form-label" for="username">User number</label>
 *       <div><input class="iam-form-control" id="username" ...></div>
 *     </div>
 *   </iam-row-with-label>
 *
 * Usage:
 *   import { resolveIamElement, isIamPage } from './bancaStato/iamResolver.js';
 *   if (isIamPage()) { const data = resolveIamElement(el); }
 */

// ── Detection ───────────────────────────────────────────────────────────────

/** Check if the current page is a BancaStato IAM page. */
export function isIamPage() {
  return !!document.querySelector('iam-loginapp, iam-page, [data-iam-flow-id]');
}

/** Check if an element is inside or part of the IAM framework. */
export function isIamElement(el) {
  if (!el) return false;
  const tag = el.tagName?.toLowerCase() || '';
  if (tag.startsWith('iam-')) return true;
  if (el.closest('iam-loginapp, iam-page, iam-card, iam-form')) return true;
  if (el.className?.includes?.('iam-')) return true;
  return false;
}

// ── Element resolution ──────────────────────────────────────────────────────

/**
 * Extract structured data from any IAM element.
 *
 * @param {Element} el — any element on the page
 * @returns {object|null} structured data, or null if not an IAM element
 */
export function resolveIamElement(el) {
  if (!el || !isIamElement(el)) return null;

  const tag = el.tagName.toLowerCase();

  // Determine what kind of IAM component we're dealing with
  if (isFormInput(el))       return resolveFormInput(el);
  if (isIamButton(el))       return resolveButton(el);
  if (isIamCard(el))         return resolveCard(el);
  if (isIamLink(el))         return resolveLink(el);
  if (isIamMessage(el))      return resolveMessage(el);
  if (isLanguageSwitch(el))  return resolveLanguageSwitch(el);

  // Generic fallback — walk up to find the nearest meaningful container
  const row = el.closest('iam-row-with-label, iam-input-row');
  if (row) return resolveFormRow(row);

  const card = el.closest('iam-card, .iam-card');
  if (card) return resolveCard(card);

  return null;
}

// ── Form input resolver ─────────────────────────────────────────────────────

function isFormInput(el) {
  const tag = el.tagName.toLowerCase();
  return (tag === 'input' || tag === 'select' || tag === 'textarea')
    && el.closest('iam-form, iam-input-row, iam-row-with-label');
}

function resolveFormInput(el) {
  const row = el.closest('iam-row-with-label, iam-input-row, .iam-form-group');
  const label = findIamLabel(el, row);

  return {
    iamType:    'form-input',
    inputType:  el.type || el.tagName.toLowerCase(),
    inputId:    el.id || '',
    inputName:  el.name || '',
    label:      label,
    value:      el.value || '',
    placeholder: el.placeholder || '',
    disabled:   el.disabled || false,
    required:   el.required || el.getAttribute('aria-required') === 'true',
    someText:   label || el.placeholder || el.name || el.id || '',
    pageTitle:  getPageTitle(),
    cardTitle:  getCardTitle(el),
  };
}

function resolveFormRow(row) {
  const label = row.querySelector('label');
  const input = row.querySelector('input, select, textarea');

  return {
    iamType:    'form-row',
    label:      clean(label?.textContent),
    inputType:  input?.type || '',
    inputId:    input?.id || '',
    inputName:  input?.name || '',
    value:      input?.value || '',
    someText:   clean(label?.textContent) || input?.name || input?.id || '',
    pageTitle:  getPageTitle(),
    cardTitle:  getCardTitle(row),
  };
}

// ── Button resolver ─────────────────────────────────────────────────────────

function isIamButton(el) {
  const tag = el.tagName.toLowerCase();
  return tag === 'button' || el.closest('iam-button, iam-button-row');
}

function resolveButton(el) {
  const btn = el.tagName.toLowerCase() === 'button' ? el : el.querySelector('button');
  if (!btn) return { iamType: 'button', someText: '', label: '' };

  return {
    iamType:    'button',
    buttonId:   btn.id || '',
    label:      clean(btn.textContent),
    title:      btn.title || '',
    disabled:   btn.disabled || false,
    type:       btn.type || 'button',
    someText:   clean(btn.textContent) || btn.title || btn.id || '',
    pageTitle:  getPageTitle(),
  };
}

// ── Card resolver ───────────────────────────────────────────────────────────

function isIamCard(el) {
  return el.tagName?.toLowerCase() === 'iam-card'
    || el.classList?.contains('iam-card')
    || !!el.closest('iam-card, .iam-card');
}

function resolveCard(el) {
  const card = el.closest('iam-card, .iam-card') || el;
  const header = card.querySelector('.iam-card-header');
  const body = card.querySelector('.iam-card-body');

  // Extract form fields from the card
  const fields = [];
  card.querySelectorAll('iam-row-with-label, iam-input-row').forEach(row => {
    const label = row.querySelector('label');
    const input = row.querySelector('input, select, textarea');
    if (label || input) {
      fields.push({
        label: clean(label?.textContent),
        inputId: input?.id || '',
        inputType: input?.type || '',
        inputName: input?.name || '',
      });
    }
  });

  return {
    iamType:    'card',
    title:      clean(header?.textContent),
    fields:     fields,
    someText:   clean(header?.textContent) || '',
    pageTitle:  getPageTitle(),
  };
}

// ── Link resolver ───────────────────────────────────────────────────────────

function isIamLink(el) {
  return el.tagName?.toLowerCase() === 'a'
    && (el.classList?.contains('iam-general-link')
    || el.closest('iam-card, iam-form, .iam-text-message'));
}

function resolveLink(el) {
  return {
    iamType:    'link',
    text:       clean(el.textContent),
    href:       el.href || '',
    linkId:     el.id || '',
    someText:   clean(el.textContent) || el.id || '',
    pageTitle:  getPageTitle(),
  };
}

// ── Message resolver ────────────────────────────────────────────────────────

function isIamMessage(el) {
  return el.closest('iam-maintenance-message, iam-text-message, .iam-text-message, .iam-page-alerts');
}

function resolveMessage(el) {
  const container = el.closest('iam-text-message, .iam-text-message') || el;
  return {
    iamType:    'message',
    text:       clean(container.textContent).slice(0, 300),
    messageId:  container.id || '',
    someText:   clean(container.textContent).slice(0, 200) || 'message',
    pageTitle:  getPageTitle(),
  };
}

// ── Language switcher ───────────────────────────────────────────────────────

function isLanguageSwitch(el) {
  return !!el.closest('.iam-language-switcher, #iamLanguageSwitcher');
}

function resolveLanguageSwitch(el) {
  const link = el.closest('a[data-lang]') || el.querySelector('a[data-lang]');
  const active = document.querySelector('.iam-language-switcher .iam-active');

  return {
    iamType:        'language-switch',
    selectedLang:   link?.getAttribute('data-lang') || '',
    selectedText:   clean(link?.textContent),
    currentLang:    active?.getAttribute('data-lang') || '',
    someText:       clean(link?.textContent) || 'language',
    pageTitle:      getPageTitle(),
  };
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function clean(text) {
  return (text || '').trim().replace(/\s+/g, ' ');
}

/** Find the label for a form input within an IAM row. */
function findIamLabel(input, row) {
  // 1. label[for="id"]
  if (input.id) {
    const label = document.querySelector('label[for="' + input.id + '"]');
    if (label) {
      const text = clean(label.textContent);
      if (text) return text;
    }
  }
  // 2. label inside the same row
  if (row) {
    const label = row.querySelector('label.iam-col-form-label, label');
    if (label) return clean(label.textContent);
  }
  // 3. Previous sibling label
  const prev = input.previousElementSibling;
  if (prev?.tagName?.toLowerCase() === 'label') return clean(prev.textContent);

  return '';
}

/** Get the page title from the card header or <title> tag. */
function getPageTitle() {
  const cardHeader = document.querySelector('.iam-card-header');
  if (cardHeader) {
    // Get just the first text line (before any sub-elements)
    const clone = cardHeader.cloneNode(true);
    const sub = clone.querySelector('.iam-general-small');
    if (sub) sub.remove();
    const text = clean(clone.textContent);
    if (text) return text;
  }
  return document.title || '';
}

/** Get the card title for an element inside an iam-card. */
function getCardTitle(el) {
  const card = el?.closest('iam-card, .iam-card');
  if (!card) return '';
  const header = card.querySelector('.iam-card-header');
  return clean(header?.textContent) || '';
}
