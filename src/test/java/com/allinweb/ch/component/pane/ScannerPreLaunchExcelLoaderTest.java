package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.util.ExtractedData;
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
}
