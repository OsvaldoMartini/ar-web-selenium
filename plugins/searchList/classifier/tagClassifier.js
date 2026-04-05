/**
 * searchList — Tag classification from XPath
 */

export function classifyTag(tagName, xpath) {
  if (typeof xpath !== 'string' || xpath.trim() === '') return 'unknown';

  const parts = xpath.split('/').filter((p) => p.trim() !== '');

  for (let i = parts.length - 1; i >= 0; i--) {
    const part = parts[i];
    const tagMatch = part.match(/^([a-zA-Z-]+)(?:\[\d+\])?/);
    if (!tagMatch) continue;
    const tag = tagMatch[1].toLowerCase();

    if (tag === 'a') return 'a';
    if (tag === 'input') {
      const typeMatch = part.match(/@type=["']?([^"'\]]+)["']?/);
      const type = typeMatch ? typeMatch[1].toLowerCase() : '';
      return ['button', 'submit', 'reset'].includes(type) ? 'button' : 'input';
    }
    if (tag === 'button') return 'button';
    if (tag.includes('expansion-panel-header') || tag.includes('sidenav') || tag.includes('nav')) return 'button';
    if (tag === 'select' || tag === 'option') return 'select';
    if (tag === 'textarea') return 'input';

    if (tag.includes('mat-button') || tag.includes('mat-raised-button') || tag.includes('mat-icon-button') ||
        tag.includes('mat-menu-item') || tag.includes('mat-select') || tag.includes('mat-option') || tag.includes('matinput'))
      return 'button';

    if (tag.includes('data-testid') || tag.includes('aria-label') || part.includes("@role='button'") ||
        part.includes("@role='textbox'") || part.includes('react-button') || part.includes('react-link') || part.includes('react-input')) {
      if (part.includes('react-input')) return 'input';
      if (part.includes('react-link')) return 'a';
      return 'button';
    }

    if (part.includes('mdc-button') || part.includes('mdc-text-field') || part.includes('mdc-list-item'))
      return part.includes('mdc-text-field') ? 'input' : 'button';

    if (part.includes('el-button') || part.includes('el-input__inner') || part.includes('el-select-dropdown__item')) {
      if (part.includes('el-input__inner')) return 'input';
      if (part.includes('el-select-dropdown__item')) return 'select';
      return 'button';
    }
  }
  return tagName;
}
