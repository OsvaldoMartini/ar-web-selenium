/**
 * scanner/iframeInspector.js
 *
 * Recursively traverses iframes (inline srcdoc, cross-origin src, XHR-fetched).
 * Extracted from the I() and C() functions in the original monolithic payload.
 */

import { generateXPath } from './xpathResolver.js';
import { buildDescriptor } from './domWalker.js';

/**
 * Inspect all iframes in a document and push descriptors into results.
 *
 * @param {Document} doc
 * @param {string[]} searchTerms
 * @param {boolean}  includeHidden
 * @param {object[]} results       — mutated in place
 */
export function inspectIframes(doc, searchTerms, includeHidden, results) {
  _walkIframes(doc, searchTerms, includeHidden, results);
}

function _walkIframes(doc, searchTerms, includeHidden, results) {
  doc.querySelectorAll('iframe').forEach(iframe => {
    try {
      const iframeXPath = generateXPath(iframe);

      // Describe the iframe itself as an element
      const iframeDescriptor = buildDescriptor(iframe, includeHidden);
      if (iframeDescriptor) results.push(iframeDescriptor);

      const contentDoc = iframe.contentDocument || iframe.contentWindow?.document;

      // srcdoc branch — parse the inline HTML
      if (iframe.srcdoc) {
        const parsed = new DOMParser().parseFromString(iframe.srcdoc, 'text/html');
        _extractFromDocument(parsed, iframeXPath, searchTerms, includeHidden, results);
      }

      // src branch — try XHR fetch (same-origin only)
      if (iframe.src) {
        const fetched = _fetchSrc(iframe.src);
        if (fetched) {
          logIframe(iframe, iframeXPath, fetched.length);
          fetched.forEach(el => {
            const d = buildDescriptor(el, includeHidden);
            if (d) {
              d.iFrameXPath = iframeXPath;
              results.push(d);
            }
          });
        }
      }

      // contentDocument branch — live document
      if (contentDoc) {
        _extractFromDocument(contentDoc, iframeXPath, searchTerms, includeHidden, results);
        // Recurse into nested iframes
        _walkIframes(contentDoc, searchTerms, includeHidden, results);
      }
    } catch (_) {
      // Cross-origin iframe — cannot access contentDocument; skip silently
    }
  });
}

function _extractFromDocument(doc, iframeXPath, searchTerms, includeHidden, results) {
  doc.querySelectorAll('*').forEach(el => {
    const d = buildDescriptor(el, includeHidden);
    if (d) {
      d.iFrameXPath = iframeXPath;
      results.push(d);
    }
  });
}

function _fetchSrc(src) {
  try {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', src, false); // synchronous — intentional, mirrors original
    xhr.send();
    if (xhr.status !== 200) return null;
    const parsed = new DOMParser().parseFromString(xhr.responseText, 'text/html');
    return Array.from(parsed.querySelectorAll('*'));
  } catch (_) {
    return null;
  }
}

function logIframe(iframe, xPath, elementCount) {
  const desc = iframe.src || iframe.title || iframe.id || iframe.name || 'No description';
  console.log(`iFrame Found: ${desc}; Elements inside iframe: ${elementCount}`);
}
