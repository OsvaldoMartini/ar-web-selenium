package com.allinweb.ch.facade.actions;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARExecution;

/**
 * Pure builders for validation / check-value / get-variable report rows (cluster E).
 * Every method returns a pipe-separated JTable row or a prefixed message — no state,
 * no side effects. Bodies moved verbatim from PerformActions.
 */
public final class ValidationMessageBuilder {

    private ValidationMessageBuilder() {}

    public static String buildValidationReason(
            String invalidValues,
            String parent,
            String actualValue, // current web field value
            String expectedValue, // EXPECTED VALUE AS PARAM
            String lastInstructionExecuted,
            String[] operations, // [0]=variableName, [1]=operator
            ARExecution.ConditionStatus conditionStatus,
            boolean byPassFlagLoop,
            boolean includeLengths,
            String blockName,
            Integer testRow,
            boolean success) {

        if (operations == null || operations.length < 2) {
            return withConditionalPrefix(conditionStatus, "Validation failed - malformed operation definition");
        }

        if (byPassFlagLoop) {
            return withConditionalPrefix(conditionStatus, lastInstructionExecuted);
        }

        String varName = operations.length > 0 ? operations[0] : "?";
        String op = operations.length > 1 ? operations[1] : "?";

        String rawActual = actualValue == null ? "" : actualValue;
        String rawExpected = expectedValue == null ? "" : expectedValue;

        String safeActual = rawActual;
        String safeExpected = rawExpected;

        if (">".equals(op) || "<".equals(op)) {
            safeActual = normalizeNumber(rawActual);
            safeExpected = normalizeNumber(rawExpected);
        }

        // ✅ Professional summary: passed / failed
        String summary;
        if (invalidValues == null || invalidValues.trim().isEmpty()) {
            summary = success ? "Validation passed" : "Validation failed";
        } else {
            summary = invalidValues.trim() + " Operator: (" + op + ")";
        }

        String conditionText; // ✅ will go into "Condition" column

        if (">".equals(op)) {
            conditionText = String.format(
                    "value \"%s\" %s \"%s\" (variable \"%s\")",
                    safeActual, (success ? "is >" : "is not >"), safeExpected, varName);

        } else if ("<".equals(op)) {
            conditionText = String.format(
                    "value \"%s\" %s \"%s\" (variable \"%s\")",
                    safeActual, (success ? "is <" : "is not <"), safeExpected, varName);

        } else if ("!=".equals(op)) {
            conditionText = String.format(
                    "value \"%s\" %s \"%s\" (variable \"%s\")",
                    safeActual, (success ? "is !=" : "is not !="), safeExpected, varName);

        } else {
            String opPhrase = success ? ("is " + op) : ("is not " + op);

            conditionText = String.format(
                    "value \"%s\" %s \"%s\" (variable \"%s\")", safeActual, opPhrase, safeExpected, varName);

            if (includeLengths) {
                conditionText +=
                        String.format(" [actualLen=%d, expectedLen=%d]", safeActual.length(), safeExpected.length());
            }
        }

        // ✅ Main Field column should be just the field name (no quotes)
        String mainField = (parent == null) ? "" : parent;

        // ✅ Description column
        String desc = (blockName == null) ? "" : blockName;

        // ✅ Test column
        String testName = (testRow == null) ? "" : String.valueOf(testRow);

        // ✅ Result column
        String result = success ? "PASSED" : "FAIL";

        // ✅ Time column (your JTable expects Time as first col)
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        // Keep your output format (Condition column = conditionText).
        // If you want to include summary too, replace conditionText with (summary + " - " + conditionText).
        String row =
                time + " | " + testName + " | " + desc + " | " + mainField + " | " + conditionText + " | " + result;

        return row;
    }

    public static String sanitizeValue(String input) {
        if (input == null) return "";

        return input.replace('\u00A0', ' ') // NO-BREAK SPACE
                .replace('\u202F', ' ') // NARROW NO-BREAK SPACE
                .replace('\u2007', ' ') // FIGURE SPACE
                .replaceAll("\\s+", " ") // collapse whitespace
                .trim();
    }

    /**
     * Builds a JTable row (pipe-separated) for CHECK_VALUE / validation messages.
     *
     * Output pattern:
     *   time | testName | desc | mainField | conditionText | result
     */
    public static String checkValidationMesssage(
            String action,
            InstructionLoad currentInstruction,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus,
            String parentField, // e.g. "8838-BancaStato" OR "BancaStato"
            String variableField, // e.g. "$BancaStato"
            String actualValue, // actual extracted value
            String expectedValue, // expected value
            String operator, // "=", "!=", ">", "<", etc.
            boolean byPassFlagLoop,
            String blockName,
            Integer testRow,
            boolean includeLengths,
            boolean success) {

        if (byPassFlagLoop) {
            return withConditionalPrefix(conditionStatus, lastInstructionExecuted);
        }

        String instrName =
                currentInstruction != null && currentInstruction.getName() != null ? currentInstruction.getName() : "?";

        String var = (variableField == null) ? "?" : variableField;

        String rawActual = (actualValue == null) ? "" : actualValue;
        String rawExpected = (expectedValue == null) ? "" : expectedValue;

        String safeActual = rawActual;
        String safeExpected = rawExpected;

        if (">".equals(operator) || "<".equals(operator)) {
            safeActual = normalizeNumber(rawActual);
            safeExpected = normalizeNumber(rawExpected);
        }

        // ✅ parse TEST + Main Field like your other methods
        String testName = "";
        String mainField = "";
        if (parentField != null && parentField.contains("-")) {
            int idx = parentField.indexOf('-');
            testName = parentField.substring(0, idx).trim();
            mainField = parentField.substring(idx + 1).trim();
        } else {
            mainField = (parentField == null) ? "" : parentField;
            testName = (testRow == null) ? "" : String.valueOf(testRow);
        }

        // ✅ Description column
        String desc = (blockName == null) ? "" : blockName;

        // ✅ Condition column (action first)
        String op = (operator == null) ? "?" : operator;

        String conditionText;
        if (">".equals(op)) {
            conditionText = String.format(
                    "%s] --> value \"%s\" is not > \"%s\" (variable \"%s\", instruction \"%s\")",
                    action, safeActual, safeExpected, var, instrName);

        } else if ("<".equals(op)) {
            conditionText = String.format(
                    "%s] --> value \"%s\" is not < \"%s\" (variable \"%s\", instruction \"%s\")",
                    action, safeActual, safeExpected, var, instrName);

        } else if ("!=".equals(op)) {
            conditionText = String.format(
                    "%s] --> value \"%s\" is not != \"%s\" (variable \"%s\", instruction \"%s\")",
                    action, safeActual, safeExpected, var, instrName);

        } else {
            conditionText = String.format(
                    "%s] --> value \"%s\" is not %s \"%s\" (variable \"%s\", instruction \"%s\")",
                    action, safeActual, op, safeExpected, var, instrName);

            if (includeLengths) {
                conditionText +=
                        String.format(" [actualLen=%d, expectedLen=%d]", safeActual.length(), safeExpected.length());
            }
        }

        // ✅ Result column
        String result = success ? "PASSED" : "FAIL";

        // ✅ Time column
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        String row =
                time + " | " + testName + " | " + desc + " | " + mainField + " | " + conditionText + " | " + result;

        return withConditionalPrefix(conditionStatus, row);
    }

    /**
     * Builds a JTable row (same pipe-separated pattern as buildValidationReason)
     * for the case "GET variable missing / not assigned".
     *
     * Output pattern:
     *   time | testName | desc | mainField | conditionText | result
     */
    public static String buildGetVariableReason(
            String action,
            InstructionLoad currentInstruction,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus,
            String parentField, // MAIN FIELD (e.g. "BancaStato")
            String variableField, // variable name (e.g. "$BancaStato")
            boolean byPassFlagLoop,
            String blockName,
            Integer testRow,
            boolean success // usually false for "not defined"
            ) {

        if (byPassFlagLoop) {
            return withConditionalPrefix(conditionStatus, lastInstructionExecuted);
        }

        // Preserve raw values
        String instrName =
                currentInstruction != null && currentInstruction.getName() != null ? currentInstruction.getName() : "?";

        String var = (variableField == null) ? "?" : variableField;
        String parent = (parentField == null) ? "" : parentField;

        // ✅ Condition column text (human readable, like buildValidationReason)
        String conditionText;
        if (ARConstantsEngine.EXTRACT_FIELD.equals(action) || ARConstantsEngine.CHECK_VALUE.equals(action)) {
            conditionText = String.format(
                    "Get Value Is Not Defined - variable \"%s\" has not been assigned (instruction \"%s\")",
                    var, instrName);
        } else {
            if (parentField != null) {
                conditionText = String.format(
                        "Get Value Is Not Defined - no GET value defined for instruction \"%s\" (parent field \"%s\")",
                        instrName, parent);
            } else {
                conditionText = String.format(
                        "Get Value Is Not Defined - no GET value defined for instruction \"%s\" (parent field not defined)",
                        instrName);
            }
        }

        // ✅ Main Field column: just field name
        String mainField = parent;

        // ✅ Description column
        String desc = (blockName == null) ? "" : blockName;

        // ✅ Test column
        String testName = (testRow == null) ? "" : String.valueOf(testRow);

        // ✅ Result column
        String result = success ? "PASSED" : "FAIL";

        // ✅ Time column
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        String row =
                time + " | " + testName + " | " + desc + " | " + mainField + " | " + conditionText + " | " + result;

        // Preserve conditional prefix behavior
        return withConditionalPrefix(conditionStatus, row);
    }

    public static String normalizeNumber(String value) {
        if (value == null) return null;
        return value.replaceAll("[^0-9,.-]", "").replace(",", ".");
    }

    public static String withConditionalPrefix(ARExecution.ConditionStatus conditionStatus, String message) {
        String conditionalBlock = conditionStatus == ARExecution.ConditionStatus.IF_PASSED
                ? "Closing Block { IF -> ELSE } -> "
                : conditionStatus == ARExecution.ConditionStatus.ELSEIF_PASSED
                        ? "Closing Block { ELSEIF -> ELSE } -> "
                        : conditionStatus == ARExecution.ConditionStatus.ELSE_PASSED
                                ? "Closing Block { ELSE -> ENDIF } -> "
                                : "";

        if (conditionStatus != null && conditionStatus != ARExecution.ConditionStatus.NONE) {
            return conditionalBlock + message;
        }
        return message;
    }
}
