/**
 * hoverPick — Click handler
 *
 * Handles mouse click events: resolves the target element,
 * inspects iframes and shadow DOM, builds identity DTOs,
 * and triggers data sending.
 */

import { getElementIdentity } from '../scanner/elementIdentity.js';
import { elementDTO, resolveControlFromClicked } from '../classifier/nameLabelResolver.js';
import { getCoordinatesElement, getOriginalStyles, showPickedToast } from '../highlight/highlighter.js';
import { sendData } from '../sender/dataSender.js';

/**
 * Build and return the click handler function.
 *
 * @param {boolean} hiddenFields  whether hidden elements are included
 * @param {object}  wsClient      WebSocket client
 * @returns {function} event handler for 'click'
 */
export function buildClickHandler(hiddenFields, wsClient) {

  function handleMartiniClick(event) {
    event.preventDefault();
    event.stopPropagation();

    const coordsEl = getCoordinatesElement();
    if (coordsEl) coordsEl.style.display = 'none';

    const clickX = event.clientX;
    const clickY = event.clientY;

    let rawClickedElement = document.elementFromPoint(clickX, clickY);
    window.__scannerRawClickedElement = rawClickedElement;

    let clickedElement = resolveControlFromClicked(rawClickedElement);
    window.__scannerLastClickedElement = clickedElement;

    if (coordsEl) coordsEl.style.display = 'block';

    const originalStyles = getOriginalStyles();

    // Highlight clicked element
    if (clickedElement) {
      if (!originalStyles.has(clickedElement)) {
        originalStyles.set(clickedElement, clickedElement.style.outline);
      }
      clickedElement.style.outline = '3px solid blue';
    }

    // ── Iframe path ─────────────────────────────────────────────────────
    if (clickedElement && clickedElement.tagName.toLowerCase() === 'iframe') {
      const iframeDoc = clickedElement.contentDocument || clickedElement.contentWindow.document;
      if (iframeDoc) {
        window.allElementInfo = [];
        const identity = getElementIdentity(hiddenFields, clickedElement);
        const xPathIFrame = identity ? identity.xPath : '';

        if (identity) {
          window.elementInfoMap.set(identity.xPath, elementDTO('clicked-iFrame', identity));
        }

        iframeDoc.querySelectorAll('*').forEach((el) => {
          const id = getElementIdentity(hiddenFields, el);
          if (id) {
            id.iFrameXPath = xPathIFrame;
            window.elementInfoMap.set(id.xPath, elementDTO('iFrame-Child', id));
          }
        });
      }
    } else {
      // ── Regular element path ────────────────────────────────────────────
      const tagName = clickedElement.tagName.toLowerCase();
      if (['html', 'body', 'main'].includes(tagName)) return;

      window.elementInfoMap.clear();
      console.log('Clicked element:', clickedElement);

      // Check for shadow DOM
      let shadowHost = clickedElement;
      while (shadowHost && !shadowHost.shadowRoot) {
        shadowHost = shadowHost.parentElement;
      }

      if (shadowHost && shadowHost.shadowRoot) {
        const shadowRoot = shadowHost.shadowRoot;
        findClickableElements(shadowRoot).forEach((el) => {
          pushElement(hiddenFields, el, shadowHost, shadowRoot, originalStyles);
        });
      } else {
        pushElement(hiddenFields, clickedElement, null, null, originalStyles);
      }
    }

    // Show toast with picked element info
    showPickedToast(clickedElement);

    sendData(wsClient);

    setTimeout(() => {
      window.allElementInfo = [];
      window.elementInfoMap.clear();
    }, 1000);
  }

  return handleMartiniClick;
}

// ── Internal helpers ──────────────────────────────────────────────────────────

function pushElement(hiddenFields, element, shadowHost, shadowRoot, originalStyles) {
  const identityTemp = getElementIdentity(hiddenFields, element);

  let shadowHostSelector = '';
  let elementCssSelector = '';
  const shadowPath = [];

  function buildCss(el) {
    if (!el) return '';
    let sel = el.tagName.toLowerCase();
    if (el.id) sel += `#${el.id}`;
    if (el.className) sel += `.${el.className.replace(/\s+/g, '.')}`;
    return sel;
  }

  let currentHost = shadowHost;
  while (currentHost) {
    shadowPath.unshift(buildCss(currentHost));
    currentHost = currentHost.parentNode instanceof ShadowRoot
      ? currentHost.parentNode.host
      : null;
  }

  if (shadowHost)  shadowHostSelector = buildCss(shadowHost);
  if (element)     elementCssSelector = buildCss(element);

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

  if (identity) {
    if (!originalStyles.has(element)) {
      originalStyles.set(element, element.style.outline);
    }
    element.style.outline = '3px solid red';
    window.elementInfoMap.set(identity.xPath, elementDTO('clicked', identity));
  }
}

function findClickableElements(root) {
  const results = [];
  ['button', 'a'].forEach((sel) => {
    results.push(...root.querySelectorAll(sel));
  });
  return results;
}
