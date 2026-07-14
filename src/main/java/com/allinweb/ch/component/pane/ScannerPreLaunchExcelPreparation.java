package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.util.ExtractedData;

final class ScannerPreLaunchExcelPreparation {
    private final ExcelLoader excelLoader;
    private final Operations operations;

    ScannerPreLaunchExcelPreparation(ScannerPreLaunchExcelLoader excelLoader, Operations operations) {
        this(new ScannerPreLaunchExcelLoaderAdapter(excelLoader), operations);
    }

    ScannerPreLaunchExcelPreparation(ExcelLoader excelLoader, Operations operations) {
        this.excelLoader = excelLoader;
        this.operations = operations;
    }

    void prepareExcel() {
        try {
            operations.setExtractedData(excelLoader.load(operations.excelPath(), operations.performLists()));
        } catch (Exception error) {
            operations.error("Error Processing Excel File");
            operations.showExcelProcessingError();
        }
    }

    boolean validateExcel() {
        ExtractedData extractedData = excelLoader.ensureEmptyDataRow(operations.extractedData());
        operations.setExtractedData(extractedData);

        if (excelLoader.hasExcelError(extractedData)) {
            operations.showExcelValidationError(extractedData.getErrorMessage());
            operations.reenableLaunchButton();
            return false;
        }
        return true;
    }

    interface Operations {
        String excelPath();

        PerformLists performLists();

        ExtractedData extractedData();

        void setExtractedData(ExtractedData extractedData);

        void showExcelProcessingError();

        void showExcelValidationError(String errorMessage);

        void reenableLaunchButton();

        void error(String message);
    }

    interface ExcelLoader {
        ExtractedData load(String excelPath, PerformLists performLists) throws Exception;

        ExtractedData ensureEmptyDataRow(ExtractedData extractedData);

        boolean hasExcelError(ExtractedData extractedData);
    }

    private record ScannerPreLaunchExcelLoaderAdapter(ScannerPreLaunchExcelLoader excelLoader) implements ExcelLoader {
        @Override
        public ExtractedData load(String excelPath, PerformLists performLists) throws Exception {
            return excelLoader.load(excelPath, performLists);
        }

        @Override
        public ExtractedData ensureEmptyDataRow(ExtractedData extractedData) {
            return excelLoader.ensureEmptyDataRow(extractedData);
        }

        @Override
        public boolean hasExcelError(ExtractedData extractedData) {
            return excelLoader.hasExcelError(extractedData);
        }
    }
}
