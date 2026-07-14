package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ExtractedData;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchExcelLoaderTest {

    @Test
    void ensureEmptyDataRowAddsLegacyEmptyField() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();

        loader.ensureEmptyDataRow(extractedData);

        assertEquals(1, extractedData.getNumberOfDataRows());
        assertEquals("$EMPTY", extractedData.getFieldValue("$EMPTY", 0));
    }

    @Test
    void ensureEmptyDataRowLeavesExistingRowsUnchanged() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.addFieldValue("username", "martini", 0);

        loader.ensureEmptyDataRow(extractedData);

        assertEquals(1, extractedData.getNumberOfDataRows());
        assertEquals("martini", extractedData.getFieldValue("username", 0));
    }

    @Test
    void ensureEmptyDataRowCreatesDataWhenReaderReturnedNothing() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();

        ExtractedData extractedData = loader.ensureEmptyDataRow(null);

        assertEquals(1, extractedData.getNumberOfDataRows());
        assertEquals("$EMPTY", extractedData.getFieldValue("$EMPTY", 0));
    }

    @Test
    void hasExcelErrorDetectsReaderErrorMessage() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.setErrorMessage("Missing field");

        assertTrue(loader.hasExcelError(extractedData));
    }

    @Test
    void requiresMultipleRowsConfirmationWhenMultipleRowsWithoutExcelGoto() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.addFieldValue("username", "first", 0);
        extractedData.addFieldValue("username", "second", 1);

        assertTrue(loader.requiresMultipleRowsConfirmation(extractedData, List.of()));
        assertFalse(loader.requiresMultipleRowsConfirmation(extractedData, List.of(new InstructionLoad())));
    }

    @Test
    void requiresMultipleRowsConfirmationIgnoresSingleRow() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.addFieldValue("username", "first", 0);

        assertFalse(loader.requiresMultipleRowsConfirmation(extractedData, List.of()));
    }
}
