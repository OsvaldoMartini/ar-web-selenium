package com.allinweb.ch.component;

import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.util.ARConstants;
import com.google.common.base.Strings;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetElementHelper {

    private static volatile TargetElementHelper instance;
    private static final Logger log = LoggerFactory.getLogger(TargetElementHelper.class);

    private PerformActions performActions;
    private PerformMessage performMessage;
    private ARScannedElementPane arScannedElementPane;

    private TargetElementHelper() {
        // private constructor to enforce singleton
    }

    public static TargetElementHelper getInstance() {
        if (instance == null) {
            synchronized (TargetElementHelper.class) {
                if (instance == null) {
                    instance = new TargetElementHelper();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the helper with the necessary dependencies.
     */
    public void initialize(
            PerformActions performActions, PerformMessage performMessage, ARScannedElementPane arScannedElementPane) {
        this.performActions = performActions;
        this.performMessage = performMessage;
        this.arScannedElementPane = arScannedElementPane;
    }

    /**
     * Extracts and defines a cloned TargetElement from the given ElementDTO.
     */
    public TargetElement extractPickClone(ElementDTO elementDTO) {

        if (performActions == null || performMessage == null || arScannedElementPane != null) {
            throw new IllegalStateException("TargetElementHelper not initialized. Call initialize() first.");
        }

        arScannedElementPane.xpathTextPrevious = elementDTO.getXPath();

        TargetElement targetLocal = performActions.defineSearchReturn(elementDTO, null);

        WebElement elementFound = performActions.findWebElement(targetLocal);
        if (targetLocal.getElement() == null && elementFound != null) {
            targetLocal.setElement(elementFound);
        }

        // Save references for different coordinate strategies
        // 3 Different Coordinates // Original from JavaScript  // WebDriver Selenium ElementFound
        // FallBack React Computed
        // TO DO:   KEEP THE ORIGINALS  FROM ANDROID
        performActions.defineSavedReferenced(targetLocal);

        // Define tag name/title
        targetLocal = performActions.defineNameTitles(targetLocal);

        // Validate Shadow DOM or regular CSS selectors
        if (Strings.isNullOrEmpty(targetLocal.getShadowHost()) && Strings.isNullOrEmpty(targetLocal.getCssSelector())) {

            TargetElement targetValidated = checkValidateSearchPriorities(targetLocal);

            if (targetValidated.getElement() == null) {
                log.error("Cannot define this element. Try to get it again via 'Hover Pick Element' or 'Pick One'.");
                performMessage.errorMessage(
                        "I Cannot define this element",
                        "I will use the Locator 'COORDINATES'",
                        "Try again using 'HOVER PICK ELEMENT' or 'PICK ONE'",
                        null,
                        null,
                        0);
                return null;
            }

        } else if (!Strings.isNullOrEmpty(targetLocal.getCssSelector())) {
            targetLocal.setXPathWorkedFirst(ARConstants.REGULAR_XPATH);
        } else {
            targetLocal.setXPathWorkedFirst(ARConstants.SHADOW_DOM);
        }

        // Update UI checkboxes and return final target
        arScannedElementPane.defineCheckBoxesClickable(targetLocal);

        return targetLocal;
    }

    public TargetElement checkValidateSearchPriorities(TargetElement target) {
        WebElement elementValid = null;
        if (!Strings.isNullOrEmpty(target.getCurrentXPath())) {

            if (target.getForceCoordinates() != null && target.getForceCoordinates()) {
                // Try by coordinates
                try {
                    FieldData filedData = new FieldData("&EMPTY", "&EMPTY");
                    boolean passed = performActions.executeActionsAtCoordinates(
                            target.getCoordinates(), filedData, ARConstants.VISUALIZE, false);
                    if (passed) {
                        elementValid = performActions.getElementFromCoordinates(target.getCoordinates());
                        if (elementValid != null && elementValid.getTagName() != null) {
                            target.setElement(elementValid);
                        }

                        target.setXPathWorkedFirst(ARConstants.SEARCH_COORD);
                    }

                } catch (Exception e) {

                    log.warn(String.format("Cannot locate a Web Element with Name: %s", target.getAttribName()));
                }
            } else if (elementValid == null) {
                try {
                    elementValid = performActions.getCurrentDriver().findElement(By.xpath(target.getCurrentXPath()));
                    if (elementValid != null && elementValid.getTagName() != null) {
                        target.setElement(elementValid);
                        target.setXPathWorkedFirst(
                                ARConstants.REGULAR_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {

                    log.warn(String.format(
                            "Cannot locate a Web Element with Regular XPath: %s", target.getCurrentXPath()));
                }
            } else if (elementValid == null) {
                try {
                    elementValid = performActions.getCurrentDriver().findElement(By.xpath(target.getCustomXPath()));
                    if (elementValid != null && elementValid.getTagName() != null) {
                        target.setElement(elementValid);
                        target.setXPathWorkedFirst(
                                ARConstants.CUSTOM_XPATH); // BECAUSE OS LIMITATION OF ACCESS DB 255 CHARACTER
                    }
                } catch (Exception e) {

                    log.warn(String.format(
                            "Cannot locate a Web Element with Absolut XPath: %s", target.getAttributeData()));
                }
            } else {
                if (elementValid == null) {
                    //            if (searchReturn.getCurrentXPath().startsWith("id(")) {
                    if (!Strings.isNullOrEmpty(target.getAttribId())) {
                        try {
                            elementValid = performActions.getCurrentDriver().findElement(By.id(target.getAttribId()));
                            if (elementValid != null && elementValid.getTagName() != null) {
                                target.setElement(elementValid);
                                target.setXPathWorkedFirst(ARConstants.ATTRIBUTE_ID);
                                target.setAttributeType("id");
                                target.setAttributeValue(target.getAttribId());
                            }
                        } catch (Exception e) {

                            log.warn(String.format("Cannot locate a Web Element with ID: %s", target.getAttribId()));
                        }
                    }
                } else if (elementValid == null) {

                    if (!Strings.isNullOrEmpty(target.getAttribName())) {
                        try {
                            elementValid =
                                    performActions.getCurrentDriver().findElement(By.name(target.getAttribName()));
                            if (elementValid != null && elementValid.getTagName() != null) {
                                target.setElement(elementValid);
                                target.setAttributeType("name");
                                target.setXPathWorkedFirst(ARConstants.ATTRIBUTE_NAME);
                            }
                        } catch (Exception e) {

                            log.warn(
                                    String.format("Cannot locate a Web Element with Name: %s", target.getAttribName()));
                        }
                    }
                }
            }
        }

        target.setElement(elementValid);

        return target;
    }
}
