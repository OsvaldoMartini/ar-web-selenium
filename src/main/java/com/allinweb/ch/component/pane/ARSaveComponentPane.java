package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.InstructionLoad;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javax.websocket.Session;

public class ARSaveComponentPane extends ARPane {

    protected static volatile ARSaveComponentPane instance;

    // Private constructor to prevent instantiation
    private ARSaveComponentPane() {

        super();
    }

    public static ARSaveComponentPane getInstance() {
        if (instance == null) {
            synchronized (ARSaveComponentPane.class) {
                if (instance == null) {
                    instance = new ARSaveComponentPane();
                }
            }
        }
        return instance;
    }

    public void initialize(BlockDetailsDTO blockDetailsDTO) {
        this.blockDetailsDTO = blockDetailsDTO;
    }

    private static Map<String, Session> activeSessions;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    private BlockDetailsDTO blockDetailsDTO;

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    private List<BlockLoadDTO> savedBlockLoadList = new ArrayList<>();
    private List<ComponentInstructionDTO> originalLoopInstruction;
    private List<ComponentReferenceDTO> originalReferences;

    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformActions performAction = PerformActions.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();

    TextField nameTextField;
    TextArea descriptionTextField;

    Text regularText;
    Text variableText1;
    Text variableText2;

    Label warningLabel;

    private Button saveNewComponentButton;
    Button closeButton;

    private AnchorPane mainPane;

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
        alertToShow.initModality(Modality.WINDOW_MODAL);
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

        saveNewComponentButton = builder.buildButton("Save New Component", ARConstants.SPACE_L);
        closeButton = builder.buildButton(" Close ", ARConstants.SPACE_L);

        HBox actionPanel = new HBox(saveNewComponentButton, closeButton);
        actionPanel.setSpacing(ARConstants.SPACE_SM);
        actionPanel.setAlignment(Pos.CENTER);

        Label nameLabel = new Label("Name :         ");

        nameTextField = new TextField(blockDetailsDTO.getBlockName());
        HBox nameHBox = new HBox(nameLabel, nameTextField);
        HBox.setHgrow(nameTextField, Priority.ALWAYS);
        HBox.setMargin(nameLabel, new Insets(ARConstants.SPACE_XS));

        Label descriptionLabel = new Label("Description : ");
        descriptionTextField = new TextArea(blockDetailsDTO.getBlockDescription());

        regularText = new Text("Excluded Special Operations: ");
        variableText1 = new Text("Set Value" + " / " + "Get Value");
        variableText2 = new Text("Check Value" + " / " + "Excel Save");
        variableText1.setFill(Color.BLUE);
        variableText2.setFill(Color.BLUE);

        HBox descriptionHBox = new HBox(descriptionLabel, descriptionTextField);
        HBox.setHgrow(descriptionTextField, Priority.ALWAYS);
        HBox.setMargin(descriptionLabel, new Insets(ARConstants.SPACE_XS));

        nameTextField.setMaxHeight(ARConstants.SPACE_XL);
        nameTextField.setPrefHeight(ARConstants.SPACE_XL);

        descriptionTextField.setMaxHeight(100);
        descriptionTextField.setPrefHeight(100);
        descriptionLabel.setMaxWidth(Double.MAX_VALUE);

        warningLabel = new Label();
        warningLabel.setMaxWidth(Double.MAX_VALUE);
        warningLabel.setTextFill(Color.RED);
        warningLabel.setAlignment(Pos.CENTER);

        VBox separtorPanel = new VBox(nameHBox, descriptionHBox, warningLabel, actionPanel);
        VBox.setVgrow(descriptionHBox, Priority.ALWAYS);
        separtorPanel.setMaxWidth(Double.MAX_VALUE);
        separtorPanel.setSpacing(ARConstants.SPACE_SM);

        // separtorPanel.setPrefWidth(400);

        AnchorPane.setTopAnchor(separtorPanel, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(separtorPanel, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(separtorPanel, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(separtorPanel, ARConstants.SPACE_M);

        mainPane = new AnchorPane(separtorPanel);
    }

    @Override
    public void initUIBehaviour() {

        saveNewComponentButton.setOnMouseClicked(e -> {
            //            PerformDataBase..cacheEntitiesFromDB();

            if (nameTextField.getText() != null
                    && !nameTextField.getText().trim().isEmpty()
                    && descriptionTextField.getText() != null
                    && !descriptionTextField.getText().trim().isEmpty()) {
                try {
                    // Ensure UI update happens on the JavaFX Application Thread
                    warningMSG("");

                    blockDetailsDTO.setBlockName(nameTextField.getText().trim());
                    blockDetailsDTO.setBlockDescription(
                            descriptionTextField.getText().trim());

                    this.savedBlockLoadList = performDataBase.loadSavedBlocksForBotJob(
                            blockDetailsDTO.getHomeBankingId(),
                            blockDetailsDTO.getBotJobId(),
                            blockDetailsDTO.getBotJobName());

                    //                    originalLoopInstruction =
                    // performDBSavedBlock.createSavedBlockLoopInstructionsFromBlocksDTO(
                    //                            detailsDTO, componentBlockDTO);

                    // Debugging: Ensure originalLoopInstruction has the right data
                    //                    ARLogger.getInstance(ARSaveComponentPane.class)
                    //                            .fine("originalLoopInstruction Size: " +
                    // originalLoopInstruction.size());

                    boolean existName = savedBlockLoadList.stream().anyMatch(block -> block.getName()
                            .equalsIgnoreCase(nameTextField.getText().trim()));

                    if (existName) {
                        performMessage.showCustomModalDialogDragWin11(
                                "Name Already Taken!",
                                "<span style='font-weight: bold;'>Change the Component Name.</span>",
                                "<span style='font-weight: bold; color: #e854c8;'>The Name: \""
                                        + nameTextField.getText().trim()
                                        + "\" Has been take!</span>, and after  will jump back to <span style='font-weight: bold;'>first block (Use Case).</span>",
                                null,
                                null,
                                false,
                                "OK",
                                null,
                                0);

                        return;
                    }

                    // Debugging: Print statements to track data
                    ARLogger.getInstance(ARSaveComponentPane.class)
                            .fine("Saving New Component Block: " + blockDetailsDTO.getBlockName());

                    try (Connection conn = performDataBase.getConnection()) {

                        //                        performDataBase.deleteNullBlocks("component_block",
                        // blockDetailsDTO.getHomeBankingId());

                        ErrorMessage errorMessage = performDataBase.createCompBlock(blockDetailsDTO);
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createCompInstructions(blockDetailsDTO);
                        }
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createCompVariables(blockDetailsDTO);
                        }
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createUpdateCompInstruction(blockDetailsDTO);
                        }
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createCompReferences(blockDetailsDTO);
                        }

                        if (errorMessage == null) {
                            performDataBase.loadBlocks(blockDetailsDTO.getHomeBankingId(), "", "component_block");
                            errorMessage = performDataBase.updateBlockOrderNumber(
                                    "component_block", blockDetailsDTO.getHomeBankingId(), true);
                        }

                        if (errorMessage == null) {

                            errorMessage = performDataBase.loadComponentsComplete(
                                    blockDetailsDTO.getHomeBankingId(),
                                    blockDetailsDTO.getBotJobId(),
                                    blockDetailsDTO.getBotJobName());

                            if (errorMessage != null) {
                                performMessage.errorMessage(
                                        errorMessage.getErrorTitle(),
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                                        "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                                + errorMessage.getErrorHeader(),
                                        "<span style='font-style: italic;'>Detail:</span> "
                                                + errorMessage.getErrorMessage(),
                                        null,
                                        0);
                            }

                            String jsonData = "[]";
                            if (!performLists.getListBotJobComp().isEmpty()) {
                                List<InstructionLoad> blockLoopInstructions = performDataBase.buildJsonViewData(
                                        performLists.getListBotJobComp(),
                                        blockDetailsDTO.getHomeBankingId(),
                                        "component_instruction");
                                jsonData = gson.toJson(blockLoopInstructions);
                            }
                            // simpleWebSocketServer.sendMessageJson(blockDetailsDTO.getSessionId(), jsonData,
                            // "componentsUpdate");
                            webSocketSessionManager.sendMessageJson(
                                    blockDetailsDTO.getHomeBankingId(), "componentTasks", jsonData, "componentsUpdate");
                        } else {
                            //                            performDataBase.deleteNullBlocks("component_block",
                            // blockDetailsDTO.getHomeBankingId());

                            performMessage.errorMessage(
                                    "Access Database error",
                                    errorMessage.getErrorTitle(),
                                    errorMessage.getErrorHeader(),
                                    "Verify  [INSERT] or [UPDATE] or [SELECT]",
                                    null,
                                    0);
                        }
                        ARLogger.getInstance(ARSaveComponentPane.class).finer("ARSaveComponentPane Close()");
                        Platform.runLater(() -> {
                            Stage stage =
                                    (Stage) ((Button) e.getSource()).getScene().getWindow();
                            stage.close();
                        });
                    } catch (SQLException error) {
                        System.out.println(error.getMessage());
                    }

                    // Ensure closing is done on the JavaFX Application Thread
                    Platform.runLater(this::Close);

                } catch (Exception error) {
                    // Handle the exception and display a warning message on the JavaFX Application Thread

                    ARLogger.getInstance(Task.class)
                            .severe("Error: Unable to save the block. Please try again.\nError: " + error.getMessage());

                    showAlertTimer(
                            Alert.AlertType.ERROR,
                            "Error Component",
                            "Unable to create new Component.",
                            "Error creating a new component",
                            "Please try again.",
                            null,
                            null);

                    warningMSG("Error creating a new componen! Please try again.");
                }

                //                PerformDataBase..changeDbConnection(previousDB);

                //                Close();
            } else {

                // Ensure UI update happens on the JavaFX Application Thread
                warningMSG("Warning: give the correct name and description");
            }
        });

        closeButton.setOnAction(e -> Close());
    }

    private void Close() {
        ARLogger.getInstance(ARSaveClonePane.class).finer("ARSaveClonePane Close()");
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
    }

    private void warningMSG(String msg) {
        Platform.runLater(() -> {
            warningLabel.setText(msg);
        });
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
