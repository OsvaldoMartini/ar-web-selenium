package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.util.ExtractedData;

final class ScannerTestRunExcelPreparation {
    private final ExcelLoader excelLoader;
    private final Operations operations;

    ScannerTestRunExcelPreparation(ScannerPreLaunchExcelLoader excelLoader, Operations operations) {
        this(new ScannerPreLaunchExcelLoaderAdapter(excelLoader), operations);
    }

    ScannerTestRunExcelPreparation(ExcelLoader excelLoader, Operations operations) {
        this.excelLoader = excelLoader;
        this.operations = operations;
    }

    Result prepare(String excelPath, PerformLists performLists) {
        ExtractedData extractedData = null;
        Exception loadError = null;
        try {
            extractedData = excelLoader.load(excelPath, performLists);
        } catch (Exception error) {
            loadError = error;
        }

        extractedData = excelLoader.ensureEmptyDataRow(extractedData);
        operations.setExtractedData(extractedData);
        return new Result(loadError);
    }

    record Result(Exception loadError) {
        boolean usedSyntheticFallback() {
            return loadError != null;
        }
    }

    interface Operations {
        void setExtractedData(ExtractedData extractedData);
    }

    interface ExcelLoader {
        ExtractedData load(String excelPath, PerformLists performLists) throws Exception;

        ExtractedData ensureEmptyDataRow(ExtractedData extractedData);
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
    }
}
