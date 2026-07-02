package com.allinweb.ch.facade.actions;

import com.allinweb.ch.model.*;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.util.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution reporting (cluster O + Excel rows): accumulates total execution time, writes
 * Excel report rows, the operations log and the IF/ELSEIF/ELSE condition state machine.
 * Owns totalExecutionTime (the facade getter delegates here). Bodies moved verbatim from
 * PerformActions.
 */
public class ExecutionReporter {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private long totalExecutionTime = 0;

    public long getTotalExecutionTime() {
        return totalExecutionTime;
    }

    public short operationLog(boolean success, String mainMsg, String currentExecution, long duration) {

        if (success) {

            logOperations.info(String.format(
                    success ? "Success %s Current Cmd: %s - Duration: %s" : "Failed %s Current Cmd: %s - Duration: %s",
                    mainMsg,
                    currentExecution,
                    LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME)));
        } else {

            logOperations.warn(String.format(
                    success ? "Success %s Current Cmd: %s - Duration: %s" : "Failed %s Current Cmd: %s - Duration: %s",
                    mainMsg,
                    currentExecution,
                    LocalTime.ofNanoOfDay(duration).format(FORMAT_TIME)));
        }

        return (short) (success ? ExcelReportStatusEnum.SUCCESS.ordinal() : ExcelReportStatusEnum.ERROR.ordinal());
    }

    public String messageExcel(
            String action, // ✅ action FIRST (e.g. "EXCEL", "INSERT", etc.)
            InstructionLoad instruction, // for desc
            String parentField, // e.g. "8838-BancaStato"
            String variableField, // e.g. "331-$BancaStato"
            String value, // value written to Excel
            String blockName, // fallback desc
            Integer testRow, // fallback test
            boolean success // PASSED / FAIL
            ) {

        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        // TEST + Main Field parsing (unchanged)
        String testName = "";
        String mainField = "";

        if (parentField != null && parentField.contains("-")) {
            int idx = parentField.indexOf('-');
            testName = parentField.substring(0, idx).trim();
            mainField = parentField.substring(idx + 1).trim();
        } else {
            mainField = (parentField == null) ? "" : parentField;
            testName = (testRow == null) ? "" : String.valueOf(testRow);
        }

        // desc (unchanged)
        String desc = (instruction != null && instruction.getName() != null)
                ? instruction.getName()
                : (blockName == null ? "" : blockName);

        // ✅ ONLY CHANGE: conditionText built here, ACTION first
        String conditionText = success
                ? action + " --> Insert into Excel -> " + variableField + "-" + value
                : action + " --> NO Export Excel File defined -> " + variableField + "-" + value;

        String result = success ? "PASSED" : "FAIL";

        return time + " | " + testName + " | " + desc + " | " + mainField + " | " + conditionText + " | " + result;
    }

    public boolean excelReportWrite(
            ARExecution.ConditionStatus currentCondition,
            String blockName,
            boolean success,
            String[] actions,
            FieldData msgLoop,
            long duration,
            Map<String, String> dataExcel,
            ExcelWriter.ExcelChain writerReport) {
        return writerReport.insertInstructionResult(
                currentCondition, blockName, actions, msgLoop, dataExcel, LocalTime.ofNanoOfDay(duration), success);
    }

    public void logAndReport(
            ARExecution.ConditionStatus currentCondition,
            boolean excelReport,
            boolean logOperation,
            long blockStartTime,
            String blockReportName,
            boolean success,
            String[] action,
            FieldData msgBlock,
            Map<String, String> dataExcel,
            ExcelWriter.ExcelChain writerReport,
            String mainMsg,
            String bodyLog) {
        long duration = WaitSupport.duration(blockStartTime);

        if (excelReport) {
            excelReportWrite(
                    currentCondition, blockReportName, success, action, msgBlock, duration, dataExcel, writerReport);
            totalExecutionTime += duration;
        }
        if (logOperation) {

            operationLog(success, mainMsg, bodyLog, duration);
        }

        totalExecutionTime += duration;
    }

    public ARExecution.ConditionStatus updateProgressSuccess(
            boolean success, ARExecution.ConditionStatus currentCondition) {
        // It Gets last Progress Status
        // Machine State
        if (currentCondition.equals(ARExecution.ConditionStatus.IF)) {
            return success ? ARExecution.ConditionStatus.IF_PASSED : ARExecution.ConditionStatus.IF_FAILED;
        } else if (currentCondition.equals(ARExecution.ConditionStatus.ELSEIF)) {
            return success ? ARExecution.ConditionStatus.ELSEIF_PASSED : ARExecution.ConditionStatus.ELSEIF_FAILED;
        } else if (currentCondition.equals(ARExecution.ConditionStatus.ELSE)) {
            return success ? ARExecution.ConditionStatus.ELSE_PASSED : ARExecution.ConditionStatus.ELSE_FAILED;
        } else if (currentCondition.equals(ARExecution.ConditionStatus.ENDIF)) {
            return ARExecution.ConditionStatus.NONE;
        }
        return ARExecution.ConditionStatus.NONE;
    }
}
