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
            (elements, includeHidden) => {
              function generateXPath(el) {
                if (!el || el.nodeType !== 1) return '';
                if (el.id) return "//*[@id='" + el.id.replace(/'/g, "\\\\'") + "']";
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

              function cssSelector(el) {
                if (!el) return '';
                const tag = el.tagName.toLowerCase();
                if (el.id) return tag + '#' + CSS.escape(el.id);
                const name = el.getAttribute('name');
                if (name) return tag + '[name="' + name.replace(/"/g, '\\\\"') + '"]';
                const testId = el.getAttribute('data-testid') || el.getAttribute('data-test-id') || el.getAttribute('test-id') || el.getAttribute('data-cy') || el.getAttribute('data-qa');
                if (testId) {
                  const attrName = el.getAttribute('data-testid') ? 'data-testid'
                    : el.getAttribute('data-test-id') ? 'data-test-id'
                    : el.getAttribute('test-id') ? 'test-id'
                    : el.getAttribute('data-cy') ? 'data-cy'
                    : 'data-qa';
                  return tag + '[' + attrName + '="' + testId.replace(/"/g, '\\\\"') + '"]';
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
                if (tag === 'input' && (el.type || '').toLowerCase() === 'hidden') return true;
                const style = window.getComputedStyle(el);
                const rect = el.getBoundingClientRect();
                return rect.width > 0
                  && rect.height > 0
                  && style.visibility !== 'hidden'
                  && style.display !== 'none';
              }

              function someText(el, attrs) {
                const by = attrs.find(a => a.name === 'aria-labelledby')?.value;
                if (by) {
                  const label = document.getElementById(by);
                  if (label?.textContent) return label.textContent.trim().replace(/\\s+/g, ' ').slice(0, 200);
                }
                if (el.id) {
                  const label = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
                  if (label?.textContent) return label.textContent.trim().replace(/\\s+/g, ' ').slice(0, 200);
                }
                const wrappedLabel = el.closest('label');
                if (wrappedLabel?.textContent) return wrappedLabel.textContent.trim().replace(/\\s+/g, ' ').slice(0, 200);
                const aria = attrs.find(a => a.name === 'aria-label')?.value;
                if (aria) return aria.trim();
                const placeholder = attrs.find(a => a.name === 'placeholder')?.value;
                if (placeholder) return placeholder.trim();
                return (el.innerText || el.textContent || '').trim().replace(/\\s+/g, ' ').slice(0, 200);
              }

              function inputType(el) {
                return (el.getAttribute('type') || '').toLowerCase();
              }

              function controlKind(el, tag, role) {
                const type = inputType(el);
                if (tag === 'option') return 'select-option';
                if (tag === 'select') return 'select';
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
                return role || tag;
              }

              function typeElementFor(el, tag, role, kind) {
                const type = inputType(el);
                if (['text-input', 'autocomplete-input'].includes(kind)) return 'input';
                if (tag === 'input' && !['button', 'submit', 'reset', 'checkbox', 'radio', 'file'].includes(type)) return 'input';
                if (tag === 'textarea' || role === 'textbox' || el.isContentEditable) return 'input';
                if ([
                  'button', 'select', 'select-option', 'checkbox-option', 'radio-option', 'switch-option',
                  'menu-option', 'tree-option', 'tab-option', 'calendar-day-option', 'file-upload', 'icon-button'
                ].includes(kind)) return 'button';
                if (['button', 'link', 'option', 'menuitem', 'tab', 'checkbox', 'radio', 'switch', 'treeitem', 'combobox'].includes(role)) return 'button';
                if (el.hasAttribute('aria-haspopup')) return 'button';
                if (['button', 'a', 'select', 'option'].includes(tag)) return 'button';
                if (tag.startsWith('mat-')) return 'button';
                return tag;
              }

              function nearestCombobox(selectEl) {
                const container = selectEl.closest('.form-field, [id], div') || selectEl.parentElement;
                if (!container) return null;
                return container.querySelector('[role="combobox"], button, [data-slot="select-trigger"]');
              }

              function isNativeSelectHiddenButUseful(el, tag) {
                if (tag !== 'select') return false;
                return Array.from(el.options || []).some(o => (o.textContent || '').trim());
              }

              function optionXPath(selectEl, option) {
                const base = generateXPath(selectEl);
                const options = Array.from(selectEl.options || []);
                const index = Math.max(1, options.indexOf(option) + 1);
                return base + '/option[' + index + ']';
              }

              function makeDto(el, idx, override) {
                const tag = el.tagName.toLowerCase();
                const forceKeep = override?.forceKeep === true || isNativeSelectHiddenButUseful(el, tag);
                if (!forceKeep && !isVisibleEnough(el, tag)) return null;
                const attrs = Array.from(el.attributes || []).map(a => ({ name: a.name, value: a.value }));
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                const role = attr(el, 'role');
                const testId = attr(el, 'data-testid') || attr(el, 'data-test-id') || attr(el, 'test-id') || attr(el, 'data-cy') || attr(el, 'data-qa');
                const zIndex = style.zIndex && style.zIndex !== 'auto' ? style.zIndex : '';
                const kind = override?.attributeType || controlKind(el, tag, role);
                if (role) attrs.push({ name: 'role', value: role });
                if (testId) attrs.push({ name: 'data-testid', value: testId });
                if (zIndex) attrs.push({ name: 'z-index', value: zIndex });
                if (kind) attrs.push({ name: 'control.kind', value: kind });
                if (role) attrs.push({ name: 'control.role', value: role });
                return {
                  id: idx + 1,
                  tagName: override?.tagName || tag,
                  typeElement: override?.typeElement || typeElementFor(el, tag, role, kind),
                  nameLabel: '',
                  nameField: '',
                  definedName: '',
                  clientNamed: '',
                  xPath: override?.xPath || generateXPath(el),
                  someText: override?.someText || someText(el, attrs),
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
                const base = makeDto(el, idx);
                if (base) out.push(base);

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
        Object raw = locator.evaluateAll(SCAN_SCRIPT, includeHidden);
        List<ElementDTO> elements = mapElements(raw);
        log.info("Playwright scanner returned {} element(s) for selector '{}'", elements.size(), selector);
        return elements;
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
            if (normalized.contains("with id")) {
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
        if (joined.contains("select") || joined.contains("combobox") || joined.contains("listbox") || joined.contains("option")) {
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

            ElementDTO dto = mapElement(map);
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
        dto.setTypeElement(classifyTag(
                asString(map.get("tagName")), asString(map.get("typeElement")), dto.getAttributeData()));
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

    private static String classifyTag(String tagName, String scannedTypeElement, AttributeData[] attributeData) {
        if (isInputType(scannedTypeElement)) {
            return "input";
        }
        if (isOutputType(scannedTypeElement)) {
            return "output";
        }
        if (isButtonType(scannedTypeElement) || isClickableKind(attributeData)) {
            return "button";
        }
        String tag = Objects.toString(tagName, "").toLowerCase(Locale.ROOT);
        if ("textarea".equals(tag)) {
            return "input";
        }
        if ("button".equals(tag)
                || "a".equals(tag)
                || "select".equals(tag)
                || "option".equals(tag)
                || tag.startsWith("mat-")
                || "svg".equals(tag)) {
            return "button";
        }
        return tag;
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
            return List.of("button", "link", "option", "menuitem", "tab", "checkbox", "radio", "switch", "treeitem", "combobox")
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
