package com.allinweb.ch.util;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionLoad;
import com.allinweb.ch.component.scene.ARAlertScene;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.google.common.base.Strings;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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

        if (!fileCheck.exists() || fileCheck.isDirectory()) {
            // File does not exist or is a directory → create normally
            Task<Void> excelTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    new ExcelUtils()
                            .generateExcelFiles(extractedData, selectedBotJob.getName(), nameToDuplicate, false);
                    return null;
                }
            };
            new Thread(excelTask).start();
        } else if (fileCheck.exists() && nameToDuplicate != null && !nameToDuplicate.isBlank()) {
            // File exists, but nameToDuplicate is provided → create new Excel with new name
            Task<Void> excelTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    new ExcelUtils().generateExcelFiles(extractedData, nameToDuplicate, null, false);
                    return null;
                }
            };
            new Thread(excelTask).start();
        }
    }

    public static ExtractedData isFileExists(String botJobName, List<String> allActions) {

        String excelFolderPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
        String fileName = String.format("%s/%s%s", excelFolderPath, botJobName, ARConstants.FILE_FORMAT_EXCEL);

        // Create a File object
        File fileCheck = new File(fileName);
        if (fileCheck.exists() && !fileCheck.isDirectory()) {

            File file = new File(fileName);

            ExcelReader excelReader = new ExcelReader();
            ExtractedData extractedData = null;
            try {
                extractedData = excelReader.extractData(fileName, allActions);
            } catch (Exception e) {
                log.error("Excel File Error. Check All Excel Columns and Values!");
                performMessage.errorMessage(
                        "Excel File Error", "Check All Excel Columns and Values!", null, null, null, 0);
                return null;
            }

            return extractedData;
        } else {
            // Return false if the directory does not exist
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
                fileWriter.flush();
                fileWriter.close();
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
            excelFolder.mkdirs();
        }
        //        generateUnfilteredCSVFile(botJob);
        File file = generateUnfilteredExcelFile(extractedData, newFileName, nameToDuplicate);

        if (openExcel) {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException error) {
                //                new ARAlertScene(
                //                        Alert.AlertType.ERROR,
                //                        "Couldn't open the file",
                //                        "The file could not be opened. Reason: " + e,
                //                        ButtonType.OK);
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

        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            new ARAlertScene(
                    Alert.AlertType.ERROR,
                    "Couldn't open the file",
                    "The file could not be opened. Reason: " + e,
                    ButtonType.OK);
        }
    }

    private File generateUnfilteredExcelFile(ExtractedData extractedData, String newFileName, String nameToDuplicate) {
        String fileName = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL) + "/" + newFileName
                + ARConstants.FILE_FORMAT_EXCEL;

        File file = new File(fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            log.info(e.getMessage());
        }

        boolean duplicate = false;
        if (!Strings.isNullOrEmpty(nameToDuplicate)) {
            duplicate = true;
        }

        File fileDuplica = null;
        if (duplicate) {
            String fileNameDuplica = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL) + "/" + nameToDuplicate
                    + ARConstants.FILE_FORMAT_EXCEL;
            fileDuplica = new File(fileNameDuplica);
            try {
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

                // List to store the filtered instructions
                List<InstructionLoad> filteredInstructions = new ArrayList<>();

                // Retrieve all instructions for the block

                performDataBase.loadInstructions(block.getBotJobId(), block.getId(), -1, "instruction");
                List<InstructionLoad> allInstructions = performLists.getListInstruction();

                // Iterate over the instructions and apply filtering manually
                for (InstructionLoad instruction : allInstructions) {
                    if (Objects.equals(instruction.getBlockId(), block.getId())
                            && instruction.getActions().contains(ARConstants.INSERT + ":")) {
                        filteredInstructions.add(instruction);
                    }
                }

                // Sort the filtered list by instruction order number
                filteredInstructions.sort(Comparator.comparingInt(instruction ->
                        instruction.getInstructionOrderNumber() != null ? instruction.getInstructionOrderNumber() : 0));

                //                // Filter the list based on the block ID and action condition
                //                List<InstructionLoad> filteredInstructions = InstructionLoad.stream()
                //                        .filter(instruction -> instruction.getBlockId() == block.getId()
                //                                && instruction.getActions().contains(ARConstants.INSERT + ":"))
                //
                // .sorted(Comparator.comparingInt(InstructionLoad::getInstructionOrderNumber))
                //                        .collect(Collectors.toList());

                for (InstructionLoad instruction : filteredInstructions) {
                    String action = instruction.getActions();
                    boolean hasReference = action.contains(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);

                    if (hasReference) {
                        // Split once and store the parts in an array
                        String[] parts = action.split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);

                        // Get the reference from the second part of the split action

                        String reference = parts[1];

                        if (parts[0].equals(ARConstants.INSERT) && parts[1].equals(ARConstants.ENTER)) {
                            reference = parts[2];
                        }

                        if (!fieldAddedSet.contains(reference)) {
                            fieldAddedSet.add(reference);

                            // Create cell for instruction field
                            Cell instructionFieldCell = instructionFieldRow.createCell(currentIndex, CellType.STRING);
                            instructionFieldCell.setCellValue(
                                    reference); // Use reference directly instead of re-splitting

                            int DYNAMIC_ROW = SECOND_ROW + 1;

                            if (extractedData != null) {
                                // Loop through extracted data rows
                                for (int i = 0; i < extractedData.getNumberOfDataRows(); i++) {
                                    Row belowRow = spreadsheet.getRow(DYNAMIC_ROW); // Get the row below
                                    if (belowRow == null) {
                                        belowRow = spreadsheet.createRow(DYNAMIC_ROW); // Create if it doesn't exist
                                    }

                                    Map<String, String> dataExcel = extractedData.getRowFieldValues(i);
                                    // Search for the "reference" in ExtractedData and set it in the below cell
                                    String valueFromExtractedData = dataExcel.get(reference); // Example row = 1
                                    Cell belowCell = belowRow.createCell(currentIndex, CellType.STRING);
                                    if (valueFromExtractedData != null) {
                                        belowCell.setCellValue(valueFromExtractedData);
                                    } else {
                                        belowCell.setCellValue("CHANGE ME"); // Fallback message if value is missing
                                    }
                                    DYNAMIC_ROW++;
                                }
                            } else {
                                // Fallback if extractedData is null
                                Row belowRow = spreadsheet.getRow(DYNAMIC_ROW); // Get the row below
                                if (belowRow == null) {
                                    belowRow = spreadsheet.createRow(DYNAMIC_ROW); // Create if it doesn't exist
                                }
                                Cell belowCell = belowRow.createCell(currentIndex, CellType.STRING);
                                belowCell.setCellValue("CHANGE ME"); // Fallback message if value is missing
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
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            workbook.write(fileOutputStream);
            fileOutputStream.close();
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
