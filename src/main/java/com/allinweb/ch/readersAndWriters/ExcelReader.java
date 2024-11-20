package com.allinweb.ch.readersAndWriters;

import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.allinweb.ch.util.Constants;
import com.allinweb.ch.util.ExtractedData;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelReader {

    private static final int EXCEL_DATA_COLUMN_INTESTATION_ROW = 1;

    private static String executed = "EXECUTED";

    private static String OUTCOME = "outcome";

    public ExcelReader() {}

    public ExtractedData extractData(String paymentsFilePath, List<String> allActions) throws Exception {

        // getting first Excel sheet
        try (XSSFWorkbook workbook = new XSSFWorkbook(new File(paymentsFilePath))) {
            Sheet firstSheet = workbook.getSheetAt(0);

            if (allActions == null || allActions.isEmpty()) {
                //                throw new Exception("No actions provided");
            }

            Row fieldNamesRow = firstSheet.getRow(EXCEL_DATA_COLUMN_INTESTATION_ROW);
            if (fieldNamesRow == null) {
                throw new Exception("Field names row is missing in the Excel sheet");
            }

            // Directly use allActions to filter and map
            Set<String> blockFields = allActions.stream()
                    .filter(action -> action.contains(Constants.INSERT)
                            && action.contains(Constants.ACTION_SPECIFICATIONS_SPLITTER))
                    .map(action -> action.split(Constants.ACTION_SPECIFICATIONS_SPLITTER)[1])
                    .collect(Collectors.toSet());

            ExtractedData extractedData = new ExtractedData();

            // Cache field names in the extracted data
            for (int i = fieldNamesRow.getFirstCellNum(); i < fieldNamesRow.getLastCellNum(); i++) {
                extractedData.addField(getCellValue(fieldNamesRow.getCell(i)));
            }

            // Validate the fields
            boolean fieldCheckPassed = blockFields.stream().allMatch(extractedData::containsField);

            if (!fieldCheckPassed) {
                extractedData.setErrorMessage("Fields in the excel not matching the botjob requirements");
                //                return extractedData;
            }

            // Find the fields that are missing in ExtractedData
            Set<String> missingFields = new HashSet<>();

            for (String blockField : blockFields) {
                boolean found = false;

                for (String extractedField : extractedData.getExtractedFields()) {
                    // Assuming `getExtractedFields()` returns a Set or List of field names in ExtractedData
                    if (blockField.equalsIgnoreCase(extractedField)) {
                        found = true;
                        break;
                    } 
                }

                if (!found) {
                    missingFields.add(blockField);
                }
            }

            // If there are missing fields, set the error message and return
            if (!missingFields.isEmpty()) {
                extractedData.setMissingFields(
                        "Fields in the Excel do not match the botjob requirements. Missing fields: "
                                + String.join(", ", missingFields));
                //                return extractedData;
            }

            // Iterate over rows and cache row access
            for (int currentRowIndex = EXCEL_DATA_COLUMN_INTESTATION_ROW + 1;
                    currentRowIndex <= firstSheet.getLastRowNum();
                    currentRowIndex++) {
                Row currentRow = firstSheet.getRow(currentRowIndex);
                if (currentRow == null) {
                    continue;
                }

                // Cache field names and values for the current row
                for (int currentCellIndex = currentRow.getFirstCellNum();
                        currentCellIndex < currentRow.getLastCellNum();
                        currentCellIndex++) {
                    String fieldName = getCellValue(fieldNamesRow.getCell(currentCellIndex));
                    String value = getCellValue(currentRow.getCell(currentCellIndex));
                    extractedData.addFieldValue(
                            fieldName, value, currentRowIndex - EXCEL_DATA_COLUMN_INTESTATION_ROW - 1);
                }
            }

            return extractedData;
        }catch (Exception ex){
            return null;
        }
    }

    public File createLogFile(String filePath) {

        File paymentsFile = new File(filePath);
        String paymentsFileName = paymentsFile.getName();
        int lastPeriodPos = paymentsFileName.lastIndexOf('.');
        paymentsFileName = paymentsFileName.substring(0, lastPeriodPos);
        String logDirectory = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG);
        String logFilePath = logDirectory + "\\" + paymentsFileName + ABRConstants.FILE_FORMAT_LOG;

        File logFile = null;
        try {
            logFile = new File(logFilePath);
            logFile.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }

        return logFile;
    }

    /* TODO: To be removed
    public File createPaymentsExcelLogFile(String paymentsFilePath) {


    	File paymentsFile = new File(paymentsFilePath);
    	File logDirectory = new File(paymentsFile.getParent() + logFolderName);
    	logDirectory.mkdir();

    	String paymentsFileName = paymentsFile.getName();
    	int lastPeriodPos = paymentsFileName.lastIndexOf('.');
    	paymentsFileName = paymentsFileName.substring(0, lastPeriodPos);
    	String logExcelFilePath = logDirectory + "\\" + paymentsFileName + executed + executedExcelFileExtension;


    	try {
    		Workbook workbookPayments = WorkbookFactory.create(paymentsFile);
    		Row intestationRow = workbookPayments.getSheetAt(0).getRow(0);

    		Workbook logExcelWorkbook = new XSSFWorkbook();
    		Sheet page0 = logExcelWorkbook.createSheet();
    		Row intestationRowLog = page0.createRow(0);

    		Cell logCell;
    		int maxColumn = 0;
    		for (int i = 0; i < intestationRow.getLastCellNum(); i++) {
    			logCell = intestationRowLog.createCell(i);
    			logCell.setCellValue(intestationRow.getCell(i).getStringCellValue());
    			maxColumn = i;
    		}
    		logCell = intestationRowLog.createCell(maxColumn);
    		logCell.setCellValue(OUTCOME);

    		FileOutputStream outputStream = new FileOutputStream(logExcelFilePath);
    		logExcelWorkbook.write(outputStream);
    		outputStream.close();
    		logExcelWorkbook.close();

    	} catch (Exception e) {
    		e.printStackTrace();
    		System.exit(0);
    	}
    	return new File(logExcelFilePath);
    }
    */

    private static String getCellValue(Cell cell) {
        String cellValue = null;
        CellType type = cell.getCellType();
        switch (type) {
            case STRING -> {
                String val = cell.getStringCellValue();
                if (!val.isBlank()) {
                    cellValue = val;
                }
            }
            case NUMERIC -> {
                cellValue = String.valueOf(cell.getNumericCellValue());
                if (cellValue.contains(".0")) {
                    cellValue = cellValue.substring(0, cellValue.indexOf(".0"));
                }
                return cellValue;
            }
            case BOOLEAN, BLANK, FORMULA, ERROR -> {}
        }
        return cellValue;
    }
}
