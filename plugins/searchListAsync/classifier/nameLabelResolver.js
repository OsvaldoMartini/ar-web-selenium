/**
 * searchListAsync — Label resolution, post-processing, DTO
 */

import { evaluateXPath } from '../scanner/xpathResolver.js';

export function elementDTO(typeElement, identity, names) {
  return {
    typeElement,
    tagName:              identity.tagName              ?? 'No Tag Name Detected',
    xPath:                identity.xPath                ?? '',
    someText:             identity.someText             ?? '',
    attribId:             identity.attribId             ?? '',
    attribName:           identity.attribName           ?? '',
    coordinates:          identity.coordinates          ?? '',
    attributeData:        identity.attributeData        ?? '',
    customXPath:          identity.customXPath          ?? '',
    iFrameXPath:          identity.iFrameXPath          ?? '',
    shadowHost:           identity.shadowHost           ?? '',
    shadowRoot:           identity.shadowRoot           ?? '',
    nestedShadow:         identity.nestedShadow         ?? '',
    cssSelector:          identity.cssSelector          ?? '',
    attributeValue:       identity.attributeValue       ?? '',
    attributeType:        identity.attributeType        ?? '',
    searchAttributeValue: identity.searchAttributeValue ?? '',
    nameLabel:            names?.nameLabel              ?? '',
    nameField:            names?.nameField              ?? '',
    definedName:          names?.definedName            ?? '',
  };
}

export function findMatLabel(sortedList) {
  sortedList.forEach((item) => {
    if (item.attribId || item.attribName) {
      let searchText = item.someText;
      let searchId   = item.attribId;
      let searchName = item.attribName;
      let foundLabelText = null;

      const selectors = [];
      if (searchId) {
        selectors.push(`label[for="${searchId}"] mat-label`);
        selectors.push(`mat-label[for="${searchId}"]`);
        selectors.push(`mat-checkbox[test-id="${searchName}"] .mdc-label`);
        selectors.push(`label[for="${searchId}"]`);
      }
      if (searchName) {
        selectors.push(`label[for="${searchName}"] mat-label`);
        selectors.push(`mat-label[for="${searchName}"]`);
        selectors.push(`mat-checkbox[test-id="${searchName}"] .mdc-label`);
        selectors.push(`label[for="${searchId}"]`);
      }

      selectors.forEach((selector) => {
        const el = document.querySelector(selector);
        if (el && foundLabelText === null) {
          foundLabelText = el.textContent.trim();
          item.attributeData.push({ name: 'someText', value: searchText });
          item.someText = foundLabelText;
        }
      });
    }
  });
}

export function changeDivToLabelWithSomeText(sortedList) {
  sortedList.forEach((item) => {
    if (item.someText && item.tagName === 'div') item.tagName = 'label';
  });
}

export function normalizeSomeTextForTables(sortedList) {
  if (!Array.isArray(sortedList) || sortedList.length === 0) return;

  sortedList.forEach((item) => {
    if (!item || !item.xPath) return;

    const raw = evaluateXPath(item.xPath);
    if (!raw) return;

    const inTable =
      raw.closest?.('avq-instrument-table') || raw.closest?.('avq-trades-table') ||
      raw.closest?.('table[mat-table]') || raw.closest?.('table.mat-mdc-table');
    if (!inTable) return;

    item.tagName = 'button';

    const cell = raw.closest?.('td[role="gridcell"], td, th');
    if (!cell) return;

    const cellText = (cell.innerText || cell.textContent || '').replace(/\s+/g, ' ').trim();
    if (!cellText) return;

    item.someText = cellText;

    if (Array.isArray(item.attributeData)) {
      const idx = item.attributeData.findIndex((a) => a.name === 'someText');
      if (idx >= 0) item.attributeData[idx].value = item.someText;
      else item.attributeData.push({ name: 'someText', value: item.someText });
    }
  });
}
