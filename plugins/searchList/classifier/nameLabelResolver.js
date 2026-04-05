/**
 * searchList — Label resolution, post-processing, DTO
 *
 * Note: this sync version does NOT include nameLabel/nameField/definedName
 * (those are only in searchListAsync).
 */

export function elementDTO(typeElement, identity) {
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
