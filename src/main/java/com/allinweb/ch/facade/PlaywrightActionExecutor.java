package com.allinweb.ch.facade;

import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.util.InputFlags;
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
    private static final String OPTION_CANDIDATE_SELECTOR =
            "[role=\"option\"], [role=\"menuitem\"], [cmdk-item], [data-radix-select-item]";

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
            // 3) MDC/Avaloq wrapped controls (BUG Not Clik): the native input is an
            //    invisible overlay covered by ripple/touch-target layers, and the click
            //    a human makes lands on the wrapping control or the card host. Click the
            //    nearest effective ancestor before resorting to JS dispatch.
            try {
                Locator effective =
                        target.locator("xpath=ancestor-or-self::*[self::mat-radio-button or self::mat-checkbox"
                                + " or self::mat-slide-toggle or contains(@class,'avq-state-layer-host')][1]");
                if (effective.count() > 0) {
                    effective.first().click(new Locator.ClickOptions().setTimeout(ACTION_TIMEOUT_MS));
                    return true;
                }
            } catch (Exception ancestor) {
                log.debug("Playwright ancestor-control click failed, trying JS dispatch: {}", ancestor.getMessage());
            }
            // 4) JS-dispatched click (fires the handler even when the WebDriver-style click is intercepted)
            try {
                target.dispatchEvent("click");
                return true;
            } catch (Exception dispatch) {
                log.debug("Playwright dispatch click failed, trying coordinates: {}", dispatch.getMessage());
            }
        }

        return clickCoordinates(page, instruction.getCoordinates());
    }

    /**
     * Single-shot click used by TEST_CLICK_DTO / TEST_INPUT_DTO scanner buttons.
     *
     * <p>Locator discovery may try multiple locator sources, but once a concrete Playwright action
     * is attempted it does not run force-click, JS dispatch, or coordinate retry. This prevents the
     * manual scanner test buttons from producing duplicate clicks when the first action changes the
     * page but Playwright reports a failure.
     */
    public boolean clickOnce(Page page, InstructionLoad instruction) {
        if (page == null || page.isClosed() || instruction == null) {
            return false;
        }

        Locator locator = locate(page, instruction);
        if (locator == null || locator.count() == 0) {
            return runCoordinateClick(page, instruction.getCoordinates());
        }

        Locator target = locator.first();
        try {
            target.scrollIntoViewIfNeeded(
                    new Locator.ScrollIntoViewIfNeededOptions().setTimeout(ACTION_TIMEOUT_MS));
        } catch (Exception scroll) {
            log.debug("Playwright single-shot scroll failed before click: {}", scroll.getMessage());
        }

        try {
            target.click(new Locator.ClickOptions().setTimeout(ACTION_TIMEOUT_MS));
            return true;
        } catch (Exception click) {
            log.debug("Playwright single-shot click failed; no second click will be attempted: {}", click.getMessage());
            return false;
        }
    }

    private boolean clickSelectOption(Page page, InstructionLoad instruction) {
        String value = firstReferenceValue(instruction, "select.option.value", "option-value", "AttrData:option-value");
        String text = firstReferenceValue(instruction, "select.option.text", "option-text", "AttrData:option-text");
        String nativeSelectXPath =
                firstReferenceValue(instruction, "select.native.xpath", "select-xpath", "AttrData:select-xpath");
        String triggerCss =
                firstReferenceValue(instruction, "select.trigger.css", "trigger-selector", "AttrData:trigger-selector");

        String optionText = !text.isBlank() ? text : value;
        // A stable option test-id/XPath is safe to use while its own overlay is still open.
        // Never start with a page-global text match: linked selects commonly expose the same
        // currency text and that would click whichever side happened to be open.
        if (clickSavedExactOption(page, instruction, optionText)) {
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

        OpenedSelect opened = openSelectTrigger(page, instruction, triggerCss, nativeSelectXPath);
        if (!opened.opened()) return false;

        // Angular/CDK recreates the option after opening. Its stable automation id is the
        // strongest contract and remains scoped to the intended debit/credit owner.
        if (clickSavedExactOption(page, instruction, optionText)) {
            return true;
        }
        if (!opened.panelCss().isBlank()
                && clickExactOptions(page.locator(opened.panelCss()).locator(OPTION_CANDIDATE_SELECTOR), optionText, true)) {
            return true;
        }
        // Last resort for libraries without aria-controls: only click when exactly one visible
        // option on the entire page has the exact normalized text.
        if (clickExactOptions(page.locator(OPTION_CANDIDATE_SELECTOR), optionText, true)) {
            return true;
        }

        return false;
    }

    private boolean clickSavedExactOption(Page page, InstructionLoad instruction, String optionText) {
        if (instruction.getCssSelector() != null
                && !instruction.getCssSelector().isBlank()
                && clickExactOptions(page.locator(instruction.getCssSelector()), optionText, true)) {
            return true;
        }
        if (instruction.getXpath() != null && !instruction.getXpath().isBlank()) {
            try {
                if (clickExactOptions(page.locator("xpath=" + instruction.getXpath()), optionText, true)) return true;
            } catch (Exception invalidXPath) {
                log.debug("Saved option XPath failed: {}", invalidXPath.getMessage());
            }
        }

        String[][] automationReferences = {
            {"test-id", "AttrData:test-id"},
            {"data-testid", "AttrData:data-testid"},
            {"data-test-id", "AttrData:data-test-id"},
            {"data-cy", "AttrData:data-cy"},
            {"data-qa", "AttrData:data-qa"}
        };
        for (String[] referenceTypes : automationReferences) {
            String value = firstReferenceValue(instruction, referenceTypes);
            if (value.isBlank()) continue;
            String selector = "[" + referenceTypes[0] + "=\"" + cssAttribute(value) + "\"]";
            if (clickExactOptions(page.locator(selector), optionText, true)) return true;
        }
        return false;
    }

    private boolean clickExactOptions(Locator candidates, String expectedText, boolean requireUnique) {
        if (candidates == null || expectedText == null || expectedText.isBlank()) return false;
        List<Locator> matches = new ArrayList<>();
        try {
            int count = candidates.count();
            for (int index = 0; index < count; index++) {
                Locator candidate = candidates.nth(index);
                if (!candidate.isVisible()) continue;
                String actual = candidate.innerText();
                if (normalizeOptionText(expectedText).equals(normalizeOptionText(actual))) {
                    matches.add(candidate);
                }
            }
        } catch (Exception lookup) {
            log.debug("Exact option lookup failed: {}", lookup.getMessage());
            return false;
        }
        if (matches.isEmpty() || (requireUnique && matches.size() != 1)) return false;
        return clickLocator(matches.get(0));
    }

    private OpenedSelect openSelectTrigger(
            Page page, InstructionLoad instruction, String triggerCss, String nativeSelectXPath) {
        if (!triggerCss.isBlank()) {
            OpenedSelect opened = clickTrigger(page.locator(triggerCss));
            if (opened.opened()) return opened;
        }

        if (!nativeSelectXPath.isBlank()) {
            String siblingTriggerXPath =
                    nativeSelectXPath + "/preceding-sibling::*[@role='combobox' or self::button][1]";
            OpenedSelect opened = clickTrigger(page.locator("xpath=" + siblingTriggerXPath));
            if (opened.opened()) return opened;
        }

        Locator trigger = locate(page, instruction);
        return clickTrigger(trigger);
    }

    private OpenedSelect clickTrigger(Locator locator) {
        try {
            if (locator == null || locator.count() == 0) return OpenedSelect.notOpened();
            Locator trigger = locator.first();
            if (!clickLocator(trigger)) return OpenedSelect.notOpened();
            String panelId = trigger.getAttribute("aria-controls");
            if (panelId == null || panelId.isBlank()) panelId = trigger.getAttribute("aria-owns");
            if (panelId == null || panelId.isBlank()) return new OpenedSelect(true, "");
            String firstPanelId = panelId.trim().split("\\s+")[0];
            return new OpenedSelect(true, "[id=\"" + cssAttribute(firstPanelId) + "\"]");
        } catch (Exception error) {
            log.debug("Playwright select trigger failed: {}", error.getMessage());
            return OpenedSelect.notOpened();
        }
    }

    private record OpenedSelect(boolean opened, String panelCss) {
        private static OpenedSelect notOpened() {
            return new OpenedSelect(false, "");
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
            return fillCoordinates(page, instruction, data == null ? "" : data.getValue());
        }

        Locator first = locator.first();
        try {
            first.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(ACTION_TIMEOUT_MS));
        } catch (Exception ignore) {
            // best-effort
        }
        try {
            first.fill(data == null ? "" : data.getValue(), new Locator.FillOptions().setTimeout(ACTION_TIMEOUT_MS));
            return pressPostInputKeys(page, instruction);
        } catch (Exception fillError) {
            log.debug("Playwright fill failed, trying coordinates: {}", fillError.getMessage());
            return fillCoordinates(page, instruction, data == null ? "" : data.getValue());
        }
    }

    /**
     * Single-shot fill used by TEST_INPUT_DTO. If the selected element is click-only, it delegates
     * to {@link #clickOnce(Page, InstructionLoad)} so radio/checkbox/select scanner tests still run
     * one Playwright action only.
     */
    public boolean fillOnce(Page page, InstructionLoad instruction, FieldData data) {
        if (page == null || page.isClosed() || instruction == null) {
            return false;
        }

        if (isClickOnlyInstruction(instruction)) {
            log.info(
                    "Playwright single-shot fill requested for click-only control '{}'; routing to one click",
                    instruction.getName());
            return clickOnce(page, instruction);
        }

        Locator locator = locateWritable(page, instruction);
        if (locator == null || locator.count() == 0) {
            return runCoordinateFill(page, instruction, data == null ? "" : data.getValue());
        }

        Locator first = locator.first();
        try {
            first.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(ACTION_TIMEOUT_MS));
        } catch (Exception scroll) {
            log.debug("Playwright single-shot scroll failed before fill: {}", scroll.getMessage());
        }
        try {
            first.fill(data == null ? "" : data.getValue(), new Locator.FillOptions().setTimeout(ACTION_TIMEOUT_MS));
            return pressPostInputKeys(page, instruction);
        } catch (Exception fill) {
            log.debug("Playwright single-shot fill failed; no second fill/click will be attempted: {}", fill.getMessage());
            return false;
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
        TextResult result = textResult(page, instruction);
        return result.found() ? result.value() : "";
    }

    /**
     * Reads text without collapsing a legitimate empty string into locator/read failure.
     * Existing callers keep the legacy {@link #text} compatibility behavior; new fail-closed
     * execution paths should use this typed result.
     */
    public TextResult textResult(Page page, InstructionLoad instruction) {
        if (page == null || page.isClosed() || instruction == null) {
            return TextResult.missing();
        }

        Locator locator = locate(page, instruction);
        if (locator == null || locator.count() == 0) {
            return TextResult.missing();
        }
        try {
            String value = locator.first().innerText();
            return value == null ? TextResult.missing() : TextResult.found(value);
        } catch (RuntimeException readFailure) {
            log.debug("Playwright text read failed: {}", readFailure.getMessage());
            return TextResult.missing();
        }
    }

    public record TextResult(boolean found, String value) {
        public TextResult {
            if (found && value == null) {
                throw new IllegalArgumentException("A found Playwright text value cannot be null");
            }
            if (!found) value = null;
        }

        public static TextResult found(String value) {
            return new TextResult(true, value);
        }

        public static TextResult missing() {
            return new TextResult(false, null);
        }
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

    static List<String> selectorsFor(InstructionLoad instruction) {
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

            if (type.contains("xpath")) {
                addXPath(selectors, value);
            } else if (type.contains("css")) {
                addCss(selectors, value);
            } else if (type.startsWith("attrdata:")) {
                String attributeName = type.substring("attrdata:".length());
                if (isUsableDomAttribute(attributeName)) {
                    addCss(selectors, "[" + attributeName + "=\"" + cssAttribute(value) + "\"]");
                }
            } else if (type.equals("test-id")
                    || type.equals("data-testid")
                    || type.equals("data-test-id")
                    || type.equals("data-cy")
                    || type.equals("data-qa")) {
                addCss(selectors, "[" + type + "=\"" + cssAttribute(value) + "\"]");
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

    private static boolean isUsableDomAttribute(String value) {
        if (value == null || !value.matches("[a-z_:][a-z0-9_.:-]{0,127}")) return false;
        return switch (value) {
            case "generated-id", "original-tag", "select-xpath", "option-value", "option-text",
                    "trigger-selector", "text-source", "dom-label", "control.kind", "control.role", "z-index",
                    "clickable" -> false;
            default -> true;
        };
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
        if (List.of("radio", "checkbox", "button", "submit", "reset", "file", "hidden")
                .contains(type)) {
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

    private static boolean runCoordinateClick(Page page, String coordinates) {
        try {
            return clickCoordinates(page, coordinates);
        } catch (Exception error) {
            log.debug("Playwright coordinate click failed: {}", error.getMessage());
            return false;
        }
    }

    private static boolean fillCoordinates(Page page, InstructionLoad instruction, String value) {
        String coordinates = instruction == null ? null : instruction.getCoordinates();
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
        return pressPostInputKeys(page, instruction);
    }

    private static boolean runCoordinateFill(Page page, InstructionLoad instruction, String value) {
        try {
            return fillCoordinates(page, instruction, value);
        } catch (Exception error) {
            log.debug("Playwright coordinate fill failed: {}", error.getMessage());
            return false;
        }
    }

    private static boolean pressPostInputKeys(Page page, InstructionLoad instruction) {
        InputFlags flags = InputFlags.of(instruction == null ? null : instruction.getForceCoordinates());
        try {
            if (flags.hasEnter()) {
                page.keyboard().press("Enter");
            }
            if (flags.hasTab()) {
                page.keyboard().press("Tab");
            }
            return true;
        } catch (Exception error) {
            log.debug("Playwright post-input key press failed: {}", error.getMessage());
            return false;
        }
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

    private static String normalizeOptionText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    @SuppressWarnings("unused")
    private static BoundingBox touchBoundingBox(BoundingBox box) {
        return box;
    }
}
