/**
 * searchList — XPath generation and evaluation
 */

export function generateXPath(element) {
  if (element === document.body) return '/html/body';
  let ix = 0;
  const siblings = element.parentNode ? element.parentNode.childNodes : [];
  for (let i = 0; i < siblings.length; i++) {
    const sibling = siblings[i];
    if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
      if (sibling === element) {
        return generateXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']';
      }
      ix++;
    }
  }
  return '';
}
