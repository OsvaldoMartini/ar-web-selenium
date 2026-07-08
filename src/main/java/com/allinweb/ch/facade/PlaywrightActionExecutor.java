package com.allinweb.ch.facade;

import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlaywrightActionExecutor {

    /** Short per-attempt timeout so hidden/unactionable elements fail fast instead of the 30s default. */
    private static final double ACTION_TIMEOUT_MS = 6000;

    public boolean click(Page page, InstructionLoad instruction) {
        if (page == null || page.isClosed() || instruction == null) {
            return false;
        }

        if (isSelectOptionInstruction(instruction) && clickSelectOption(page, instruction)) {
            return true;
        }

        Locator locator = locate(page, instruction);
        if (locator != null && locator.count() > 0) {
            Locator target = locator.first();
            try {
                target.scrollIntoViewIfNeeded(
                        new Locator.ScrollIntoViewIfNeededOptions().setTimeout(ACTION_TIMEOUT_MS));
            } catch (Exception ignore) {
                // scroll is best-effort; continue to the click attempts
            }
            // 1) normal actionable click
            try {
                target.click(new Locator.ClickOptions().setTimeout(ACTION_TIMEOUT_MS));
                return true;
            } catch (Exception normal) {
                log.debug("Playwright normal click failed, trying force: {}", normal.getMessage());
            }
            // 2) force click (bypasses visibility/stability/hit-test — needed for hidden nav/skip links)
            try {
                target.click(new Locator.ClickOptions().setForce(true).setTimeout(ACTION_TIMEOUT_MS));
                return true;
            } catch (Exception forced) {
                log.debug("Playwright force click failed, trying JS dispatch: {}", forced.getMessage());
            }
            // 3) JS-dispatched click (fires the handler even when the WebDriver-style click is intercepted)
            try {
                target.dispatchEvent("click");
                return true;
            } catch (Exception dispatch) {
                log.debug("Playwright dispatch click failed, trying coordinates: {}", dispatch.getMessage());
            }
        }

        return clickCoordinates(page, instruction.getCoordinates());
    }

    private boolean clickSelectOption(Page page, InstructionLoad instruction) {
        String value = firstReferenceValue(instruction, "select.option.value", "option-value", "AttrData:option-value");
        String text = firstReferenceValue(instruction, "select.option.text", "option-text", "AttrData:option-text");
        String nativeSelectXPath =
                firstReferenceValue(instruction, "select.native.xpath", "select-xpath", "AttrData:select-xpath");
        String triggerCss =
                firstReferenceValue(instruction, "select.trigger.css", "trigger-selector", "AttrData:trigger-selector");

        String optionText = !text.isBlank() ? text : value;
        if (clickVisibleOption(page, optionText)) {
            return true;
        }

        if (openSelectTrigger(page, instruction, triggerCss, nativeSelectXPath)
                && clickVisibleOption(page, optionText)) {
            return true;
        }

        if (!nativeSelectXPath.isBlank() && !value.isBlank()) {
            try {
                page.locator("xpath=" + nativeSelectXPath)
                        .selectOption(value, new Locator.SelectOptionOptions().setTimeout(ACTION_TIMEOUT_MS));
                page.locator("xpath=" + nativeSelectXPath).dispatchEvent("change");
                return true;
            } catch (Exception nativeSelect) {
                log.debug("Playwright native selectOption fallback failed: {}", nativeSelect.getMessage());
            }
        }

        return false;
    }

    private boolean clickVisibleOption(Page page, String optionText) {
        if (optionText == null || optionText.isBlank()) {
            return false;
        }

        String textSelector = quotePlaywrightText(optionText);
        String[] selectors = {
            "[role=\"option\"]:has-text(" + textSelector + ")",
            "[role=\"menuitem\"]:has-text(" + textSelector + ")",
            "[cmdk-item]:has-text(" + textSelector + ")",
            "[data-radix-select-item]:has-text(" + textSelector + ")",
            "text=" + textSelector
        };

        for (String selector : selectors) {
            try {
                Locator option = page.locator(selector);
                if (option.count() > 0) {
                    if (clickLocator(option.last())) {
                        return true;
                    }
                }
            } catch (Exception optionClick) {
                log.debug("Playwright option click failed for {}: {}", selector, optionClick.getMessage());
            }
        }
        return false;
    }

    private boolean openSelectTrigger(
            Page page, InstructionLoad instruction, String triggerCss, String nativeSelectXPath) {
        if (!triggerCss.isBlank() && clickFirst(page.locator(triggerCss))) {
            return true;
        }

        if (!nativeSelectXPath.isBlank()) {
            String siblingTriggerXPath =
                    nativeSelectXPath + "/preceding-sibling::*[@role='combobox' or self::button][1]";
            if (clickFirst(page.locator("xpath=" + siblingTriggerXPath))) {
                return true;
            }
        }

        Locator trigger = locate(page, instruction);
        return trigger != null && trigger.count() > 0 && clickLocator(trigger.first());
    }

    private boolean clickFirst(Locator locator) {
        try {
            return locator != null && locator.count() > 0 && clickLocator(locator.first());
        } catch (Exception error) {
            log.debug("Playwright clickFirst failed: {}", error.getMessage());
            return false;
        }
    }

    private boolean clickLocator(Locator locator) {
        try {
            locator.click(new Locator.ClickOptions().setTimeout(ACTION_TIMEOUT_MS));
            return true;
        } catch (Exception normal) {
            log.debug("Playwright normal locator click failed: {}", normal.getMessage());
        }
        try {
            locator.click(new Locator.ClickOptions().setForce(true).setTimeout(ACTION_TIMEOUT_MS));
            return true;
        } catch (Exception forced) {
            log.debug("Playwright force locator click failed: {}", forced.getMessage());
        }
        try {
            locator.dispatchEvent("click");
            return true;
        } catch (Exception dispatch) {
            log.debug("Playwright dispatch locator click failed: {}", dispatch.getMessage());
            return false;
        }
    }

    private boolean isWritableLocator(Locator locator) {
        try {
            Object writable = locator.evaluate(
                    """
                    (el) => {
                      if (!el) return false;
                      const tag = (el.tagName || '').toLowerCase();
                      const type = (el.getAttribute('type') || 'text').toLowerCase();
                      const role = (el.getAttribute('role') || '').toLowerCase();
                      if (el.isContentEditable || role === 'textbox') return true;
                      if (tag === 'textarea') return true;
                      if (tag !== 'input') return false;
                      return !['button', 'submit', 'reset', 'file', 'checkbox', 'radio', 'hidden', 'image'].includes(type);
                    }
                    """);
            return Boolean.TRUE.equals(writable);
        } catch (Exception error) {
            log.debug("Playwright writable probe failed: {}", error.getMessage());
            return false;
        }
    }

    public boolean fill(Page page, InstructionLoad instruction, FieldData data) {
        if (page == null || page.isClosed() || instruction == null) {
            return false;
        }

        if (isClickOnlyInstruction(instruction)) {
            log.info("Playwright fill requested for click-only control '{}'; routing to click", instruction.getName());
            return click(page, instruction);
        }

        Locator locator = locateWritable(page, instruction);
        if (locator == null || locator.count() == 0) {
            return fillCoordinates(page, instruction.getCoordinates(), data == null ? "" : data.getValue());
        }

        Locator first = locator.first();
        try {
            first.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(ACTION_TIMEOUT_MS));
        } catch (Exception ignore) {
            // best-effort
        }
        try {
            first.fill(data == null ? "" : data.getValue(), new Locator.FillOptions().setTimeout(ACTION_TIMEOUT_MS));
            return true;
        } catch (Exception fillError) {
            log.debug("Playwright fill failed, trying coordinates: {}", fillError.getMessage());
            return fillCoordinates(page, instruction.getCoordinates(), data == null ? "" : data.getValue());
        }
    }

    private Locator locateWritable(Page page, InstructionLoad instruction) {
        List<String> selectors = selectorsFor(instruction);
        for (String selector : selectors) {
            try {
                Locator locator;
                if (instruction.getIFrameXPath() != null
                        && !instruction.getIFrameXPath().isBlank()) {
                    FrameLocator frame = page.frameLocator("xpath=" + instruction.getIFrameXPath());
                    locator = frame.locator(selector);
                } else {
                    locator = page.locator(selector);
                }

                if (locator.count() > 0 && isWritableLocator(locator.first())) {
                    return locator;
                }
            } catch (Exception error) {
                log.debug("Playwright writable locator failed for {}: {}", selector, error.getMessage());
            }
        }
        return null;
    }

    public String text(Page page, InstructionLoad instruction) {
        if (page == null || page.isClosed() || instruction == null) {
            return "";
        }

        Locator locator = locate(page, instruction);
        if (locator == null || locator.count() == 0) {
            return "";
        }
        return locator.first().innerText();
    }

    private Locator locate(Page page, InstructionLoad instruction) {
        List<String> selectors = selectorsFor(instruction);
        for (String selector : selectors) {
            try {
                Locator locator;
                if (instruction.getIFrameXPath() != null
                        && !instruction.getIFrameXPath().isBlank()) {
                    FrameLocator frame = page.frameLocator("xpath=" + instruction.getIFrameXPath());
                    locator = frame.locator(selector);
                } else {
                    locator = page.locator(selector);
                }

                if (locator.count() > 0) {
                    return locator;
                }
            } catch (Exception error) {
                log.debug("Playwright locator failed for {}: {}", selector, error.getMessage());
            }
        }
        return null;
    }

    private static List<String> selectorsFor(InstructionLoad instruction) {
        List<String> selectors = new ArrayList<>();

        addXPath(selectors, instruction.getXpath());
        addCss(selectors, instruction.getCssSelector());
        addReferences(selectors, instruction.getReferenceLoadDTOList());

        return selectors;
    }

    private static void addReferences(List<String> selectors, List<ReferenceLoadDTO> references) {
        if (references == null) {
            return;
        }

        String originalTag = "";
        String id = "";
        String name = "";
        for (ReferenceLoadDTO ref : references) {
            if (ref == null
                    || ref.getReferenceType() == null
                    || ref.getValue() == null
                    || ref.getValue().isBlank()) {
                continue;
            }

            String type = ref.getReferenceType().toLowerCase(Locale.ROOT);
            String value = ref.getValue();
            if (type.equals("dom.originaltag") || type.equals("attrdata:original-tag")) {
                originalTag = value;
            } else if (type.equals("locator.best.byid") || type.equals("attrdata:id")) {
                id = value;
            } else if (type.equals("locator.best.byname") || type.equals("attrdata:name")) {
                name = value;
            }

            // Order matters: "test-id"/"data-testid" both contain "id", so they must be checked
            // BEFORE the generic id branch, or they'd be mis-built as an #id selector.
            if (type.contains("xpath")) {
                addXPath(selectors, value);
            } else if (type.contains("css")) {
                addCss(selectors, value);
            } else if (type.contains("test-id") || type.contains("data-testid")) {
                String escaped = cssAttribute(value);
                addCss(selectors, "[test-id=\"" + escaped + "\"], [data-testid=\"" + escaped + "\"]");
            } else if (type.contains("id")) {
                addCss(selectors, "#" + cssEscape(value.replaceFirst("^#", "")));
            } else if (type.contains("name")) {
                addCss(selectors, "[name=\"" + cssAttribute(value) + "\"]");
            }
        }

        addOriginalTagFallbacks(selectors, originalTag, id, name);
    }

    private static void addOriginalTagFallbacks(List<String> selectors, String originalTag, String id, String name) {
        if (originalTag == null || originalTag.isBlank() || !isSafeTagName(originalTag)) {
            return;
        }

        String tag = originalTag.toLowerCase(Locale.ROOT);
        if (id != null && !id.isBlank()) {
            addCss(selectors, tag + "#" + cssEscape(id.replaceFirst("^#", "")));
            addXPath(selectors, "//" + tag + "[@id='" + id + "']");
        }
        if (name != null && !name.isBlank()) {
            addCss(selectors, tag + "[name=\"" + cssAttribute(name) + "\"]");
            addXPath(selectors, "//" + tag + "[@name='" + name + "']");
        }
    }

    private static boolean isSafeTagName(String value) {
        return value.matches("[A-Za-z][A-Za-z0-9_-]*");
    }

    private static boolean isSelectOptionInstruction(InstructionLoad instruction) {
        return !firstReferenceValue(instruction, "select.option.value", "option-value", "AttrData:option-value")
                        .isBlank()
                || !firstReferenceValue(instruction, "select.option.text", "option-text", "AttrData:option-text")
                        .isBlank()
                || "select-option"
                        .equalsIgnoreCase(firstReferenceValue(
                                instruction, "control.kind", "AttrData:control.kind", "attributeType"));
    }

    private static boolean isClickOnlyInstruction(InstructionLoad instruction) {
        String kind = firstReferenceValue(instruction, "control.kind", "AttrData:control.kind", "attributeType")
                .toLowerCase(Locale.ROOT);
        String type = firstReferenceValue(instruction, "type", "AttrData:type").toLowerCase(Locale.ROOT);
        String role = firstReferenceValue(instruction, "control.role", "AttrData:control.role", "role", "AttrData:role")
                .toLowerCase(Locale.ROOT);

        if (isSelectOptionInstruction(instruction)) {
            return true;
        }
        if (kind.contains("radio")
                || kind.contains("checkbox")
                || kind.contains("switch")
                || kind.contains("button")
                || kind.contains("option")
                || kind.contains("menu")
                || kind.contains("tree")
                || kind.contains("tab")
                || kind.contains("calendar")
                || kind.contains("upload")) {
            return true;
        }
        if (List.of("radio", "checkbox", "button", "submit", "reset", "file", "hidden").contains(type)) {
            return true;
        }
        return List.of("radio", "checkbox", "button", "switch", "option", "menuitem", "tab", "treeitem")
                .contains(role);
    }

    private static String firstReferenceValue(InstructionLoad instruction, String... referenceTypes) {
        if (referenceTypes == null) {
            return "";
        }
        for (String referenceType : referenceTypes) {
            String value = referenceValue(instruction, referenceType);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String referenceValue(InstructionLoad instruction, String referenceType) {
        if (instruction == null || instruction.getReferenceLoadDTOList() == null || referenceType == null) {
            return "";
        }
        for (ReferenceLoadDTO ref : instruction.getReferenceLoadDTOList()) {
            if (ref != null
                    && ref.getReferenceType() != null
                    && referenceType.equalsIgnoreCase(ref.getReferenceType())
                    && ref.getValue() != null) {
                return ref.getValue().trim();
            }
        }
        return "";
    }

    private static void addXPath(List<String> selectors, String xpath) {
        if (xpath != null && !xpath.isBlank()) {
            selectors.add("xpath=" + xpath);
        }
    }

    private static void addCss(List<String> selectors, String css) {
        if (css != null && !css.isBlank()) {
            selectors.add(css);
        }
    }

    private static boolean clickCoordinates(Page page, String coordinates) {
        double[] point = parseCoordinates(coordinates);
        if (point == null) {
            return false;
        }
        page.mouse().click(point[0], point[1]);
        return true;
    }

    private static boolean fillCoordinates(Page page, String coordinates, String value) {
        double[] point = parseCoordinates(coordinates);
        if (point == null) {
            return false;
        }
        page.mouse().click(point[0], point[1]);
        Object writable = page.evaluate(
                """
                () => {
                  const el = document.activeElement;
                  if (!el) return false;
                  const tag = (el.tagName || '').toLowerCase();
                  const type = (el.getAttribute('type') || 'text').toLowerCase();
                  const role = (el.getAttribute('role') || '').toLowerCase();
                  if (el.isContentEditable || role === 'textbox') return true;
                  if (tag === 'textarea') return true;
                  if (tag !== 'input') return false;
                  return !['button', 'submit', 'reset', 'file', 'checkbox', 'radio', 'hidden', 'image'].includes(type);
                }
                """);
        if (!Boolean.TRUE.equals(writable)) {
            log.warn("Playwright coordinate fill refused: focused element is not writable at {}", coordinates);
            return false;
        }
        page.keyboard().press("Control+A");
        page.keyboard().type(value == null ? "" : value);
        return true;
    }

    private static double[] parseCoordinates(String coordinates) {
        if (coordinates == null || coordinates.isBlank()) {
            return null;
        }

        String[] parts = coordinates.split(",");
        if (parts.length < 2) {
            return null;
        }

        try {
            return new double[] {Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static String cssEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace(" ", "\\ ");
    }

    private static String cssAttribute(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String quotePlaywrightText(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @SuppressWarnings("unused")
    private static BoundingBox touchBoundingBox(BoundingBox box) {
        return box;
    }
}
