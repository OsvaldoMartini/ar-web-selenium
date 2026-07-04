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

    public boolean fill(Page page, InstructionLoad instruction, FieldData data) {
        if (page == null || page.isClosed() || instruction == null) {
            return false;
        }

        Locator locator = locate(page, instruction);
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
