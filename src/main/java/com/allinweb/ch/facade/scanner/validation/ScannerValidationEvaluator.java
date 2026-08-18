package com.allinweb.ch.facade.scanner.validation;

import com.google.common.base.Strings;

public final class ScannerValidationEvaluator {
    private final Operations operations;

    public ScannerValidationEvaluator(Operations operations) {
        this.operations = operations;
    }

    public int handleGreaterThan(String value1, String value2) {
        double num1 = parseValueGreaterThan(clean(value1), true);
        double num2 = parseValueGreaterThan(clean(value2), false);

        return num1 > num2 ? 1 : 0;
    }

    public int handleLessThan(String value1, String value2) {
        double num1 = parseValueForLessThan(clean(value1), true);
        double num2 = parseValueForLessThan(clean(value2), false);

        return num1 < num2 ? 1 : 0;
    }

    public ValidationResult evaluateOperation(String actualRaw, String operator, String expectedRaw) {
        if (actualRaw == null || expectedRaw == null || operator == null) {
            return new ValidationResult(false, "Null values");
        }

        String actual = actualRaw.trim();
        String expected = expectedRaw.trim();

        switch (operator.trim()) {
            case "=":
                return new ValidationResult(actual.equalsIgnoreCase(expected), null);

            case "!=":
                return new ValidationResult(!actual.equalsIgnoreCase(expected), null);

            case ">": {
                int resp = handleGreaterThan(actual, expected);
                if (resp == 1) return new ValidationResult(true, null);
                if (resp == 0) return new ValidationResult(false, null);
                return new ValidationResult(false, "Invalid Numbers");
            }

            case "<": {
                int resp = handleLessThan(actual, expected);
                if (resp == 1) return new ValidationResult(true, null);
                if (resp == 0) return new ValidationResult(false, null);
                return new ValidationResult(false, "Invalid Numbers");
            }

            default:
                return new ValidationResult(false, "Unknown operator: " + operator);
        }
    }

    public String finalLogMessage(String failedMessage, String resultActions) {
        if (!Strings.isNullOrEmpty(failedMessage)) {
            return failedMessage + resultActions;
        }
        return resultActions;
    }

    private double parseValueGreaterThan(String value, boolean isValue1) {
        if (isEmptyMarker(value)) {
            return Double.MIN_VALUE;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            if (isValue1) {
                operations.warnInvalidNumericValue("value1", value);
                return Double.MIN_VALUE;
            }
            operations.warnInvalidNumericValue("value2", value);
            return Double.MAX_VALUE;
        }
    }

    private double parseValueForLessThan(String value, boolean isValue1) {
        if (isEmptyMarker(value)) {
            return Double.MAX_VALUE;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            if (isValue1) {
                operations.warnInvalidNumericValue("value1", value);
                return Double.MAX_VALUE;
            }
            operations.warnInvalidNumericValue("value2", value);
            return Double.MIN_VALUE;
        }
    }

    private boolean isEmptyMarker(String value) {
        return value == null || "$EMPTY".equalsIgnoreCase(value) || "#EMPTY".equalsIgnoreCase(value);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        return value.replace(".", "").replace(",", "");
    }

    public static final class ValidationResult {
        public final boolean valid;
        public final String invalidReason;

        ValidationResult(boolean valid, String invalidReason) {
            this.valid = valid;
            this.invalidReason = invalidReason;
        }
    }

    public interface Operations {
        void warnInvalidNumericValue(String fieldName, String value);
    }
}
