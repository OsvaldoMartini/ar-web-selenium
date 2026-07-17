package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARExecution;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * PerformMessage.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
@Slf4j
public class PerformMessage {

    // Static final variable to hold the singleton instance
    protected static volatile PerformMessage instance;

    // Private constructor to prevent instantiation
    private PerformMessage() {}

    // Public method to access the singleton instance
    public static PerformMessage getInstance() {
        if (instance == null) {
            synchronized (PerformMessage.class) {
                if (instance == null) {
                    instance = new PerformMessage();
                }
            }
        }
        return instance;
    }

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    public void initializePerformMessages() {}

    public void couldNotFindElement(String criteria) {
        publishWarning(
                criteria,
                "1. Verify if you are on the correct web page.",
                "2. Check if the page layout or content has been updated.",
                "3. Consider increasing the wait time or rescanning the element.");
    }

    public void couldNotInputBotJobVeryFast(String criteria) {
        publishWarning(
                criteria,
                "If fields are written to previous fields, they may depend on parent data.",
                "Data loading delays may require waiting time.",
                "Our AI report analysis can determine the necessary wait times.");
    }

    public void multipleActionsElement(String criteria) {
        publishWarning(
                criteria,
                "Attention Required!",
                "This element may require multiple actions.",
                "Use TEST ACTIONS first to verify the element.");
    }

    public void errorMessageOperationFailed(ErrorMessage errorMessage) {
        log.error(
                "Error: {} Title: {} Message: {}",
                errorMessage.getErrorHeader(),
                errorMessage.getErrorTitle(),
                errorMessage.getErrorMessage());
        publish(
                ScannerDialogPublisher.Severity.ERROR,
                errorMessage.getErrorHeader(),
                errorMessage.getErrorTitle(),
                errorMessage.getErrorMessage());
    }

    public void errorMessage(String criteria, String msg1, String msg2, String msg3, String msg4, int height) {
        showCustomModalDialogDragWin11(criteria, msg1, msg2, msg3, msg4, true, "OK", null, height);
    }

    public void showCustomDialog(String title, String message) {
        publish(ScannerDialogPublisher.Severity.INFO, title, message, null);
    }

    public void showCustomModalDialog(String title, String message, String message2) {
        publish(ScannerDialogPublisher.Severity.INFO, title, message, message2);
    }

    public void showCustomModalDialogDrag(String title, String message, String message2) {
        publish(ScannerDialogPublisher.Severity.INFO, title, message, message2);
    }

    public ARExecution.DialogModal showCustomModalDialog(
            String title,
            String message,
            String message2,
            String message3,
            String message4,
            boolean redMsg,
            String firstButton,
            String secondButton,
            int height) {
        return publishModal(title, message, message2, message3, message4, redMsg, firstButton, secondButton, 0, false);
    }

    public ARExecution.DialogModal showCustomModalDialogDrag(
            String title,
            String message,
            String message2,
            String message3,
            String message4,
            boolean redMsg,
            String firstButton,
            String secondButton,
            int height) {
        return publishModal(title, message, message2, message3, message4, redMsg, firstButton, secondButton, 0, false);
    }

    public List<String> distributeMsg(List<String> lstOrigin) {
        List<String> result = new ArrayList<>(3);

        if (lstOrigin == null || lstOrigin.isEmpty()) {
            result.add(null);
            result.add(null);
            result.add(null);
            return result;
        }

        int listSize = lstOrigin.size();

        if (listSize <= 3) {
            for (int i = 0; i < listSize; i++) {
                result.add(lstOrigin.get(i));
            }
            while (result.size() < 3) {
                result.add(null);
            }
        } else if (listSize <= 6) {
            String msg1 = "";
            String msg2 = "";
            String msg3 = "";

            for (int i = 0; i < listSize; i++) {
                if (i < 2) {
                    msg1 += lstOrigin.get(i) + "\n";
                } else if (i < 4) {
                    msg2 += lstOrigin.get(i) + "\n";
                } else {
                    msg3 += lstOrigin.get(i) + "\n";
                }
            }
            result.add(msg1);
            result.add(msg2);
            result.add(msg3);
        } else {
            String msg1 = "";
            String msg2 = "";
            String msg3 = "";

            int itemsPerMessage = listSize / 3;
            int remainingItems = listSize % 3;

            for (int i = 0; i < listSize; i++) {
                if (i < itemsPerMessage + remainingItems) {
                    msg1 += lstOrigin.get(i) + "\n";
                } else if (i < (itemsPerMessage * 2) + remainingItems) {
                    msg2 += lstOrigin.get(i) + "\n";
                } else {
                    msg3 += lstOrigin.get(i) + "\n";
                }
            }
            result.add(msg1);
            result.add(msg2);
            result.add(msg3);
        }
        return result;
    }

    public ARExecution.DialogModal showCustomModalDialogDragWin11(
            String title,
            String message1,
            String message2,
            String message3,
            String message4,
            boolean redMsg,
            String firstButton,
            String secondButton,
            int height) {
        return publishModal(title, message1, message2, message3, message4, redMsg, firstButton, secondButton, 0, false);
    }

    public ARExecution.DialogModal showCustomModalDialogDragWin11Timer(
            String title,
            String message1,
            String message2,
            String message3,
            String message4,
            boolean redMsg,
            String firstButton,
            String secondButton,
            int height,
            int seconds) {
        return publishModal(
                title, message1, message2, message3, message4, redMsg, firstButton, secondButton, seconds, false);
    }

    public ARExecution.DialogModal showCustomModalDialogDragWin11TimerAuto(
            String title,
            String message1,
            String message2,
            String message3,
            String message4,
            boolean redMsg,
            String firstButton,
            String secondButton,
            int height,
            int seconds) {
        return publishModal(
                title, message1, message2, message3, message4, redMsg, firstButton, secondButton, seconds, true);
    }

    private void publishWarning(String title, String message1, String message2, String message3) {
        publish(ScannerDialogPublisher.Severity.WARNING, title, message1, combine(message2, message3, null));
    }

    private ARExecution.DialogModal publishModal(
            String title,
            String message1,
            String message2,
            String message3,
            String message4,
            boolean redMsg,
            String firstButton,
            String secondButton,
            int seconds,
            boolean autoStop) {
        ScannerDialogPublisher.Severity severity = redMsg
                ? ScannerDialogPublisher.Severity.ERROR
                : ScannerDialogPublisher.Severity.INFO;
        String body = combine(message2, message3, message4);
        if (seconds > 0) {
            String timerText =
                    autoStop ? "Auto action in " + seconds + " seconds." : "Continues in " + seconds + " seconds.";
            body = combine(body, timerText, null);
        }
        boolean sent = publish(severity, title, message1, body);
        if (!sent) {
            log.warn("React dialog unavailable; {}: {} {}", title, message1, body == null ? "" : body);
        }
        if (autoStop && seconds > 0 && secondButton != null && !secondButton.isBlank()) {
            return ARExecution.DialogModal.STOP;
        }
        return ARExecution.DialogModal.OK;
    }

    private boolean publish(ScannerDialogPublisher.Severity severity, String title, String header, String body) {
        String safeTitle = title == null || title.isBlank() ? "Application Message" : title;
        String safeHeader = header == null ? "" : header;
        String safeBody = body == null ? "" : body;
        boolean sent = dialogPublisher.alert(severity, safeTitle, safeHeader, safeBody);
        if (!sent) {
            log.warn("React alert unavailable [{}] {} - {} {}", severity, safeTitle, safeHeader, safeBody);
        }
        return sent;
    }

    private String combine(String first, String second, String third) {
        StringBuilder body = new StringBuilder();
        appendLine(body, first);
        appendLine(body, second);
        appendLine(body, third);
        return body.isEmpty() ? null : body.toString();
    }

    private void appendLine(StringBuilder body, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!body.isEmpty()) {
            body.append("\n");
        }
        body.append(value);
    }
    public String renderInstructionActions(InstructionLoad instruction) {
        // List of valid actions
        List<String> validActions = Arrays.asList("SET", "GET", "CK", "E");

        // Handle the "CK" action with special formatting for operation
        if ("CK".equals(instruction.getActions()) && instruction.getOperation() != null) {
            String[] parts = instruction.getOperation().split(":");
            if (parts.length == 3) {
                String left = parts[0].trim();
                String middle = parts[1].trim();
                String right = parts[2].trim();

                // Handle special case where middle is "="
                if ("=".equals(middle)) {
                    return String.format("(%d)%s %s %s", instruction.getParentId(), left, middle, right);
                }
            }
        }

        // Handle operations for other actions (SET, GET)
        if (instruction.getOperation() != null && validActions.contains(instruction.getActions())) {
            String[] parts = instruction.getOperation().split(":");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim();
                return String.format("(%d)%s: %s", instruction.getParentId(), left, right);
            }
        }

        // Handle if the action is valid but has no operation
        if (validActions.contains(instruction.getActions())) {
            return instruction.getActions();
        }

        // Return empty string for no actions
        return "";
    }

    public void outputJson(
            List<InstructionLoad> blockLoopInstructions, String fileName, String jsonPath, boolean genTestData) {
        List<InstructionLoad> updatedList = new ArrayList<>(); // Create a new list for updated instructions

        for (InstructionLoad instruction : blockLoopInstructions) {
            // Create a new InstructionLoad object to avoid modifying the original
            InstructionLoad updatedInstruction = new InstructionLoad();

            int genData = 0;
            if (genTestData) {
                genData = 1000;
            }
            // Copy original fields and add 1000 where necessary
            updatedInstruction.setHomeBankingId(instruction.getHomeBankingId() + genData);
            updatedInstruction.setId(instruction.getId() + genData);
            updatedInstruction.setBotJobId(instruction.getBotJobId() + genData);
            updatedInstruction.setBlockId(instruction.getBlockId() + genData);
            updatedInstruction.setBlockOrderNumber(
                    instruction.getBlockOrderNumber()); // Copy without change (if needed)

            // Add 1000 to parentId if it's greater than 0
            if (instruction.getParentId() > 0) {
                updatedInstruction.setParentId(instruction.getParentId() + genData);
            } else {
                updatedInstruction.setParentId(instruction.getParentId()); // Keep original if not greater than 0
            }

            // Copy other fields as is (no change)
            updatedInstruction.setBotJobName(instruction.getBotJobName());
            updatedInstruction.setInstructionOrderNumber(instruction.getInstructionOrderNumber());
            updatedInstruction.setActions(instruction.getActions());
            updatedInstruction.setName(instruction.getName());
            updatedInstruction.setXpath(instruction.getXpath());
            updatedInstruction.setDescription(instruction.getDescription());
            updatedInstruction.setOptional(instruction.getOptional());
            updatedInstruction.setBlockMarked(instruction.getBlockMarked());
            updatedInstruction.setDefaultValue(instruction.getDefaultValue());
            updatedInstruction.setActionCustomMaxWaitSec(instruction.getActionCustomMaxWaitSec());
            updatedInstruction.setOnHoldSeconds(instruction.getOnHoldSeconds());
            updatedInstruction.setCodified(instruction.getCodified());
            updatedInstruction.setExportToABR(instruction.getExportToABR());
            updatedInstruction.setExportToABR(instruction.getExportToABR());
            updatedInstruction.setExecuted(instruction.getExecuted());
            updatedInstruction.setPriority(instruction.getPriority());
            updatedInstruction.setOperation(instruction.getOperation());
            updatedInstruction.setExportFile(instruction.getExportFile());
            updatedInstruction.setBlockName(instruction.getBlockName());
            updatedInstruction.setBlockActive(instruction.getInstructionActive());
            updatedInstruction.setBlockWait(instruction.getBlockWait());
            updatedInstruction.setEditMode(instruction.getEditMode());
            updatedInstruction.setRefreshLoop(instruction.getRefreshLoop());
            updatedInstruction.setLoopOnly(instruction.getLoopOnly());
            updatedInstruction.setInstructionActive(instruction.getInstructionActive());

            // Add the updated instruction to the new list
            updatedList.add(updatedInstruction);
        }

        // Define Gson ExclusionStrategy to ignore specific fields
        ExclusionStrategy strategy = new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                // Skip fields by name (e.g., 'botJobId', 'botJobName')
                return f.getName().equals("optional")
                        || f.getName().equals("blockMarked")
                        || f.getName().equals("editMode");
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        };

        // Initialize Gson with pretty printing for better readability
        Gson gson = new GsonBuilder()
                .setExclusionStrategies(strategy)
                .setPrettyPrinting()
                .create();

        // Serialize the list of InstructionLoad to JSON
        String jsonData = gson.toJson(updatedList);

        // Create the file path
        String outputFilePath = jsonPath + "/" + fileName + ".json";

        // Write the JSON data to the file
        try (FileWriter writer = new FileWriter(outputFilePath, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(jsonData);
            log.info("JSON file saved to: " + outputFilePath);
        } catch (IOException e) {
            log.error("Error writing JSON to file: " + e.getMessage());
        }
    }

    public void outputJsonElementDTO(
            ElementDTO[] elementDTO, List<String> fieldsToExclude, String fileName, String jsonPath) {
        outputJsonElementDTO(elementDTO, fieldsToExclude, fileName, jsonPath, false);
    }

    /**
     * Write the elementDTO array to {@code <PATH_DB>/page_diagnostics/{fileName}.json}.
     *
     * <p>When {@code append == true}, the existing JSON array (if any) is read first and the
     * new elements are concatenated, deduped by xPath. This is the cumulative hover-pick mode:
     * each click adds to the running list rather than overwriting it. The picker UI's
     * "Clear Grid All" button (with the Hover Pick option checked) is what truncates the file.
     */
    public void outputJsonElementDTO(
            ElementDTO[] elementDTO, List<String> fieldsToExclude, String fileName, String jsonPath, boolean append) {
        ExclusionStrategy strategy = new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                return fieldsToExclude.contains(f.getName());
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        };

        Gson gson = new GsonBuilder()
                .setExclusionStrategies(strategy)
                .setPrettyPrinting()
                .create();

        String outputFilePath;
        try {
            Path diagDir = Paths.get(jsonPath, PageDiagnosticDumper.SUBFOLDER);
            Files.createDirectories(diagDir);
            outputFilePath = diagDir.resolve(fileName + ".json").toString();
        } catch (IOException dirEx) {
            log.error("Could not create diagnostics folder, falling back to root: " + dirEx.getMessage());
            outputFilePath = jsonPath + "/" + fileName + ".json";
        }

        ElementDTO[] toWrite = elementDTO == null ? new ElementDTO[0] : elementDTO;

        if (append) {
            try {
                Path existing = Paths.get(outputFilePath);
                if (Files.exists(existing) && Files.size(existing) > 0) {
                    String prevJson = new String(Files.readAllBytes(existing), java.nio.charset.StandardCharsets.UTF_8);
                    ElementDTO[] previous = gson.fromJson(prevJson, ElementDTO[].class);
                    if (previous != null && previous.length > 0) {
                        java.util.LinkedHashMap<String, ElementDTO> merged = new java.util.LinkedHashMap<>();
                        for (ElementDTO el : previous) {
                            if (el == null) continue;
                            String key = el.getXPath() == null ? "" : el.getXPath();
                            merged.put(key, el);
                        }
                        for (ElementDTO el : toWrite) {
                            if (el == null) continue;
                            String key = el.getXPath() == null ? "" : el.getXPath();
                            merged.put(key, el);
                        }
                        // Re-id sequentially so the merged file always has 1..N ids without gaps.
                        ElementDTO[] mergedArr = merged.values().toArray(new ElementDTO[0]);
                        for (int i = 0; i < mergedArr.length; i++) mergedArr[i].setId(i + 1);
                        toWrite = mergedArr;
                    }
                }
            } catch (IOException | RuntimeException ex) {
                log.warn(
                        "Append mode: could not merge with existing {} ({}), overwriting.",
                        outputFilePath,
                        ex.getMessage());
            }
        }

        try (FileWriter writer = new FileWriter(outputFilePath, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(gson.toJson(toWrite));
            log.info(
                    "JSON file saved to: {} ({} entries, mode={})",
                    outputFilePath,
                    toWrite.length,
                    append ? "append" : "overwrite");
        } catch (IOException e) {
            log.error("Error writing JSON to file: " + e.getMessage());
        }
    }

    /**
     * Truncate (delete) the {@code elementDTO-HP.json} (and {@code AI-ElementDTO-HP.json})
     * file under {@code <PATH_DB>/page_diagnostics/}. Called by the picker's "Clear Grid All"
     * button when Hover Pick mode is on.
     */
    public void clearHoverPickJson(String jsonPath) {
        String[] names = {"elementDTO-HP.json", "AI-ElementDTO-HP.json"};
        for (String name : names) {
            try {
                Path p = Paths.get(jsonPath, PageDiagnosticDumper.SUBFOLDER, name);
                if (Files.deleteIfExists(p)) {
                    log.info("Cleared hover-pick file: {}", p);
                }
            } catch (IOException e) {
                log.warn("Could not delete hover-pick file {}: {}", name, e.getMessage());
            }
        }
    }

    //    public void generalErrorIFrame(String xpath) {
    //        // Styled text elements
    //        Text titleText = new Text("Fail Searching IFrame Elements");
    //        titleText.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
    //
    //        Text errorText = new Text("Error: Attempt identify IFrame elements");
    //        errorText.setStyle("-fx-font-size: 18px; -fx-fill: red;");
    //
    //        Text xpathText = new Text(xpath);
    //        xpathText.setStyle("-fx-font-size: 18px; -fx-fill: red;");
    //
    //        // Create a container for the message
    //        VBox messageContainer = new VBox(5); // Adds spacing of 5px
    //
    //        // Add relevant elements to the container
    //        messageContainer.getChildren().addAll(titleText, errorText);
    //
    //        if (!Strings.isNullOrEmpty(xpath)) {
    //            messageContainer.getChildren().add(xpathText);
    //        }
    //
    //        // Display the alert message
    //        showAlertCombinedVBOX(
    //                Alert.AlertType.WARNING,
    //                "iFrame Web Elements",
    //                "Action: Search iFrame Elements!",
    //                null,
    //                messageContainer);
    //    }
}
