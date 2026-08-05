package com.allinweb.ch.facade.scanner.prelaunch;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.util.ExcelUtils;
import com.allinweb.ch.util.ExtractedData;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScannerPreLaunchExcelLoader {
    private final ExcelExecutionDatasetRegistry datasets;

    public ScannerPreLaunchExcelLoader() {
        this(ExcelExecutionDatasetRegistry.getInstance());
    }

    ScannerPreLaunchExcelLoader(ExcelExecutionDatasetRegistry datasets) {
        this.datasets = datasets;
    }

    public ExtractedData load(String excelPath, PerformLists performLists) throws Exception {
        Path workbook = requireWorkbook(excelPath);
        if (!datasets.isExecutionEnabled(workbook)) return new ExtractedData();
        if (Files.notExists(workbook)) {
            String workbookName = workbookName(workbook);
            File generated = new ExcelUtils().generateExcelFiles(null, workbookName, null, false);
            Path generatedPath = generated.toPath().toAbsolutePath().normalize();
            if (!generatedPath.equals(workbook)) {
                throw new IllegalStateException(
                        "Generated Excel data file does not match the active Bot Job: " + generatedPath);
            }
            log.info("Created missing Excel data file for active Bot Job: {}", generatedPath);
        }
        return datasets
                .load(
                        workbook,
                        () -> new ExcelReader()
                                .extractData(
                                        workbook.toString(),
                                        performLists.getAllActions(),
                                        ExcelUtils.buildAliasMap(performLists.getListBlock())))
                .data();
    }

    /** Releases the retained dataset only when its owning client explicitly closes it. */
    public boolean close(String excelPath) {
        return datasets.close(requireWorkbook(excelPath));
    }

    /** Replaces the shared working dataset without writing the workbook to disk. */
    public ExtractedData replaceInMemory(String excelPath, ExtractedData data) {
        return datasets.replace(requireWorkbook(excelPath), data).data();
    }

    public void setExecutionEnabled(String excelPath, boolean enabled) {
        datasets.setExecutionEnabled(requireWorkbook(excelPath), enabled);
    }

    private Path requireWorkbook(String excelPath) {
        if (excelPath == null || excelPath.isBlank()) {
            throw new IllegalArgumentException("Excel data file path is required");
        }
        Path workbook = Path.of(excelPath).toAbsolutePath().normalize();
        if (Files.exists(workbook) && !Files.isRegularFile(workbook)) {
            throw new IllegalStateException("Excel data file path is not a file: " + workbook);
        }
        return workbook;
    }

    private String workbookName(Path workbook) {
        String fileName = workbook.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Excel data file must use the .xlsx extension: " + workbook);
        }
        String workbookName = fileName.substring(0, fileName.length() - ".xlsx".length()).trim();
        if (workbookName.isEmpty()) {
            throw new IllegalArgumentException("Excel data file must use the active Bot Job name");
        }
        return workbookName;
    }

    public ExtractedData ensureEmptyDataRow(ExtractedData extractedData) {
        ExtractedData data = extractedData == null ? new ExtractedData() : extractedData;
        if (data.getNumberOfDataRows() == null || data.getNumberOfDataRows() == 0) {
            data.addField("$EMPTY");
            data.addFieldValue("$EMPTY", "$EMPTY", 0);
        }
        return data;
    }

    public boolean hasExcelError(ExtractedData extractedData) {
        return extractedData != null && extractedData.getErrorMessage() != null;
    }

    public boolean requiresMultipleRowsConfirmation(ExtractedData extractedData, List<InstructionLoad> excelDataGoto) {
        return extractedData.getNumberOfDataRows() != null
                && extractedData.getNumberOfDataRows() > 1
                && excelDataGoto.isEmpty();
    }
}
