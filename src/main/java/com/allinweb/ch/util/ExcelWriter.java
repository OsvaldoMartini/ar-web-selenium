package com.allinweb.ch.util;

import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelWriter {
    private static final int FIRST_ROW = 0;
    private static final int SECOND_ROW = 1;

    public ExcelWriter() {}

    public void generateExcelFiles(BotJobDTO botJob) {
        File excelFolder = new File(ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL));
        if (!excelFolder.exists()) {
            excelFolder.mkdirs();
        }
        generateUnfilteredExcelFile(botJob);
        generateFilteredExcelFile(botJob);
    }

    private void generateUnfilteredExcelFile(BotJobDTO botJob) {
        String fileName = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL) + "/"
                + botJob.getName() + ABRConstants.FILE_FORMAT_EXCEL;

        File file = new File(fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<BlockDTO> blockList = botJob.getBlocks();

        Set<String> fieldAddedSet = new HashSet<>();

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet();
        Row blockNameRow = spreadsheet.createRow(FIRST_ROW);
        Row instructionFieldRow = spreadsheet.createRow(SECOND_ROW);
        int currentIndex = 0;
        for (BlockDTO block : blockList) {
            Cell blockNameCell = blockNameRow.createCell(currentIndex, CellType.STRING);
            blockNameCell.setCellValue("#" + block.getName());
            List<BlockLoopInstructionDTO> instructionList = ABRSharedResources.getInstance()
                    .getEntityList(
                            BlockLoopInstructionDTO.class,
                            Comparator.comparingInt(BlockLoopInstructionDTO::getInstructionOrderNumber),
                            (instruction) -> instruction.getBlock().getId() == block.getId()
                                    && instruction.getActions().contains(ABRConstants.INSERT));
            for (BlockLoopInstructionDTO instruction : instructionList) {
                String action = instruction.getActions();
                boolean hasReference = action.contains(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);
                if (hasReference) {
                    String reference = action.split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER)[1];
                    if (!fieldAddedSet.contains(reference)) {
                        fieldAddedSet.add(reference);
                        Cell instructionFieldCell = instructionFieldRow.createCell(currentIndex, CellType.STRING);
                        instructionFieldCell.setCellValue(action.split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER)[1]);
                        currentIndex++;
                    }
                }
            }
        }
        writeExcelWorkbookOnDisk(workbook, file);
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            new ABRAlertScene(
                    Alert.AlertType.ERROR,
                    "Couldn't open the file",
                    "The file could not be opened. Reason: " + e,
                    ButtonType.OK);
        }
    }

    private void generateFilteredExcelFile(BotJobDTO botJob) {
        String fileName = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL) + "/"
                + botJob.getName() + ABRConstants.DEFAULT_FILENAME_FOR_ABR + ABRConstants.FILE_FORMAT_EXCEL;

        File file = new File(fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Set<String> fieldSet = botJob.getBlocks().stream()
                .map(BlockDTO::getBlockLoopInstructions)
                .reduce((identity, accumulated) -> {
                    accumulated.addAll(identity);
                    return accumulated;
                })
                .get()
                .stream()
                .filter(BlockLoopInstructionDTO::getExportToABR)
                .map(BlockLoopInstructionDTO::getActions)
                .filter(action -> action.contains(ABRConstants.INSERT))
                .map(action -> action.split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER)[1])
                .collect(Collectors.toSet());

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
            new ABRAlertScene(
                    Alert.AlertType.ERROR,
                    "Excel file generation failed",
                    "There was a problem with the excel file generation. Reason: " + e,
                    ButtonType.OK);
        }
    }

    public static boolean isFileExits(BotJobDTO botJob) {
        File excelFolder = new File(ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL));
        if (excelFolder.exists()) {
            String fileName = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL) + "/"
                    + botJob.getName() + ABRConstants.DEFAULT_FILENAME_FOR_ABR + ABRConstants.FILE_FORMAT_EXCEL;
            File file = new File(fileName);
            return file.exists();
        } else {
            return false;
        }
    }
}
