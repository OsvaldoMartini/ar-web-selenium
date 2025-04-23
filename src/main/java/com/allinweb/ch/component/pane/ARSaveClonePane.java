package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ARSaveClonePane extends ARPane {

    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;

    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    private BotJobLoadDTO selecBotJobDTO;
    private List<BotJobLoadDTO> botJobList;
    // UI

    private Label nameLabel;
    private Label descriptionLabel;

    private TextField nameField;
    private TextField descriptionField;

    private Button saveButton;

    private Pane mainPane;

    public ARSaveClonePane(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList) {
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobList = botJobList;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        //  Alert Timer Components
        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");
        countdownLabel.setVisible(false);
        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));

        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Title");
        alertToShow.setHeaderText("Header Message");
        alertToShow.setContentText("Main Message");
        alertToShow.initModality(Modality.APPLICATION_MODAL);
        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);
        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

        nameLabel = new Label("Name of new Bot Job:");
        descriptionLabel = new Label("Description:");
        nameField = new TextField(selecBotJobDTO.getName().trim());
        descriptionField = new TextField("Description");

        saveButton = new Button("Clone Bot Job");

        VBox group = new VBox(nameLabel, nameField, descriptionLabel, descriptionField, saveButton);
        group.setAlignment(Pos.CENTER);
        AnchorPane.setTopAnchor(group, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(group, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(group, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(group, ARConstants.SPACE_M);

        mainPane = new AnchorPane(group);
    }

    @Override
    public void initUIBehaviour() {

        saveButton.setOnMouseClicked(e -> {
            String newBotJobName = nameField.getText().trim();
            String newDescription = descriptionField.getText().trim();

            // Clean Spaces
            Platform.runLater(() -> nameField.setText(newBotJobName));

            if (Strings.isNullOrEmpty(nameField.getText().trim())) {
                performMessage.errorMessage(
                        "Select a new Bot Job name", "There is NOT a name defined", null, null, null, 0);
                return;
            }

            BotJobLoadDTO existBotJob = botJobList.stream()
                    .filter(botJob -> botJob.getName().equals(newBotJobName))
                    .findFirst()
                    .orElse(null); //

            if (existBotJob != null) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists",
                        "The name you have entered is already in use.",
                        "Please choose a different name and try again.",
                        null,
                        null,
                        0);
                return;
            }

            ARPropertyManager managerProps = ARPropertyManager.getInstance();
            String excelPath = managerProps.getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL);
            String originalFilePath =
                    excelPath + "\\" + selecBotJobDTO.getName().trim() + ".xlsx";
            String newFilePath = excelPath + "\\" + newBotJobName + ".xlsx";
            boolean excelCreation = duplicateExcelFile(originalFilePath, newFilePath);
            if (!excelCreation) {
                showAlertTimer(
                        Alert.AlertType.ERROR,
                        "Error",
                        "Error Duplicating File Excel",
                        "Excel File Name",
                        newBotJobName + ".xlsx",
                        null,
                        null);
                return;
            }

            int homeBankId = botJobList.get(0).getHomeBankingId();

            try (Connection conn = performDataBase.getConnection()) {
                int newBotJobId = performDataBase.getMaxId(conn, "bot_job") + 1;

                if (newBotJobId > -1) {

                    String[] arrayTables = {"block", "instruction", "reference", "complex_instruction", "variable"};
                    ErrorMessage errorMessage = performDataBase.duplicateBotJobById(
                            conn,
                            homeBankId,
                            selecBotJobDTO.getId(),
                            newBotJobId,
                            newBotJobName,
                            newDescription,
                            arrayTables);

                    if (errorMessage == null) {
                        performMessage.showCustomModalDialogDragWin11(
                                "Success: Bot Job Duplicated",
                                "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Bot Job Duplication Successful!</span> ✅",
                                "<span style='color: #1976D2;'>New Bot Job Details:</span>",
                                "<span style='font-weight: bold;'>ID:</span> " + newBotJobId + "<br>"
                                        + "<span style='font-weight: bold;'>Name:</span> '" + newBotJobName + "'",
                                "<span style='font-style: italic;'>Description: " + newDescription + "</span>",
                                false,
                                "OK",
                                null,
                                0);

                    } else {

                        String errorType = "Database error";
                        String errorDetail = "Verify  [INSERT] or [UPDATE] or [SELECT]";

                        performMessage.errorMessage(
                                "Error Encountered",
                                "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                                "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> " + errorType,
                                "<span style='font-style: italic;'>Detail:</span> " + errorDetail,
                                null,
                                0);
                    }
                } else {
                    performMessage.errorMessage(
                            "Access Denied",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Access Denied!</span> 🔒",
                            "<span style='color: #E65100;'>Cannot access the file.</span>",
                            "<span style='font-style: italic;'>Verify if the file is currently open in another application.</span>",
                            "<span style='font-style: italic;'>Please close the file in other applications and try again.</span>",
                            0);
                }

                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                stage.close();
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        });
    }

    //    private void clearBotJob(BotJobDTO botJob) {
    //        Queue<BlockDTO> blocks = new LinkedList<>(botJob.getBlocks());
    //        PerformDataBase..removeAllEntity(blocks, BlockDTO.class);
    //    }

    private boolean duplicateExcelFile(String originalFilePath, String newFilePath) {
        try {
            // Load the existing Excel file
            FileInputStream fis = new FileInputStream(new File(originalFilePath));
            Workbook workbook = new XSSFWorkbook(fis);

            // Create a new file output stream for the new file (to a restricted folder)
            FileOutputStream fos = new FileOutputStream(new File(newFilePath));

            // Write the workbook data to the new file
            workbook.write(fos);

            // Close all streams
            fos.close();
            fis.close();
            return true;
        } catch (IOException e) {
            String errorMessage = "Error occurred while copying the Excel file.";
            String errorDetails = "An error occurred while attempting to clone the file.";

            // Check if the exception message contains "Access is denied"
            if (e.getMessage() != null && e.getMessage().contains("Access is denied")) {
                errorDetails =
                        "Access Denied: You do not have permission to write to this location. Please check your permissions.";
            } else if (e instanceof FileNotFoundException) {
                File file = new File(newFilePath);
                if (!file.exists()
                        && file.getParentFile() != null
                        && !file.getParentFile().canWrite()) {
                    errorDetails =
                            "You don't have permission to write in the specified folder. Please check the folder's write permissions.";
                } else {
                    errorDetails = "The specified file path is invalid or the file is already in use.";
                }
            }

            // Show the error message
            performMessage.errorMessage("Excel File Cloning Error", errorMessage, errorDetails, null, null, 0);
            return false;
        }
    }

    private void showAlertTimer(
            Alert.AlertType alertType,
            String title,
            String header,
            String msg1,
            String msg2,
            String msg3,
            String msg4) {
        executorService = Executors.newSingleThreadExecutor();
        alertToShow.setAlertType(alertType);
        alertToShow.setTitle(title);
        alertToShow.setHeaderText(header);
        //        alertToShow.setContentText(content);

        // Remove the border of the DialogPane
        alertToShow.getDialogPane().setStyle("-fx-border-color: transparent; -fx-border-width: 0;");

        // Create VBox to hold multiple Text elements
        VBox allMsgVer = new VBox();
        allMsgVer.setSpacing(10); // Add some spacing between texts
        allMsgVer.setPadding(new Insets(20));

        Text variableText1Styled = new Text(msg1);
        Text variableText2Styled = new Text(msg2);
        Text variableText3Styled = new Text(msg3);
        Text variableText4Styled = new Text(msg4);

        // Set styles based on alert type
        if (alertType.equals(Alert.AlertType.ERROR)) {
            // Change the font color of the title to red
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
        } else {
            // Change the font color of the title to red
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
        }

        // Add Text elements to VBox
        if (msg1 != null && msg2 == null && msg3 == null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled);
        } else if (msg1 != null && msg2 != null && msg3 == null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled);
        } else if (msg1 != null && msg2 != null && msg3 != null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled, variableText3Styled);
        } else {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled, variableText3Styled);
        }

        // Create a StackPane to hold the VBox
        StackPane stackPane = new StackPane(allMsgVer);
        stackPane.setPadding(new Insets(20));

        // Set StackPane content to alert dialog pane
        alertToShow.getDialogPane().setContent(stackPane);

        executorService.execute(() -> {
            timeline.setCycleCount(SECONDS); // Run for seconds
            timeline.play(); // Start the timeline

            // Show the alert on the JavaFX Application Thread
            javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
        });

        if (executorService != null) {
            remainingSeconds = SECONDS;
            executorService.shutdown();
        }
    }
}
