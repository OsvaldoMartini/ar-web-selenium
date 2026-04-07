/**
 * hoverPick — Label resolution and post-processing
 *
 * Resolves mat-label text, normalises table someText,
 * and provides the elementDTO shape sent over WebSocket.
 */

/**
 * Build the DTO shape expected by the Java WebSocket server.
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

/**
 * If the clicked element is a label / mat-label, resolve the real form control.
 */
export function resolveControlFromClicked(el) {
  if (!el) return el;

  const tag = el.tagName?.toLowerCase();

  // LABEL → resolve to associated form control
  if (tag === 'mat-label' || tag === 'label') {
    const label = tag === 'label' ? el : el.closest('label');
    const forId = label?.getAttribute('for');
    if (forId) {
      const control = document.getElementById(forId);
      if (control) return control;
    }
    const mff = el.closest('mat-form-field');
    const control = mff?.querySelector('textarea, input, select');
    if (control) return control;
  }

  // CARD with radio/checkbox → resolve to the input control inside the card
  // Handles Angular Material cards (avq-card, mat-card) where the actual
  // actionable element is a radio or checkbox buried inside the card structure.
  if (tag !== 'input') {
    const card = el.closest('avq-card, avq-portfolio-card, avq-trading-recent-trade-card, mat-card');
    if (card) {
      const radioInput = card.querySelector(
        'mat-radio-button input[type="radio"], mat-checkbox input[type="checkbox"]'
      );
      if (radioInput) {
        alert('[hoverPick] Card resolved → input[type=' + radioInput.type + '] id=' + (radioInput.id || 'none'));
        return radioInput;
      }
    }
  }

  return el;
}

/**
 * Replace someText with the associated mat-label text where applicable.
 */
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
        const labelElement = document.querySelector(selector);
        if (labelElement && foundLabelText === null) {
          foundLabelText = labelElement.textContent.trim();
          console.log(
            `Found mat-label text for input with id/name '${searchId || searchName}':`,
            foundLabelText,
          );
          item.attributeData.push({ name: 'someText', value: searchText });
          item.someText = foundLabelText;
        }
      });
    }
  });
}

/**
 * Promote div elements with someText to "label" tag.
 */
export function changeDivToLabelWithSomeText(sortedList) {
  sortedList.forEach((item) => {
    if (item.someText && item.tagName === 'div') {
      item.tagName = 'label';
    }
  });
}

/**
 * When the click was inside a mat-table / instrument-table,
 * normalise someText to the clicked cell's text.
 */
export function normalizeSomeTextForTables(sortedList) {
  const raw = window.__scannerRawClickedElement;
  if (!raw || !Array.isArray(sortedList) || sortedList.length === 0) return;

  const inTable =
    raw.closest?.('avq-instrument-table') ||
    raw.closest?.('avq-trades-table') ||
    raw.closest?.('table[mat-table]') ||
    raw.closest?.('table.mat-mdc-table');

  if (!inTable) return;

  sortedList.forEach((item) => { if (item) item.tagName = 'button'; });

  const cell = raw.closest?.('td[role="gridcell"], td, th');
  if (!cell) return;

  const cellText = (cell.innerText || cell.textContent || '').replace(/\s+/g, ' ').trim();
  if (!cellText) return;

  sortedList.forEach((item) => {
    if (!item) return;

    if (item.tagName === 'input') {
      const ariaLabel = item.attributeData?.find((a) => a.name === 'aria-label')?.value?.trim() || '';
      const existingSomeText = item.attributeData?.find((a) => a.name === 'someText')?.value?.trim() || '';
      if (!existingSomeText || existingSomeText === ariaLabel) {
        item.someText = cellText;
      }
    } else {
      item.someText = cellText;
    }

    if (Array.isArray(item.attributeData)) {
      const idx = item.attributeData.findIndex((a) => a.name === 'someText');
      if (idx >= 0) item.attributeData[idx].value = item.someText;
      else item.attributeData.push({ name: 'someText', value: item.someText });
    }
  });
}
