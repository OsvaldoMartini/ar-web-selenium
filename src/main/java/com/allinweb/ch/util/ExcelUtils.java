package com.allinweb.ch.util;

import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.google.common.base.Strings;
import java.io.*;
import java.util.*;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Slf4j
public class ExcelUtils {
    private static final int FIRST_ROW = 0;
    private static final int SECOND_ROW = 1;
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();

    public ExcelUtils() {}

    public static void createExcelDataFile(BotJobLoadDTO selectedBotJob, String nameToDuplicate) {
        if (selectedBotJob == null) {
            log.error("Not Able to Create an Excel File.The selected Bot Job s null");
            performMessage.errorMessage(
                    "Not Able to Create an Excel File",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to create an Excel file!</span>",
                    "<span style='color: #E65100; font-weight: bold;'>No Bot Job Found</span>",
                    "<span style='font-style: italic;'>Bot-Job List is empty!</span>",
                    null,
                    0);
            return;
        }

        String excelFolderPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
        String fileName = String.format(
                "%s/%s%s", excelFolderPath, selectedBotJob.getName().trim(), ARConstants.FILE_FORMAT_EXCEL);

        File fileCheck = new File(fileName);

        // Load blocks and actions
        ErrorMessage errorMessage = null;
        if (performLists.getListBlock().isEmpty()) {
            errorMessage = performDataBase.loadBlocks(selectedBotJob.getId(), selectedBotJob.getName(), "block");
        }
        if (errorMessage == null && performLists.getAllActions().isEmpty()) {
            errorMessage = performDBEngine.loadAllActionsPerBlock(performLists.getListBlock());
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        // Check if the Excel file already exists
        ExtractedData extractedData = ExcelUtils.isFileExists(selectedBotJob.getName(), performLists.getAllActions());
        if (extractedData != null && !Strings.isNullOrEmpty(extractedData.getErrorMessage())) {
            return;
        }

        if (!fileCheck.exists() || fileCheck.isDirectory()) {
            // File does not exist or is a directory → create normally
            new Thread(
                            () -> new ExcelUtils()
                                    .generateExcelFiles(
                                            extractedData, selectedBotJob.getName(), nameToDuplicate, false),
                            "excel-data-file-create")
                    .start();
        } else if (fileCheck.exists() && nameToDuplicate != null && !nameToDuplicate.isBlank()) {
            // File exists, but nameToDuplicate is provided → create new Excel with new name
            new Thread(
                            () -> new ExcelUtils().generateExcelFiles(extractedData, nameToDuplicate, null, false),
                            "excel-data-file-duplicate")
                    .start();
        }
    }

    public static ExtractedData isFileExists(String botJobName, List<String> allActions) {
        return isFileExists(botJobName, allActions, buildAliasMap(performLists.getListBlock()));
    }

    public static ExtractedData isFileExists(
            String botJobName, List<String> allActions, Map<String, String> aliasOfCanonical) {

        String excelFolderPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
        String fileName = String.format("%s/%s%s", excelFolderPath, botJobName, ARConstants.FILE_FORMAT_EXCEL);

        File fileCheck = new File(fileName);
        if (fileCheck.exists() && !fileCheck.isDirectory()) {

            ExcelReader excelReader = new ExcelReader();
            try {
                return excelReader.extractData(fileName, allActions, aliasOfCanonical);
            } catch (Exception e) {
                log.error("Unable to read the existing Excel file: {}", fileName, e);
                performMessage.errorMessage(
                        "Excel File Error", "Check All Excel Columns and Values!", null, null, null, 0);
                ExtractedData failure = new ExtractedData();
                failure.setErrorTitle("Excel File Error");
                failure.setErrorMessage("The existing Excel file could not be read. Close the file and verify its columns before generating it again.");
                return failure;
            }
        } else {
            return null;
        }
    }

    /**
     * Build a canonical-name -> clientNamed alias map for INPUT instructions across the given
     * blocks. Only entries with a non-blank clientNamed are included. Used by the Excel reader
     * so the missing-fields warning doesn't false-positive when an instruction has been
     * renamed in the React grid.
     *
     * <p>Side effect: calls {@code performDataBase.loadInstructions(...)} per block, which
     * mutates {@code performLists.getListInstruction()}. Callers that re-load instructions
     * later (e.g. {@code generateUnfilteredExcelFile}) are unaffected because they re-issue
     * their own load.
     */
    public static Map<String, String> buildAliasMap(List<BlockLoadDTO> blocks) {
        Map<String, String> aliasMap = new HashMap<>();
        if (blocks == null || blocks.isEmpty()) return aliasMap;

        for (BlockLoadDTO block : blocks) {
            ErrorMessage instructionError =
                    performDataBase.loadInstructions(block.getBotJobId(), block.getId(), -1, "instruction");
            if (instructionError != null) {
                throw new IllegalStateException(excelErrorText("Unable to load Excel instructions", instructionError));
            }
            List<InstructionLoad> instructions = performLists.getListInstruction();
            if (instructions == null) continue;
            for (InstructionLoad instr : instructions) {
                if (!Objects.equals(instr.getBlockId(), block.getId())) continue;
                if (instr.getActions() == null) continue;
                if (!instr.getActions().contains(ARConstants.INSERT + ":")) continue;
                String canonical = instr.getName();
                String alias = instr.getClientNamed();
                if (canonical != null && alias != null && !alias.isBlank()) {
                    aliasMap.put(canonical, alias);
                }
            }
        }
        return aliasMap;
    }

    public static void writeCsvFile(String fileName, List<BlockLoadDTO> blockDTOList) {
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter(fileName);

            // Write the CSV file header
            fileWriter.append("name");
            fileWriter.append("\n");

            // Write a new blockDTO object list to the CSV file
            for (BlockLoadDTO blockDTO : blockDTOList) {
                fileWriter.append(blockDTO.getName());
                fileWriter.append("\n");
            }

            log.info("CSV file was created successfully!");

        } catch (Exception e) {
            log.info("Error in CsvFileWriter!");
            log.info(e.getMessage());
        } finally {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                log.info("Error while flushing/closing fileWriter!");
                log.info(e.getMessage());
            }
        }
    }

    public File generateExcelFiles(
            ExtractedData extractedData, String newFileName, String nameToDuplicate, boolean openExcel) {
        File excelFolder = ExcelFileStore.requireOutputDirectory(
                arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL));
        //        generateUnfilteredCSVFile(botJob);
        File file = generateUnfilteredExcelFile(extractedData, newFileName, nameToDuplicate, excelFolder);

        if (openExcel) {
            log.info("Excel file ready: {}", file.getAbsolutePath());
        }
        return file;
    }

    private void generateUnfilteredCSVFile(BotJobLoadDTO botJob) {
        String fileName = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL) + "/" + botJob.getName()
                + ARConstants.FILE_FORMAT_CSV;

        BufferedWriter bufferedWriter = null;
        File file = new File(fileName);
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(file));

            BotJobLoadDTO botJobLoadDTO = performLists.getQuickBotJobById(botJob.getId());

            Set<String> fieldAddedSet = new HashSet<>();

            for (BlockLoadDTO block : botJobLoadDTO.getBlockLoadDTOList()) {
                String firstRow = "#" + block.getName();
                bufferedWriter.write(firstRow);
                bufferedWriter.newLine();

                //                List<InstructionLoad> instructionList = PerformDataBase.
                //                        .getEntityList(
                //                                InstructionLoad.class,
                //
                // Comparator.comparingInt(InstructionLoad::getInstructionOrderNumber),
                //                                (instruction) -> instruction.getBlockId().equals(block.getId())
                //                                        && instruction.getActions().contains(ARConstants.INSERT));

                performDataBase.loadInstructions(block.getBotJobId(), block.getId(), -1, "instruction");
                List<InstructionLoad> allInstructions = performLists.getListInstruction();

                List<InstructionLoad> instructionList = new ArrayList<>();

                for (InstructionLoad instruction : allInstructions) {
                    if (instruction.getBlockId().equals(block.getId())
                            && instruction.getActions().contains(ARConstants.INSERT)) {
                        instructionList.add(instruction);
                    }
                }

                Integer last = instructionList.size();
                for (InstructionLoad instruction : instructionList) {
                    String action = instruction.getActions();
                    boolean hasReference = action.contains(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                    if (hasReference) {
                        String reference = action.split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)[1];
                        if (!fieldAddedSet.contains(reference)) {
                            fieldAddedSet.add(reference);
                            bufferedWriter.write(action.split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)[1]);
                            last--;
                            if (last > 0) {
                                bufferedWriter.write(",");
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.info("Error in CsvFileWriter!");
            log.info(e.getMessage());
        } finally {
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.flush();
                    bufferedWriter.close();
                }
            } catch (IOException e) {
                log.info("Error while flushing/closing bufferedWriter!");
                log.info(e.getMessage());
            }
        }

        log.info("CSV file ready: {}", file.getAbsolutePath());
    }

    private File generateUnfilteredExcelFile(
            ExtractedData extractedData, String newFileName, String nameToDuplicate, File excelFolder) {
        File file = new File(excelFolder, newFileName + ARConstants.FILE_FORMAT_EXCEL);

        boolean duplicate = false;
        if (!Strings.isNullOrEmpty(nameToDuplicate)) {
            duplicate = true;
        }

        File fileDuplica = null;
        if (duplicate) {
            fileDuplica = new File(excelFolder, nameToDuplicate + ARConstants.FILE_FORMAT_EXCEL);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet spreadsheet = workbook.createSheet();
            Row blockNameRow = spreadsheet.createRow(FIRST_ROW);
            Row instructionFieldRow = spreadsheet.createRow(SECOND_ROW);
            int currentIndex = 0;

            if (!performLists.getListBlock().isEmpty()) {

                for (BlockLoadDTO block : performLists.getListBlock()) {
                // Per-block dedup: same canonical/displayKey across blocks must each get their
                // own column. Reset on every block so the column is keyed by (block, header).
                Set<String> fieldAddedInBlock = new HashSet<>();

                Cell blockNameCell = blockNameRow.createCell(currentIndex, CellType.STRING);
                blockNameCell.setCellValue("#" + block.getName());

                List<InstructionLoad> filteredInstructions = new ArrayList<>();

                ErrorMessage instructionError =
                        performDataBase.loadInstructions(block.getBotJobId(), block.getId(), -1, "instruction");
                if (instructionError != null) {
                    throw new IllegalStateException(excelErrorText("Unable to load Excel instructions", instructionError));
                }
                List<InstructionLoad> allInstructions = performLists.getListInstruction();

                for (InstructionLoad instruction : allInstructions) {
                    if (Objects.equals(instruction.getBlockId(), block.getId())
                            && instruction.getActions().contains(ARConstants.INSERT + ":")) {
                        filteredInstructions.add(instruction);
                    }
                }

                filteredInstructions.sort(Comparator.comparingInt(instruction ->
                        instruction.getInstructionOrderNumber() != null ? instruction.getInstructionOrderNumber() : 0));

                for (InstructionLoad instruction : filteredInstructions) {
                    String action = instruction.getActions();
                    boolean hasReference = action.contains(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                    if (!hasReference) continue;

                    // Header text the user sees and the lookup key both writer/reader use.
                    // Falls back to instruction.name when clientNamed is unset.
                    String header = instruction.displayKey();
                    if (header == null || header.isBlank()) {
                        // Defensive: instruction with no name AND no clientNamed — skip rather
                        // than create a blank column.
                        continue;
                    }

                    if (fieldAddedInBlock.contains(header)) continue;
                    fieldAddedInBlock.add(header);

                    Cell instructionFieldCell = instructionFieldRow.createCell(currentIndex, CellType.STRING);
                    instructionFieldCell.setCellValue(header);

                    int DYNAMIC_ROW = SECOND_ROW + 1;

                    if (extractedData != null && extractedData.getNumberOfDataRows() != null) {
                        // getNumberOfDataRows() is 0 for a headers-only file (the reader no
                        // longer synthesizes a CHANGE ME row). At least one data row must be
                        // written or new columns get a header with no CHANGE ME placeholder;
                        // existing values still win via the per-row lookups below.
                        int totalRows = Math.max(1, extractedData.getNumberOfDataRows());
                        for (int i = 0; i < totalRows; i++) {
                            Row belowRow = spreadsheet.getRow(DYNAMIC_ROW);
                            if (belowRow == null) {
                                belowRow = spreadsheet.createRow(DYNAMIC_ROW);
                            }

                            // Rename-preserving lookup. The Excel may have been written under
                            // (block, displayKey), but if the user just toggled clientNamed it
                            // could still be under (block, instruction.name). Cross-block flat
                            // lookup is the last resort for legacy files written before block
                            // scoping was real.
                            String value = extractedData.getFieldValue(block.getName(), header, i);
                            if (value == null) {
                                value = extractedData.getFieldValue(block.getName(), instruction.getName(), i);
                            }
                            if (value == null) {
                                Map<String, String> flatRow = extractedData.getRowFieldValues(i);
                                value = flatRow.get(header);
                                if (value == null) value = flatRow.get(instruction.getName());
                            }

                            Cell belowCell = belowRow.createCell(currentIndex, CellType.STRING);
                            belowCell.setCellValue(value != null ? value : "CHANGE ME");
                            DYNAMIC_ROW++;
                        }
                    } else {
                        Row belowRow = spreadsheet.getRow(DYNAMIC_ROW);
                        if (belowRow == null) {
                            belowRow = spreadsheet.createRow(DYNAMIC_ROW);
                        }
                        Cell belowCell = belowRow.createCell(currentIndex, CellType.STRING);
                        belowCell.setCellValue("CHANGE ME");
                    }

                    currentIndex++;
                }
            }
            } else {
                Cell blockNameCell = blockNameRow.createCell(currentIndex, CellType.STRING);
                blockNameCell.setCellValue("#" + newFileName + " default block");
            }

            // Auto-resize all columns after content is added
            for (int i = 0; i < currentIndex; i++) {
                spreadsheet.autoSizeColumn(i);
            }

            // Publish complete workbooks without truncating an existing file on write failure.
            writeExcelWorkbookOnDisk(workbook, file);
            if (duplicate && fileDuplica != null) {
                writeExcelWorkbookOnDisk(workbook, fileDuplica);
            }
            return file;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to close the generated Excel workbook", error);
        }
    }

    private void writeExcelWorkbookOnDisk(Workbook workbook, File file) {
        ExcelFileStore.writeAtomically(workbook, file);
    }

    private static String excelErrorText(String prefix, ErrorMessage error) {
        if (error == null) return prefix;
        if (!Strings.isNullOrEmpty(error.getErrorMessage())) return prefix + ": " + error.getErrorMessage();
        if (!Strings.isNullOrEmpty(error.getErrorHeader())) return prefix + ": " + error.getErrorHeader();
        return Strings.isNullOrEmpty(error.getErrorTitle()) ? prefix : prefix + ": " + error.getErrorTitle();
    }
}
