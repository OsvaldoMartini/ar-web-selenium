package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerValidationEvaluatorTest {

    @Test
    void evaluateEqualsIgnoresCaseAndWhitespace() {
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(new RecordingOperations());

        ScannerValidationEvaluator.ValidationResult result = evaluator.evaluateOperation("  Ready ", "=", "ready");

        assertTrue(result.valid);
        assertNull(result.invalidReason);
    }

    @Test
    void evaluateNotEqualsIgnoresCaseAndWhitespace() {
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(new RecordingOperations());

        ScannerValidationEvaluator.ValidationResult result = evaluator.evaluateOperation("Ready", "!=", "ready");

        assertFalse(result.valid);
        assertNull(result.invalidReason);
    }

    @Test
    void greaterThanCleansSeparatorsBeforeComparing() {
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(new RecordingOperations());

        assertEquals(1, evaluator.handleGreaterThan("1,200", "1199"));
        assertEquals(0, evaluator.handleGreaterThan("1.199", "1200"));
    }

    @Test
    void lessThanCleansSeparatorsBeforeComparing() {
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(new RecordingOperations());

        assertEquals(1, evaluator.handleLessThan("1,199", "1200"));
        assertEquals(0, evaluator.handleLessThan("1.200", "1199"));
    }

    @Test
    void emptyMarkersKeepExistingGreaterAndLessThanFallbacks() {
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(new RecordingOperations());

        assertEquals(0, evaluator.handleGreaterThan("$EMPTY", "1"));
        assertEquals(0, evaluator.handleLessThan("#EMPTY", "1"));
    }

    @Test
    void invalidNumericValuesAreReportedWithExistingFallbackDirection() {
        RecordingOperations operations = new RecordingOperations();
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(operations);

        assertEquals(0, evaluator.handleGreaterThan("bad-left", "10"));
        assertEquals(0, evaluator.handleLessThan("10", "bad-right"));
        assertEquals(List.of("value1=bad-left", "value2=bad-right"), operations.warnings);
    }

    @Test
    void evaluateReportsNullAndUnknownOperators() {
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(new RecordingOperations());

        ScannerValidationEvaluator.ValidationResult nullResult = evaluator.evaluateOperation(null, "=", "x");
        ScannerValidationEvaluator.ValidationResult unknownResult = evaluator.evaluateOperation("a", "contains", "a");

        assertFalse(nullResult.valid);
        assertEquals("Null values", nullResult.invalidReason);
        assertFalse(unknownResult.valid);
        assertEquals("Unknown operator: contains", unknownResult.invalidReason);
    }

    @Test
    void finalLogMessagePrefixesFailedMessageWhenPresent() {
        ScannerValidationEvaluator evaluator = new ScannerValidationEvaluator(new RecordingOperations());

        assertEquals("failedaction", evaluator.finalLogMessage("failed", "action"));
        assertEquals("action", evaluator.finalLogMessage("", "action"));
        assertEquals("action", evaluator.finalLogMessage(null, "action"));
    }

    private static final class RecordingOperations implements ScannerValidationEvaluator.Operations {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void warnInvalidNumericValue(String fieldName, String value) {
            warnings.add(fieldName + "=" + value);
        }
    }
}
