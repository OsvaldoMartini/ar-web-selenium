/**
 * hoverPick — XPath generation and evaluation
 *
 * Generates absolute XPath strings for DOM elements and provides
 * lookup helpers (by XPath, by coordinates).
 */

/**
 * Build an absolute XPath for the given element.
 * e.g. /html/body/div[2]/form[1]/input[3]
 */
export function generateXPath(element) {
  if (element === document.body) return '/html/body';
  let ix = 0;
  const siblings = element.parentNode ? element.parentNode.childNodes : [];
  for (let i = 0; i < siblings.length; i++) {
    const sibling = siblings[i];
    if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
      if (sibling === element) {
        return (
          generateXPath(element.parentNode) +
          '/' +
          element.tagName.toLowerCase() +
          '[' +
          (ix + 1) +
          ']'
        );
      }
      ix++;
    }
  }
  return '';
}

/**
 * Evaluate an XPath expression and return the first matching node.
 */
export function evaluateXPath(xpath) {
  try {
    const result = document.evaluate(
      xpath,
      document,
      null,
      XPathResult.FIRST_ORDERED_NODE_TYPE,
      null,
    );
    return result.singleNodeValue;
  } catch (error) {
    console.error('Error finding element by XPath:', error);
    return null;
  }
}

/**
 * Find an element at the given "x,y" coordinate string.
 */
export function getElementByCoordinates(coordString) {
  const [xStr, yStr] = coordString.split(',');
  const x = parseFloat(xStr.trim());
  const y = parseFloat(yStr.trim());

  if (isNaN(x) || isNaN(y)) return null;

  return document.elementFromPoint(x, y);
}
