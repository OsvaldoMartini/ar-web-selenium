/**
 * hoverPick — Tag classification from XPath
 *
 * Maps raw DOM tag names to canonical types using XPath analysis
 * and framework-specific heuristics.
 */

/**
 * Check if an element is genuinely clickable (button, link, input, or role=button).
 */
export function isActuallyClickable(el) {
  if (!el || typeof el.getAttribute !== 'function') return false;
  return (
    el instanceof HTMLButtonElement ||
    el instanceof HTMLAnchorElement ||
    el instanceof HTMLInputElement ||
    el.onclick ||
    el.getAttribute('role') === 'button'
  );
}

/**
 * Classify an element's tag name based on its XPath and clickability.
 *
 * @param {string}  tagName  raw lowercase tag name
 * @param {string}  xpath    absolute XPath string
 * @param {Element} element  the DOM element
 * @returns {string} canonical type: 'input' | 'button' | 'a' | 'select' | 'label'
 */
export function classifyTag(tagName, xpath, element) {
  if (typeof xpath !== 'string' || xpath.trim() === '') return 'label';

  const parts = xpath.split('/').filter((p) => p.trim() !== '');

  for (let i = parts.length - 1; i >= 0; i--) {
    const part = parts[i];
    const tagMatch = part.match(/^([a-zA-Z-]+)(?:\[\d+\])?/);
    if (!tagMatch) continue;

    const tag = tagMatch[1].toLowerCase();

    if (tag === 'a' && isActuallyClickable(element)) return 'a';

    if (tag === 'input') {
      const typeMatch = part.match(/@type=["']?([^"'\]]+)["']?/);
      const type = typeMatch ? typeMatch[1].toLowerCase() : '';
      return ['button', 'submit', 'reset'].includes(type) ? 'button' : 'input';
    }

    if (tag === 'button') return 'button';

    if (isActuallyClickable(element) &&
        (tag.includes('expansion-panel-header') || tag.includes('sidenav') || tag.includes('nav'))) {
      return 'button';
    }

    if (tag === 'select' || tag === 'option') return 'select';
    if (tag === 'textarea') return 'input';

    // Angular Material / MDC / React / Element UI heuristics
    if (isActuallyClickable(element) &&
        (tag.includes('mat-button') || tag.includes('mat-raised-button') ||
         tag.includes('mat-icon-button') || tag.includes('mat-menu-item') ||
         tag.includes('mat-select') || tag.includes('mat-option') ||
         tag.includes('matinput'))) {
      return 'button';
    }

    if (isActuallyClickable(element) &&
        (tag.includes('data-testid') || tag.includes('aria-label') ||
         part.includes("@role='button'") || part.includes("@role='textbox'") ||
         part.includes('react-button') || part.includes('react-link') ||
         part.includes('react-input'))) {
      if (part.includes('react-input')) return 'input';
      if (part.includes('react-link')) return 'a';
      return 'button';
    }

    if (isActuallyClickable(element) &&
        (part.includes('mdc-button') || part.includes('mdc-text-field') || part.includes('mdc-list-item'))) {
      return part.includes('mdc-text-field') ? 'input' : 'button';
    }

    if (isActuallyClickable(element) &&
        (part.includes('el-button') || part.includes('el-input__inner') || part.includes('el-select-dropdown__item'))) {
      if (part.includes('el-input__inner')) return 'input';
      if (part.includes('el-select-dropdown__item')) return 'select';
      return 'button';
    }
  }

  return 'label';
}
