/**
 * classifier/nameLabelResolver.js
 *
 * Resolves the human-readable name and label for a classified element.
 * Priority order: label, for=, formcontrolname, test-id, name, aria-label,
 * href, id, data-testid, title, someText, tagName.
 *
 * Extracted from the h() function in the original monolithic payload.
 * Pure computation — no DOM API calls. WASM migration candidate (M-8).
 */

const MAX_DEFINED_NAME_LENGTH = 30;

/**
 * @param {object} descriptor — raw element descriptor from domWalker
 * @returns {{ nameLabel: string, nameField: string, definedName: string }}
 */
export function resolveNameLabel(descriptor) {
  const attrs  = descriptor.attributeData || [];
  const tag    = (descriptor.tagName || '').toLowerCase();
  const isLink = tag === 'a';
  const isOpt  = tag === 'option';

  const get = (name) => findAttr(attrs, name);

  const label        = get('label');
  const forAttr      = get('for');
  const id           = get('id');
  const name         = get('name');
  const ariaLabel    = get('aria-label');
  const fcName       = get('formcontrolname');
  const testId       = get('test-id');
  const dataTestId   = get('data-testid');
  const title        = get('title');
  const value        = get('value');
  const innerHtml    = get('innerhtml');
  const href         = get('href');
  const someText     = trim(descriptor.someText);
  const fileExt      = extractFileExt(href);

  let nameLabel = '';
  let nameField = '';

  if (isNotEmpty(label))                        { nameLabel = label;              nameField = label; }
  else if (isNotEmpty(forAttr))                 { nameLabel = forAttr;            nameField = forAttr; }
  else if (isOpt && isNotEmpty(value))          { nameLabel = value;              nameField = value; }
  else if (isNotEmpty(fcName))                  { nameLabel = fcName;             nameField = fcName; }
  else if (isNotEmpty(testId))                  { nameLabel = testId;             nameField = testId; }
  else if (isNotEmpty(name))                    { nameLabel = name;               nameField = name; }
  else if (isNotEmpty(ariaLabel))               { nameLabel = ariaLabel;          nameField = ariaLabel; }
  else if (isLink && isNotEmpty(innerHtml) && !/<>/.test(innerHtml)) { nameLabel = innerHtml; nameField = innerHtml; }
  else if (isNotEmpty(id))                      { nameLabel = id;                 nameField = id; }
  else if (isNotEmpty(fileExt))                 { nameLabel = `${fileExt} File`;  nameField = `${fileExt} File`; }
  else if (isNotEmpty(someText))                { nameLabel = someText;           nameField = tag; }
  else if (isNotEmpty(dataTestId))              { nameLabel = dataTestId;         nameField = dataTestId; }
  else if (isNotEmpty(title))                   { nameLabel = title;              nameField = title; }
  else                                          { nameLabel = tag || '';          nameField = 'NO IDENTIFICATION'; }

  nameLabel = trim(nameLabel);
  nameField = trim(nameField);

  // Compute definedName: prefer someText, then attribId, then attribName
  let definedName = nameLabel;
  if (isNotEmpty(descriptor.attribId) || isNotEmpty(descriptor.attribName) || isNotEmpty(descriptor.someText)) {
    if (isNotEmpty(descriptor.someText)) {
      definedName = truncate(descriptor.someText, MAX_DEFINED_NAME_LENGTH);
    } else if (isNotEmpty(descriptor.attribId)) {
      definedName = trim(descriptor.attribId);
    } else if (isNotEmpty(descriptor.attribName)) {
      definedName = trim(descriptor.attribName);
    }
  }

  return { nameLabel, nameField, definedName };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function findAttr(attrs, name) {
  if (!Array.isArray(attrs)) return '';
  const found = attrs.find(a => typeof a.name === 'string' && a.name.toLowerCase() === name.toLowerCase());
  return found?.value ?? '';
}

function trim(v) {
  return (v ?? '').toString().trim().replace(/\s+/g, ' ');
}

function isNotEmpty(v) {
  return trim(v).length > 0;
}

function truncate(str, maxLen) {
  const s = trim(str);
  return s.length > maxLen ? s.slice(0, maxLen) : s;
}

function extractFileExt(href) {
  if (!href) return '';
  try {
    const path = new URL(href, window.location.href).pathname || '';
    const match = (path.split('/').pop() || '').match(/\.([a-z0-9]+)$/i);
    return match ? match[1] : '';
  } catch {
    const match = href.match(/\.([a-z0-9]+)(?:[?#].*)?$/i);
    return match ? match[1] : '';
  }
}
