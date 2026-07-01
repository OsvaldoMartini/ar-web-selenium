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

    public boolean click(Page page, InstructionLoad instruction) {
        if (page == null || page.isClosed() || instruction == null) {
            return false;
        }

        Locator locator = locate(page, instruction);
        if (locator != null && locator.count() > 0) {
            locator.first().scrollIntoViewIfNeeded();
            locator.first().click();
            return true;
        }

        return clickCoordinates(page, instruction.getCoordinates());
    }

    public boolean fill(Page page, InstructionLoad instruction, FieldData data) {
        if (page == null || page.isClosed() || instruction == null) {
            return false;
        }

        Locator locator = locate(page, instruction);
        if (locator == null || locator.count() == 0) {
            return fillCoordinates(page, instruction.getCoordinates(), data == null ? "" : data.getValue());
        }

        Locator first = locator.first();
        first.scrollIntoViewIfNeeded();
        first.fill(data == null ? "" : data.getValue());
        return true;
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

        for (ReferenceLoadDTO ref : references) {
            if (ref == null
                    || ref.getReferenceType() == null
                    || ref.getValue() == null
                    || ref.getValue().isBlank()) {
                continue;
            }

            String type = ref.getReferenceType().toLowerCase(Locale.ROOT);
            String value = ref.getValue();
            if (type.contains("xpath")) {
                addXPath(selectors, value);
            } else if (type.contains("css")) {
                addCss(selectors, value);
            } else if (type.contains("id")) {
                addCss(selectors, "#" + cssEscape(value.replaceFirst("^#", "")));
            } else if (type.contains("name")) {
                addCss(selectors, "[name=\"" + cssAttribute(value) + "\"]");
            } else if (type.contains("test-id") || type.contains("data-testid")) {
                String escaped = cssAttribute(value);
                addCss(selectors, "[test-id=\"" + escaped + "\"], [data-testid=\"" + escaped + "\"]");
            }
        }
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

    @SuppressWarnings("unused")
    private static BoundingBox touchBoundingBox(BoundingBox box) {
        return box;
    }
}
