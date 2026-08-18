package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.CryptationAlgorithm;
import com.allinweb.ch.util.InputFlags;
import com.allinweb.ch.util.UtilsMethods;
import com.google.common.base.Strings;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Element interaction (clusters B + L): click/insert/select with the post-input key cascade
 * (NEXT → TAB → strong ENTER) and the multi-step command sequencer. Coordinate fallbacks are
 * delegated to {@link CoordinateActions}. The driver is always re-read from the context at
 * call time — never cached. Bodies moved verbatim from PerformActions.
 */
public class ElementInteraction {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private final ActionContext ctx;
    private final CoordinateActions coordinateActions;

    public ElementInteraction(ActionContext ctx, CoordinateActions coordinateActions) {
        this.ctx = ctx;
        this.coordinateActions = coordinateActions;
    }

    public boolean scrollToElement(boolean byPassNotFound, WebElement element) throws Exception {
        try {
            UtilsMethods.exceptionIfNullWebElement(element);
            ((JavascriptExecutor) ctx.driver()).executeScript("arguments[0].scrollIntoView(true);", element);
            return true;
        } catch (Exception e) {

            logOperations.error(String.format(
                    "Failed to Scroll to Element \"%s\" -> Cause: %s", element.getTagName(), e.getMessage()));
            if (!byPassNotFound) {
                PerformMessage.getInstance().couldNotFindElement("Failed to Scroll to Element " + element.getTagName());
            }
            return false;
        }
    }

    public boolean clickElement(boolean byPassNotFound, WebElement element) throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);

        try {
            // A quick check
            if (element != null && (!element.isEnabled() || !element.isDisplayed())) {
                logOperations.error(
                        "Step Failed - Web Field is not Visible. Verify the rules and behavior of your web page.");
                return false;
            }
            ctx.actionWait().until(ExpectedConditions.visibilityOf(element).andThen(e -> {
                ((JavascriptExecutor) ctx.driver()).executeScript("arguments[0].scrollIntoView(true);", element);
                return ctx.actionWait().until(ExpectedConditions.elementToBeClickable(element));
            }));
        } catch (Exception e) {

            logOperations.error(
                    "Step Failed - Web Field is not Visible. Verify the rules and behavior of your web page.");

            if (!byPassNotFound) {
                PerformMessage.getInstance().couldNotFindElement(element.getTagName());
            }
            return false;
        }

        // Custom visibility and enabled checks
        if (!element.isDisplayed()) {
            logOperations.error(
                    "Step Failed - Web Field is not Visible. Verify the rules and behavior of your web page.");
            return false;
        }

        if (!element.isEnabled()) {
            logOperations.error(
                    "Step Failed - Web Field is not Visible. Verify the rules and behavior of your web page.");
            return false;
        }

        String pointerEvents = element.getCssValue("pointer-events");
        if ("none".equals(pointerEvents)) {
            logOperations.error(
                    "Step Failed - Web Field is not Visible. Verify the rules and behavior of your web page.");

            return false;
        }

        try {
            element.click();
            return true;
        } catch (ElementClickInterceptedException e) {
            try {
                JavascriptExecutor jse = (JavascriptExecutor) ctx.driver();
                jse.executeScript("arguments[0].click()", element);
                return true;
            } catch (Exception ex) {

                logOperations.error(
                        "Step Failed - Web Field is not Visible. Verify the rules and behavior of your web page.");
                return false;
            }
        }
    }

    public boolean insertInElement(
            boolean byPassNotFound,
            WebElement element,
            String dataFieldValue,
            String defaultValue,
            boolean isEncrypted,
            InputFlags flags)
            throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);

        try {
            ctx.actionWait().until(ExpectedConditions.visibilityOf(element));
        } catch (Exception e) {
            if (ctx.actionWait() == null) {
                logOperations.warn("WaitForAction is null");
            }

            logOperations.warn(
                    String.format("Could Not Find TagName \"%s\" -> Cause: %s", element.getTagName(), e.getMessage()));
            if (!byPassNotFound) {
                PerformMessage.getInstance().couldNotFindElement(element.getTagName());
            }
            return false;
        }

        try {

            if (Strings.isNullOrEmpty(defaultValue)) {

                if (isEncrypted) {
                    dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
                }

                if (dataFieldValue != null) {
                    // Pause briefly to let JS clearing take effect
                    Thread.sleep(100); // Consider using WebDriverWait for stability
                    // Clear using sendKeys with BACK_SPACE (optional but defensive)
                    element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
                    element.sendKeys(Keys.BACK_SPACE);
                    // Pause again if needed (some inputs behave asynchronously)
                    Thread.sleep(100);

                    element.sendKeys(dataFieldValue);
                    // Waits component reaction
                    Thread.sleep(100);
                    pressAfter(element, flags);
                } else {
                    element.sendKeys(UtilsMethods.generateRandomID(10));
                    // Waits component reaction
                    Thread.sleep(100);
                    pressAfter(element, flags);
                }
            } else {
                dataFieldValue = defaultValue;

                if (isEncrypted) {
                    dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
                }
                element.sendKeys(dataFieldValue);
                // Waits component reaction
                Thread.sleep(100);
                pressAfter(element, flags);
            }
        } catch (Exception e) {

            logOperations.error(String.format(
                    "Could Not Input Value to \"%s\" -> Cause: %s", element.getTagName(), e.getMessage()));

            return false;
        }

        return true;
    }

    // ── Post-input key dispatch ──────────────────────────────────────────────

    /**
     * Fire the post-input keys for the given flag set.
     * <ul>
     *   <li>N solo (no E, no T)        → cascade N → T → E with failure fallback
     *   <li>Any explicit combination   → fire each key in order N, E, T, NO cascade
     *   <li>E alone                    → pressEnterStrong
     *   <li>T alone                    → Keys.TAB
     *   <li>nothing                    → default to TAB (legacy behaviour)
     * </ul>
     */
    private void pressAfter(WebElement element, InputFlags flags) {
        if (flags == null) flags = InputFlags.of(0);
        if (flags.isNextSolo()) {
            pressNextWithFallback(element);
            return;
        }
        boolean anyExplicit = flags.hasNext() || flags.hasEnter() || flags.hasTab();
        if (!anyExplicit) {
            // Legacy default: TAB to move focus and commit the field.
            try {
                // element.sendKeys(Keys.TAB);
            } catch (Exception ignored) {
            }
            return;
        }
        if (flags.hasNext()) tryPressNext(element); // explicit combo: no cascade
        if (flags.hasEnter()) pressEnterStrong(element);
        if (flags.hasTab()) {
            try {
                element.sendKeys(Keys.TAB);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Stronger ENTER than bare {@code sendKeys(Keys.ENTER)}.
     *   1) Native sendKeys — best-effort WebDriver keyboard input.
     *   2) JS-dispatched KeyboardEvent (keydown + keypress + keyup) — fires the exact
     *      sequence framework handlers (Angular, React) listen for even when the
     *      WebDriver input stack is intercepted by custom onkeydown handlers.
     *   3) {@code form.requestSubmit()} if the element is inside a &lt;form&gt;.
     */
    private void pressEnterStrong(WebElement element) {
        try {
            element.sendKeys(Keys.ENTER);
        } catch (Exception ignored) {
        }
        try {
            ((JavascriptExecutor) ctx.driver())
                    .executeScript(
                            "var el = arguments[0];"
                                    + "var opts = {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true};"
                                    + "el.dispatchEvent(new KeyboardEvent('keydown', opts));"
                                    + "el.dispatchEvent(new KeyboardEvent('keypress', opts));"
                                    + "el.dispatchEvent(new KeyboardEvent('keyup', opts));"
                                    + "try { if (el.form && el.form.requestSubmit) el.form.requestSubmit(); } catch(_) {}",
                            element);
        } catch (Exception e) {
            logOperations.debug("pressEnterStrong JS dispatch failed: {}", e.getMessage());
        }
    }

    /**
     * N-solo cascade: try NEXT, fall back to TAB, finally pressEnterStrong.
     * Fallback triggers on exception OR unchanged focus after the attempt.
     */
    private void pressNextWithFallback(WebElement element) {
        WebElement before = safeActiveElement();
        if (tryPressNext(element) && focusMoved(before)) return;
        if (tryPressTab(element) && focusMoved(before)) return;
        pressEnterStrong(element);
    }

    /**
     * Attempt the platform "Next" action.
     *   • Appium mobile drivers use the on-screen IME "Next" button (accessibility id "Next")
     *     when available; otherwise fall back to a TAB key event.
     *   • Desktop Selenium falls back to a JS focus shift to the next form control.
     * Returns true if the attempt ran without throwing.
     */
    private boolean tryPressNext(WebElement element) {
        try {
            String driverClass =
                    ctx.driver() == null ? "" : ctx.driver().getClass().getSimpleName();
            if (driverClass.contains("Android") || driverClass.contains("IOS") || driverClass.contains("Appium")) {
                try {
                    // Try tapping an on-screen "Next" button (iOS/Android soft keyboards commonly expose this).
                    WebElement nextBtn = ctx.driver()
                            .findElement(org.openqa.selenium.By.xpath(
                                    "//*[@name='Next' or @content-desc='Next' or @accessibility-id='Next']"));
                    nextBtn.click();
                    return true;
                } catch (Exception ignored) {
                    // Fall through to TAB as the platform key proxy.
                    element.sendKeys(Keys.TAB);
                    return true;
                }
            }
            // Desktop: move focus to the next form element via JS.
            ((JavascriptExecutor) ctx.driver())
                    .executeScript(
                            "var el = arguments[0], f = el.form;"
                                    + "if (f) { var els = Array.from(f.elements), i = els.indexOf(el);"
                                    + "  for (var k = i + 1; k < els.length; k++) {"
                                    + "    var n = els[k]; if (n && !n.disabled && n.offsetParent !== null) { n.focus(); return; }"
                                    + "  }"
                                    + "}",
                            element);
            return true;
        } catch (Exception e) {
            logOperations.debug("tryPressNext failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryPressTab(WebElement element) {
        try {
            element.sendKeys(Keys.TAB);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private WebElement safeActiveElement() {
        try {
            return ctx.driver().switchTo().activeElement();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean focusMoved(WebElement before) {
        WebElement after = safeActiveElement();
        if (before == null || after == null) return false;
        try {
            return !before.equals(after);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean insertDataInSelectElement(
            boolean byPassNotFound, WebElement element, String coordinates, FieldData data, boolean pressEnterAfter)
            throws Exception {
        UtilsMethods.exceptionIfNullWebElement(element);
        try {
            ctx.actionWait().until(ExpectedConditions.visibilityOf(element));
        } catch (Exception e) {

            logOperations.warn(String.format(
                    "Could Not Find Select \"%s\" Value  \"%s\" -> Cause: %s",
                    data.getKey(), data.getValue(), e.getMessage()));
            if (!byPassNotFound) {
                PerformMessage.getInstance().couldNotFindElement(data.getKey());
            }
        }

        try {
            String[] coordArray = new String[] {coordinates, "coordinates"};
            sequenceOfCommands(element, ARConstantsEngine.SELECT, coordArray, data, ctx.driver(), pressEnterAfter);

        } catch (Exception e) {

            logOperations.error(String.format(
                    "Could Not Input Value to \"%s\" -> Cause: %s", element.getTagName(), e.getMessage()));

            PerformMessage.getInstance().couldNotFindElement("Could Input Values to Element " + element.getTagName());

            return false;
        }
        return true;
    }

    public String sequenceOfCommands(
            WebElement element,
            String typeCommand,
            String[] coordinates,
            FieldData fieldData,
            WebDriver driver,
            boolean pressEnterAfter) {

        String message = "Nothing to execute";
        try {
            if (typeCommand.equals(ARConstantsEngine.SELECT)) {
                // Create a Select instance to interact with the dropdown
                message = "Select(element)";
                Select selectCountry = new Select(element);
                selectCountry.selectByVisibleText(fieldData.getValue());
            } else if (typeCommand.equals(ARConstantsEngine.CLEAR)) {
                message = "clear()";
                element.clear();
                for (String coords : coordinates) {
                    coordinateActions.clearValueAtCoordinates(coords);
                }

            } else if (typeCommand.equals(ARConstantsEngine.CLICK)) {
                message = "click()";
                element.click();
            } else if (typeCommand.equals(ARConstantsEngine.INSERT)) {
                message = "sendKeys(\"" + fieldData.getValue() + "\")";
                element.sendKeys(fieldData.getValue());
            } else if (typeCommand.equals(ARConstantsEngine.TAB)) {
                message = "(Keys.TAB)";
                element.sendKeys(Keys.TAB);
            } else if (typeCommand.equals(ARConstantsEngine.GET_VALUE)) {
                message = "getText()";
                element.getText();
            } else if (typeCommand.equals(ARConstantsEngine.FOCUS)) {
                message = "focusElement(element, driver)";
                focusElement(element, driver);
            } else if (typeCommand.equals(ARConstantsEngine.COORD_VISUALIZA)) {
                message = "Coordinates Visualiza";
                for (String coords : coordinates) {
                    coordinateActions.executeActionsAtCoordinates(
                            coords, fieldData, ARConstantsEngine.VISUALIZE, pressEnterAfter);
                }
            } else if (typeCommand.equals(ARConstantsEngine.COORD_CLICK)) {
                message = "Coordinates Click";
                for (String coords : coordinates) {
                    coordinateActions.clickElementAtCoordinates(coords);
                }
            } else if (typeCommand.equals(ARConstantsEngine.COORD_INSERT)) {
                message = "Coordinates Insert";
                if (pressEnterAfter) {
                    message = "Coordinates Insert with <ENTER>";
                }
                for (String coords : coordinates) {
                    coordinateActions.setValueAtCoordinates(coords, fieldData.getValue());
                }
            } else if (typeCommand.equals(ARConstantsEngine.COORD_MOVE_CLICK_RED)) {
                message = "Coordinates Move Insert Red Circle";
                for (String coords : coordinates) {
                    coordinateActions.moveAndClickAtCoordinates(coords, pressEnterAfter);
                }
            }
            return "Success " + message;
        } catch (Exception ex) {
            return "Failed Attempt " + message;
        }
    }

    private void focusElement(WebElement element, WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].focus();", element);

        Actions actions = new Actions(driver);
        actions.moveToElement(element).perform();
    }

    public void clearElement(WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) ctx.driver();
        js.executeScript("arguments[0].value='';", element);

        Actions actions = new Actions(ctx.driver());
        actions.moveToElement(element).perform();
    }
}
