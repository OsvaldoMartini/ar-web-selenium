package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.util.ExtractedData;
import org.junit.jupiter.api.Test;

class ScannerTestRunExcelPreparationTest {

    @Test
    void prepareAssignsLoadedExcelData() {
        FakeExcelLoader loader = new FakeExcelLoader();
        ExtractedData loadedData = new ExtractedData();
        loadedData.addFieldValue("username", "martini", 0);
        loader.loadedData = loadedData;
        FakeOperations operations = new FakeOperations();
        ScannerTestRunExcelPreparation preparation = new ScannerTestRunExcelPreparation(loader, operations);

        ScannerTestRunExcelPreparation.Result result = preparation.prepare("D:\\Excel\\Job.xlsx", null);

        assertFalse(result.usedSyntheticFallback());
        assertSame(loadedData, operations.extractedData);
        assertEquals("D:\\Excel\\Job.xlsx", loader.excelPath);
    }

    @Test
    void prepareAssignsEmptyFallbackWhenExcelLoadFails() {
        FakeExcelLoader loader = new FakeExcelLoader();
        loader.loadError = new IllegalStateException("missing workbook");
        FakeOperations operations = new FakeOperations();
        ScannerTestRunExcelPreparation preparation = new ScannerTestRunExcelPreparation(loader, operations);

        ScannerTestRunExcelPreparation.Result result = preparation.prepare("D:\\Excel\\Job.xlsx", null);

        assertTrue(result.usedSyntheticFallback());
        assertEquals("missing workbook", result.loadError().getMessage());
        assertEquals(1, operations.extractedData.getNumberOfDataRows());
        assertEquals("$EMPTY", operations.extractedData.getFieldValue("$EMPTY", 0));
    }

    private static final class FakeOperations implements ScannerTestRunExcelPreparation.Operations {
        private ExtractedData extractedData;

        @Override
        public void setExtractedData(ExtractedData extractedData) {
            this.extractedData = extractedData;
        }
    }

    private static final class FakeExcelLoader implements ScannerTestRunExcelPreparation.ExcelLoader {
        private String excelPath;
        private ExtractedData loadedData;
        private Exception loadError;

        @Override
        public ExtractedData load(String excelPath, PerformLists performLists) throws Exception {
            this.excelPath = excelPath;
            if (loadError != null) {
                throw loadError;
            }
            return loadedData;
        }

        @Override
        public ExtractedData ensureEmptyDataRow(ExtractedData extractedData) {
            ExtractedData data = extractedData == null ? new ExtractedData() : extractedData;
            if (data.getNumberOfDataRows() == null || data.getNumberOfDataRows() == 0) {
                data.addField("$EMPTY");
                data.addFieldValue("$EMPTY", "$EMPTY", 0);
            }
            return data;
        }
    }
}
