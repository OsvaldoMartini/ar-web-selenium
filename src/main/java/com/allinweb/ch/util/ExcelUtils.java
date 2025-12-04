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
import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
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
            log.error("Not Able to Create an Excel File. The selected Bot Job is null");
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

        if (!fileCheck.exists() || fileCheck.isDirectory()) {
            // File does not exist or is a directory → create normally
            Runnable excelTask = () -> {
                new ExcelUtils().generateExcelFiles(extractedData, selectedBotJob.getName(), nameToDuplicate, false);
            };
            new Thread(excelTask, "ExcelGen-" + selectedBotJob.getName()).start();
        } else if (fileCheck.exists() && nameToDuplicate != null && !nameToDuplicate.isBlank()) {
            // File exists, but nameToDuplicate is provided → create new Excel with new name
            Runnable excelTask = () -> {
                new ExcelUtils().generateExcelFiles(extractedData, nameToDuplicate, null, false);
            };
            new Thread(excelTask, "ExcelGen-" + nameToDuplicate).start();
        }
    }

    public static ExtractedData isFileExists(String botJobName, List<String> allActions) {
        String excelFolderPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
        String fileName = String.format("%s/%s%s", excelFolderPath, botJobName, ARConstants.FILE_FORMAT_EXCEL);

        File fileCheck = new File(fileName);
        if (fileCheck.exists() && !fileCheck.isDirectory()) {

            ExcelReader excelReader = new ExcelReader();
            ExtractedData extractedData;
            try {
                extractedData = excelReader.extractData(fileName, allActions);
            } catch (Exception e) {
                log.error("Excel File Error. Check All Excel Columns and Values! {}", e.getMessage());
                performMessage.errorMessage(
                        "Excel File Error", "Check All Excel Columns and Values!", null, null, null, 0);
                return null;
            }

            return extractedData;
        } else {
            // Return null if the file does not exist
            return null;
        }
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
                if (fileWriter != null) {
                    fileWriter.flush();
                    fileWriter.close();
                }
            } catch (IOException e) {
                log.info("Error while flushing/closing fileWriter!");
                log.info(e.getMessage());
            }
        }
    }

    public void generateExcelFiles(
            ExtractedData extractedData, String newFileName, String nameToDuplicate, boolean openExcel) {

        File excelFolder = new File(arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL));
        if (!excelFolder.exists()) {
            // noinspection ResultOfMethodCallIgnored
            excelFolder.mkdirs();
        }

        File file = generateUnfilteredExcelFile(extractedData, newFileName, nameToDuplicate);

        if (openExcel) {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException error) {
                log.error("Error: Excel File: {} - {}", file.getAbsolutePath(), error.getMessage());
                performMessage.errorMessage(
                        "Excel File Error",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Couldn't open the file!</span>",
                        "<span style='color: #E65100; font-weight: bold;'>File:</span> <span style='font-weight: bold;'>"
                                + file.getAbsolutePath() + "</span>",
                        "<span style='font-style: italic;'>The application was unable to access or read the file. It might be in use or you lack permissions.</span>",
                        "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                        0);
            }
        }
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
                            bufferedWriter.write(reference);
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

        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            log.error("Couldn't open the CSV file. Reason: {}", e.getMessage());
            performMessage.errorMessage(
                    "Couldn't open the file",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The file could not be opened.</span>",
                    "<span style='color: #E65100; font-weight: bold;'>File:</span> <span style='font-weight: bold;'>"
                            + file.getAbsolutePath() + "</span>",
                    "<span style='font-style: italic;'>Reason: " + e + "</span>",
                    null,
                    0);
        }
    }

    private File generateUnfilteredExcelFile(ExtractedData extractedData, String newFileName, String nameToDuplicate) {
        String fileName = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL) + "/" + newFileName
                + ARConstants.FILE_FORMAT_EXCEL;

        File file = new File(fileName);
        try {
            // noinspection ResultOfMethodCallIgnored
            file.createNewFile();
        } catch (IOException e) {
            log.info(e.getMessage());
        }

        boolean duplicate = !Strings.isNullOrEmpty(nameToDuplicate);

        File fileDuplica = null;
        if (duplicate) {
            String fileNameDuplica = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL) + "/" + nameToDuplicate
                    + ARConstants.FILE_FORMAT_EXCEL;
            fileDuplica = new File(fileNameDuplica);
            try {
                // noinspection ResultOfMethodCallIgnored
                fileDuplica.createNewFile();
            } catch (IOException e) {
                log.info(e.getMessage());
            }
        }

        Set<String> fieldAddedSet = new HashSet<>();

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet();
        Row blockNameRow = spreadsheet.createRow(FIRST_ROW);
        Row instructionFieldRow = spreadsheet.createRow(SECOND_ROW);
        int currentIndex = 0;

        if (!performLists.getListBlock().isEmpty()) {

            for (BlockLoadDTO block : performLists.getListBlock()) {
                Cell blockNameCell = blockNameRow.createCell(currentIndex, CellType.STRING);
                blockNameCell.setCellValue("#" + block.getName());

                List<InstructionLoad> filteredInstructions = new ArrayList<>();

                performDataBase.loadInstructions(block.getBotJobId(), block.getId(), -1, "instruction");
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

                    if (hasReference) {
                        String[] parts = action.split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                        String reference = parts[1];

                        if (parts[0].equals(ARConstants.INSERT) && parts[1].equals(ARConstants.ENTER)) {
                            reference = parts[2];
                        }

                        if (!fieldAddedSet.contains(reference)) {
                            fieldAddedSet.add(reference);

                            Cell instructionFieldCell = instructionFieldRow.createCell(currentIndex, CellType.STRING);
                            instructionFieldCell.setCellValue(reference);

                            int dynamicRow = SECOND_ROW + 1;

                            if (extractedData != null) {
                                for (int i = 0; i < extractedData.getNumberOfDataRows(); i++) {
                                    Row belowRow = spreadsheet.getRow(dynamicRow);
                                    if (belowRow == null) {
                                        belowRow = spreadsheet.createRow(dynamicRow);
                                    }

                                    Map<String, String> dataExcel = extractedData.getRowFieldValues(i);
                                    String valueFromExtractedData = dataExcel.get(reference);

                                    Cell belowCell = belowRow.createCell(currentIndex, CellType.STRING);
                                    if (valueFromExtractedData != null) {
                                        belowCell.setCellValue(valueFromExtractedData);
                                    } else {
                                        belowCell.setCellValue("CHANGE ME");
                                    }
                                    dynamicRow++;
                                }
                            } else {
                                Row belowRow = spreadsheet.getRow(dynamicRow);
                                if (belowRow == null) {
                                    belowRow = spreadsheet.createRow(dynamicRow);
                                }
                                Cell belowCell = belowRow.createCell(currentIndex, CellType.STRING);
                                belowCell.setCellValue("CHANGE ME");
                            }

                            currentIndex++;
                        }
                    }
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

        // Write the workbook to the file
        writeExcelWorkbookOnDisk(workbook, file);
        if (duplicate && fileDuplica != null) {
            writeExcelWorkbookOnDisk(workbook, fileDuplica);
        }
        return file;
    }

    private void writeExcelWorkbookOnDisk(Workbook workbook, File file) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            workbook.write(fileOutputStream);
        } catch (FileNotFoundException error) {
            log.error("Excel file generation failed. File not found: {}", error.getMessage());
            performMessage.errorMessage(
                    "Excel file generation failed",
                    "There was a problem with the excel file generation.",
                    "Reason: " + error.getMessage(),
                    null,
                    null,
                    0);
        } catch (IOException error) {
            log.error("Excel file generation failed. Error: {}", error.getMessage());
            performMessage.errorMessage(
                    "Excel file generation failed",
                    "There was a problem with the excel file generation.",
                    "Reason: " + error.getMessage(),
                    null,
                    null,
                    0);
        }
    }
}
