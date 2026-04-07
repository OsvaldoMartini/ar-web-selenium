/**
 * hoverPick — Element identity extraction
 *
 * Inspects a single DOM element and returns a descriptor object with
 * XPath, tag name, attributes, coordinates, and visible text.
 */

import { generateXPath }  from './xpathResolver.js';
import { classifyTag }     from '../classifier/tagClassifier.js';

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
function getVisibleText(tagName, attributeData, element) {
  let textResult = '';

  if (element) {
    // For the element itself (if visible)
    if (!isHidden(element)) {
      const extracted = extractVisibleTextFromHTML(element);
      textResult = [...extracted.titles, ...extracted.text, ...extracted.labels]
        .map((t) => t.trim()).filter(Boolean).join('; ');
    }

    // For hidden inputs inside Angular Material cards/wrappers:
    // 1. Try aria-label first (most reliable for Material components)
    if (!textResult) {
      const ariaLabel = element.getAttribute('aria-label');
      if (ariaLabel && ariaLabel.trim()) {
        textResult = ariaLabel.trim();
      }
    }

    // 2. Walk up to the card and extract targeted text (title, content)
    if (!textResult) {
      const cardParent = findCardAncestor(element);
      if (cardParent) {
        textResult = extractCardText(cardParent);
      }
    }
  }

  const attributePriority = [
    'aria-label','aria-labelledby','aria-describedby','placeholder',
    'label','name','title','alt','for','data-label','data-name',
    'data-title','id','data-testid',
  ];

  let firstMeaningfulText = '';

  const getAttrText = (name, value) => {
    if (name === 'aria-labelledby' || name === 'aria-describedby') {
      const ref = document.getElementById(value);
      if (ref && !isHidden(ref)) return ref.textContent.trim();
    }
    return value.trim();
  };

  if (textResult && !/^\..*\{.*\}$/.test(textResult)) {
    firstMeaningfulText = textResult;
  } else {
    const titleAttr = attributeData.find(({ name }) => name === 'title');
    if (titleAttr) firstMeaningfulText = getAttrText(titleAttr.name, titleAttr.value);

    if (!firstMeaningfulText) {
      for (const attr of attributePriority) {
        const found = attributeData.find(({ name }) => name === attr);
        if (found) {
          firstMeaningfulText = getAttrText(found.name, found.value);
          if (firstMeaningfulText) break;
        }
      }
    }
  }

  return firstMeaningfulText;
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
  const someText = getVisibleText(tagName, attributeData, element);
  const xPath  = generateXPath(element);
  const classified = classifyTag(tagName, xPath, element);
  if (classified !== tagName) tagName = classified;

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
