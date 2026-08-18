/**
 * classifier/tagClassifier.js
 *
 * Maps raw DOM tag names and XPath segments to canonical element types:
 * 'input' | 'button' | 'a' | 'select' | 'unknown'
 *
 * Extracted from the c() function in the original monolithic payload.
 * This module is a candidate for WASM migration (Milestone M-8) because
 * it performs pure string computation with no DOM API dependency.
 */

const BUTTON_XPATH_FRAGMENTS = [
  'expansion-panel-header', 'sidenav', 'nav',
  'mat-button', 'mat-raised-button', 'mat-icon-button',
  'mat-menu-item', 'mat-option', 'matinput',
  'mdc-button', 'mdc-list-item',
  'el-button', 'el-select-dropdown__item',
  'react-button', 'react-link',
];

const INPUT_XPATH_FRAGMENTS = [
  'mdc-text-field', 'el-input__inner', 'react-input',
];

const ROLE_PATTERNS = {
  button:  /@role='button'/,
  textbox: /@role='textbox'/,
};

/**
 * Classify an element's semantic tag type.
 *
 * @param {string} rawTag  — el.tagName.toLowerCase()
 * @param {string} xpath   — absolute XPath from xpathResolver
 * @returns {'input'|'button'|'a'|'select'|string}
 */
export function classifyTag(rawTag, xpath) {
  if (!xpath) return rawTag;

  const segments = xpath.split('/').filter(Boolean);

  for (let i = segments.length - 1; i >= 0; i--) {
    const seg  = segments[i];
    const raw  = (seg.match(/^([a-zA-Z-]+)(?:\[\d+\])?/) || [])[1]?.toLowerCase();
    if (!raw) continue;

    // Exact tag matches
    if (raw === 'a')        return 'a';
    if (raw === 'button')   return 'button';
    if (raw === 'select' || raw === 'option') return 'select';
    if (raw === 'textarea') return 'input';

    // Input with type= inspection
    if (raw === 'input') {
      const typeMatch = seg.match(/@type=["']?([^"'\]]+)["']?/);
      const type = typeMatch?.[1]?.toLowerCase() ?? '';
      return ['button', 'submit', 'reset'].includes(type) ? 'button' : 'input';
    }

    // Fragment-based detection
    const segFull = seg.toLowerCase();
    if (INPUT_XPATH_FRAGMENTS.some(f => segFull.includes(f))) return 'input';
    if (BUTTON_XPATH_FRAGMENTS.some(f => segFull.includes(f))) return 'button';

    // Role attribute patterns
    if (ROLE_PATTERNS.button.test(seg))  return 'button';
    if (ROLE_PATTERNS.textbox.test(seg)) return 'input';

    if (seg.includes('react-link')) return 'a';
    if (seg.includes('el-select-dropdown__item')) return 'select';
  }

  return rawTag;
}
