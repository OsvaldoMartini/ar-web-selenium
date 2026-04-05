/**
 * searchListAsync — Iframe inspection and recursive traversal
 */

import { generateXPath }       from './xpathResolver.js';
import { getElementIdentity }  from './elementIdentity.js';
import { filterSearchTerms, collectShadowElements, findClickableElements } from './domWalker.js';

/**
 * Recursively collect iframe elements.
 */
export function collectIframeElements(doc, collectionFound, searchTerms, isChild = false) {
  doc.querySelectorAll('iframe').forEach((iframe) => {
    try {
      const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;

      collectShadowElements(iframeDoc, searchTerms);

      if (iframe) {
        let iframeParsed   = null;
        let srcDocElements = null;
        const xPathIFrame  = generateXPath(iframe);

        const id = getElementIdentity(window.__slAsync_hiddenFields, iframe);
        if (id) filterSearchTerms(iframe, 'iFrame-Found', id.xPath, id, searchTerms);

        const parser = new DOMParser();
        if (iframe.srcdoc) {
          iframeParsed   = parser.parseFromString(iframe.srcdoc, 'text/html');
          srcDocElements = iframeParsed.querySelectorAll('*');
        }

        if (iframe.src) {
          const srcElements = fetchAndParseIframeContent(iframe);
          if (srcElements) {
            iFrameDetails(iframe, xPathIFrame, srcElements.length);
            srcElements.forEach((el) => {
              const elId = getElementIdentity(window.__slAsync_hiddenFields, el);
              if (elId) {
                elId.iFrameXPath = xPathIFrame;
                filterSearchTerms(el, 'iFrame-Child', `${xPathIFrame}${elId?.xPath}`, elId, searchTerms);
              }
            });
          }
        }

        if (!iframe.src) {
          iFrameDetails(iframe, xPathIFrame,
            srcDocElements ? srcDocElements.length
              : iframeDoc ? iframeDoc.querySelectorAll('*').length : 0);
        }

        iframeDoc.querySelectorAll('*').forEach((el) => {
          const elId = getElementIdentity(window.__slAsync_hiddenFields, el);
          if (elId) {
            elId.iFrameXPath = xPathIFrame;
            filterSearchTerms(el, 'iFrame-Child', `${xPathIFrame}${elId?.xPath}`, elId, searchTerms);
          }
        });

        srcDocElements?.forEach((el) => {
          const elId = getElementIdentity(window.__slAsync_hiddenFields, el);
          if (elId) {
            elId.iFrameXPath = xPathIFrame;
            filterSearchTerms(el, 'iFrame-Child', `${xPathIFrame}${elId?.xPath}`, elId, searchTerms);
          }
        });

        if (iframeParsed) processIframeElements(iframeParsed, xPathIFrame, searchTerms);

        collectIframeElements(iframeDoc, collectionFound, searchTerms, true);
      }
    } catch (_) {}
  });
}

function processIframeElements(iframeDoc, xPathIFrame, searchTerms) {
  iframeDoc.querySelectorAll('*').forEach((el) => {
    const elId = getElementIdentity(window.__slAsync_hiddenFields, el);
    if (elId) {
      elId.iFrameXPath = xPathIFrame;
      filterSearchTerms(el, 'iFrame-Child', `${xPathIFrame}${elId?.xPath}`, elId, searchTerms);
    }
  });
}

function fetchAndParseIframeContent(iframe) {
  try {
    if (!iframe.src) return null;
    const url = new URL(iframe.src, window.location.href);
    if (url.origin !== window.location.origin) return null;

    const xhr = new XMLHttpRequest();
    xhr.open('GET', iframe.src, false);
    xhr.send();
    if (xhr.status !== 200) return null;

    return new DOMParser().parseFromString(xhr.responseText, 'text/html').querySelectorAll('*');
  } catch (_) {
    return null;
  }
}

function iFrameDetails(iframe, xPathIFrame, childSize) {
  const desc = iframe.src || iframe.title || iframe.id || iframe.name || 'No description';
  console.log(`iFrame Found: ${desc}; Elements inside iframe: ${childSize}`);
  window.elementInfoMap.set(xPathIFrame, `xpath:${xPathIFrame};text:${desc};Elements inside iframe: ${childSize}`);
}
