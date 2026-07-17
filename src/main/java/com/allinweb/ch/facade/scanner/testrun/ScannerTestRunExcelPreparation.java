package com.allinweb.ch.facade.scanner.testrun;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExcelLoader;
import com.allinweb.ch.util.ExtractedData;

public final class ScannerTestRunExcelPreparation {
    private final ExcelLoader excelLoader;
    private final Operations operations;

    public ScannerTestRunExcelPreparation(ScannerPreLaunchExcelLoader excelLoader, Operations operations) {
        this(new ScannerPreLaunchExcelLoaderAdapter(excelLoader), operations);
    }

    public ScannerTestRunExcelPreparation(ExcelLoader excelLoader, Operations operations) {
        this.excelLoader = excelLoader;
        this.operations = operations;
    }

    public Result prepare(String excelPath, PerformLists performLists) {
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

    public record Result(Exception loadError) {
        public boolean usedSyntheticFallback() {
            return loadError != null;
        }
    }

    public interface Operations {
        void setExtractedData(ExtractedData extractedData);
    }

    public interface ExcelLoader {
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
