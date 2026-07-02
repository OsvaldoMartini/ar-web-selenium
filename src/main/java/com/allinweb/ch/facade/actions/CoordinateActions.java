package com.allinweb.ch.facade.actions;

import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.util.ARConstantsEngine;
import java.time.Duration;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Coordinate-based fallback actions (cluster D): scroll/click/type/clear at pixel coordinates,
 * used when an element cannot be interacted with through its locator. The driver is always
 * re-read from the context at call time — never cached. Bodies moved verbatim from
 * PerformActions.
 */
public class CoordinateActions {

    private final ActionContext ctx;

    public CoordinateActions(ActionContext ctx) {
        this.ctx = ctx;
    }

    public boolean executeActionsAtCoordinates(
            String savedCoordinates, FieldData data, String action, boolean pressEnterAfter) {

        boolean forceCLick = false;

        int x = 0;
        int y = 0;
        int xCoord = 0;
        int yCoord = 0;
        try {
            String[] coordinates = savedCoordinates.split(ARConstantsEngine.FIELDS_SEPARATOR);
            double temp1 = Double.parseDouble(coordinates[0]);
            double temp2 = Double.parseDouble(coordinates[1]);
            x = (int) temp1;
            y = (int) temp2;
            int maxHeight = ctx.driver().manage().window().getSize().getHeight();
            int maxWidth = ctx.driver().manage().window().getSize().getWidth();
            int offsetY = y - maxHeight;
            int offsetX = x - maxWidth;
            xCoord = x > maxWidth ? x - offsetX : x;
            yCoord = y > maxHeight ? y - offsetY : y;

            if (ARConstantsEngine.VISUALIZE.equals(action)) {
                scrollToCoordinates(x, y);
            } else if (ARConstantsEngine.CLICK.equals(action)) {
                scrollToCoordinates(x, y);
                //                circleAtCoordinates(x, y, this.currentDriver);
                ctx.holdForSeconds(null);
                clickAtCoordinates(xCoord, yCoord);
            } else if (ARConstantsEngine.INSERT.equals(action)) {
                scrollToCoordinates(x, y);
                //                sendInputJS(x, y, data.getValue(),this.currentDriver);
                //                circleAtCoordinates(x, y, this.currentDriver);
                ctx.holdForSeconds(null);
                //                clickAtCoordinates(xCoord, yCoord);
                //                onHoldForSeconds(null);
                typeCharacters(savedCoordinates, data);
                if (pressEnterAfter) {
                    boolean respAction = sendActionEnter(xCoord, yCoord);
                    if (!respAction) {
                        sendEnterWithJS();
                    }
                }
            } else if (ARConstantsEngine.INSERT.equals(action) && forceCLick) {
                scrollToCoordinates(x, y);
                //                sendInputJS(x, y, data.getValue(),this.currentDriver);
                //                circleAtCoordinates(x, y, this.currentDriver);
                ctx.holdForSeconds(null);
                clickAtCoordinates(xCoord, yCoord);
                ctx.holdForSeconds(null);
                typeCharacters(savedCoordinates, data);

                if (pressEnterAfter) {
                    boolean respAction = sendActionEnter(xCoord, yCoord);
                    if (!respAction) {
                        sendEnterWithJS();
                    }
                }
            }
            ctx.holdForSeconds(null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void scrollToCoordinates(int x, int y) {
        int maxHeight = ctx.driver().manage().window().getSize().getHeight();
        int maxWidth = ctx.driver().manage().window().getSize().getWidth();
        int offsetY = y - maxHeight;
        int offsetX = x - maxWidth;
        if (offsetX > 0 || offsetY > 0) {
            String script = "function getScrollableParent(element){\n" + "    console.log(\"finding\");"
                    + "    let value = window.getComputedStyle(element).overflowY;\n"
                    + "    if(value !== \"scroll\" && value !== \"auto\"){\n"
                    + "        return getScrollableParent(element.parentNode);\n"
                    + "    }\n"
                    + "    return element;\n"
                    + "}\n"
                    + "getScrollableParent(document.elementFromPoint("
                    + (maxWidth / 2) + "," + (maxHeight / 2)
                    + ")).scrollTo(" + Math.max(offsetX, 0) + "," + Math.max(offsetY, 0) + ");" + "return true;";
            new WebDriverWait(ctx.driver(), Duration.ofSeconds(5))
                    .until((item) -> (Boolean) ((JavascriptExecutor) ctx.driver()).executeScript(script));
        }
    }

    private void clickAtCoordinates(int x, int y) {
        new Actions(ctx.driver()).moveToLocation(x, y).click().perform();
    }

    public WebElement getElementFromCoordinates(String savedCoordinates) {
        int x = 0;
        int y = 0;
        int xCoord = 0;
        int yCoord = 0;
        try {
            String[] coordinates = savedCoordinates.split(ARConstantsEngine.FIELDS_SEPARATOR);
            double temp1 = Double.parseDouble(coordinates[0]);
            double temp2 = Double.parseDouble(coordinates[1]);
            x = (int) temp1;
            y = (int) temp2;
            int maxHeight = ctx.driver().manage().window().getSize().getHeight();
            int maxWidth = ctx.driver().manage().window().getSize().getWidth();
            int offsetY = y - maxHeight;
            int offsetX = x - maxWidth;
            xCoord = x > maxWidth ? x - offsetX : x;
            yCoord = y > maxHeight ? y - offsetY : y;

            JavascriptExecutor js = (JavascriptExecutor) ctx.driver();

            WebElement elementFound = (WebElement)
                    js.executeScript("return document.elementFromPoint(arguments[0], arguments[1]);", xCoord, yCoord);

            return elementFound;

        } catch (Exception e) {
            return null;
        }
    }

    private void circleAtCoordinates(int x, int y, WebDriver driver) {
        String script = "function createCircle(x, y, diameter) {\n"
                + "    const randomColor = Math.floor(Math.random()*16777215).toString(16);\n"
                + "\n"
                + "    return `\n"
                + "    <svg style='height:100%;width:100%;position:absolute;top:0;z-index:9999'><circle\n"
                + "        cx=\"${x}\"\n"
                + "      cy=\"${y}\"\n"
                + "      r=\"${diameter/2}\"\n"
                + "      fill=\"#${randomColor}\"\n"
                + "    ></circle></svg>\n"
                + "  `;\n"
                + "}\n"
                + "\n"
                + "function pri(ev){\n"
                + "    console.log(ev);\n"
                + "    document.body.innerHTML += createCircle(ev.pageX,ev.pageY,10);\n"
                + "}\n"
                + "\n"
                + "window.addEventListener(\"click\", pri);";
        ((JavascriptExecutor) driver).executeScript(script);
    }

    private void typeCharacters(String savedCoords, FieldData fieldData) {
        clearValueAtCoordinates(savedCoords);
        boolean passed = setValueAtCoordinates(savedCoords, fieldData.getValue().trim());
        if (!passed) {
            new Actions(ctx.driver()).sendKeys(fieldData.getValue().trim()).perform();
        }
    }

    private boolean sendActionEnter(int x, int y) {
        try {
            new Actions(ctx.driver()).moveByOffset(x, y).sendKeys(Keys.ENTER).perform();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean sendEnterWithJS() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) ctx.driver();

            String script =
                    """
                                var evt = new KeyboardEvent('keydown', {
                                    key: 'Enter',
                                    code: 'Enter',
                                    keyCode: 13,
                                    which: 13,
                                    bubbles: true,
                                    cancelable: true
                                });
                                document.activeElement.dispatchEvent(evt);
                            """;

            js.executeScript(script);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setValueAtCoordinates(String savedCoords, String textToSet) {

        try {
            String[] coordinates = savedCoords.split(ARConstantsEngine.FIELDS_SEPARATOR);
            double temp1 = Double.parseDouble(coordinates[0]);
            double temp2 = Double.parseDouble(coordinates[1]);

            JavascriptExecutor jsExecutor = (JavascriptExecutor) ctx.driver();

            String script = "const temp1 = Number(arguments[0]);\n" + "const temp2 = Number(arguments[1]);\n"
                    + "console.log('temp1', temp1);\n"
                    + "console.log('temp2', temp2);\n"
                    + "const elementAtPoint = document.elementFromPoint(temp1, temp2);\n"
                    + "if (elementAtPoint && (elementAtPoint.tagName === 'INPUT' || elementAtPoint.tagName === 'TEXTAREA')) {\n"
                    + "\telementAtPoint.value = \"" + textToSet + "\";\n"
                    + "} else if (elementAtPoint && elementAtPoint.isContentEditable) {\n"
                    + "\telementAtPoint.textContent = arguments[2];\n"
                    + "} else {\n"
                    + "\tconsole.log(\"No suitable element (input, textarea, or contenteditable) found at coordinates (\" + arguments[0] + \", \" + arguments[1] + \")\");\n"
                    + "}";

            jsExecutor.executeScript(script, temp1, temp2, textToSet);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    public boolean clearValueAtCoordinates(String savedCoords) {

        try {
            String[] coordinates = savedCoords.split(ARConstantsEngine.FIELDS_SEPARATOR);
            double temp1 = Double.parseDouble(coordinates[0]);
            double temp2 = Double.parseDouble(coordinates[1]);
            JavascriptExecutor jsExecutor = (JavascriptExecutor) ctx.driver();

            String script =
                    """
                                function getElementAtCoordinates(x, y) {
                                  return document.elementFromPoint(x, y);
                                }

                                const elementAtPoint = getElementAtCoordinates(arguments[0], arguments[1]);

                                if (elementAtPoint && (elementAtPoint.tagName === 'INPUT' || elementAtPoint.tagName === 'TEXTAREA')) {
                                  elementAtPoint.value = '';
                                } else if (elementAtPoint && elementAtPoint.isContentEditable) {
                                  elementAtPoint.textContent = '';
                                } else {
                                  console.log("No suitable element (input, textarea, or contenteditable) found at coordinates (" + arguments[0] + ", " + arguments[1] + ")");
                                }
                            """;

            jsExecutor.executeScript(script, temp1, temp2);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    public boolean clickElementAtCoordinates(String savedCoords) {
        try {
            String[] coordinates = savedCoords.split(ARConstantsEngine.FIELDS_SEPARATOR);
            double temp1 = Double.parseDouble(coordinates[0]);
            double temp2 = Double.parseDouble(coordinates[1]);
            JavascriptExecutor jsExecutor = (JavascriptExecutor) ctx.driver();
            String script =
                    """
                                function getElementAtCoordinates(x, y) {
                                  return document.elementFromPoint(x, y);
                                }

                                const elementAtPoint = getElementAtCoordinates(arguments[0], arguments[1]);

                                if (elementAtPoint) {
                                  elementAtPoint.click();
                                } else {
                                  console.log("No element found at coordinates (" + arguments[0] + ", " + arguments[1] + ")");
                                }
                            """;

            jsExecutor.executeScript(script, temp1, temp2);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    public void sendInputJS(int x, int y, String text, WebDriver driver) {
        String script = "function sendTextToElementAtCoordinates(x, y, text) {\n"
                + "    const element = document.elementFromPoint(x, y);\n"
                + "    if (element) {\n"
                + "        console.log('Found element:', element);\n"
                + "        element.click();\n"
                + "        if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {\n"
                + "            element.focus();\n"
                + "            element.value = text;\n"
                + "            const event = new Event('input', { bubbles: true });\n"
                + "            element.dispatchEvent(event);\n"
                + "        } else {\n"
                + "            console.warn('Element is not an input or textarea.');\n"
                + "        }\n"
                + "    } else {\n"
                + "        console.warn('No element found at the given coordinates.');\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "sendTextToElementAtCoordinates(arguments[0], arguments[1], arguments[2]);";

        // Execute the JavaScript with the provided x, y, and text arguments
        ((JavascriptExecutor) driver).executeScript(script, x, y, text);
    }

    public String moveAndClickAtCoordinates(String savedCoordinates, boolean pressEnterAfter) {
        String[] coordinates = savedCoordinates.split(ARConstantsEngine.FIELDS_SEPARATOR);
        double temp1 = Double.parseDouble(coordinates[0]);
        double temp2 = Double.parseDouble(coordinates[1]);
        int xCoord = (int) temp1;
        int yCoord = (int) temp2;
        try {
            String script =
                    "function moveAndClickMouse(x, y) {\n" + "    const mouseDiv = document.createElement('div');\n"
                            + "    mouseDiv.style.position = 'absolute';\n"
                            + "    mouseDiv.style.width = '10px';\n"
                            + "    mouseDiv.style.height = '10px';\n"
                            + "    mouseDiv.style.backgroundColor = 'red';\n"
                            + "    mouseDiv.style.borderRadius = '50%';\n"
                            + "    mouseDiv.style.zIndex = '10000';\n"
                            + "    mouseDiv.style.pointerEvents = 'none';\n"
                            + "    mouseDiv.id = 'virtualMouse';\n"
                            + "    document.body.appendChild(mouseDiv);\n"
                            + "\n"
                            + "    function blinkMouse() {\n"
                            + "        const mouse = document.getElementById('virtualMouse');\n"
                            + "        if (mouse) {\n"
                            + "            mouse.style.visibility = mouse.style.visibility === 'hidden' ? 'visible' : 'hidden';\n"
                            + "        }\n"
                            + "    }\n"
                            + "\n"
                            + "    const blinkInterval = setInterval(blinkMouse, 500);\n"
                            + "\n"
                            + "    mouseDiv.style.left = `${x}px`;\n"
                            + "    mouseDiv.style.top = `${y}px`;\n"
                            + "\n"
                            + "    const element = document.elementFromPoint(x, y);\n"
                            + "    if (element) {\n"
                            + "        element.click();\n"
                            + "    }\n"
                            + "\n"
                            + "    setTimeout(() => {\n"
                            + "        clearInterval(blinkInterval);\n"
                            + "        const mouse = document.getElementById('virtualMouse');\n"
                            + "        if (mouse) {\n"
                            + "            mouse.remove();\n"
                            + "        }\n"
                            + "    }, 3000);\n"
                            + "}\n"
                            + "\n"
                            + "moveAndClickMouse(arguments[0], arguments[1]);";

            ((JavascriptExecutor) ctx.driver()).executeScript(script, xCoord, yCoord);

            if (pressEnterAfter) {
                boolean respAction = sendActionEnter(xCoord, yCoord);
                if (!respAction) {
                    sendEnterWithJS();
                }
            }

            return "Success Move And Click -> Red Circle";

        } catch (Exception error) {
            return "Failed Move And Click -> Red Circle";
        }
    }
}
