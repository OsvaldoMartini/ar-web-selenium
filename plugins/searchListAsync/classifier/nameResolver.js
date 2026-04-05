/**
 * searchListAsync — Name/label/definedName resolution
 *
 * Minimal port of the Java defineNameTitles + setElementText logic.
 */

function normalizeSpaces(s) {
  return (s ?? '').toString().trim().replace(/\s+/g, ' ');
}

function truncateAndNormalize(s, maxLen) {
  const t = normalizeSpaces(s);
  if (!t) return '';
  return t.length > maxLen ? t.slice(0, maxLen) : t;
}

function getAttr(attributeData, name) {
  if (!Array.isArray(attributeData)) return '';
  const found = attributeData.find(
    (a) => a && typeof a.name === 'string' && a.name.toLowerCase() === name.toLowerCase(),
  );
  return found?.value ?? '';
}

function hasText(s) {
  return normalizeSpaces(s).length > 0;
}

function extractFileExtensionFromHref(href) {
  const v = normalizeSpaces(href);
  if (!v) return '';
  try {
    const u = new URL(v, window.location.href);
    const last = (u.pathname || '').split('/').pop() || '';
    const m = last.match(/\.([a-z0-9]+)$/i);
    return m ? m[1] : '';
  } catch {
    const m = v.match(/\.([a-z0-9]+)(?:[?#].*)?$/i);
    return m ? m[1] : '';
  }
}

/**
 * Resolve nameLabel, nameField, and definedName for an element identity.
 */
export function defineNameTitlesJs(identity) {
  const tag   = (identity.tagName || '').toLowerCase();
  const attrs = identity.attributeData || [];

  const labelAttr       = getAttr(attrs, 'label');
  const forLabelAttr    = getAttr(attrs, 'for');
  const idAttr          = getAttr(attrs, 'id');
  const nameAttr        = getAttr(attrs, 'name');
  const ariaLabel       = getAttr(attrs, 'aria-label');
  const formControlName = getAttr(attrs, 'formcontrolname');
  const testId          = getAttr(attrs, 'test-id');
  const dataTestId      = getAttr(attrs, 'data-test-id');
  const title           = getAttr(attrs, 'title');
  const valueAttr       = getAttr(attrs, 'value');
  const innerHTML       = getAttr(attrs, 'innerhtml');
  const href            = getAttr(attrs, 'href');

  const textLabel     = normalizeSpaces(identity.someText);
  const valueHrefFile = extractFileExtensionFromHref(href);

  const isAnchor = tag === 'a';
  const isOption = tag === 'option';

  let nameLabel = '';
  let nameField = '';

  if      (hasText(labelAttr))       { nameLabel = labelAttr;       nameField = labelAttr; }
  else if (hasText(forLabelAttr))    { nameLabel = forLabelAttr;    nameField = forLabelAttr; }
  else if (isOption && hasText(valueAttr)) { nameLabel = valueAttr; nameField = valueAttr; }
  else if (hasText(formControlName)) { nameLabel = formControlName; nameField = formControlName; }
  else if (hasText(testId))          { nameLabel = testId;          nameField = testId; }
  else if (hasText(nameAttr))        { nameLabel = nameAttr;        nameField = nameAttr; }
  else if (hasText(ariaLabel))       { nameLabel = ariaLabel;       nameField = ariaLabel; }
  else if (isAnchor && hasText(innerHTML) && !/[<>]/.test(innerHTML)) { nameLabel = innerHTML; nameField = innerHTML; }
  else if (hasText(idAttr))          { nameLabel = idAttr;          nameField = idAttr; }
  else if (hasText(valueHrefFile))   { nameLabel = `${valueHrefFile} File`; nameField = `${valueHrefFile} File`; }
  else if (hasText(textLabel))       { nameLabel = textLabel;       nameField = tag; }
  else if (hasText(dataTestId))      { nameLabel = dataTestId;      nameField = dataTestId; }
  else if (hasText(title))           { nameLabel = title;           nameField = title; }
  else                               { nameLabel = tag || '';       nameField = 'NO IDENTIFICATION'; }

  nameLabel = normalizeSpaces(nameLabel);
  nameField = normalizeSpaces(nameField);

  let definedName = nameLabel;

  const hasAnyPriority = hasText(identity.attribId) || hasText(identity.attribName) || hasText(identity.someText);
  if (hasAnyPriority) {
    if      (hasText(identity.someText))   definedName = truncateAndNormalize(identity.someText, 30);
    else if (hasText(identity.attribId))   definedName = normalizeSpaces(identity.attribId);
    else if (hasText(identity.attribName)) definedName = normalizeSpaces(identity.attribName);
  }

  return { nameLabel, nameField, definedName };
}
