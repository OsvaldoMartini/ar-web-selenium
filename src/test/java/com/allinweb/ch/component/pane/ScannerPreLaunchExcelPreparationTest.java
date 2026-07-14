package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.util.ExtractedData;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchExcelPreparationTest {

    @Test
    void prepareExcelLoadsAndStoresExtractedData() {
        FakeExcelLoader loader = new FakeExcelLoader();
        FakeOperations operations = new FakeOperations();
        ExtractedData loaded = new ExtractedData();
        loader.loadedData = loaded;
        ScannerPreLaunchExcelPreparation preparation = new ScannerPreLaunchExcelPreparation(loader, operations);

        preparation.prepareExcel();

        assertSame(loaded, operations.extractedData);
        assertEquals("C:\\excel\\Job.xlsx", loader.excelPath);
        assertEquals(0, operations.processingErrorCalls);
    }

    @Test
    void prepareExcelReportsProcessingErrorWhenLoadFails() {
        FakeExcelLoader loader = new FakeExcelLoader();
        FakeOperations operations = new FakeOperations();
        loader.loadFailure = new IllegalStateException("bad workbook");
        ScannerPreLaunchExcelPreparation preparation = new ScannerPreLaunchExcelPreparation(loader, operations);

        preparation.prepareExcel();

        assertEquals(1, operations.errorCalls);
        assertEquals(1, operations.processingErrorCalls);
    }

    @Test
    void validateExcelStoresEnsuredDataAndAcceptsValidWorkbook() {
        FakeExcelLoader loader = new FakeExcelLoader();
        FakeOperations operations = new FakeOperations();
        ExtractedData ensured = new ExtractedData();
        loader.ensuredData = ensured;
        ScannerPreLaunchExcelPreparation preparation = new ScannerPreLaunchExcelPreparation(loader, operations);

        assertTrue(preparation.validateExcel());

        assertSame(ensured, operations.extractedData);
        assertEquals(0, operations.validationErrorCalls);
        assertEquals(0, operations.reenableCalls);
    }

    @Test
    void validateExcelReportsReaderErrorAndReenablesLaunch() {
        FakeExcelLoader loader = new FakeExcelLoader();
        FakeOperations operations = new FakeOperations();
        ExtractedData ensured = new ExtractedData();
        ensured.setErrorMessage("Missing column");
        loader.ensuredData = ensured;
        loader.hasExcelError = true;
        ScannerPreLaunchExcelPreparation preparation = new ScannerPreLaunchExcelPreparation(loader, operations);

        assertFalse(preparation.validateExcel());

        assertEquals("Missing column", operations.validationErrorMessage);
        assertEquals(1, operations.validationErrorCalls);
        assertEquals(1, operations.reenableCalls);
    }

    private static final class FakeOperations implements ScannerPreLaunchExcelPreparation.Operations {
        private ExtractedData extractedData;
        private int processingErrorCalls;
        private int validationErrorCalls;
        private int reenableCalls;
        private int errorCalls;
        private String validationErrorMessage;

        @Override
        public String excelPath() {
            return "C:\\excel\\Job.xlsx";
        }

        @Override
        public PerformLists performLists() {
            return null;
        }

        @Override
        public ExtractedData extractedData() {
            return extractedData;
        }

        @Override
        public void setExtractedData(ExtractedData extractedData) {
            this.extractedData = extractedData;
        }

        @Override
        public void showExcelProcessingError() {
            processingErrorCalls++;
        }

        @Override
        public void showExcelValidationError(String errorMessage) {
            validationErrorCalls++;
            validationErrorMessage = errorMessage;
        }

        @Override
        public void reenableLaunchButton() {
            reenableCalls++;
        }

        @Override
        public void error(String message) {
            errorCalls++;
        }
    }

    private static final class FakeExcelLoader implements ScannerPreLaunchExcelPreparation.ExcelLoader {
        private ExtractedData loadedData;
        private ExtractedData ensuredData;
        private RuntimeException loadFailure;
        private boolean hasExcelError;
        private String excelPath;

        @Override
        public ExtractedData load(String excelPath, PerformLists performLists) throws Exception {
            this.excelPath = excelPath;
            if (loadFailure != null) {
                throw loadFailure;
            }
            return loadedData;
        }

        @Override
        public ExtractedData ensureEmptyDataRow(ExtractedData extractedData) {
            return ensuredData == null ? extractedData : ensuredData;
        }

        @Override
        public boolean hasExcelError(ExtractedData extractedData) {
            return hasExcelError;
        }
    }
}
