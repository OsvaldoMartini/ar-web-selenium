package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARExecution;
import com.allinweb.ch.util.ExtractedData;
import java.util.List;

final class ScannerPreLaunchMultipleRowsConfirmation {
    private final ExcelLoader excelLoader;
    private final Operations operations;

    ScannerPreLaunchMultipleRowsConfirmation(ScannerPreLaunchExcelLoader excelLoader, Operations operations) {
        this(new ScannerPreLaunchExcelLoaderAdapter(excelLoader), operations);
    }

    ScannerPreLaunchMultipleRowsConfirmation(ExcelLoader excelLoader, Operations operations) {
        this.excelLoader = excelLoader;
        this.operations = operations;
    }

    boolean confirm() {
        if (!excelLoader.requiresMultipleRowsConfirmation(
                operations.extractedData(), operations.excelDataGoto())) {
            return true;
        }

        operations.warn("Multiple Excel Rows Detected: each next row will return to first block");
        ARExecution.DialogModal response = operations.showMultipleRowsConfirmation();
        if (response == ARExecution.DialogModal.STOP) {
            operations.requestIntercept();
            operations.markNotRunning();
            operations.reenableLaunchButton();
            operations.lastBrowserTab();
            return false;
        }
        return true;
    }

    interface Operations {
        ExtractedData extractedData();

        List<InstructionLoad> excelDataGoto();

        ARExecution.DialogModal showMultipleRowsConfirmation();

        void requestIntercept();

        void markNotRunning();

        void reenableLaunchButton();

        boolean lastBrowserTab();

        void warn(String message);
    }

    interface ExcelLoader {
        boolean requiresMultipleRowsConfirmation(ExtractedData extractedData, List<InstructionLoad> excelDataGoto);
    }

    private record ScannerPreLaunchExcelLoaderAdapter(ScannerPreLaunchExcelLoader excelLoader) implements ExcelLoader {
        @Override
        public boolean requiresMultipleRowsConfirmation(
                ExtractedData extractedData, List<InstructionLoad> excelDataGoto) {
            return excelLoader.requiresMultipleRowsConfirmation(extractedData, excelDataGoto);
        }
    }
}
