/**
 * scanner/xpathResolver.js
 *
 * Generates absolute XPath expressions from DOM nodes and evaluates them.
 * Extracted from the D() and M() functions in the original monolithic payload.
 */

/**
 * Generate an absolute XPath string for a given DOM element.
 * Walks up the tree, counting siblings of the same tag name at each level.
 *
 * @param {Element} el
 * @returns {string}  e.g. "/html/body/div[2]/form[1]/input[3]"
 */
export function generateXPath(el) {
  if (el === document.body) return '/html/body';

  const siblings = el.parentNode ? Array.from(el.parentNode.childNodes) : [];
  let siblingIndex = 0;

  for (let i = 0; i < siblings.length; i++) {
    const sibling = siblings[i];
    if (sibling.nodeType === Node.ELEMENT_NODE && sibling.tagName === el.tagName) {
      if (sibling === el) {
        const parentXPath = generateXPath(el.parentNode);
        return `${parentXPath}/${el.tagName.toLowerCase()}[${siblingIndex + 1}]`;
      }
      siblingIndex++;
    }
  }
  return '';
}

/**
 * Evaluate an XPath expression and return the first matching element.
 *
 * @param {string} xpath
 * @returns {Element|null}
 */
export function evaluateXPath(xpath) {
  try {
    return document.evaluate(
      xpath,
      document,
      null,
      XPathResult.FIRST_ORDERED_NODE_TYPE,
      null
    ).singleNodeValue;
  } catch (err) {
    console.error('Error evaluating XPath:', xpath, err);
    return null;
  }
}
