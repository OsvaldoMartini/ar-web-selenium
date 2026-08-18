package com.allinweb.ch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtractedDataRowMoveTest {
    @Test
    void movesOneLogicalRowForwardAcrossEveryBlockAndColumn() {
        ExtractedData data = dataset();

        assertTrue(data.moveRow(0, 2));

        assertEquals(3, data.getNumberOfDataRows());
        assertEquals("IBAN-2", data.getFieldValue("Account", "IBAN", 0));
        assertEquals("IBAN-3", data.getFieldValue("Account", "IBAN", 1));
        assertEquals("IBAN-1", data.getFieldValue("Account", "IBAN", 2));
        assertEquals("20", data.getFieldValue("Payment", "Amount", 0));
        assertEquals("30", data.getFieldValue("Payment", "Amount", 1));
        assertEquals("10", data.getFieldValue("Payment", "Amount", 2));
    }

    @Test
    void movesOneLogicalRowBackwardAcrossEveryBlockAndColumn() {
        ExtractedData data = dataset();

        assertTrue(data.moveRow(2, 0));

        assertEquals("IBAN-3", data.getFieldValue("Account", "IBAN", 0));
        assertEquals("IBAN-1", data.getFieldValue("Account", "IBAN", 1));
        assertEquals("IBAN-2", data.getFieldValue("Account", "IBAN", 2));
        assertEquals("30", data.getFieldValue("Payment", "Amount", 0));
        assertEquals("10", data.getFieldValue("Payment", "Amount", 1));
        assertEquals("20", data.getFieldValue("Payment", "Amount", 2));
    }

    @Test
    void preservesExplicitNullAndEmptyCellsWhileMovingRows() {
        ExtractedData data = new ExtractedData();
        data.addFieldValue("Account", "Name", "Alice", 0);
        data.addFieldValue("Account", "Name", "", 1);
        data.addFieldValue("Account", "Name", "Claire", 2);
        data.addFieldValue("Payment", "Reference", "first", 0);
        data.addFieldValue("Payment", "Reference", null, 1);
        data.addFieldValue("Payment", "Reference", "third", 2);

        assertTrue(data.moveRow(1, 0));

        assertEquals(3, data.getNumberOfDataRows());
        assertEquals("", data.getFieldValue("Account", "Name", 0));
        assertEquals("Alice", data.getFieldValue("Account", "Name", 1));
        assertNull(data.getFieldValue("Payment", "Reference", 0));
        assertEquals("first", data.getFieldValue("Payment", "Reference", 1));
        assertEquals("third", data.getFieldValue("Payment", "Reference", 2));
    }

    @Test
    void rejectsOutOfBoundsIndicesAndTreatsSameIndexAsSuccessfulNoOp() {
        ExtractedData data = dataset();

        assertFalse(data.moveRow(-1, 0));
        assertFalse(data.moveRow(0, -1));
        assertFalse(data.moveRow(3, 0));
        assertFalse(data.moveRow(0, 3));
        assertTrue(data.moveRow(1, 1));

        assertEquals("IBAN-1", data.getFieldValue("Account", "IBAN", 0));
        assertEquals("IBAN-2", data.getFieldValue("Account", "IBAN", 1));
        assertEquals("IBAN-3", data.getFieldValue("Account", "IBAN", 2));
        assertEquals("10", data.getFieldValue("Payment", "Amount", 0));
        assertEquals("20", data.getFieldValue("Payment", "Amount", 1));
        assertEquals("30", data.getFieldValue("Payment", "Amount", 2));
    }

    private static ExtractedData dataset() {
        ExtractedData data = new ExtractedData();
        data.addFieldValue("Account", "IBAN", "IBAN-1", 0);
        data.addFieldValue("Account", "IBAN", "IBAN-2", 1);
        data.addFieldValue("Account", "IBAN", "IBAN-3", 2);
        data.addFieldValue("Payment", "Amount", "10", 0);
        data.addFieldValue("Payment", "Amount", "20", 1);
        data.addFieldValue("Payment", "Amount", "30", 2);
        return data;
    }
}
