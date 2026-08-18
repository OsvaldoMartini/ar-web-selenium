package com.allinweb.ch.facade.scanner.prelaunch;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARExecution;
import com.allinweb.ch.util.ExtractedData;
import java.util.List;

public final class ScannerPreLaunchMultipleRowsConfirmation {
    private final ExcelLoader excelLoader;
    private final Operations operations;

    public ScannerPreLaunchMultipleRowsConfirmation(ScannerPreLaunchExcelLoader excelLoader, Operations operations) {
        this(new ScannerPreLaunchExcelLoaderAdapter(excelLoader), operations);
    }

    public ScannerPreLaunchMultipleRowsConfirmation(ExcelLoader excelLoader, Operations operations) {
        this.excelLoader = excelLoader;
        this.operations = operations;
    }

    public boolean confirm() {
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

    public interface Operations {
        ExtractedData extractedData();

        List<InstructionLoad> excelDataGoto();

        ARExecution.DialogModal showMultipleRowsConfirmation();

        void requestIntercept();

        void markNotRunning();

        void reenableLaunchButton();

        boolean lastBrowserTab();

        void warn(String message);
    }

    public interface ExcelLoader {
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
