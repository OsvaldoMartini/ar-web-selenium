package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.*;
import com.allinweb.ch.util.*;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Field-data extraction (cluster J): resolves INSERT field values from Excel-extracted data
 * and reads OUTPUT text from elements through a multi-strategy ladder. Bodies moved verbatim
 * from PerformActions.
 */
public class DataExtractor {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private final ActionContext ctx;

    public DataExtractor(ActionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Extracts the dataFieldName and dataFieldValue based on the instruction and DTO.
     */
    /**
     * Extracts the fieldName and fieldValue based on the instruction and DTO.
     */
    public FieldData extractFieldData(
            Map<String, String> data, String[] actions, String defaultValue, boolean isEncrypted) throws Exception {

        String dataFieldName = "";
        String dataFieldValue = "";

        if (data != null) {
            if (actions.length >= 3
                    && actions[0].equals(ARConstantsEngine.INSERT)
                    && actions[1].equals(ARConstantsEngine.ENTER)) {

                dataFieldName = actions[2].split(ARConstantsEngine.PATH_FIELD_SUBSTITUTION)[0];
                dataFieldValue = data.get(dataFieldName);

                if (isEncrypted && dataFieldValue != null) {
                    dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
                }

            } else if (actions.length == 2 && actions[0].equals(ARConstantsEngine.INSERT)) {

                dataFieldName = actions[1].split(ARConstantsEngine.PATH_FIELD_SUBSTITUTION)[0];
                dataFieldValue = data.get(dataFieldName);

                if (isEncrypted && dataFieldValue != null) {
                    dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
                }
            }
        } else if (defaultValue != null && !defaultValue.isEmpty()) {
            dataFieldValue = defaultValue;
            if (isEncrypted) {
                dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
            }
        }

        return new FieldData(dataFieldName, dataFieldValue);
    }

    /**
     * Block scoped, clientNamed aware variant. Use this from executeJob and from the engine
     * runner. The legacy {@code extractFieldData(Map, ...)} above keeps the flat map signature
     * for any other caller but suffers from two issues now that clientNamed exists.
     *
     * <ol>
     *   <li>Excel column headers are {@code instruction.displayKey()}, which is clientNamed
     *       when set and the canonical name otherwise. The legacy method looks up by the
     *       canonical name from the action string and would miss every renamed field.
     *   <li>The flat row map merges values across all blocks. Two blocks with the same
     *       displayKey or the same canonical name silently share one cell, so the second
     *       block value is lost. The block scoped lookup against {@link ExtractedData} keeps
     *       each block column independent.
     * </ol>
     *
     * Lookup ladder is block displayKey, then block canonical, then null. The canonical
     * fallback handles legacy Excel files written before clientNamed was set.
     */
    public FieldData extractFieldData(
            ExtractedData extractedData,
            String blockName,
            int row,
            InstructionLoad instruction,
            String[] actions,
            String defaultValue,
            boolean isEncrypted)
            throws Exception {

        String dataFieldName = "";
        String dataFieldValue = "";

        if (extractedData != null) {
            if (actions.length >= 3
                    && actions[0].equals(ARConstantsEngine.INSERT)
                    && actions[1].equals(ARConstantsEngine.ENTER)) {
                dataFieldName = actions[2].split(ARConstantsEngine.PATH_FIELD_SUBSTITUTION)[0];
            } else if (actions.length == 2 && actions[0].equals(ARConstantsEngine.INSERT)) {
                dataFieldName = actions[1].split(ARConstantsEngine.PATH_FIELD_SUBSTITUTION)[0];
            }

            if (!dataFieldName.isEmpty()) {
                String displayKey = (instruction != null) ? instruction.displayKey() : dataFieldName;
                dataFieldValue = extractedData.getFieldValue(blockName, displayKey, row);
                if (dataFieldValue == null && !displayKey.equals(dataFieldName)) {
                    dataFieldValue = extractedData.getFieldValue(blockName, dataFieldName, row);
                }

                if (isEncrypted && dataFieldValue != null) {
                    dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
                }
            }
        } else if (defaultValue != null && !defaultValue.isEmpty()) {
            dataFieldValue = defaultValue;
            if (isEncrypted) {
                dataFieldValue = CryptationAlgorithm.decrypt(dataFieldValue);
            }
        }

        return new FieldData(dataFieldName, dataFieldValue);
    }

    public String getOutPutElement(
            boolean byPassNotFound,
            WebElement element,
            String fieldName,
            String action,
            Map<String, String> mapOperators)
            throws Exception {

        UtilsMethods.exceptionIfNullWebElement(element);

        try {
            ctx.actionWait().until(ExpectedConditions.visibilityOf(element));
        } catch (Exception ex) {

            logOperations.warn(
                    String.format("Could Not Find Field Name \"%s\" -> Cause: %s", fieldName, ex.getMessage()));

            if (!byPassNotFound) {
                PerformMessage.getInstance().couldNotFindElement(fieldName);
            }
            return null;
        }

        String textByhJS = "";
        String finalTextNested = "";
        String textAttribute = "";
        String textContext = "";
        boolean outputReadSucceeded = false;

        try {
            JavascriptExecutor js = (JavascriptExecutor) ctx.driver();
            textByhJS = (String) js.executeScript("return arguments[0].textContent;", element);
            outputReadSucceeded = true;
        } catch (Exception ex) {

            logOperations.warn(
                    String.format("By JavascriptExecutor - Not succeeded to get a Text from Label for: %s", fieldName));
        }

        try {
            List<WebElement> children = element.findElements(By.xpath(".//*"));
            StringBuilder textByNested = new StringBuilder();
            for (WebElement child : children) {
                textByNested.append(child.getText()).append(" ");
            }
            finalTextNested = textByNested.toString().trim();
            outputReadSucceeded = true;
        } catch (Exception ex) {

            logOperations.warn(
                    String.format("By Text Nested - Not succeeded to get a Text from Label for: %s", fieldName));
        }

        try {
            textAttribute = element.getAttribute("value");
            outputReadSucceeded = true;
        } catch (Exception ex) {

            logOperations.warn(String.format(
                    "By Text Attribute - Not succeeded to get a Text from Label for: %s Operation: %s",
                    fieldName, action));
        }

        try {
            textContext = element.getAttribute("textContent");
            outputReadSucceeded = true;
        } catch (Exception ex) {

            logOperations.warn(String.format(
                    "By Text Content - Not succeeded to get a Text from Label for: %s Operation: %s",
                    fieldName, action));
        }

        // Check if the element is clickable
        boolean isClickable = false;
        try {
            ctx.actionWait().until(ExpectedConditions.elementToBeClickable(element));
            isClickable = true;
        } catch (Exception e) {

            logOperations.warn(String.format("Element is not clickable: \"%s\"", fieldName));
        }

        // Set the final text value by priority and add to mapOperators
        String finalText = "";

        if (isClickable && finalTextNested != null && !finalTextNested.trim().isEmpty()) {
            finalText = finalTextNested; // Use nested text if the element is clickable
            mapOperators.put(fieldName.trim(), finalText.trim());
        } else if (textByhJS != null && !textByhJS.trim().isEmpty()) {
            finalText = textByhJS;
            mapOperators.put(fieldName.trim(), finalText.trim());
        } else if (finalTextNested != null && !finalTextNested.trim().isEmpty()) {
            finalText = finalTextNested;
            mapOperators.put(fieldName.trim(), finalText.trim());
        } else if (textAttribute != null && !textAttribute.trim().isEmpty()) {
            finalText = textAttribute;
            mapOperators.put(fieldName.trim(), finalText.trim());
        } else if (textContext != null && !textContext.trim().isEmpty()) {
            finalText = textContext;
            mapOperators.put(fieldName.trim(), finalText.trim());
        } else if (outputReadSucceeded) {
            // The element can legitimately contain an empty value. Keep the produced empty String
            // as data; never replace it with a fake failure sentence that a later check could
            // mistake for page content.
            mapOperators.put(fieldName.trim(), "");
            logOperations.warn(String.format(
                    "No non-empty text source was found for element \"%s\"; preserving an empty value",
                    fieldName));
        } else {
            logOperations.warn(String.format(
                    "No OUTPUT read strategy succeeded for element \"%s\"; value remains unavailable",
                    fieldName));
            return null;
        }

        return finalText;
    }
}
