package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.*;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.util.*;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-run user feedback (cluster F): pause/abort dialogs, value-not-defined and
 * wrong-parent-block report rows, validation-failed messages, in-page JS alert banner and
 * per-action result rows. Bodies moved verbatim from PerformActions.
 */
public class EngineDialogs {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private final ActionContext ctx;

    public EngineDialogs(ActionContext ctx) {
        this.ctx = ctx;
    }

    public void quit(int status) {
        ctx.driver().quit();
        if (status == 0) {
            System.exit(status);
        }
    }

    public String pauseEngine(String blockName) {

        //        JavascriptExecutor js = (JavascriptExecutor) ctx.driver();
        //        js.executeScript("alert('This is a custom alert modal!');");
        String message = "PAUSE REQUESTED "
                + "<br>-------------------------------------------------<br>"
                + "BOT JOB in PAUSE MODE:: <b style='color:red;'><br>"
                + blockName
                + "</b>"
                + "<br>-------------------------------------------------<br>";

        alertMessage(message);

        return "BOT JOG in PAUSE MODE: " + blockName;
    }

    public String getValueIsNotDefinedEngine(
            InstructionLoad currentInstruction, String lastInstructionExecuted, boolean ifClause, boolean elseClause) {

        if (!ifClause && !elseClause) {
            String message = "There is NOT GET VALUE defined for: "
                    + "<br>-------------------------------------------------<br>"
                    + "Validation Error: <b style='color:red;'>"
                    + currentInstruction.getName()
                    + "</b>"
                    + "<br>-------------------------------------------------<br>"
                    + "Check the GET for <b style='color:red;'>"
                    + currentInstruction.getParentId() + "-"
                    + currentInstruction.getOperation()
                    + "</b>";
            alertMessage(message);
        }

        String conditionalBlock = ifClause
                ? "Closing Block { IF -> ELSE }  -> "
                : elseClause ? "Closing Block { ELSE -> ENDIF }  -> " : "";

        if (ifClause || elseClause) {
            return conditionalBlock + " -> " + lastInstructionExecuted;

        } else {
            return lastInstructionExecuted;
        }
    }

    public String getValueIsNotDefined(
            String action,
            InstructionLoad currentInstruction,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus,
            String parentField,
            String variableField) {

        if (conditionStatus.equals(ARExecution.ConditionStatus.NONE)) {
            String msg1, msg2, msg3, msg4 = null;

            if (action.equals(ARConstantsEngine.EXTRACT_FIELD)
                    || action.equals(ARConstantsEngine.CHECK_VALUE)
                    || action.equals(ARConstantsEngine.PDF_CHECK)
                    || action.equals(ARConstantsEngine.CSV_CHECK)) {
                msg1 = "The variable \"" + variableField + "\" has not been assigned.";
                msg2 = "Please add a <span style='color: #000080; font-weight: bold;'>GET</span> step for \""
                        + currentInstruction.getName() + "\" to assign this variable.";
                msg3 = "Missing a <span style='color: #000080; font-weight: bold;'>GET</span> for variable \""
                        + variableField + "\" .";
            } else {
                msg1 = "No GET value has been defined for: \"" + currentInstruction.getName() + "\".";
                msg2 = "Please add a GET step for instruction ID: " + currentInstruction.getParentId()
                        + " - Operation: " + currentInstruction.getOperation() + ".";

                if (parentField != null) {
                    msg3 = "Parent Web Field:";
                    msg4 = "Instruction ID " + currentInstruction.getParentId() + " - \"" + parentField + "\".";
                } else {
                    msg3 = "Parent Web Field is not defined!";
                    msg4 = "Ensure a valid parent field is assigned.";
                }
            }
            logOperations.error(
                    "Missing Variable for \"{}\" - {} - {} - {} - {}",
                    currentInstruction.getName(),
                    msg1,
                    msg2,
                    msg3,
                    msg4);
            PerformMessage.getInstance()
                    .errorMessage(
                            "Missing Variable for \"" + currentInstruction.getName() + "\"", msg1, msg2, msg3, msg4, 0);
        }

        String conditionalBlock = conditionStatus.equals(ARExecution.ConditionStatus.IF_PASSED)
                ? "Closing Block { IF -> ELSE }  -> "
                : conditionStatus.equals(ARExecution.ConditionStatus.ELSEIF_PASSED)
                        ? "Closing Block { ELSEIF -> ELSE }  -> "
                        : conditionStatus.equals(ARExecution.ConditionStatus.ELSE_PASSED)
                                ? "Closing Block { ELSE -> ENDIF }  -> "
                                : "Get Value Is Not Defined";

        if (!conditionStatus.equals(ARExecution.ConditionStatus.NONE)) {
            return conditionalBlock + " -> " + lastInstructionExecuted;

        } else {
            return lastInstructionExecuted;
        }
    }

    public String parentValueIsNotDefined(String instructionName, String parentField, String resultActions) {

        //        showAlert(
        //                Alert.AlertType.ERROR,
        //                "Parent is Not Defined for \"" + instructionName + "\"",
        //                "\"" + instructionName + "\" - Parent is Not Defined",
        //                "There is NOT PARENT VALUE defined for: "
        //                        + instructionName
        //                        + "\n --------------------- "
        //                        + "\nCheck the PARENT Web field for "
        //                        + parentId + "- Unknown");
        String msg1 = "Parent is Not Defined for \"" + instructionName + "\"";
        String msg2 = "There is NOT PARENT VALUE defined for: ";
        String msg3 = "Check the PARENT Web field for \"" + parentField + "\"";

        logOperations.error("Parent Id Error: {} - {} - {}", msg1, msg2, msg3);
        PerformMessage.getInstance().errorMessage("Parent Id Error", msg1, msg2, msg3, null, 0);

        return resultActions;
    }

    public String parentValueIsNotDefinedEngine(String instructionName, String parentField, String resultActions) {

        //        showAlert(
        //                Alert.AlertType.ERROR,
        //                "Parent is Not Defined for \"" + instructionName + "\"",
        //                "\"" + instructionName + "\" - Parent is Not Defined",
        //                "There is NOT PARENT VALUE defined for: "
        //                        + instructionName
        //                        + "\n --------------------- "
        //                        + "\nCheck the PARENT Web field for \"" + parentField+ "\"");

        String msg1 = "Parent is Not Defined for \"" + instructionName + "\"";
        String msg2 = "There is NOT PARENT VALUE defined for: \"" + instructionName + "\"";
        String msg3 = "Check the PARENT Web field for \"" + parentField + "\"";

        logOperations.error("Parent Id Error: {} - {} - {}", msg1, msg2, msg3);
        PerformMessage.getInstance().errorMessage("Parent Id Error", msg1, msg2, msg3, null, 0);

        return resultActions;
    }

    public String parentIdWrongBlockEngine(
            InstructionLoad currentInstruction, BlockLoadDTO blockLoad, boolean ifClause, boolean elseClause) {
        if (!ifClause && !elseClause) {
            String message = "The Parent Id: <b style='color:red;'>"
                    + "The Parent Id: \"(" + currentInstruction.getParentId() + ")"
                    + currentInstruction
                            .getOperation()
                            .substring(0, currentInstruction.getOperation().indexOf(":")) + "\""
                    + "<br>-------------------------------------------------<br>"
                    + "<b style='color:red;'>" + "Does not belong to this block: \"" + blockLoad.getBlockOrderNumber()
                    + "-\"" + blockLoad.getName() + "\"" + "</b>"
                    + "</br>"
                    + "<b style='color:red;'>"
                    + "Attempted Operation : \"" + currentInstruction.getActions() + "\" -> \""
                    + currentInstruction.getOperation() + "\"" + "</b>"
                    + "<br>-------------------------------------------------<br>"
                    + "<b style='color:blue;'>"
                    + "Check the Web Field \" ( ID ) <NAME>\" per Block</b>";

            alertMessage(message);
        }

        String conditionalBlock = ifClause
                ? "Closing Block { IF -> ELSE }  -> "
                : elseClause ? "Closing Block { ELSE -> ENDIF }  -> " : "";

        if (ifClause || elseClause) {

            logOperations.warn(String.format(
                    "%sParent Id Error Check Parent Id: %d "
                            + "For the \"%s\" Does not belong to this block: "
                            + blockLoad.getId() + "-" + blockLoad.getName(),
                    conditionalBlock,
                    currentInstruction.getParentId(),
                    currentInstruction.getOperation()));

        } else {

            logOperations.error(String.format(
                    "Parent Id Error Check Parent Id: %d "
                            + "For the \"%s\" Does not belong to this block: "
                            + blockLoad.getId() + "-" + blockLoad.getName(),
                    currentInstruction.getParentId(),
                    currentInstruction.getOperation()));
        }

        return String.format(
                "This ParentId: %d does not belong to this block: %d - %s. Check the Field Names and Fields Ids",
                currentInstruction.getParentId(), blockLoad.getId(), blockLoad.getName());
    }

    public String parentIdWrongBlock(
            InstructionLoad currentInstruction,
            BlockLoadDTO blockLoad,
            String lastInstructionExecuted,
            ARExecution.ConditionStatus conditionStatus) {

        if (conditionStatus.equals(ARExecution.ConditionStatus.NONE)) {
            String operation = currentInstruction.getOperation();
            int colonIndex = operation.indexOf(":");
            String parentOperationPart = colonIndex != -1 ? operation.substring(0, colonIndex) : "Unknown Operation";

            String msg1 = "The Parent Id: \"(" + currentInstruction.getParentId() + ")" + parentOperationPart + "\"";
            String msg2 = "Does not belong to the block: \"" + blockLoad.getBlockOrderNumber() + "-"
                    + blockLoad.getName() + "\"";
            String msg3 = "Attempted Operation : \""
                    + (currentInstruction.getActions().equals(ARConstantsEngine.EXTRACT_FIELD)
                            ? "Extract "
                            : currentInstruction.getActions())
                    + "\" -> \""
                    + operation + "\"";
            String msg4 = "Check the Web Field \" ( ID ) <NAME> \" per Block";

            logOperations.error("Parent Id Error: {} - {} - {} - {}", msg1, msg2, msg3, msg4);
            PerformMessage.getInstance().errorMessage("Parent Id Error", msg1, msg2, msg3, msg4, 0);
        }

        String conditionalBlock = conditionStatus.equals(ARExecution.ConditionStatus.IF_PASSED)
                ? "Closing Block { IF -> ELSE }  -> "
                : conditionStatus.equals(ARExecution.ConditionStatus.ELSEIF_PASSED)
                        ? "Closing Block { ELSEIF -> ELSE }  -> "
                        : conditionStatus.equals(ARExecution.ConditionStatus.ELSE_PASSED)
                                ? "Closing Block { ELSE -> ENDIF }  -> "
                                : "Parent Id in Wrong Block";

        if (!conditionStatus.equals(ARExecution.ConditionStatus.NONE)) {

            logOperations.warn(String.format(
                    "%sParent Id Error Check Parent Id: %d For the \"%s\" Does not belong to this block: %d-%s",
                    conditionalBlock,
                    currentInstruction.getParentId(),
                    currentInstruction.getOperation(),
                    blockLoad.getId(),
                    blockLoad.getName()));
        } else {

            logOperations.error(String.format(
                    "Parent Id Error Check Parent Id: %d For the \"%s\" Does not belong to this block: %d-%s",
                    currentInstruction.getParentId(),
                    currentInstruction.getOperation(),
                    blockLoad.getId(),
                    blockLoad.getName()));
        }

        if (!conditionStatus.equals(ARExecution.ConditionStatus.NONE)) {
            return conditionalBlock + " -> " + lastInstructionExecuted;
        } else {
            return lastInstructionExecuted;
        }
    }

    public String checkValidationFailedEngine(
            String parent,
            String expected,
            String lastInstructionExecuted,
            String[] operations,
            boolean ifClause,
            boolean elseClause,
            boolean byPassFlagLoop) {
        if (!ifClause && !elseClause && !byPassFlagLoop) {
            String message = "The Value of: <b style='color:red;'>\"" + operations[2] + "\""
                    + "</b> is not " + "<b>" + operations[1] + " "
                    + " \"" + expected + "\"" + "</b> Length: (<b>" + expected.length() + "</b>)"
                    + "<br>-------------------------------------------------<br>"
                    + "The Variable \"" + operations[0] + "\" holds value \"" + operations[2] + "\"</br>"
                    + "<br>Current Web Field: <b style='color:red;'> \"" + parent + "\" value: \"" + expected
                    + "\"</b> Length: (<b>\"" + expected.length() + ")</b>"
                    + "<br>Expected value: <b style='color:green;'>" + operations[2] + "</b> Length: (<b>"
                    + operations[2].length() + "</b>)";

            alertMessage(message);
        }

        String conditionalBlock = ifClause
                ? "Closing Block { IF -> ELSE }  -> "
                : elseClause ? "Closing Block { ELSE -> ENDIF }  -> " : "";

        if (ifClause || elseClause) {
            return conditionalBlock + " -> " + lastInstructionExecuted;

        } else {
            return lastInstructionExecuted;
        }
    }

    public String checkValidationFailed(
            String invalidValues,
            String parent,
            String expected,
            String lastInstructionExecuted,
            String[] operations,
            ARExecution.ConditionStatus conditionStatus,
            boolean byPassFlagLoop) {

        if (conditionStatus.equals(ARExecution.ConditionStatus.NONE) && !byPassFlagLoop) {

            String msg1;
            if (operations[1].equals(">")) {
                msg1 = "The Value of: \"" + expected + "\" is not <span style='color: #000080; font-weight: bold;'>( "
                        + operations[1] + " )</span> \"" + operations[2] + "\"";
            } else if (operations[1].equals("<")) {
                msg1 = "The Value of: \"" + operations[2]
                        + "\" is not <span style='color: #000080; font-weight: bold;'>( &lt; )</span> \"" + expected
                        + "\"";
            } else {
                msg1 = "The Value of: \"" + operations[2] + "\" is not " + operations[1] + " \""
                        + expected + "\" Length: ("
                        + expected.length()
                        + ")";
            }

            String msg2 = "The Variable \"" + operations[0] + "\" holds value \"" + operations[2] + "\"";

            String msg3;
            if (operations[1].equals(">") || operations[1].equals("<")) {
                msg3 = "Current Web Field \"" + parent + "\" value: \"" + expected + "\"";
            } else {
                msg3 = "Current Web Field \"" + parent + "\" value: \""
                        + expected + "\" Length: (" + expected.length()
                        + ")";
            }

            String msg4;
            if (operations[1].equals(">") || operations[1].equals("<")) {
                msg4 = "Expected value: " + operations[2];
            } else {
                msg4 = "Expected value: " + operations[2] + " Length: (" + operations[2].length() + ")";
            }

            if (Strings.isNullOrEmpty(invalidValues)) {
                invalidValues = "Check Validation Value Error";
            } else {

                if (operations[1].equals("<")) {
                    invalidValues += " Operator: (\" &lt; \")";
                } else {
                    invalidValues += " Operator: (\" " + operations[1] + " \")";
                }
            }
            logOperations.error("Invalid Values Error: {} - {} - {} - {} - {}", invalidValues, msg1, msg2, msg3, msg4);
            PerformMessage.getInstance().errorMessage(invalidValues, msg1, msg2, msg3, msg4, 0);
        }

        String conditionalBlock = conditionStatus.equals(ARExecution.ConditionStatus.IF_PASSED)
                ? "Closing Block { IF -> ELSE }  -> "
                : conditionStatus.equals(ARExecution.ConditionStatus.ELSEIF_PASSED)
                        ? "Closing Block { ELSEIF -> ELSE }  -> "
                        : conditionStatus.equals(ARExecution.ConditionStatus.ELSE_PASSED)
                                ? "Closing Block { ELSE -> ENDIF }  -> "
                                : "";

        if (!conditionStatus.equals(ARExecution.ConditionStatus.NONE)) {
            return conditionalBlock + " -> " + lastInstructionExecuted;

        } else {
            return lastInstructionExecuted;
        }
    }

    public String blockGotoFailed(String resultActions) {
        //        showAlert(
        //                Alert.AlertType.ERROR, "Block GO TO Error", "Check Correct Block Existence", "CMD: \n" +
        // resultActions);

        String msg1 = "Block GO TO Error";
        String msg2 = "Check Correct Block Existence";
        String msg3 = "CMD: " + resultActions;

        logOperations.error("Parent Id Error: {} - {} - {}", msg1, msg2, msg3);
        PerformMessage.getInstance().errorMessage("Parent Id Error", msg1, msg2, msg3, null, 0);

        logOperations.error("Block GO TO Error: -> Check Correct Block Existence! -> CMD: " + resultActions);

        return resultActions;
    }

    public void gotoLimitExecution(int executionTimes, String lastInstructionExecuted) {
        //        showAlert(
        //                Alert.AlertType.ERROR,
        //                "Block Execution Time LIMIT",
        //                "Attention The Process Reached the LIMIT of Block Loop Executions",
        //                String.format(
        //                        "Attention the Process Reached the Block LOOP LIMIT of %d\nLast Instruction Executed :
        // %s\nWe are Exiting All of processes Now!",
        //                        executionTimes, lastInstructionExecuted));

        logOperations.warn(
                "Block Execution LIMIT Reached!. Process Reached BLOCK LIMIT of {} executions. Last Exetution: {}",
                executionTimes,
                lastInstructionExecuted);
        PerformMessage.getInstance()
                .errorMessage(
                        "Block Execution LIMIT Reached!",
                        String.format("Process Reached BLOCK LIMIT of %d executions", executionTimes),
                        "Exiting All processes Now!",
                        "Last Execution",
                        lastInstructionExecuted,
                        0);
    }

    public void alertMessage(String message) {
        JavascriptExecutor js = (JavascriptExecutor) ctx.driver();

        // Escape the quotes in the JavaScript string
        String script = "let alertBox = document.createElement('div');" + "alertBox.style.position = 'fixed';"
                + "alertBox.style.top = '50%';"
                + "alertBox.style.left = '50%';"
                + "alertBox.style.transform = 'translate(-50%, -50%)';"
                + "alertBox.style.padding = '20px';"
                + "alertBox.style.backgroundColor = '#FFDA33';"
                + // Light orange background
                "alertBox.style.border = '2px solid #ff0000';"
                + // Red border
                "alertBox.style.borderRadius = '10px';"
                + "alertBox.style.boxShadow = '0 0 10px rgba(0, 0, 0, 0.5)';"
                + "alertBox.style.zIndex = '10000';"
                + "alertBox.innerHTML = \""
                + message.replace("\"", "\\\"") + "\";" + "document.body.appendChild(alertBox);";

        js.executeScript(script);

        // Optional: Handle the alert
        org.openqa.selenium.Alert alert = ctx.driver().switchTo().alert();

        // Optional: pause for a few seconds to view the alert
        try {
            Thread.sleep(5000); // 10 minutes in milliseconds
        } catch (InterruptedException e) {
            logOperations.warn(e.getMessage());
        }

        // Accept (close) the alert
        alert.accept();
    }

    public String actionResultMessage(String blockJobName, String[] actions, FieldData msgInstruction) {

        // ✅ existing message becomes "conditionText"
        String conditionText;

        switch (actions[0]) {
            case ARConstantsEngine.VISUALIZE:
                conditionText = "Visualize " + msgInstruction.getKey();
                break;
            case ARConstantsEngine.OTHER:
                conditionText = "Other Element --> " + msgInstruction.getKey();
                break;
            case ARConstantsEngine.OUTPUT:
                conditionText = "Output Element --> " + msgInstruction.getKey();
                break;
            case ARConstantsEngine.CLICK:
                conditionText = "Click Element --> " + msgInstruction.getKey();
                break;
            case ARConstantsEngine.INSERT:
                if (actions[0].equals(ARConstantsEngine.INSERT) && actions[1].equals(ARConstantsEngine.ENTER)) {
                    conditionText = "Insert/<Enter> action for  -> " + msgInstruction.getKey() + " = "
                            + msgInstruction.getValue();
                } else {
                    conditionText =
                            "Insert action for  -> " + msgInstruction.getKey() + " = " + msgInstruction.getValue();
                }
                break;
            case ARConstantsEngine.LIST_OPERATION:
                conditionText = "List Operation " + msgInstruction.getKey();
                break;
            case ARConstantsEngine.HOLD:
                conditionText = "Hold executed " + msgInstruction.getKey();
                break;
            case ARConstantsEngine.PAUSE:
                conditionText = "Pause action triggered";
                break;
            case ARConstantsEngine.NEXT_ENTER: // NEXT FIELD / FOCUS NEXT / ENTER
                conditionText = "Next/Enter action triggered";
                break;
            case ARConstantsEngine.SWIPE_UP:
                conditionText = "Swipe UP action triggered";
                break;
            case ARConstantsEngine.SWIPE_DOWN:
                conditionText = "Swipe DOWN action triggered";
                break;
            case ARConstantsEngine.GOTO:
                if (msgInstruction.getValue().equals("Unknown")) {
                    conditionText = msgInstruction.getKey();
                } else {
                    String[] parts = msgInstruction.getKey().split(":");
                    conditionText = String.format(
                            "GO TO Block \"%s\" Limit %s times",
                            "(" + parts[0] + ")-#" + parts[2] + " " + parts[3], msgInstruction.getValue());
                }
                break;
            case ARConstantsEngine.REFRESH_ONLY:
                conditionText = " Refresh Web Page";
                break;
            case ARConstantsEngine.REFRESH_HOLD:
                String[] msgParent = msgInstruction.getKey().split(":");
                String[] msgValue = msgInstruction.getValue().split(":");
                conditionText = String.format(
                        "Wait for Parent \"%s\" Limit %s seconds",
                        "(" + msgParent[1] + ") " + msgParent[2], msgValue[0]);
                break;
            case ARConstantsEngine.LOOP:
                if (msgInstruction.getValue().equals("Unknown")) {
                    conditionText = msgInstruction.getKey();
                } else {
                    msgParent = msgInstruction.getKey().split(":");
                    conditionText = String.format(
                            "Jump To Parent \"%s\" Limit %s times",
                            msgParent[0] + "-(" + msgParent[1] + ") " + msgParent[2], msgInstruction.getValue());
                }
                break;
            case ARConstantsEngine.REFRESH_LOOP:
                if (msgInstruction.getValue().equals("Unknown")) {
                    conditionText = msgInstruction.getKey();
                } else {
                    msgParent = msgInstruction.getKey().split(":");
                    msgValue = msgInstruction.getValue().split(":");
                    conditionText = String.format(
                            "Refresh in %s seconds Loop %s times Jump To Parent \"%s\" ",
                            msgValue[0], msgValue[1], msgParent[0] + "-(" + msgParent[1] + ") " + msgParent[2]);
                }
                break;
            case ARConstantsEngine.QUIT:
                conditionText = "Quit action processed";
                break;
            case ARConstantsEngine.SCREEN:
                conditionText = "Screen action executed for " + msgInstruction.getKey() + " --> " + blockJobName;
                break;
            case ARConstantsEngine.GET_VALUE:
            case ARConstantsEngine.SET_VALUE:
                conditionText = actions[0]
                        + ARConstantsEngine.BLANK_STRING
                        + msgInstruction.getKey()
                        + ARConstantsEngine.BLANK_STRING
                        + msgInstruction.getValue();
                break;
            case ARConstantsEngine.CHECK_VALUE:
            case ARConstantsEngine.PDF_CHECK:
            case ARConstantsEngine.CSV_CHECK:
                conditionText = actions[0]
                        + ARConstantsEngine.BLANK_STRING
                        + msgInstruction.getValue()
                        + ARConstantsEngine.BLANK_STRING
                        + msgInstruction.getKey();
                break;
            case ARConstantsEngine.EXTRACT_FIELD:
                conditionText = ARConstantsEngine.BLANK_STRING
                        + msgInstruction.getKey() + " Extract "
                        + ARConstantsEngine.BLANK_STRING
                        + msgInstruction.getValue();
                break;

            default:
                conditionText = "No Action Detected for " + msgInstruction.getKey();
                break;
        }

        // ✅ SAME PATTERN AS performOperatorActions
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        // If you don't have a test number here, keep it empty (caller can fill elsewhere)
        String testName = "";

        // Best default mainField: the key (e.g. "(8869)-OK" or "8838-BancaStato")
        String mainField = (msgInstruction == null || msgInstruction.getKey() == null) ? "" : msgInstruction.getKey();

        // Description: block/job name (or empty)
        String desc = (blockJobName == null) ? "" : blockJobName;

        // ✅ default PASSED for this method
        String result = "PASSED";

        return time + " | " + testName + " | " + desc + " | " + mainField + " | " + conditionText + " | " + result;
    }
}
