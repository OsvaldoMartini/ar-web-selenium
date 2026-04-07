/**
 * hoverPick — Element identity extraction
 *
 * Inspects a single DOM element and returns a descriptor object with
 * XPath, tag name, attributes, coordinates, and visible text.
 */

import { generateXPath }  from './xpathResolver.js';
import { classifyTag }     from '../classifier/tagClassifier.js';
import { resolveElement as resolveBancaElement, extractSomeText as bancaExtractText, detectBank } from '../../bancaContext/index.js';

/**
 * Check if an element is visually hidden.
 */
export function isHidden(el) {
  const style = window.getComputedStyle(el);
  return (
    style.display === 'none' ||
    style.visibility === 'hidden' ||
    el.hasAttribute('aria-hidden')
  );
}

/**
 * Recursively extract visible text, labels, and titles from an element tree.
 */
export function extractVisibleTextFromHTML(element) {
  if (!element) return { text: [], labels: [], titles: [] };

  const result = { text: new Set(), labels: new Set(), titles: new Set() };

  const isVisible = (el) => {
    const style = window.getComputedStyle(el);
    return !(
      style.display === 'none' ||
      style.visibility === 'hidden' ||
      el.hasAttribute('aria-hidden')
    );
  };

  const isTechnicalPattern = (word) =>
    word.includes('_') || word.includes('--') || word.includes('-');

  // Direct text content
  if (element.textContent?.trim() && isVisible(element)) {
    const filtered = element.textContent.trim().split(/\s+/)
      .filter((w) => !isTechnicalPattern(w)).join(' ').trim();
    if (filtered) result.text.add(filtered);
  }

  // Labels
  element.querySelectorAll('label').forEach((label) => {
    if (isVisible(label) && label.textContent?.trim()) {
      result.labels.add(label.textContent.trim());
    }
    const forAttr = label.getAttribute('for');
    if (forAttr) {
      const inputEl = document.getElementById(forAttr);
      if (inputEl && isVisible(inputEl)) {
        const val = inputEl.value?.trim();
        const ph  = inputEl.placeholder?.trim();
        const raw = val || ph || '';
        if (raw) {
          const f = raw.split(/\s+/).filter((w) => !isTechnicalPattern(w)).join(' ').trim();
          if (f) result.text.add(f);
        }
      }
    }
  });

  // Inline / block elements
  const tags = ['p','h1','h2','h3','h4','h5','h6','li','span','div','strong','em','b','i','blockquote'];
  tags.forEach((tag) => {
    element.querySelectorAll(tag).forEach((child) => {
      if (isVisible(child) && child.textContent?.trim()) {
        const f = child.textContent.trim().split(/\s+/)
          .filter((w) => !isTechnicalPattern(w)).join(' ').trim();
        if (f) result.text.add(f);
      }
    });
  });

  // Links
  element.querySelectorAll('a').forEach((link) => {
    if (isVisible(link) && link.textContent?.trim()) {
      const f = link.textContent.trim().split(/\s+/)
        .filter((w) => !isTechnicalPattern(w)).join(' ').trim();
      if (f) result.text.add(f);
    }
  });

  // Iframes
  element.querySelectorAll('iframe').forEach((iframe) => {
    if (iframe.hasAttribute('title')) {
      const title = iframe.getAttribute('title')?.trim();
      if (title) result.titles.add(title);
    }
    try {
      const doc = iframe.contentDocument || new DOMParser().parseFromString(iframe.srcdoc || '', 'text/html');
      if (doc.body) {
        const sub = extractVisibleTextFromHTML(doc.body);
        sub.titles.forEach((t) => result.titles.add(t));
        sub.text.forEach((t) => result.text.add(t));
        sub.labels.forEach((t) => result.labels.add(t));
      }
    } catch (e) {
      console.log('Could not access iframe content', e);
    }
  });

  return {
    text:   Array.from(result.text),
    labels: Array.from(result.labels),
    titles: Array.from(result.titles),
  };
}

/**
 * Resolve the "someText" label for an element using visible text
 * and attribute priority fallback.
 */
/**
 * Extract someText by looking at what's visually on screen.
 *
 * Strategy: walk UP the DOM from the element, at each level scan
 * child nodes left-to-right / top-to-bottom for the first visible text.
 * Stops as soon as meaningful text is found.
 */
function getVisibleText(tagName, attributeData, element) {
  if (!element) return '';

  // 1. Associated <label for="..."> — most reliable for form controls
  const labelText = findAssociatedLabel(element);
  if (labelText) return labelText;

  // 2. The element itself — direct text or value
  const ownText = getDirectText(element);
  if (ownText) return ownText;

  // 3. aria-label on the element
  const ariaLabel = element.getAttribute('aria-label');
  if (ariaLabel && ariaLabel.trim()) return ariaLabel.trim();

  // 4. placeholder
  const placeholder = element.getAttribute('placeholder');
  if (placeholder && placeholder.trim()) return placeholder.trim();

  // 5. Nearest title (h1-h6, *-title tags) — walk up DOM
  const titleText = findNearestTitle(element);
  if (titleText) return titleText;

  // 6. Siblings — scan left/right for first visible text
  const siblingText = scanSiblingsForText(element);
  if (siblingText) return siblingText;

  // 7. Walk UP — scan children at each level
  let node = element.parentElement;
  for (let i = 0; i < 6 && node; i++) {
    const text = scanChildrenForText(node);
    if (text) return text;
    node = node.parentElement;
  }

  // 8. Attribute fallback
  const fallbackAttrs = ['title', 'alt', 'test-id', 'data-testid', 'name'];
  for (const attr of fallbackAttrs) {
    const val = element.getAttribute(attr);
    if (val && val.trim() && val.trim().length < 100) return val.trim();
  }

  return '';
}

/** Find associated <label> for a form element. */
function findAssociatedLabel(el) {
  if (!el) return '';
  // 1. label[for="id"]
  if (el.id) {
    const label = document.querySelector('label[for="' + el.id + '"]');
    if (label) {
      const text = label.textContent?.trim();
      if (text && text.length > 0 && !label.classList?.contains('avq-visually-hidden')
          && !label.classList?.contains('cdk-visually-hidden')) {
        return text;
      }
    }
  }
  // 2. Wrapping <label> ancestor
  const parentLabel = el.closest('label');
  if (parentLabel) {
    const text = parentLabel.textContent?.trim();
    if (text && text.length > 0) return text;
  }
  // 3. mat-label sibling in mat-form-field
  const matFF = el.closest('mat-form-field');
  if (matFF) {
    const matLabel = matFF.querySelector('mat-label');
    if (matLabel) {
      const text = matLabel.textContent?.trim();
      if (text) return text;
    }
  }
  return '';
}

/**
 * Walk UP the DOM and at each level search for a title element.
 * Matches: *-title tags, [class*=title], h1-h6, [title] attribute,
 * aria-label, label[for], mat-label.
 */
function findNearestTitle(el) {
  const TITLE_SELECTORS = [
    // Custom title tags (Angular Material, framework components)
    'avq-card-title', 'mat-card-title', 'mat-card-header',
    '[class*="title"]', '[class*="header-title"]',
    // Standard headings
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    // Labels
    'mat-label', 'label:not(.avq-visually-hidden):not(.mdc-label):not(.cdk-visually-hidden)',
    // Elements with title attribute
    '[title]',
  ];

  let node = el.parentElement;
  for (let depth = 0; depth < 10 && node; depth++) {
    for (const sel of TITLE_SELECTORS) {
      try {
        const found = node.querySelector(sel);
        if (found && !isHidden(found)) {
          // For [title] attribute, read the attribute itself
          if (sel === '[title]') {
            const attr = found.getAttribute('title')?.trim();
            if (attr && attr.length > 1) return attr.slice(0, 200);
          }
          const text = (found.textContent || '').trim().replace(/\s+/g, ' ');
          if (text && text.length > 1 && text.length < 300) return text.slice(0, 200);
        }
      } catch (_) {}
    }
    node = node.parentElement;
  }
  return '';
}

/** Get direct text of an element (not from children). */
function getDirectText(el) {
  if (!el) return '';
  // Check for value (inputs)
  if (el.value && el.tagName.toLowerCase() !== 'select') return el.value.trim().slice(0, 200);
  // Check for direct text nodes only (not nested children)
  let text = '';
  for (const child of el.childNodes) {
    if (child.nodeType === Node.TEXT_NODE) {
      text += child.textContent;
    }
  }
  text = text.trim().replace(/\s+/g, ' ');
  if (text && text.length > 1) return text.slice(0, 200);
  return '';
}

/** Scan sibling elements for the first visible text. */
function scanSiblingsForText(el) {
  if (!el.parentElement) return '';
  const siblings = Array.from(el.parentElement.children);
  for (const sib of siblings) {
    if (sib === el) continue;
    const text = getVisibleChildText(sib);
    if (text) return text;
  }
  return '';
}

/** Scan direct children of a container for the first meaningful visible text. */
function scanChildrenForText(container) {
  if (!container) return '';
  const children = Array.from(container.children);
  for (const child of children) {
    const text = getVisibleChildText(child);
    if (text) return text;
  }
  return '';
}

/** Get visible text from an element, preferring short meaningful strings. */
function getVisibleChildText(el) {
  if (!el || isHidden(el)) return '';
  const tag = el.tagName.toLowerCase();
  // Skip framework noise
  if (['script', 'style', 'svg', 'path', 'mat-icon'].includes(tag)) return '';
  // Skip visually-hidden elements
  if (el.classList?.contains('avq-visually-hidden') || el.classList?.contains('cdk-visually-hidden')) return '';

  const text = (el.textContent || '').trim().replace(/\s+/g, ' ');
  if (text && text.length > 1 && text.length < 300) return text.slice(0, 200);
  return '';
}

/**
 * Walk up the DOM to find the nearest Angular Material card or meaningful container.
 * Returns the card element, or null if none found.
 */
const CARD_TAGS = [
  'avq-card', 'avq-portfolio-card', 'avq-trading-recent-trade-card',
  'mat-card', 'mat-radio-button', 'mat-checkbox', 'mat-slide-toggle',
  'mat-button-toggle', 'mat-list-option', 'mat-option',
];

/**
 * Extract meaningful text from a card/wrapper element.
 * Looks for title elements, content text, and labels — avoids
 * pulling in the entire card's textContent which includes framework noise.
 */
function extractCardText(card) {
  const parts = [];

  // Priority selectors for meaningful text inside cards
  const selectors = [
    // Angular Material / custom card titles
    'avq-card-title', 'mat-card-title', '[class*="card-title"]',
    '[class*="card-header-title"]', '[class*="title"]',
    // Content / subtitle
    'avq-card-subtitle', 'mat-card-subtitle', '[class*="subtitle"]',
    // Labels
    'label:not(.avq-visually-hidden):not(.mdc-label)',
    // Currency / value
    'avq-currency', '[class*="currency"]',
    // Any text with test-id (usually meaningful)
    '[test-id]',
  ];

  for (const sel of selectors) {
    try {
      card.querySelectorAll(sel).forEach((el) => {
        const text = el.textContent?.trim();
        if (text && text.length > 1 && text.length < 200) {
          parts.push(text.replace(/\s+/g, ' '));
        }
      });
    } catch (_) {}
  }

  // Deduplicate (child text is often repeated in parent)
  const seen = new Set();
  const unique = parts.filter((t) => {
    if (seen.has(t)) return false;
    // Remove entries that are substrings of already-added entries
    for (const s of seen) { if (s.includes(t)) return false; }
    seen.add(t);
    return true;
  });

  return unique.join(' | ') || '';
}

function findCardAncestor(el) {
  let node = el.parentElement;
  for (let i = 0; i < 10 && node; i++) {
    const tag = node.tagName.toLowerCase();
    if (CARD_TAGS.includes(tag)) return node;
    // Also match custom card components with 'card' in the tag name
    if (tag.includes('card') || tag.includes('option') || tag.includes('list-item')) return node;
    node = node.parentElement;
  }
  return null;
}

/**
 * Build a full identity descriptor for a DOM element.
 *
 * @param {boolean} hiddenFields  whether to include hidden elements
 * @param {Element} element       the target DOM element
 * @returns {object|null} descriptor or null if the element should be skipped
 */
export function getElementIdentity(hiddenFields, element) {
  if (!hiddenFields) {
    if (
      (element.offsetWidth === 0 ||
        element.offsetHeight === 0 ||
        window.getComputedStyle(element).visibility === 'hidden') &&
      !(element.tagName.toLowerCase() === 'input' &&
        element.type.toLowerCase() === 'hidden') &&
      // Allow hidden inputs inside Angular Material wrappers (radio, checkbox, etc.)
      !findCardAncestor(element)
    ) {
      return null;
    }
  }

  const attributeData = Array.from(element.attributes).map((attr) => ({
    name: attr.name,
    value: attr.value,
  }));
  const attribId   = element.id || '';
  const attribName = element.name || '';
  const coordinates = `${element.getBoundingClientRect().left.toFixed(2)},${element.getBoundingClientRect().top.toFixed(2)}`;

  let tagName  = element.tagName.toLowerCase();
  const xPath  = generateXPath(element);
  const classified = classifyTag(tagName, xPath, element);
  if (classified !== tagName) tagName = classified;

  // ── Bank context heuristics (auto-detects which bank) ────────────────────
  const bankData = resolveBancaElement(element);

  let someText = '';
  if (bankData) {
    someText = bankData.someText || '';

    // Inject bank metadata into attributeData
    if (bankData.bank)      attributeData.push({ name: 'bank-id', value: bankData.bank });
    if (bankData.someText)  attributeData.push({ name: 'someText', value: bankData.someText });

    // IAM-specific fields
    if (bankData.iamType)   attributeData.push({ name: 'iam-type', value: bankData.iamType });
    if (bankData.label)     attributeData.push({ name: 'bank-label', value: bankData.label });
    if (bankData.pageTitle) attributeData.push({ name: 'bank-page-title', value: bankData.pageTitle });
    if (bankData.cardTitle) attributeData.push({ name: 'bank-card-title', value: bankData.cardTitle });
    if (bankData.inputType) attributeData.push({ name: 'bank-input-type', value: bankData.inputType });
    if (bankData.buttonId)  attributeData.push({ name: 'bank-button-id', value: bankData.buttonId });
    if (bankData.href)      attributeData.push({ name: 'bank-href', value: bankData.href });

    // Card-specific fields
    if (bankData.testId)     attributeData.push({ name: 'card-test-id', value: bankData.testId });
    if (bankData.radioValue) attributeData.push({ name: 'card-radio-value', value: bankData.radioValue });
    if (bankData.currency)   attributeData.push({ name: 'card-currency', value: bankData.currency });
    if (bankData.performance) attributeData.push({ name: 'card-performance', value: bankData.performance });

    // Key-value pairs
    if (bankData.keyValues) {
      bankData.keyValues.forEach(kv => {
        if (kv.label && kv.value) {
          attributeData.push({ name: 'card-kv-' + kv.label.toLowerCase().replace(/\s+/g, '-'), value: kv.value });
        }
      });
    }
    // Actions
    if (bankData.actions) {
      bankData.actions.forEach((act, i) => {
        if (act.ariaLabel) attributeData.push({ name: 'card-action-' + i, value: act.ariaLabel });
      });
    }
  } else {
    // No bank context — standard text extraction
    someText = getVisibleText(tagName, attributeData, element);
  }

  return {
    xPath,
    tagName,
    attributeData,
    customXPath: '',
    attribId,
    attribName,
    coordinates,
    someText,
  };
}
