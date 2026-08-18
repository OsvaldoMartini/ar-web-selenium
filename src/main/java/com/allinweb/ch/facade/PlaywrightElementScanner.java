package com.allinweb.ch.facade;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlaywrightElementScanner {

    private static final String DEFAULT_SELECTOR = "input, textarea, button, a, select, option, label, "
            + "[contenteditable='true'], [role='button'], [role='link'], [role='combobox'], [role='listbox'], "
            + "[role='option'], [role='menuitem'], [role='menu'], [role='menubar'], [role='tab'], [role='tablist'], "
            + "[role='checkbox'], [role='radio'], [role='switch'], [role='tree'], [role='treeitem'], "
            + "[role='grid'], [role='row'], [role='gridcell'], [role='textbox'], [role='dialog'] button, "
            + "[aria-haspopup], [aria-selected], [data-testid], [data-test-id], [test-id], [data-cy], [data-qa], "
            + "[data-radix-popper-content-wrapper], "
            + "mat-select, mat-option, mat-radio-button, mat-checkbox, mat-slide-toggle, mat-button-toggle, "
            + "mat-expansion-panel-header, mat-tab, mat-menu-item, mat-tree-node, mat-datepicker-toggle, "
            + "mat-calendar-body-cell, svg[role='button'], svg[aria-label], [mat-icon-button], mat-icon, span, div";

    private static final String SCAN_SCRIPT =
            """
            (elements, scanOptions) => {
              const includeHidden = scanOptions?.includeHidden === true;
              const configuredAttributeNames = Array.isArray(scanOptions?.attributeNames)
                ? scanOptions.attributeNames.filter(name => typeof name === 'string' && name)
                : [];
              const automationAttributeNames = Array.from(new Set([
                'data-testid', 'data-test-id', 'test-id', 'data-cy', 'data-qa',
                ...configuredAttributeNames
              ]));

              function automationAttribute(el) {
                if (!el) return null;
                const name = automationAttributeNames.find(candidate => {
                  const value = el.getAttribute(candidate);
                  return value !== null && value.trim() !== '';
                });
                return name ? { name, value: el.getAttribute(name).trim() } : null;
              }

              function isVolatileFrameworkId(id) {
                return /^(?:mat|cdk)(?:-[a-z0-9]+)*-[0-9]+(?:-[0-9]+)*$/i.test((id || '').trim());
              }

              function cssAttributeSelector(tag, name, value) {
                const slash = String.fromCharCode(92);
                const escaped = String(value || '')
                  .split(slash).join(slash + slash)
                  .split('"').join(slash + '"');
                return tag + '[' + name + '="' + escaped + '"]';
              }

              function xpathLiteral(value) {
                const text = String(value || '');
                if (!text.includes("'")) return "'" + text + "'";
                if (!text.includes('"')) return '"' + text + '"';
                const apostropheLiteral = '"' + "'" + '"';
                return 'concat(' + text.split("'").map(part => "'" + part + "'").join(', ' + apostropheLiteral + ', ') + ')';
              }

              function compositeAutomationLocator(el) {
                const controlName = el?.getAttribute('formcontrolname') || '';
                if (!controlName) return null;
                for (let owner = el.parentElement; owner; owner = owner.parentElement) {
                  const automation = automationAttribute(owner);
                  if (!automation) continue;
                  const ownerTag = owner.tagName.toLowerCase();
                  const childTag = el.tagName.toLowerCase();
                  const ownerCss = cssAttributeSelector(ownerTag, automation.name, automation.value);
                  const childCss = cssAttributeSelector(childTag, 'formcontrolname', controlName);
                  const compositeCss = ownerCss + ' ' + childCss;
                  if (document.querySelectorAll(ownerCss).length !== 1
                      || document.querySelectorAll(compositeCss).length !== 1) continue;
                  return {
                    css: compositeCss,
                    xpath: '//' + ownerTag + '[@' + automation.name + '=' + xpathLiteral(automation.value) + ']'
                      + '//' + childTag + '[@formcontrolname=' + xpathLiteral(controlName) + ']'
                  };
                }
                return null;
              }

              function generateXPath(el) {
                if (!el || el.nodeType !== 1) return '';
                const tag = el.tagName.toLowerCase();
                const composite = compositeAutomationLocator(el);
                if (composite) return composite.xpath;
                const automation = automationAttribute(el);
                const volatileId = isVolatileFrameworkId(el.id);
                if (automation && (!el.id || volatileId || tag === 'mat-option')) {
                  return '//' + tag + '[@' + automation.name + '=' + xpathLiteral(automation.value) + ']';
                }
                const controlName = el.getAttribute('formcontrolname') || '';
                if (controlName && (!el.id || volatileId)) {
                  return '//' + tag + '[@formcontrolname=' + xpathLiteral(controlName) + ']';
                }
                if (el.id) return "//*[@id=" + xpathLiteral(el.id) + "]";
                if (automation) {
                  return '//' + tag + '[@' + automation.name + '=' + xpathLiteral(automation.value) + ']';
                }
                const name = el.getAttribute('name') || '';
                if (name) return '//' + tag + '[@name=' + xpathLiteral(name) + ']';
                const parts = [];
                for (let node = el; node && node.nodeType === 1; node = node.parentElement) {
                  let index = 1;
                  for (let sib = node.previousElementSibling; sib; sib = sib.previousElementSibling) {
                    if (sib.tagName === node.tagName) index++;
                  }
                  parts.unshift(node.tagName.toLowerCase() + '[' + index + ']');
                }
                return '/' + parts.join('/');
              }

              function attr(el, name) {
                return el.getAttribute(name) || '';
              }

              function cleanText(text) {
                return (text || '').trim().replace(/\\s+/g, ' ').replace(/[：:*?!]+$/g, '').trim().slice(0, 200);
              }

              function humanizeToken(text) {
                return cleanText((text || '')
                  .replace(/[_-]+/g, ' ')
                  .replace(/([a-z])([A-Z])/g, '$1 $2'));
              }

              function looksGeneratedToken(text) {
                const raw = (text || '').trim();
                if (!raw) return false;
                if (isVolatileFrameworkId(raw)) return true;
                // Machine values must never become element names:
                // - path-style framework names: __pagevalue__/0/11/0/43/...
                if (/\\/\\d/.test(raw)) return true;
                // - opaque tokens (CSRF, base64-serialized state): long separator-free
                //   alnum runs mixing letters and digits
                if (raw.length >= 16 && /^[A-Za-z0-9+\\/=]+$/.test(raw) && /\\d/.test(raw) && /[A-Za-z]/.test(raw)) return true;
                // - framework id counters: aw-id-gen-32, ctl00_7, auto-id-3
                if (/\\d+$/.test(raw) && /(?:^|[-_])(?:id|gen|auto|uid|guid|ctl\\d*)(?:[-_]|\\d|$)/i.test(raw)) return true;
                return false;
              }

              function usefulText(text, el, kind) {
                const cleaned = cleanText(text);
                if (!cleaned) return '';
                const normalized = cleaned.toLowerCase();
                const generic = [
                  'checkbox label',
                  'radio label',
                  'input label',
                  'select label',
                  'textarea label',
                  'button label',
                  'label'
                ];
                if (generic.includes(normalized)) return '';
                if ((kind || '').includes('checkbox') && /^checkbox\\s*(label)?\\s*\\d*$/i.test(cleaned)) return '';
                if ((kind || '').includes('radio') && /^radio\\s*(label)?\\s*\\d*$/i.test(cleaned)) return '';
                return cleaned;
              }

              function rememberText(attrs, text, source, accessibleLabel) {
                pushMetadata(attrs, 'text-source', source);
                if (accessibleLabel) pushMetadata(attrs, 'dom-label', text);
                return text;
              }

              function semanticAttributeText(el) {
                const attrs = [...automationAttributeNames, 'aria-controls', 'name', 'id'];
                for (const attrName of attrs) {
                  const rawValue = el.getAttribute(attrName) || '';
                  if (looksGeneratedToken(rawValue)) continue;
                  const text = semanticToken(rawValue);
                  if (isUsefulSemanticToken(text)) return text;
                }

                if (typeof el.className === 'string') {
                  const classText = el.className
                    .split(/\\s+/)
                    .map(semanticToken)
                    .filter(isUsefulSemanticToken)
                    .join(' ');
                  if (classText) return classText;
                }
                return '';
              }

              function semanticToken(text) {
                const noiseWords = new Set([
                  'id', 'chkbox', 'checkbox', 'radio', 'input', 'textarea', 'select',
                  'handler', 'control', 'field', 'form', 'option', 'label', 'btn', 'button'
                ]);
                return humanizeToken(text)
                  .split(/\\s+/)
                  .filter(word => !noiseWords.has(word.toLowerCase()))
                  .join(' ')
                  .trim();
              }

              function isUsefulSemanticToken(text) {
                const normalized = cleanText(text).toLowerCase();
                if (!normalized || normalized.length < 3) return false;
                const noise = new Set([
                  'id', 'chkbox id', 'checkbox', 'radio', 'input', 'textarea', 'select',
                  'handler', 'control', 'field', 'form', 'option', 'label', 'btn', 'button'
                ]);
                return !noise.has(normalized);
              }

              function cssSelector(el) {
                if (!el) return '';
                const tag = el.tagName.toLowerCase();
                const composite = compositeAutomationLocator(el);
                if (composite) return composite.css;
                const automation = automationAttribute(el);
                const volatileId = isVolatileFrameworkId(el.id);
                if (automation && (!el.id || volatileId || tag === 'mat-option')) {
                  return cssAttributeSelector(tag, automation.name, automation.value);
                }
                const controlName = el.getAttribute('formcontrolname') || '';
                if (controlName && (!el.id || volatileId)) {
                  return cssAttributeSelector(tag, 'formcontrolname', controlName);
                }
                if (el.id) return tag + '#' + CSS.escape(el.id);
                const name = el.getAttribute('name');
                if (name) return tag + '[name="' + name.replace(/"/g, '\\\\"') + '"]';
                if (automation) {
                  return cssAttributeSelector(tag, automation.name, automation.value);
                }
                const role = el.getAttribute('role');
                const controls = el.getAttribute('aria-controls');
                if (role && controls) return tag + '[role="' + role + '"][aria-controls="' + controls.replace(/"/g, '\\\\"') + '"]';
                const value = el.getAttribute('value');
                if (tag === 'option' && value) return 'option[value="' + value.replace(/"/g, '\\\\"') + '"]';
                const classes = typeof el.className === 'string'
                  ? el.className.trim().split(/\\s+/).filter(Boolean).slice(0, 3)
                  : [];
                return tag + classes.map(c => '.' + CSS.escape(c)).join('');
              }

              function isVisibleEnough(el, tag) {
                if (includeHidden) return true;
                // type=hidden inputs are framework state (Avaloq: componentstate, events,
                // pageresponse, param1..5, xpos/ypos, CSRF + serialized rO0AB... blobs) —
                // never scan them unless the user opted into hidden fields above.
                if (tag === 'input' && (el.type || '').toLowerCase() === 'hidden') return false;
                const style = window.getComputedStyle(el);
                const rect = el.getBoundingClientRect();
                return rect.width > 0
                  && rect.height > 0
                  && style.visibility !== 'hidden'
                  && style.display !== 'none';
              }

              function someText(el, attrs, kind) {
                const by = attrs.find(a => a.name === 'aria-labelledby')?.value;
                if (by) {
                  const text = by.split(/\\s+/)
                    .map(id => document.getElementById(id)?.textContent || '')
                    .map(cleanText)
                    .filter(Boolean)
                    .join(' ');
                  const useful = usefulText(text, el, kind);
                  if (useful) return rememberText(attrs, useful, 'aria-labelledby', true);
                }
                if (el.id) {
                  const label = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
                  const useful = usefulText(label?.textContent, el, kind);
                  if (useful) return rememberText(attrs, useful, 'label-for', true);
                }
                const wrappedLabel = el.closest('label');
                const wrappedText = usefulText(wrappedLabel?.textContent, el, kind);
                if (wrappedText) return rememberText(attrs, wrappedText, 'wrapped-label', true);
                const aria = attrs.find(a => a.name === 'aria-label')?.value;
                const ariaText = usefulText(aria, el, kind);
                if (ariaText) return rememberText(attrs, ariaText, 'aria-label', true);
                const placeholder = attrs.find(a => a.name === 'placeholder')?.value;
                const placeholderText = usefulText(placeholder, el, kind);
                if (placeholderText) return rememberText(attrs, placeholderText, 'placeholder', false);

                // Icon-only clickables carry their meaning in the sprite reference
                // (<use xlink:href='#ext.defaulticons.languagefrempty-32'>) - the last
                // dotted segment is the only distinguishing name available. Checked before
                // the label hunt so a neighbouring header cannot mislabel the icon.
                if ((kind || '') === 'icon-button') {
                  const use = el.querySelector('use');
                  const href = use?.getAttribute('xlink:href') || use?.getAttribute('href') || '';
                  const fragment = (href.split('#').pop() || '').split('.').pop() || '';
                  const iconName = humanizeToken(fragment.replace(/-?\\d+$/, ''));
                  if (iconName) return iconName;
                }

                // Self-labeled elements (buttons, links, options, labels...) carry their
                // own visible text - that IS the name. Only unlabeled form controls
                // (input/textarea/select) need the label hunt below. Without this gate the
                // container-id humanize step renamed self-labeled elements to their DOM id
                // (e.g. 'Accetta tutti i cookie' became 'onetrust accept btn handler',
                // because closest('[id]') matches the element itself).
                const selfTag = el.tagName.toLowerCase();
                const needsExternalLabel = ['input', 'textarea', 'select', 'mat-select'].includes(selfTag)
                  || ['combobox', 'textbox'].includes(attr(el, 'role'));
                if (!needsExternalLabel) {
                  const selfText = usefulText(el.innerText || el.textContent, el, kind);
                  if (selfText) return rememberText(attrs, selfText, 'self-text', false);
                }

                let current = el;
                for (let depth = 0; current && depth < 5; depth++, current = current.parentElement) {
                  const previous = current.previousElementSibling;
                  if (previous?.tagName?.toLowerCase() === 'label') {
                    const text = usefulText(previous.textContent, el, kind);
                    if (text) return text;
                  }

                  // Header-style labels are NOT <label> elements (Avaloq awInfobox: the
                  // ..._header div with <span class='awLabel'>E-mail address</span> precedes
                  // the ..._content div holding the input). Accept a preceding sibling's
                  // text only when the sibling carries no form controls itself — otherwise
                  // the previous form ROW would donate its label to this element.
                  if (previous && !previous.querySelector('input, textarea, select, button, [contenteditable]')) {
                    const headerText = cleanText(previous.innerText || previous.textContent);
                    if (headerText && headerText.length <= 80) {
                      const text = usefulText(headerText, el, kind);
                      if (text) return text;
                    }
                  }

                  const container = current.closest?.('.form-field, [data-slot="form-item"], [class*="form-field"], [id]');
                  if (container) {
                    const labels = Array.from(container.querySelectorAll('label'));
                    for (const label of labels) {
                      if (label.contains(el)) continue;
                      const labelRect = label.getBoundingClientRect();
                      const elRect = el.getBoundingClientRect();
                      const closeAbove = labelRect.bottom <= elRect.top + 8 && Math.abs(labelRect.left - elRect.left) < 260;
                      const sameContainer = label.parentElement === container || label.closest('.form-field, [id]') === container;
                      if (closeAbove || sameContainer) {
                        const text = usefulText(label.textContent, el, kind);
                        if (text) return text;
                      }
                    }

                    const containerId = container.getAttribute('id');
                    // Generated container ids (aw-id-gen-1) must not name the element —
                    // skipping here lets the loop climb to the real header at higher depth.
                    if (containerId && !looksGeneratedToken(containerId)) {
                      const human = humanizeToken(containerId);
                      if (human && !['div', 'input', 'textarea', 'button', 'select'].includes(human.toLowerCase())) return human;
                    }
                  }
                }

                const own = needsExternalLabel ? '' : cleanText(el.innerText || el.textContent || '');
                const ownText = usefulText(own, el, kind);
                if (ownText) return rememberText(attrs, ownText, 'self-text', false);
                const semantic = semanticAttributeText(el);
                if (semantic) return semantic;
                const fallback = [el.getAttribute('name'), el.id].find(v => v && !looksGeneratedToken(v));
                return humanizeToken(fallback || '');
              }

              function inputType(el) {
                return (el.getAttribute('type') || '').toLowerCase();
              }

              function controlKind(el, tag, role) {
                const type = inputType(el);
                if (tag === 'option') return 'select-option';
                if (tag === 'select') return 'select';
                if (tag === 'mat-select') return 'select';
                if (tag === 'mat-option') return 'select-option';
                if (tag === 'textarea' || role === 'textbox' || el.isContentEditable) return 'text-input';
                if (tag === 'input') {
                  if (['button', 'submit', 'reset'].includes(type)) return 'button';
                  if (type === 'checkbox') return 'checkbox-option';
                  if (type === 'radio') return 'radio-option';
                  if (type === 'file') return 'file-upload';
                  if (role === 'combobox') return 'autocomplete-input';
                  return 'text-input';
                }
                if (role === 'option') return 'select-option';
                if (role === 'menuitem') return 'menu-option';
                if (role === 'treeitem') return 'tree-option';
                if (role === 'tab') return 'tab-option';
                if (role === 'gridcell' && el.getAttribute('aria-selected') !== null) return 'calendar-day-option';
                if (role === 'checkbox') return 'checkbox-option';
                if (role === 'radio') return 'radio-option';
                if (role === 'switch') return 'switch-option';
                if (tag.startsWith('mat-')) return tag.replace('mat-', '') + '-option';
                if (tag === 'svg' || tag === 'mat-icon') return 'icon-button';
                // Icon-only wrappers (Avaloq language flags: <span class='awIcon'><svg><use ...>)
                // have no native semantics but ARE the click targets. Direct-child svg only —
                // a deep query would also flag the layout div wrapping a row of icons.
                if ((tag === 'span' || tag === 'div') && !(el.innerText || '').trim() && el.querySelector(':scope > svg')) return 'icon-button';
                // Pure text leaves (awLabel header spans) are readable values, not containers.
                if ((tag === 'span' || tag === 'label') && el.childElementCount === 0 && (el.textContent || '').trim()) return 'output-text';
                return role || tag;
              }

              function typeElementFor(el, tag, role, kind) {
                const type = inputType(el);
                if (['text-input', 'autocomplete-input'].includes(kind)) return 'input';
                if (tag === 'input' && !['button', 'submit', 'reset', 'file'].includes(type)) return 'input';
                if (tag === 'textarea' || role === 'textbox' || el.isContentEditable) return 'input';
                if ([
                  'button', 'select', 'select-option', 'checkbox-option', 'radio-option', 'switch-option',
                  'menu-option', 'tree-option', 'tab-option', 'calendar-day-option', 'file-upload', 'icon-button'
                ].includes(kind)) return 'button';
                if (kind === 'output-text') {
                  // A text leaf inside a clickable is the clickable's caption, not a value.
                  return el.closest('button, a, [role="button"], [role="link"]') ? 'button' : 'output';
                }
                if (['button', 'link', 'option', 'menuitem', 'tab', 'checkbox', 'radio', 'switch', 'treeitem', 'combobox'].includes(role)) return 'button';
                if (el.hasAttribute('aria-haspopup')) return 'button';
                if (['button', 'a', 'select', 'option'].includes(tag)) return 'button';
                if (tag.startsWith('mat-')) return 'button';
                // Test-id carriers with no native semantics (Avaloq avq-* cards, value
                // spans): a host wrapping a selection control is the clickable card;
                // anything living inside a button/link is clickable; the rest are
                // readable outputs (ISIN / TKN / currency values).
                const automationAttribute = automationAttributeNames.find(name => el.hasAttribute(name));
                if (automationAttribute) {
                  if (el.querySelector('mat-radio-button, mat-checkbox, input[type="radio"], input[type="checkbox"], [role="radio"], [role="checkbox"]')) return 'button';
                  if (el.closest('button, a, [role="button"], [role="link"]')) return 'button';
                  return 'output';
                }
                return tag;
              }

              function nearestCombobox(selectEl) {
                const container = selectEl.closest('.form-field, [id], div') || selectEl.parentElement;
                if (!container) return null;
                return container.querySelector('[role="combobox"], button, [data-slot="select-trigger"]');
              }

              function matOptionOwner(el, optionText, optionValue) {
                const automation = automationAttribute(el);
                if (!automation?.value) return null;
                const suffixes = Array.from(new Set([optionValue, optionText]
                  .map(cleanText)
                  .filter(Boolean)
                  .flatMap(value => [value, value.replace(/ +/g, '-')])));
                const candidates = Array.from(document.querySelectorAll('[' + automation.name + ']'));
                for (const suffix of suffixes) {
                  const marker = '-' + suffix;
                  if (!automation.value.toLowerCase().endsWith(marker.toLowerCase())) continue;
                  const ownerValue = automation.value.slice(0, automation.value.length - marker.length);
                  const owners = candidates.filter(candidate => candidate !== el
                    && (candidate.getAttribute(automation.name) || '') === ownerValue);
                  if (owners.length === 1) return owners[0];
                }
                return null;
              }

              function triggerWithin(owner) {
                if (!owner) return null;
                const triggerSelector = 'mat-select[formcontrolname], select[formcontrolname], '
                  + '[role="combobox"][formcontrolname], mat-select, select, [role="combobox"], button';
                if (owner.matches(triggerSelector)) return owner;
                return owner.querySelector(triggerSelector);
              }

              function optionTriggerSelector(el, optionText, optionValue) {
                const nativeSelect = el.tagName.toLowerCase() === 'option' ? el.closest('select') : null;
                if (nativeSelect) return cssSelector(nearestCombobox(nativeSelect) || nativeSelect);
                const owner = matOptionOwner(el, optionText, optionValue);
                const trigger = triggerWithin(owner);
                return trigger ? cssSelector(trigger) : '';
              }

              function isNativeSelectHiddenButUseful(el, tag) {
                if (tag !== 'select') return false;
                return Array.from(el.options || []).some(o => (o.textContent || '').trim());
              }

              function isDuplicateActionProxy(el, tag) {
                if (tag === 'mat-form-field'
                    && el.querySelector('input, textarea, select, mat-select, [role="combobox"], [role="textbox"]')) {
                  return true;
                }
                if (tag === 'mat-icon' && el.closest('button, a, [role="button"], [role="link"]')) {
                  return true;
                }
                return false;
              }

              function pushMetadata(attrs, name, value) {
                if (!value || attrs.some(attribute => attribute.name === name)) return;
                attrs.push({ name, value });
              }

              function optionXPath(selectEl, option) {
                const base = generateXPath(selectEl);
                const options = Array.from(selectEl.options || []);
                const value = option.getAttribute('value') || '';
                if (value && options.filter(candidate => (candidate.getAttribute('value') || '') === value).length === 1) {
                  return base + '/option[@value=' + xpathLiteral(value) + ']';
                }
                const text = cleanText(option.textContent || '');
                if (text && options.filter(candidate => cleanText(candidate.textContent || '') === text).length === 1) {
                  return base + '/option[normalize-space(.)=' + xpathLiteral(text) + ']';
                }
                const index = Math.max(1, options.indexOf(option) + 1);
                return base + '/option[' + index + ']';
              }

              const usedGeneratedIds = new Set();

              function slugId(text) {
                return (text || '').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 50);
              }

              function lastHrefSegment(href) {
                const cleaned = (href || '').split(/[?#]/)[0].replace(/\\/+$/, '');
                const idx = cleaned.lastIndexOf('/');
                return idx >= 0 ? cleaned.slice(idx + 1) : cleaned;
              }

              function makeDto(el, idx, override) {
                // The current DTO/geometry contract cannot encode a ShadowRoot boundary.
                // Playwright CSS locators pierce open roots, so page.locator(...) can return
                // those descendants even though their generated XPath is not executable from
                // document. Keep the authoritative scan set document-scoped until that boundary
                // is represented end to end instead of persisting a malformed locator.
                if (el?.getRootNode?.() !== document) return null;
                const tag = el.tagName.toLowerCase();
                const forceKeep = override?.forceKeep === true || isNativeSelectHiddenButUseful(el, tag);
                if (!forceKeep && !isVisibleEnough(el, tag)) return null;
                const attrs = Array.from(el.attributes || []).map(a => ({ name: a.name, value: a.value }));
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                const role = attr(el, 'role');
                const automationAttributeName = automationAttribute(el)?.name;
                const zIndex = style.zIndex && style.zIndex !== 'auto' ? style.zIndex : '';
                const kind = override?.attributeType || controlKind(el, tag, role);
                if (automationAttributeName) {
                  pushMetadata(attrs, 'automation.test-id.attribute', automationAttributeName);
                }
                if (role) attrs.push({ name: 'role', value: role });
                if (zIndex) attrs.push({ name: 'z-index', value: zIndex });
                if (kind) attrs.push({ name: 'control.kind', value: kind });
                if (role) attrs.push({ name: 'control.role', value: role });

                if (kind === 'select-option' && (tag === 'mat-option' || tag === 'option')) {
                  const optionText = cleanText(el.innerText || el.textContent || '');
                  const optionValue = el.getAttribute('value') || el.getAttribute('ng-reflect-value') || optionText;
                  pushMetadata(attrs, 'option-text', optionText);
                  pushMetadata(attrs, 'option-value', optionValue);
                  pushMetadata(attrs, 'trigger-selector', optionTriggerSelector(el, optionText, optionValue));
                }

                // Deterministic search id when the element has no DOM id and no configured
                // automation attribute. Built from the
                // strongest stable signals — name > aria-label > href segment > visible
                // text > input type — prefixed with the tag and made unique within this
                // scan. Stored only as attributeData['generated-id'] (a locator aid);
                // el.id / attribId are never fabricated.
                if (!el.id && !automationAttributeName) {
                  const basis = el.getAttribute('name')
                    || attr(el, 'aria-label')
                    || lastHrefSegment(el.getAttribute('href') || '')
                    || cleanText(el.innerText || el.textContent || '').slice(0, 40)
                    || inputType(el);
                  const genSlug = slugId(basis);
                  if (genSlug) {
                    let candidate = tag + '-' + genSlug;
                    let n = 2;
                    while (usedGeneratedIds.has(candidate)) candidate = tag + '-' + genSlug + '-' + n++;
                    usedGeneratedIds.add(candidate);
                    attrs.push({ name: 'generated-id', value: candidate });
                  }
                }
                return {
                  id: idx + 1,
                  tagName: override?.tagName || tag,
                  typeElement: override?.typeElement || typeElementFor(el, tag, role, kind),
                  nameLabel: '',
                  nameField: '',
                  definedName: '',
                  clientNamed: '',
                  xPath: override?.xPath || generateXPath(el),
                  someText: override?.someText || someText(el, attrs, kind),
                  attribId: el.id || '',
                  attribName: el.getAttribute('name') || '',
                  coordinates: rect.left.toFixed(2) + ',' + rect.top.toFixed(2),
                  attributeData: attrs,
                  customXPath: override?.customXPath || '',
                  iFrameXPath: '',
                  shadowHost: '',
                  shadowRoot: '',
                  nestedShadow: 'false',
                  cssSelector: override?.cssSelector || cssSelector(el),
                  attributeValue: override?.attributeValue || '',
                  attributeType: override?.attributeType || kind || role || '',
                  searchAttributeValue: override?.searchAttributeValue || '',
                  autoScroll: null,
                  autoEnter: null,
                  autoTab: null,
                  autoNext: null,
                  autoForceCoords: null,
                  forceCoordinates: '',
                  androidData: null
                };
              }

              const out = [];
              elements.forEach((el, idx) => {
                const tag = el.tagName.toLowerCase();
                if (isDuplicateActionProxy(el, tag)) return;
                // Native <option> nodes are represented by the richer select synthesis below
                // (clickable + readable DTO with trigger metadata). Emitting the raw node too
                // creates a third, transient duplicate with a positional locator.
                if (tag === 'option' && el.closest('select')) return;
                // A <select> is the clickable trigger: emit it under the decided-category
                // convention (tagName 'button', like the per-option DTOs below) so grids
                // that group by tag land it in the Button bucket instead of minting a
                // one-off 'select' group. The raw tag stays traceable in attributeData.
                const base = tag === 'select'
                  ? makeDto(el, idx, { tagName: 'button', typeElement: 'button' })
                  : makeDto(el, idx);
                if (base) {
                  if (tag === 'select') {
                    base.attributeData.push({ name: 'original-tag', value: 'select' });
                    base.attributeData.push({ name: 'select-xpath', value: generateXPath(el) });
                  }
                  out.push(base);
                }

                if (tag === 'select') {
                  const trigger = nearestCombobox(el);
                  const triggerSelector = trigger ? cssSelector(trigger) : cssSelector(el);
                  Array.from(el.options || []).forEach((option, optionIndex) => {
                    const text = (option.textContent || '').trim().replace(/\\s+/g, ' ');
                    if (!text) return;
                    const value = option.value || text;
                    const clickableDto = makeDto(el, idx + out.length + optionIndex, {
                      forceKeep: true,
                      tagName: 'button',
                      typeElement: 'button',
                      xPath: optionXPath(el, option),
                      cssSelector: triggerSelector,
                      customXPath: triggerSelector,
                      someText: text,
                      attributeValue: value,
                      attributeType: 'select-option',
                      searchAttributeValue: value
                    });
                    if (clickableDto) {
                      clickableDto.attributeData.push({ name: 'original-tag', value: 'option' });
                      clickableDto.attributeData.push({ name: 'option-value', value });
                      clickableDto.attributeData.push({ name: 'option-text', value: text });
                      clickableDto.attributeData.push({ name: 'select-xpath', value: generateXPath(el) });
                      clickableDto.attributeData.push({ name: 'control.kind', value: 'select-option' });
                      clickableDto.attributeData.push({ name: 'control.role', value: 'option' });
                      if (triggerSelector) clickableDto.attributeData.push({ name: 'trigger-selector', value: triggerSelector });
                      out.push(clickableDto);
                    }

                    const outputDto = makeDto(el, idx + out.length + optionIndex, {
                      forceKeep: true,
                      tagName: 'label',
                      typeElement: 'output',
                      xPath: optionXPath(el, option),
                      cssSelector: triggerSelector,
                      customXPath: triggerSelector,
                      someText: text,
                      attributeValue: text,
                      attributeType: 'output-text',
                      searchAttributeValue: text
                    });
                    if (outputDto) {
                      outputDto.attributeData.push({ name: 'original-tag', value: 'option' });
                      outputDto.attributeData.push({ name: 'option-value', value });
                      outputDto.attributeData.push({ name: 'option-text', value: text });
                      outputDto.attributeData.push({ name: 'select-xpath', value: generateXPath(el) });
                      outputDto.attributeData.push({ name: 'control.kind', value: 'select-output' });
                      outputDto.attributeData.push({ name: 'control.role', value: 'option' });
                      if (triggerSelector) outputDto.attributeData.push({ name: 'trigger-selector', value: triggerSelector });
                      out.push(outputDto);
                    }
                  });
                }
              });
              return out;
            }
            """;

    public List<ElementDTO> scan(Page page, String[] searchTerms, boolean includeHidden) {
        if (page == null || page.isClosed()) {
            return Collections.emptyList();
        }

        String selector = buildSelector(searchTerms);
        Locator locator = page.locator(selector);
        Object raw = locator.evaluateAll(
                SCAN_SCRIPT,
                Map.of(
                        "includeHidden", includeHidden,
                        "attributeNames", configuredAttributeNames(searchTerms)));
        List<ElementDTO> elements = mapElements(raw);
        log.info("Playwright scanner returned {} element(s) for selector '{}'", elements.size(), selector);
        return elements;
    }

    /** Runs the identical scanner script through a remote, owner-authorized page evaluator. */
    public List<ElementDTO> scan(Evaluator evaluator, String[] searchTerms, boolean includeHidden) {
        Objects.requireNonNull(evaluator, "Page Scanner evaluator is required");
        String selector = buildSelector(searchTerms);
        String script = "(input) => { const elements = Array.from(document.querySelectorAll(input.selector));"
                + " const scan = " + SCAN_SCRIPT + "; return scan(elements, input.scanOptions); }";
        Object raw = evaluator.evaluate(
                script,
                Map.of(
                        "selector", selector,
                        "scanOptions", Map.of(
                                "includeHidden", includeHidden,
                                "attributeNames", configuredAttributeNames(searchTerms))));
        List<ElementDTO> elements = mapElements(raw);
        log.info("Remote Playwright scanner returned {} element(s) for selector '{}'", elements.size(), selector);
        return elements;
    }

    @FunctionalInterface
    public interface Evaluator {
        Object evaluate(String script, Object argument);
    }

    private static String buildSelector(String[] searchTerms) {
        if (searchTerms == null || searchTerms.length == 0) {
            return DEFAULT_SELECTOR;
        }

        List<String> selectors = new ArrayList<>();
        for (String term : searchTerms) {
            if (term == null || term.isBlank()) {
                continue;
            }

            String normalized = term.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("attr:")) {
                String attributeSelector = attributeSelector(term.trim());
                if (attributeSelector != null) selectors.add(attributeSelector);
            } else if (normalized.contains("with id")) {
                selectors.add("[id]");
            } else if (normalized.contains("with name")) {
                selectors.add("[name]");
            } else if (normalized.contains("with test-id")) {
                selectors.add("[test-id], [data-testid], [data-test-id], [data-cy], [data-qa]");
            } else {
                selectors.add(term.trim());
            }
        }

        if (selectors.isEmpty()) {
            return DEFAULT_SELECTOR;
        }

        addCompanionSelectors(selectors);
        return String.join(", ", selectors);
    }

    private static void addCompanionSelectors(List<String> selectors) {
        addIfMissing(selectors, "[data-testid]");
        addIfMissing(selectors, "[data-test-id]");
        addIfMissing(selectors, "[test-id]");
        addIfMissing(selectors, "[data-cy]");
        addIfMissing(selectors, "[data-qa]");

        String joined = String.join(" ", selectors).toLowerCase(Locale.ROOT);
        if (joined.contains("select")
                || joined.contains("combobox")
                || joined.contains("listbox")
                || joined.contains("option")) {
            addIfMissing(selectors, "select");
            addIfMissing(selectors, "option");
            addIfMissing(selectors, "[role='combobox']");
            addIfMissing(selectors, "[role='listbox']");
            addIfMissing(selectors, "[role='option']");
            addIfMissing(selectors, "[data-radix-popper-content-wrapper]");
            addIfMissing(selectors, "mat-select");
            addIfMissing(selectors, "mat-option");
        }
        if (joined.contains("menu")) {
            addIfMissing(selectors, "[role='menu']");
            addIfMissing(selectors, "[role='menuitem']");
            addIfMissing(selectors, "[role='menubar']");
            addIfMissing(selectors, "mat-menu-item");
        }
        if (joined.contains("tree")) {
            addIfMissing(selectors, "[role='tree']");
            addIfMissing(selectors, "[role='treeitem']");
            addIfMissing(selectors, "mat-tree-node");
        }
        if (joined.contains("tab")) {
            addIfMissing(selectors, "[role='tab']");
            addIfMissing(selectors, "[role='tablist']");
            addIfMissing(selectors, "mat-tab");
        }
        if (joined.contains("grid") || joined.contains("calendar") || joined.contains("date")) {
            addIfMissing(selectors, "[role='grid']");
            addIfMissing(selectors, "[role='row']");
            addIfMissing(selectors, "[role='gridcell']");
            addIfMissing(selectors, "[aria-selected]");
            addIfMissing(selectors, "mat-datepicker-toggle");
            addIfMissing(selectors, "mat-calendar-body-cell");
        }
        if (joined.contains("checkbox")) {
            addIfMissing(selectors, "[role='checkbox']");
            addIfMissing(selectors, "input[type='checkbox']");
            addIfMissing(selectors, "mat-checkbox");
        }
        if (joined.contains("radio")) {
            addIfMissing(selectors, "[role='radio']");
            addIfMissing(selectors, "input[type='radio']");
            addIfMissing(selectors, "mat-radio-button");
        }
        if (joined.contains("input") || joined.contains("textbox") || joined.contains("textarea")) {
            addIfMissing(selectors, "input");
            addIfMissing(selectors, "textarea");
            addIfMissing(selectors, "[role='textbox']");
            addIfMissing(selectors, "[contenteditable='true']");
        }
    }

    static List<String> configuredAttributeNames(String[] searchTerms) {
        if (searchTerms == null || searchTerms.length == 0) return List.of();
        List<String> names = new ArrayList<>();
        for (String term : searchTerms) {
            if (term == null) continue;
            String normalized = term.trim();
            if (!normalized.regionMatches(true, 0, "attr:", 0, 5)) continue;
            String specification = normalized.substring(5).trim();
            int valueSeparator = specification.indexOf('=');
            String name = (valueSeparator < 0 ? specification : specification.substring(0, valueSeparator)).trim();
            if (isSafeAttributeName(name) && !names.contains(name.toLowerCase(Locale.ROOT))) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(names);
    }

    private static String attributeSelector(String term) {
        String specification = term.substring(5).trim();
        int valueSeparator = specification.indexOf('=');
        String name = (valueSeparator < 0 ? specification : specification.substring(0, valueSeparator)).trim();
        if (!isSafeAttributeName(name)) {
            log.warn("Ignoring unsafe Page Scanner attribute expression '{}'", term);
            return null;
        }
        if (valueSeparator < 0) return "[" + name.toLowerCase(Locale.ROOT) + "]";

        String value = specification.substring(valueSeparator + 1).trim();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return "[" + name.toLowerCase(Locale.ROOT) + "=\"" + cssAttributeValue(value) + "\"]";
    }

    private static boolean isSafeAttributeName(String value) {
        return value != null && value.matches("[A-Za-z_:][A-Za-z0-9_.:-]{0,127}");
    }

    private static String cssAttributeValue(String value) {
        return Objects.toString(value, "").replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void addIfMissing(List<String> selectors, String selector) {
        if (!selectors.contains(selector)) {
            selectors.add(selector);
        }
    }

    private static List<ElementDTO> mapElements(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return Collections.emptyList();
        }

        List<ElementDTO> elements = new ArrayList<>();
        Map<String, ElementDTO> unique = new LinkedHashMap<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }

            ElementDTO dto = normalizeActionGrouping(mapElement(map));
            String key = String.join(
                    "|",
                    firstNotBlank(dto.getXPath(), dto.getCssSelector(), dto.getCoordinates()),
                    Objects.toString(dto.getTagName(), ""),
                    Objects.toString(dto.getTypeElement(), ""),
                    Objects.toString(dto.getAttributeType(), ""),
                    Objects.toString(dto.getSomeText(), ""));
            if (!key.isBlank()) {
                unique.putIfAbsent(key, dto);
            }
        }

        int index = 1;
        for (ElementDTO dto : unique.values()) {
            dto.setId(index++);
            elements.add(dto);
        }
        return elements;
    }

    private static ElementDTO mapElement(Map<?, ?> map) {
        ElementDTO dto = new ElementDTO();
        dto.setId(asInteger(map.get("id")));
        dto.setTagName(asString(map.get("tagName")));
        dto.setNameLabel(asString(map.get("nameLabel")));
        dto.setNameField(asString(map.get("nameField")));
        dto.setDefinedName(asString(map.get("definedName")));
        dto.setClientNamed(asString(map.get("clientNamed")));
        dto.setXPath(asString(map.get("xPath")));
        dto.setSomeText(asString(map.get("someText")));
        dto.setAttribId(asString(map.get("attribId")));
        dto.setAttribName(asString(map.get("attribName")));
        dto.setCoordinates(asString(map.get("coordinates")));
        dto.setAttributeData(mapAttributes(map.get("attributeData")));
        dto.setTypeElement(
                classifyTag(asString(map.get("tagName")), asString(map.get("typeElement")), dto.getAttributeData()));
        dto.setCustomXPath(asString(map.get("customXPath")));
        dto.setIFrameXPath(asString(map.get("iFrameXPath")));
        dto.setShadowHost(asString(map.get("shadowHost")));
        dto.setShadowRoot(asString(map.get("shadowRoot")));
        dto.setNestedShadow(asString(map.get("nestedShadow")));
        dto.setCssSelector(asString(map.get("cssSelector")));
        dto.setAttributeValue(asString(map.get("attributeValue")));
        dto.setAttributeType(asString(map.get("attributeType")));
        dto.setSearchAttributeValue(asString(map.get("searchAttributeValue")));
        dto.setAutoScroll(asString(map.get("autoScroll")));
        dto.setAutoEnter(asString(map.get("autoEnter")));
        dto.setAutoTab(asString(map.get("autoTab")));
        dto.setAutoNext(asString(map.get("autoNext")));
        dto.setAutoForceCoords(asString(map.get("autoForceCoords")));
        dto.setForceCoordinates(asString(map.get("forceCoordinates")));
        return dto;
    }

    private static ElementDTO normalizeActionGrouping(ElementDTO dto) {
        if (dto == null) {
            return null;
        }
        if (isInputType(dto.getTypeElement())) {
            String originalTag = Objects.toString(dto.getTagName(), "");
            if (!"input".equalsIgnoreCase(originalTag)) {
                dto.setAttributeData(addAttributeIfMissing(dto.getAttributeData(), "original-tag", originalTag));
            }
            dto.setTagName("input");
        }
        return dto;
    }

    private static AttributeData[] mapAttributes(Object raw) {
        if (!(raw instanceof List<?> attrs)) {
            return new AttributeData[0];
        }

        List<AttributeData> mapped = new ArrayList<>();
        for (Object item : attrs) {
            if (!(item instanceof Map<?, ?> attr)) {
                continue;
            }
            mapped.add(new AttributeData(asString(attr.get("name")), asString(attr.get("value"))));
        }
        return mapped.toArray(new AttributeData[0]);
    }

    private static AttributeData[] addAttributeIfMissing(AttributeData[] attributes, String name, String value) {
        if (value == null || value.isBlank()) {
            return attributes == null ? new AttributeData[0] : attributes;
        }

        AttributeData[] existing = attributes == null ? new AttributeData[0] : attributes;
        for (AttributeData attribute : existing) {
            if (attribute != null && name.equalsIgnoreCase(attribute.getName())) {
                return existing;
            }
        }

        AttributeData[] updated = new AttributeData[existing.length + 1];
        System.arraycopy(existing, 0, updated, 0, existing.length);
        updated[existing.length] = new AttributeData(name, value);
        return updated;
    }

    private static String classifyTag(String tagName, String scannedTypeElement, AttributeData[] attributeData) {
        String tag = Objects.toString(tagName, "").toLowerCase(Locale.ROOT);
        if (isWritableControl(tag, attributeData)) {
            return "input";
        }
        if (isInputType(scannedTypeElement)) {
            return "input";
        }
        if (isOutputType(scannedTypeElement)) {
            return "output";
        }
        if (isButtonType(scannedTypeElement) || isClickableKind(attributeData)) {
            return "button";
        }
        if ("button".equals(tag)
                || "a".equals(tag)
                || "select".equals(tag)
                || "option".equals(tag)
                || tag.startsWith("mat-")
                || "svg".equals(tag)) {
            return "button";
        }
        // Safety net mirroring the JS rule: an automation-id carrier with no other signal
        // is a readable output (Avaloq value spans). Preserve the exact DOM attribute name;
        // test-id and data-testid are different locator contracts.
        String anyTestId = firstNotBlank(
                attr(attributeData, "data-testid"),
                attr(attributeData, "data-test-id"),
                attr(attributeData, "test-id"),
                attr(attributeData, "data-cy"),
                attr(attributeData, "data-qa"));
        if (anyTestId != null && !anyTestId.isBlank()) {
            return "output";
        }
        return tag;
    }

    private static boolean isWritableControl(String tag, AttributeData[] attributeData) {
        if ("textarea".equals(tag)) {
            return true;
        }

        String kind = Objects.toString(attr(attributeData, "control.kind"), "").toLowerCase(Locale.ROOT);
        String role = Objects.toString(
                        firstNotBlank(attr(attributeData, "control.role"), attr(attributeData, "role")), "")
                .toLowerCase(Locale.ROOT);
        if ("text-input".equals(kind) || "autocomplete-input".equals(kind) || "textbox".equals(role)) {
            return true;
        }

        if (!"input".equals(tag)) {
            return false;
        }

        // "radio" is deliberately NOT excluded: radios are set like inputs by the engine
        // (RADIO AS INPUT — see radioInputFromPageScannerStaysInputNotButton), so an
        // <input type="radio"> must classify as input even when the JS scan said button.
        String type = Objects.toString(attr(attributeData, "type"), "").toLowerCase(Locale.ROOT);
        return !List.of("button", "submit", "reset", "file", "checkbox", "hidden")
                .contains(type);
    }

    private static boolean isInputType(String typeElement) {
        return "input".equalsIgnoreCase(typeElement);
    }

    private static boolean isButtonType(String typeElement) {
        return "button".equalsIgnoreCase(typeElement);
    }

    private static boolean isOutputType(String typeElement) {
        return "output".equalsIgnoreCase(typeElement);
    }

    private static boolean isClickableKind(AttributeData[] attributeData) {
        String kind = attr(attributeData, "control.kind");
        String role = attr(attributeData, "control.role");
        if (kind != null) {
            String normalized = kind.toLowerCase(Locale.ROOT);
            if (normalized.contains("option")
                    || normalized.contains("button")
                    || normalized.contains("upload")
                    || normalized.contains("switch")
                    || normalized.contains("menu")
                    || normalized.contains("tree")
                    || normalized.contains("tab")
                    || normalized.contains("calendar")
                    || normalized.contains("select")) {
                return true;
            }
        }
        if (role != null) {
            String normalized = role.toLowerCase(Locale.ROOT);
            return List.of(
                            "button",
                            "link",
                            "option",
                            "menuitem",
                            "tab",
                            "checkbox",
                            "radio",
                            "switch",
                            "treeitem",
                            "combobox")
                    .contains(normalized);
        }
        return false;
    }

    private static String attr(AttributeData[] attributeData, String name) {
        if (attributeData == null) {
            return null;
        }
        for (AttributeData attribute : attributeData) {
            if (attribute != null && name.equalsIgnoreCase(attribute.getName())) {
                return attribute.getValue();
            }
        }
        return null;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
