/**
 * searchListAsync — DOM walker, element collection, shadow DOM traversal
 */

import { getElementIdentity } from './elementIdentity.js';
import { elementDTO }         from '../classifier/nameLabelResolver.js';
import { defineNameTitlesJs }  from '../classifier/nameResolver.js';

// ── Public API ──────────────────────────────────────────────────────────────

/**
 * Collect elements from the document based on search terms.
 */
export function collectElements(doc, searchTerms, collectionFound) {
  if (searchTerms.length > 0) {
    searchTerms.forEach((selector) => {
      if (selector.includes('with id'))            collectionFound.push(...Array.from(doc.querySelectorAll('[id]')));
      else if (selector.includes('with name'))     collectionFound.push(...Array.from(doc.querySelectorAll('[name]')));
      else if (selector.includes('with test-id'))  collectionFound.push(...Array.from(doc.querySelectorAll('[test-id]')));
      else                                          collectionFound.push(...Array.from(doc.querySelectorAll(selector)));
    });
  } else {
    collectionFound.push(
      ...Array.from(doc.querySelectorAll('*')).filter((el) => el.tagName.toLowerCase() !== 'iframe'),
    );
  }

  collectionFound.forEach((element) => {
    if (['html','body','main','script','meta','head','style'].includes(element.tagName.toLowerCase())) return;
    const id = getElementIdentity(window.__slAsync_hiddenFields, element);
    if (id) filterSearchTerms(element, 'tagName-Found', id.xPath, id, searchTerms);
  });
}

/**
 * Recursively collect shadow DOM elements (open roots only).
 */
export function collectShadowElements(rootNode, searchTerms) {
  try {
    rootNode.querySelectorAll('*').forEach((host) => {
      if (!host || !host.shadowRoot) return;
      const sr = host.shadowRoot;

      findClickableElements(sr).forEach((shadowEl) => {
        const id = getElementIdentity(window.__slAsync_hiddenFields, shadowEl);
        if (!id) return;
        filterSearchTerms(shadowEl, 'Shadow-Child', id.xPath, id, searchTerms);
      });

      collectShadowElements(sr, searchTerms);
    });
  } catch (_) {}
}

// ── Filtering & push ────────────────────────────────────────────────────────

export function filterSearchTerms(element, typeDTO, referXPath, elementIdentity, searchTerms) {
  if (
    searchTerms.length === 0 ||
    (!searchTerms.includes('with id') && !searchTerms.includes('with name') &&
     !searchTerms.includes('with text') && !searchTerms.includes('with test-id'))
  ) {
    resolveShadowAndPush(element, elementIdentity, referXPath, typeDTO);
    return;
  }

  searchTerms.forEach((term) => {
    let matches = false;
    if (term.includes('with id') && elementIdentity.attributeData.some((a) => a.name === 'id')) matches = true;
    else if (term.includes('with name') && elementIdentity.attributeData.some((a) => a.name === 'name')) matches = true;
    else if (term.includes('with text') && elementIdentity.someText.length > 0) matches = true;

    if (matches) resolveShadowAndPush(element, elementIdentity, referXPath, typeDTO);
  });
}

function resolveShadowAndPush(element, elementIdentity, referXPath, typeDTO) {
  let shadowHost = null;
  const root = element.getRootNode && element.getRootNode();
  if (root && root instanceof ShadowRoot) {
    shadowHost = root.host;
  } else {
    shadowHost = element;
    while (shadowHost && !shadowHost.shadowRoot) shadowHost = shadowHost.parentElement;
  }

  if (shadowHost && shadowHost.shadowRoot) {
    const sr = shadowHost.shadowRoot;
    findClickableElements(sr).forEach((el) => {
      const id = getElementIdentity(window.__slAsync_hiddenFields, el);
      if (id) pushElement(el, id, id.xPath, typeDTO, shadowHost, sr);
    });
  } else {
    pushElement(element, elementIdentity, referXPath, typeDTO, null, null);
  }
}

export function pushElement(element, identityTemp, referXPath, typeDTO, shadowHost, shadowRoot) {
  let shadowHostSelector = '';
  let elementCssSelector = '';
  const shadowPath = [];

  function buildCss(el) {
    if (!el) return '';
    let sel = el.tagName.toLowerCase();
    if (el.id) sel += `#${el.id}`;
    if (el.className && typeof el.className === 'string') sel += `.${el.className.replace(/\s+/g, '.')}`;
    return sel;
  }

  let currentHost = shadowHost;
  while (currentHost) {
    shadowPath.unshift(buildCss(currentHost));
    currentHost = currentHost.parentNode instanceof ShadowRoot ? currentHost.parentNode.host : null;
  }

  if (shadowHost) shadowHostSelector = buildCss(shadowHost);
  if (element)    elementCssSelector = buildCss(element);

  let cssSelector = elementCssSelector;
  if (shadowPath.length > 0) {
    cssSelector = shadowPath.reduceRight((acc, host) => `${host} ${acc}`, elementCssSelector);
  }

  const identity = {
    ...identityTemp,
    shadowHost:   shadowHostSelector,
    shadowRoot:   String(!!shadowRoot),
    nestedShadow: String(shadowPath.length > 1),
    cssSelector:  elementCssSelector,
  };

  const names = defineNameTitlesJs(identity) || { nameLabel: '', nameField: '', definedName: '' };

  if (identity) {
    window.elementInfoMap.set(referXPath, elementDTO(typeDTO, identity, names));
  }
}

export function findClickableElements(root) {
  const results = [];
  ['button', 'a'].forEach((sel) => results.push(...root.querySelectorAll(sel)));
  return results;
}
