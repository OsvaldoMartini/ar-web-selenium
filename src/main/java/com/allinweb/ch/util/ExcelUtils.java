package com.allinweb.ch.util;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.scene.ARAlertScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelUtils {
    private static final int FIRST_ROW = 0;
    private static final int SECOND_ROW = 1;

    public ExcelUtils() {}

    private List<BotJobLoadDTO> botLoadJobs = new ArrayList<>();
    private static List<BlockLoadDTO> blocksLoaded;
    private ExtractedData extractedData;
    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;

    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    public void generateExcelFiles(
            List<BotJobLoadDTO> botLoadJobs, List<String> allActions, ExtractedData extractedData, boolean openExcel) {

        BotJobLoadDTO botJobLoad = botLoadJobs.get(0);
        this.blocksLoaded = botLoadJobs.get(0).getBlockLoadDTOList();
        this.extractedData = extractedData;

        File excelFolder = new File(ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL));
        if (!excelFolder.exists()) {
            excelFolder.mkdirs();
        }
        //        generateUnfilteredCSVFile(botJob);
        File file = generateUnfilteredExcelFile(botJobLoad, allActions, extractedData);

        if (openExcel) {
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
        generateFilteredExcelFile(botJobLoad, extractedData);
    }

    private void generateUnfilteredCSVFile(BotJobDTO botJob) {
        String fileName = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL) + "/"
                + botJob.getName() + ARConstants.FILE_FORMAT_CSV;

        BufferedWriter bufferedWriter = null;
        File file = new File(fileName);
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(file));

            List<BotJobLoadDTO> lisBotJobBlocks = performDataBase.loadBotJobAndBlocks(botJob.getId());

            Set<String> fieldAddedSet = new HashSet<>();

            for (BlockLoadDTO block : lisBotJobBlocks.get(0).getBlockLoadDTOList()) {
                String firstRow = "#" + block.getName();
                bufferedWriter.write(firstRow);
                bufferedWriter.newLine();

                //                List<InstructionLoadDTO> instructionList = ARSharedResources.getInstance()
                //                        .getEntityList(
                //                                InstructionLoadDTO.class,
                //
                // Comparator.comparingInt(InstructionLoadDTO::getInstructionOrderNumber),
                //                                (instruction) -> instruction.getBlockId().equals(block.getId())
                //                                        && instruction.getActions().contains(ARConstants.INSERT));

                List<InstructionLoadDTO> allInstructions =
                        performDataBase.getInstructionsByBlockId(block.getBotJobId(), block.getId());

                List<InstructionLoadDTO> instructionList = new ArrayList<>();

                for (InstructionLoadDTO instruction : allInstructions) {
                    if (instruction.getBlockId().equals(block.getId())
                            && instruction.getActions().contains(ARConstants.INSERT)) {
                        instructionList.add(instruction);
                    }
                }

                Integer last = instructionList.size();
                for (InstructionLoadDTO instruction : instructionList) {
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
            System.out.println("Error in CsvFileWriter!");
            System.out.println(e.getMessage());
        } finally {
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.flush();
                    bufferedWriter.close();
                }
            } catch (IOException e) {
                System.out.println("Error while flushing/closing bufferedWriter!");
                System.out.println(e.getMessage());
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

    private File generateUnfilteredExcelFile(
            BotJobLoadDTO botJobLoad, List<String> allActions, ExtractedData extractedData) {
        String fileName = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL) + "/"
                + botJobLoad.getName() + ARConstants.FILE_FORMAT_EXCEL;

        File file = new File(fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        List<BlockLoadDTO> blockList = botJobLoad.getBlockLoadDTOList();

        Set<String> fieldAddedSet = new HashSet<>();

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet();
        Row blockNameRow = spreadsheet.createRow(FIRST_ROW);
        Row instructionFieldRow = spreadsheet.createRow(SECOND_ROW);
        int currentIndex = 0;

        if (blockList.size() > 0) {

            for (BlockLoadDTO block : blockList) {
                Cell blockNameCell = blockNameRow.createCell(currentIndex, CellType.STRING);
                blockNameCell.setCellValue("#" + block.getName());

                List<InstructionLoadDTO> InstructionLoadDTO =
                        performDataBase.getInstructionsByBlockId(block.getBotJobId(), block.getId());

                // List to store the filtered instructions
                List<InstructionLoadDTO> filteredInstructions = new ArrayList<>();

                // Retrieve all instructions for the block
                List<InstructionLoadDTO> allInstructions =
                        performDataBase.getInstructionsByBlockId(block.getBotJobId(), block.getId());

                // Iterate over the instructions and apply filtering manually
                for (InstructionLoadDTO instruction : allInstructions) {
                    if (instruction.getBlockId() == block.getId()
                            && instruction.getActions().contains(ARConstants.INSERT + ":")) {
                        filteredInstructions.add(instruction);
                    }
                }

                // Sort the filtered list by instruction order number
                filteredInstructions.sort(Comparator.comparingInt(instruction ->
                        instruction.getInstructionOrderNumber() != null ? instruction.getInstructionOrderNumber() : 0));

                //                // Filter the list based on the block ID and action condition
                //                List<InstructionLoadDTO> filteredInstructions = InstructionLoadDTO.stream()
                //                        .filter(instruction -> instruction.getBlockId() == block.getId()
                //                                && instruction.getActions().contains(ARConstants.INSERT + ":"))
                //
                // .sorted(Comparator.comparingInt(InstructionLoadDTO::getInstructionOrderNumber))
                //                        .collect(Collectors.toList());

                for (InstructionLoadDTO instruction : filteredInstructions) {
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
                                        belowCell.setCellValue("No Data Found"); // Fallback message if value is missing
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
                                belowCell.setCellValue("No Data Found"); // Fallback message if value is missing
                            }

                            currentIndex++;
                        }
                    }
                }
            }
        } else {
            Cell blockNameCell = blockNameRow.createCell(currentIndex, CellType.STRING);
            blockNameCell.setCellValue("#" + botJobLoad.getName() + " default block");
        }

        // Auto-resize all columns after content is added
        for (int i = 0; i < currentIndex; i++) {
            spreadsheet.autoSizeColumn(i);
        }

        // Write the workbook to the file
        writeExcelWorkbookOnDisk(workbook, file);
        return file;
    }

    private void generateFilteredExcelFile(BotJobLoadDTO botJobLoadDTO, ExtractedData extractedData) {
        String fileName = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL) + "/"
                + botJobLoadDTO.getName() + ARConstants.DEFAULT_FILENAME_FOR_AR + ARConstants.FILE_FORMAT_EXCEL;

        File file = new File(fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        Set<String> fieldSet = new HashSet<>();
        if (botJobLoadDTO.getBlockLoadDTOList().size() > 0) {
            fieldSet = botJobLoadDTO.getBlockLoadDTOList().stream()
                    .map(BlockLoadDTO::getInstructionLoadDTOS)
                    .reduce((identity, accumulated) -> {
                        accumulated.addAll(identity);
                        return accumulated;
                    })
                    .get()
                    .stream()
                    .filter(InstructionLoadDTO::getExportToABR)
                    .map(InstructionLoadDTO::getActions)
                    .filter(action -> action.contains(ARConstants.INSERT))
                    .map(action -> action.split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)[1])
                    .collect(Collectors.toSet());
        }

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet();
        Row instructionFieldRow = spreadsheet.createRow(FIRST_ROW);
        int i = 0;
        for (String field : fieldSet) {
            Cell cell = instructionFieldRow.createCell(i++, CellType.STRING);
            cell.setCellValue(field);
        }
        writeExcelWorkbookOnDisk(workbook, file);
    }

    private void writeExcelWorkbookOnDisk(Workbook workbook, File file) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            workbook.write(fileOutputStream);
            fileOutputStream.close();
        } catch (IOException e) {
            new ARAlertScene(
                    Alert.AlertType.ERROR,
                    "Excel file generation failed",
                    "There was a problem with the excel file generation. Reason: " + e,
                    ButtonType.OK);
        }
    }

    public static ExtractedData isFileExists(String botJobName, List<String> allActions) {

        String excelFolderPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL);
        String fileName = String.format("%s/%s%s", excelFolderPath, botJobName, ARConstants.FILE_FORMAT_EXCEL);

        //        List<BlockLoadDTO> blocksLoaded = botLoadJobs.get(0).getBlockLoadDTOList();

        // Create a File object
        File fileCheck = new File(fileName);
        if (fileCheck.exists() && !fileCheck.isDirectory()) {

            File file = new File(fileName);

            // Assuming blocksLoaded is your List<BlockLoadDTO>
            //            List<String> allActions = blocksLoaded.stream()
            //                    .flatMap(
            //                            blockLoadDTO -> blockLoadDTO
            //                                    .getBlockLoopInstructionLoadDTOS()
            //                                    .stream()) // Flatten the stream of BlockLoopInstructionLoadDTO
            //                    .map(BlockLoopInstructionLoadDTO::getActions) // Extract the actions
            //                    .collect(Collectors.toList()); // Collect all actions into a List

            ExcelReader excelReader = new ExcelReader();
            ExtractedData extractedData = null;
            try {
                extractedData = excelReader.extractData(fileName, allActions);
            } catch (Exception e) {

                Text variableText1Styled = new Text("Verify the Possible Errors:");
                variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                Text variableText2Styled = new Text("1. Excel File is OPEN");
                variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                Text variableText3Styled = new Text("2. Column Names Different from INPUT names");
                variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                Text variableText4Styled = new Text("3. INPUTS names Not In Excel File");
                variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                VBox combinedTextContainer = new VBox();
                combinedTextContainer.setSpacing(5); // Add some sp

                combinedTextContainer
                        .getChildren()
                        .addAll(variableText1Styled, variableText2Styled, variableText3Styled, variableText4Styled);

                performMessage.showAlertCombinedVBOX(
                        Alert.AlertType.ERROR,
                        "Excel File Error",
                        "Check All Excel Columns and Values!",
                        null,
                        combinedTextContainer);
                return null;
                //            Platform.exit();
            }

            return extractedData;
        } else {
            // Return false if the directory does not exist
            return null;
        }
    }

    public static void writeCsvFile(String fileName, List<BlockDTO> blockDTOList) {
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter(fileName);

            // Write the CSV file header
            fileWriter.append("name");
            fileWriter.append("\n");

            // Write a new blockDTO object list to the CSV file
            for (BlockDTO blockDTO : blockDTOList) {
                fileWriter.append(blockDTO.getName());
                fileWriter.append("\n");
            }

            System.out.println("CSV file was created successfully!");

        } catch (Exception e) {
            System.out.println("Error in CsvFileWriter!");
            System.out.println(e.getMessage());
        } finally {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                System.out.println("Error while flushing/closing fileWriter!");
                System.out.println(e.getMessage());
            }
        }
    }
}
