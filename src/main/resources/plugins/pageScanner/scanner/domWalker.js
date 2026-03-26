/**
 * scanner/domWalker.js
 *
 * Entry point for DOM traversal.
 * Walks document.querySelectorAll('*'), delegates shadow DOM and iframe
 * traversal to their respective modules, and returns a flat array of raw
 * element descriptors for the classifier layer.
 *
 * Extracted from the monolithic jsSearchInUse payload (PerformPreLoad.java).
 * Original functions: A(), x(), N(), S()
 */

import { generateXPath } from './xpathResolver.js';

const SKIP_TAGS = new Set(['html', 'body', 'main', 'script', 'meta', 'head', 'style']);
const INTERACTIVE_TAGS = ['input', 'textarea', 'button', 'a', 'select', 'label', 'span', 'div'];

/**
 * Walk the document and return raw element descriptors.
 *
 * @param {Document} doc
 * @param {string[]} searchTerms  — CSS selectors / filter keywords
 * @param {boolean}  includeHidden — include elements with display:none / visibility:hidden
 * @returns {object[]}
 */
export function walkDom(doc, searchTerms, includeHidden) {
  const results = [];

  // Collect matching nodes from the flat DOM
  const candidates = collectCandidates(doc, searchTerms);

  candidates.forEach(el => {
    if (SKIP_TAGS.has(el.tagName.toLowerCase())) return;

    const descriptor = buildDescriptor(el, includeHidden);
    if (!descriptor) return;

    // Shadow DOM branch
    const shadowRoot = resolveShadowRoot(el);
    if (shadowRoot) {
      queryShadowInteractives(shadowRoot).forEach(inner => {
        const d = buildDescriptor(inner, includeHidden, shadowRoot, el);
        if (d) results.push(d);
      });
    } else {
      results.push(descriptor);
    }
  });

  // Walk shadow DOM on every element that has one
  walkShadowDom(doc, searchTerms, results);

  return results;
}

// ── Internal helpers ──────────────────────────────────────────────────────────

function collectCandidates(doc, searchTerms) {
  const nodes = [];
  if (searchTerms && searchTerms.length > 0) {
    searchTerms.forEach(term => {
      if (term.includes('with id'))      nodes.push(...doc.querySelectorAll('[id]'));
      else if (term.includes('with name'))    nodes.push(...doc.querySelectorAll('[name]'));
      else if (term.includes('with test-id')) nodes.push(...doc.querySelectorAll('[test-id]'));
      else                               nodes.push(...doc.querySelectorAll(term));
    });
  } else {
    nodes.push(
      ...Array.from(doc.querySelectorAll('*')).filter(
        el => el.tagName.toLowerCase() !== 'iframe'
      )
    );
  }
  return nodes;
}

/**
 * Build a raw element descriptor object.
 * Returns null if the element is invisible and includeHidden is false.
 */
export function buildDescriptor(el, includeHidden, shadowRoot = null, shadowHost = null) {
  const tag = el.tagName.toLowerCase();
  const isHidden = !includeHidden &&
    !(el.offsetWidth > 0 && el.offsetHeight > 0 &&
      window.getComputedStyle(el).visibility !== 'hidden') &&
    !(tag === 'input' && el.type?.toLowerCase() === 'hidden');

  if (isHidden) return null;

  const attributes = Array.from(el.attributes).map(a => ({ name: a.name, value: a.value }));
  const rect = el.getBoundingClientRect();

  return {
    xPath:        generateXPath(el),
    tagName:      tag,
    attributeData: attributes,
    customXPath:  '',
    attribId:     el.id || '',
    attribName:   el.name || '',
    coordinates:  `${rect.left.toFixed(2)},${rect.top.toFixed(2)}`,
    someText:     extractSomeText(el, attributes),
    iFrameXPath:  '',
    shadowHost:   shadowHost ? describeCssSelector(shadowHost) : '',
    shadowRoot:   String(!!shadowRoot),
    nestedShadow: 'false',
    cssSelector:  describeCssSelector(el),
    attributeValue: '',
    attributeType:  '',
    searchAttributeValue: '',
  };
}

function resolveShadowRoot(el) {
  let node = el;
  while (node && !node.shadowRoot) node = node.parentElement;
  if (node && node.shadowRoot) {
    const root = el.getRootNode?.();
    if (root instanceof ShadowRoot) return root;
  }
  return null;
}

function queryShadowInteractives(shadowRoot) {
  const els = [];
  ['button', 'a'].forEach(tag => els.push(...shadowRoot.querySelectorAll(tag)));
  return els;
}

function walkShadowDom(doc, searchTerms, results) {
  try {
    doc.querySelectorAll('*').forEach(el => {
      if (!el?.shadowRoot) return;
      queryShadowInteractives(el.shadowRoot).forEach(inner => {
        const d = buildDescriptor(inner, false);
        if (d) results.push(d);
      });
      walkShadowDom(el.shadowRoot, searchTerms, results);
    });
  } catch (_) {}
}

function describeCssSelector(el) {
  if (!el) return '';
  let s = el.tagName.toLowerCase();
  if (el.id) s += `#${el.id}`;
  if (el.className && typeof el.className === 'string') {
    s += `.${el.className.replace(/\s+/g, '.')}`;
  }
  return s;
}

function extractSomeText(el, attributes) {
  const ariaLabelledBy = attributes.find(a => a.name === 'aria-labelledby')?.value;
  if (ariaLabelledBy) {
    const labelEl = document.getElementById(ariaLabelledBy);
    if (labelEl) return labelEl.textContent.trim();
  }
  const ariaLabel = attributes.find(a => a.name === 'aria-label')?.value;
  if (ariaLabel) return ariaLabel.trim();

  const computedStyle = window.getComputedStyle(el);
  if (computedStyle.display === 'none' || computedStyle.visibility === 'hidden') return '';

  return (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 200);
}
