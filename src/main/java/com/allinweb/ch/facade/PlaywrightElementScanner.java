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

    private static final String DEFAULT_SELECTOR = "input, textarea, button, a, select, option, label, span, div";

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

              function cssSelector(el) {
                if (!el) return '';
                const tag = el.tagName.toLowerCase();
                if (el.id) return tag + '#' + CSS.escape(el.id);
                const name = el.getAttribute('name');
                if (name) return tag + '[name="' + name.replace(/"/g, '\\\\"') + '"]';
                const testId = el.getAttribute('data-testid') || el.getAttribute('test-id');
                if (testId) return tag + '[data-testid="' + testId.replace(/"/g, '\\\\"') + '"]';
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
                const aria = attrs.find(a => a.name === 'aria-label')?.value;
                if (aria) return aria.trim();
                const placeholder = attrs.find(a => a.name === 'placeholder')?.value;
                if (placeholder) return placeholder.trim();
                return (el.innerText || el.textContent || '').trim().replace(/\\s+/g, ' ').slice(0, 200);
              }

              return elements.map((el, idx) => {
                const tag = el.tagName.toLowerCase();
                if (!isVisibleEnough(el, tag)) return null;
                const attrs = Array.from(el.attributes || []).map(a => ({ name: a.name, value: a.value }));
                const rect = el.getBoundingClientRect();
                return {
                  id: idx + 1,
                  tagName: tag,
                  typeElement: tag,
                  nameLabel: '',
                  nameField: '',
                  definedName: '',
                  clientNamed: '',
                  xPath: generateXPath(el),
                  someText: someText(el, attrs),
                  attribId: el.id || '',
                  attribName: el.getAttribute('name') || '',
                  coordinates: rect.left.toFixed(2) + ',' + rect.top.toFixed(2),
                  attributeData: attrs,
                  customXPath: '',
                  iFrameXPath: '',
                  shadowHost: '',
                  shadowRoot: '',
                  nestedShadow: 'false',
                  cssSelector: cssSelector(el),
                  attributeValue: '',
                  attributeType: '',
                  searchAttributeValue: '',
                  autoScroll: null,
                  autoEnter: null,
                  autoTab: null,
                  autoNext: null,
                  autoForceCoords: null,
                  forceCoordinates: '',
                  androidData: null
                };
              }).filter(Boolean);
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
                selectors.add("[test-id], [data-testid]");
            } else {
                selectors.add(term.trim());
            }
        }

        return selectors.isEmpty() ? DEFAULT_SELECTOR : String.join(", ", selectors);
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
            String key = firstNotBlank(dto.getXPath(), dto.getCssSelector(), dto.getCoordinates());
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
        dto.setTypeElement(classifyTag(asString(map.get("tagName"))));
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

    private static String classifyTag(String tagName) {
        String tag = Objects.toString(tagName, "").toLowerCase(Locale.ROOT);
        if ("textarea".equals(tag)) {
            return "input";
        }
        if ("option".equals(tag)) {
            return "select";
        }
        return tag;
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
