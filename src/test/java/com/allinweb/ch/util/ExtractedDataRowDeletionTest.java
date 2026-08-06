package com.allinweb.ch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtractedDataRowDeletionTest {
    @Test
    void removesOneLogicalRowAcrossBlocksAndCompactsLaterRows() {
        ExtractedData data = new ExtractedData();
        data.addFieldValue("First", "IBAN", "A", 0);
        data.addFieldValue("First", "IBAN", "B", 1);
        data.addFieldValue("First", "IBAN", "C", 2);
        data.addFieldValue("Second", "Amount", "10", 0);
        data.addFieldValue("Second", "Amount", "20", 1);
        data.addFieldValue("Second", "Amount", "30", 2);

        assertTrue(data.removeRow(1));

        assertEquals(2, data.getNumberOfDataRows());
        assertEquals("A", data.getFieldValue("First", "IBAN", 0));
        assertEquals("C", data.getFieldValue("First", "IBAN", 1));
        assertEquals("10", data.getFieldValue("Second", "Amount", 0));
        assertEquals("30", data.getFieldValue("Second", "Amount", 1));
    }

    @Test
    void refusesAnIndexOutsideTheDatasetWithoutChangingRows() {
        ExtractedData data = new ExtractedData();
        data.addFieldValue("First", "IBAN", "A", 0);

        assertFalse(data.removeRow(1));
        assertEquals(1, data.getNumberOfDataRows());
        assertEquals("A", data.getFieldValue("First", "IBAN", 0));
    }
}
