package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRElementValueScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.VariableUserDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.allinweb.ch.util.ComboBoxImage;
import com.allinweb.ch.util.ComboBoxOperator;
import com.allinweb.ch.util.ComboBoxVars;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRNewCommandPane extends ABRPane {

    private static final PerformActions performAction;

    static {
        performAction = PerformActions.getInstance();
    }

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";

    // Postgres
    private static boolean POSTGRES_DB = false;
    private Connection conn = null;

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    private RowMoveDTO rowMoveDTO;
    private Pane mainPane;

    Label commandLabel;
    Label botJobVarsLabel;
    Label webPageLabel;
    Text regularText;
    Text variableText1;
    Text variableText2;
    TextFlow textFlow;

    TextField nameField;
    TextField valueToBeChecked;

    private Button variableButton;

    private Button addWaitButton30;
    private Button addWaitButton15;
    private Button addWaitButton5;
    private Button addCloseActionButton;
    private Button addScreenButton;

    double buttonWidth = 200;
    double comboxWidth = 50;

    Button addInstructionButton;
    Button cancelButton;

    private ComboBox<ComboBoxImage> comboBoxInstruc;
    private ObservableList<ComboBoxImage> itemsInstructions = FXCollections.observableArrayList();
    ;

    private ComboBox<ComboBoxVars> comboBoxVars;
    private ObservableList<ComboBoxVars> variablesItems = FXCollections.observableArrayList();

    private ObservableList<VariableUserDTO> variablesList = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxWebPage;
    private ObservableList<ComboBoxVars> webPageItems;

    private ComboBox<ComboBoxOperator> comboBoxOperator;
    private ObservableList<ComboBoxOperator> operatorsItems = FXCollections.observableArrayList();

    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
    private ComboBox<ComboBoxVars> comboBoxBlocks;
    private ObservableList<ComboBoxVars> blocksItems = FXCollections.observableArrayList();

    public ABRNewCommandPane(
            RowMoveDTO rowMoveDTO, List<BlockLoadDTO> blockLoadList, ObservableList<ComboBoxVars> webPageItems) {
        this.rowMoveDTO = rowMoveDTO;
        this.blockLoadList = blockLoadList;
        this.webPageItems = webPageItems;

        String dataBaseType = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.DATABASE_TYPE);

        if (dataBaseType != null && dataBaseType.equalsIgnoreCase("POSTGRES")) {
            POSTGRES_DB = true;
        } else {
            POSTGRES_DB = false;
        }

        // Initialize database IF IS ACCESS TO BE USED
        if (!POSTGRES_DB) {
            initializeDatabase();
        }

        String operationType = rowMoveDTO.getType();
        String firstAction = rowMoveDTO.getUpdatedRows().get(0).getActions();

        // Initialize itemsInstructions list conditionally
        try {
            itemsInstructions = FXCollections.observableArrayList();
            itemsInstructions.add(
                    new ComboBoxImage("setValue", new Image(ABRConstants.ICON_SET_VALUE_BTN), ABRConstants.SET_VALUE));
            itemsInstructions.add(
                    new ComboBoxImage("getValue", new Image(ABRConstants.ICON_GET_VALUE_BTN), ABRConstants.GET_VALUE));
            itemsInstructions.add(
                    new ComboBoxImage("Check", new Image(ABRConstants.ICON_CHECK), ABRConstants.CHECK_VALUE));

            // Add "IF" only if it does not meet the exclusion conditions
            if (!("INSERT_AFTER".equals(operationType) && "IF".equalsIgnoreCase(firstAction))
                    && !"ELSE".equalsIgnoreCase(firstAction)
                    && !("INSERT_BEFORE".equals(operationType) && "ENDIF".equalsIgnoreCase(firstAction))) {

                itemsInstructions.add(new ComboBoxImage("IF", new Image(ABRConstants.ICON_IF_ELSE), ABRConstants.IF));
            }

            itemsInstructions.add(new ComboBoxImage("GO TO", new Image(ABRConstants.ICON_GOTO), ABRConstants.GOTO));
            itemsInstructions.add(
                    new ComboBoxImage("ExcelWrite", new Image(ABRConstants.ICON_EXCEL), ABRConstants.EXTRACT_FIELD));
        } catch (Exception ex) {
            ABRLogger.getInstance(ABRNewCommandPane.class)
                    .severe("Error creating \"DropBox Instructions\"\nError: " + ex.getMessage());
        }

        try {
            operatorsItems = FXCollections.observableArrayList(
                    new ComboBoxOperator("Equals", new Image(ABRConstants.ICON_EQUAL), "="),
                    new ComboBoxOperator("Greater", new Image(ABRConstants.ICON_GREATER), ">"));
        } catch (Exception ex) {
            ABRLogger.getInstance(ABRNewCommandPane.class)
                    .severe("Error creating \"DropBox Operators\"\nError: " + ex.getMessage());
        }

        if (itemsInstructions.isEmpty() || itemsInstructions.size() == 0) {
            itemsInstructions.add(
                    new ComboBoxImage("No Instructions", new Image(ABRConstants.ICON_GREATER), ABRConstants.NO_VALUE));
        }
        if (operatorsItems.size() == 0) {
            operatorsItems.add(
                    new ComboBoxOperator("No Operators", new Image(ABRConstants.ICON_GREATER), ABRConstants.NO_VALUE));
        }

        if (webPageItems.size() == 0) {
            webPageItems.add(new ComboBoxVars("No Web Fields", ABRConstants.NO_VALUE, -1, -1));
        }

        if (this.webPageItems != null && this.webPageItems.size() > 0) {
            loadJobVariables(webPageItems.get(0).getVarId());
        }

        if (this.blockLoadList != null && this.blockLoadList.size() > 0) {
            loadBlockItems(this.blockLoadList, rowMoveDTO.getBlockId());
        }
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {

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

        // Create labels
        commandLabel = new Label("Command:");
        botJobVarsLabel = new Label("Bot-Job Variable");
        webPageLabel = new Label("WebPage Field");

        textFlow = new TextFlow();
        // Create regular Text for the first part of the label
        regularText = new Text("Variable to SET : ");
        // Create Text for the variable part and set the color to red
        variableText1 = new Text("");
        variableText2 = new Text("");
        variableText1.setFill(Color.BLUE); // Set font color to blue
        variableText2.setFill(Color.RED); // Set font color to red

        textFlow.getChildren().addAll(regularText, variableText1, variableText2);

        valueToBeChecked = new TextField();

        comboBoxInstruc = new ComboBox<>(itemsInstructions);
        comboBoxInstruc.setPrefWidth(120); // Set preferred width of ComboBox
        comboBoxInstruc.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxImage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(20); // Set the width for icon size
                    imageView.setFitHeight(20); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });
        comboBoxInstruc.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxImage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(20); // Set the width for icon size
                    imageView.setFitHeight(20); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxInstruc.getSelectionModel().selectFirst();

        comboBoxOperator = new ComboBox<>(operatorsItems);
        comboBoxOperator.setPrefWidth(50);
        // Set cell factory to display images and text
        comboBoxOperator.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxOperator item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(15); // Set the width for icon size
                    imageView.setFitHeight(15); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });
        comboBoxOperator.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxOperator item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(15); // Set the width for icon size
                    imageView.setFitHeight(15); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxOperator.getSelectionModel().selectFirst();
        reloadComboVars();

        comboBoxVars = new ComboBox<>(variablesItems);
        comboBoxVars.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });
        comboBoxVars.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxVars.getSelectionModel().selectFirst();

        comboBoxBlocks = new ComboBox<>(blocksItems);
        if (blocksItems.size() == 0) {
            blocksItems.add(new ComboBoxVars("no blocks added", "", -1, -1));
        }
        comboBoxBlocks.setPrefWidth(80);
        comboBoxBlocks.getSelectionModel().selectFirst();
        comboBoxBlocks.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });
        comboBoxBlocks.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxBlocks.getSelectionModel().selectFirst();

        comboBoxWebPage = new ComboBox<>(webPageItems);
        comboBoxWebPage.setPrefWidth(50);
        comboBoxWebPage.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });
        comboBoxWebPage.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxWebPage.getSelectionModel().selectFirst();

        defineTextFlow(comboBoxInstruc.getValue().getValue());

        String css = getClass().getResource("/button.css").toExternalForm();

        addInstructionButton = componentBuilder.buildButton("OK", ABRConstants.SPACE_L, Insets.EMPTY);
        addInstructionButton.getStyleClass().add("ok-button");

        cancelButton = componentBuilder.buildButton("Close", ABRConstants.SPACE_L, Insets.EMPTY);
        cancelButton.getStyleClass().add("cancel-button");

        variableButton = componentBuilder.buildButton(
                "Variables", ABRConstants.SPACE_L, ABRConstants.ICON_VARIABLES, ABRConstants.SPACE_M, Insets.EMPTY);

        addWaitButton30 = componentBuilder.buildButton(
                "30s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addWaitButton15 = componentBuilder.buildButton(
                "15s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addWaitButton5 = componentBuilder.buildButton(
                "5s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addCloseActionButton = componentBuilder.buildButton(
                "Add Close Browser",
                ABRConstants.SPACE_L,
                ABRConstants.ICON_CROSS,
                ABRConstants.SPACE_M,
                new Insets(5));
        addScreenButton = componentBuilder.buildButton(
                "Add Screenshot", ABRConstants.SPACE_L, ABRConstants.ICON_SCREEN, ABRConstants.SPACE_M, new Insets(5));

        // Create a new HBox for the new buttons
        HBox buttonBox = new HBox(10); // 10 is the spacing between buttons
        buttonBox
                .getChildren()
                .addAll(addWaitButton30, addWaitButton15, addWaitButton5, addCloseActionButton, addScreenButton);
        buttonBox.setAlignment(Pos.BASELINE_LEFT); // Align buttons to the left

        // Create an HBox and add all three labels into the same row
        HBox labelRow = new HBox(10); // 10 is the spacing between the labels
        labelRow.getChildren().addAll(commandLabel, botJobVarsLabel, webPageLabel);
        labelRow.setAlignment(Pos.BASELINE_LEFT); // Align the labels to the left

        // Create HBox for comboBoxes
        HBox comboBoxesRow = new HBox(10);

        comboBoxInstruc.setPrefWidth(buttonWidth);
        comboBoxVars.setPrefWidth(buttonWidth);
        comboBoxWebPage.setPrefWidth(buttonWidth);

        // Handle the visibility of comboBoxBlocks
        comboBoxBlocks.setVisible(false); // Initially hidden
        comboBoxBlocks.setManaged(false); // Ensure it does not take up space when hidden

        // Create a listener (optional) to toggle visibility dynamically
        comboBoxBlocks.visibleProperty().addListener((obs, oldValue, newValue) -> {
            comboBoxBlocks.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                comboBoxBlocks.setPrefWidth(buttonWidth); // Restore width when visible
            }
        });

        // Create a listener (optional) to toggle visibility dynamically
        comboBoxVars.visibleProperty().addListener((obs, oldValue, newValue) -> {
            comboBoxVars.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                comboBoxVars.setPrefWidth(buttonWidth); // Restore width when visible
            }
        });

        HBox boxCombos = new HBox(comboBoxVars, comboBoxBlocks);

        VBox commandBox = new VBox(commandLabel, comboBoxInstruc);
        VBox varsBox = new VBox(botJobVarsLabel, boxCombos); // Here for the visualization
        VBox webFieldsBox = new VBox(webPageLabel, comboBoxWebPage);

        comboBoxesRow.getChildren().addAll(commandBox, varsBox, webFieldsBox);

        variableButton.setPrefWidth(buttonWidth - 50);
        valueToBeChecked.setPrefWidth(buttonWidth - 50);
        textFlow.setPrefWidth(buttonWidth);

        comboBoxOperator.setVisible(false);
        comboBoxOperator.setManaged(false);

        // Create a listener (optional) to toggle visibility dynamically
        comboBoxOperator.visibleProperty().addListener((obs, oldValue, newValue) -> {
            comboBoxOperator.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                comboBoxOperator.setPrefWidth(comboxWidth); // Restore width when visible
            }
        });

        // Create a listener (optional) to toggle visibility dynamically
        valueToBeChecked.visibleProperty().addListener((obs, oldValue, newValue) -> {
            valueToBeChecked.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                valueToBeChecked.setPrefWidth(buttonWidth - 50); // Restore width when visible
            }
        });

        textFlow.visibleProperty().addListener((obs, oldValue, newValue) -> {
            textFlow.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                textFlow.setPrefWidth(buttonWidth + 50); // Restore width when visible
            }
        });

        // Create an HBox for the variable button
        HBox variableButtonRow = new HBox(10, variableButton, textFlow, comboBoxOperator, valueToBeChecked);
        variableButtonRow.setAlignment(Pos.BASELINE_LEFT); // Align variableButton to the left

        // Create HBox for instruction and cancel buttons
        HBox instructionButtonsRow = new HBox(10, addInstructionButton, cancelButton);
        addInstructionButton.setPrefWidth(buttonWidth);
        cancelButton.setPrefWidth(buttonWidth);
        instructionButtonsRow.setAlignment(Pos.BASELINE_RIGHT); // Align buttons to the right

        // Combine all HBoxes into a VBox for vertical alignment
        VBox vbox = new VBox(20);
        vbox.getChildren()
                .addAll(
                        labelRow, // Web Page Label row
                        comboBoxesRow, // ComboBoxes row
                        variableButtonRow, // Variable Button row
                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                        );
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(10)); // Padding around the VBox

        // Adjust VBox properties for better alignment
        VBox.setVgrow(vbox, Priority.ALWAYS);

        // Use AnchorPane to ensure the VBox resizes with the window
        mainPane = new AnchorPane(vbox);
        mainPane.getStylesheets().add(css);

        AnchorPane.setTopAnchor(vbox, 0.0);
        AnchorPane.setBottomAnchor(vbox, 0.0);
        AnchorPane.setLeftAnchor(vbox, 0.0);
        AnchorPane.setRightAnchor(vbox, 0.0);
    }

    private void clearData() {
        nameField.clear();
        valueToBeChecked.clear();
    }

    @Override
    public void initUIBehaviour() {
        addWaitButton30.setOnAction(e -> addInstruction(
                "Wait 30second(s)", "Waiting action", ABRConstants.HOLD, 30, "", null, null, rowMoveDTO));
        addWaitButton15.setOnAction(e -> addInstruction(
                "Wait 15second(s)", "Waiting action", ABRConstants.HOLD, 15, "", null, null, rowMoveDTO));
        addWaitButton5.setOnAction(e ->
                addInstruction("Wait 5second(s)", "Waiting action", ABRConstants.HOLD, 5, "", null, null, rowMoveDTO));
        addCloseActionButton.setOnAction(e ->
                addInstruction("Close Browser", "Close Browser", ABRConstants.QUIT, 0, "", null, null, rowMoveDTO));

        addScreenButton.setOnAction(e -> addInstruction(
                "Screenshot Browser", "Screenshot Browser", ABRConstants.SCREEN, 0, "", null, null, rowMoveDTO));

        comboBoxOperator.setVisible(false);

        // Add a listener to comboBoxInstruc to handle selection changes
        comboBoxInstruc.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Set the visibility of comboBoxOperator based on the selected value
                if (ABRConstants.CHECK_VALUE.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    textFlow.setPrefWidth(buttonWidth + 100);

                    botJobVarsLabel.setText("Bot-Job Variable");
                    botJobVarsLabel.setVisible(true);
                    webPageLabel.setVisible(true);
                    comboBoxOperator.setVisible(true);
                    comboBoxWebPage.setVisible(true);

                    valueToBeChecked.setVisible(true);
                    variableButton.setVisible(true);

                    comboBoxVars.setVisible(true);
                    comboBoxVars.setPrefWidth(buttonWidth);
                    comboBoxBlocks.setVisible(false);
                } else if (ABRConstants.GOTO.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    textFlow.setPrefWidth(buttonWidth + 100);

                    botJobVarsLabel.setText("Block Destination");
                    botJobVarsLabel.setVisible(true);
                    webPageLabel.setVisible(false);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(false);
                    valueToBeChecked.setVisible(false);
                    variableButton.setVisible(false);

                    comboBoxVars.setVisible(false);
                    comboBoxBlocks.setVisible(true);
                    comboBoxBlocks.setPrefWidth(buttonWidth);
                } else if (ABRConstants.IF.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    textFlow.setPrefWidth(buttonWidth + 100);

                    botJobVarsLabel.setVisible(false);
                    webPageLabel.setVisible(false);
                    comboBoxBlocks.setVisible(false);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(false);
                    valueToBeChecked.setVisible(false);
                    variableButton.setVisible(false);
                    comboBoxVars.setVisible(false);
                } else {
                    defineTextFlow(newValue.getValue());

                    textFlow.setVisible(true);
                    textFlow.setPrefWidth(buttonWidth);

                    botJobVarsLabel.setText("Bot-Job Variable");
                    botJobVarsLabel.setVisible(true);
                    webPageLabel.setVisible(true);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(true);
                    variableButton.setVisible(true);

                    comboBoxVars.setVisible(true);
                    comboBoxVars.setPrefWidth(buttonWidth);
                    comboBoxBlocks.setVisible(false);

                    if (ABRConstants.GET_VALUE.equalsIgnoreCase(newValue.getValue())
                            || ABRConstants.EXTRACT_FIELD.equalsIgnoreCase(newValue.getValue())) {
                        valueToBeChecked.setVisible(false);
                        textFlow.setPrefWidth(buttonWidth + 100);
                    } else {
                        valueToBeChecked.setVisible(true);
                        textFlow.setPrefWidth(buttonWidth);
                    }
                }
            }
        });

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxVars.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Set the visibility of comboBoxOperator based on the selected value
                defineTextFlow(comboBoxInstruc.getValue().getValue());
            }
        });

        // Add a listener to comboBoxBlocks to handle selection changes
        comboBoxBlocks.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Set the visibility of comboBoxOperator based on the selected value
                defineTextFlow(comboBoxInstruc.getValue().getValue());
            }
        });

        this.addInstructionButton.setOnMouseClicked((e) -> {
            // Check if the current selected index is greater than the first index

            if (!comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.IF)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.GOTO)) {

                if (comboBoxVars.getValue() != null && comboBoxVars.getValue().getVarId() < 0) {
                    performAction.showAlert(
                            Alert.AlertType.ERROR,
                            "Error",
                            "No Variable Defined",
                            "Define a variable for: \""
                                    + comboBoxWebPage.getValue().getText() + "\"");
                    return;
                } else if (comboBoxInstruc.getSelectionModel().getSelectedIndex() < 0) {
                    performAction.showAlert(
                            Alert.AlertType.ERROR,
                            "Error",
                            "No Web Fields Defined",
                            "Select Web Fields (Web Elements)!");

                    return;
                }
            }

            if (comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.GOTO)
                    && blocksItems.size() == 1
                    && (comboBoxBlocks.getValue().getInstructionId() == -1)) {
                performAction.showAlert(
                        Alert.AlertType.ERROR,
                        "Error",
                        "No Blocks Defined",
                        "It must have ate least Two Blocks defined ");

                return;
            }

            if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("setValue")) {
                String setValueTo =
                        Strings.isNullOrEmpty(comboBoxVars.getValue().getValue())
                                ? "EMPTY"
                                : comboBoxVars.getValue().getValue();
                addInstruction(
                        "SetValue",
                        "SetValue",
                        ABRConstants.SET_VALUE,
                        1,
                        comboBoxVars.getValue().getText().substring(1).toLowerCase() + ":" + setValueTo,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getInstructionId(),
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("getValue")) {
                addInstruction(
                        "GetValue",
                        "GetValue",
                        ABRConstants.GET_VALUE,
                        1,
                        comboBoxVars.getValue().getText().substring(1).toLowerCase() + ":"
                                + comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getInstructionId(),
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("check")) {
                String checkValueFor =
                        Strings.isNullOrEmpty(comboBoxVars.getValue().getValue())
                                ? "EMPTY"
                                : comboBoxVars.getValue().getValue();

                addInstruction(
                        "Check",
                        "Check Value",
                        ABRConstants.CHECK_VALUE,
                        1,
                        comboBoxVars.getValue().getText().toLowerCase() + ":"
                                + comboBoxOperator.getValue().getOperator() + ":" + checkValueFor,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getInstructionId(),
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("excelWrite")) {
                addInstruction(
                        "ExcelWrite",
                        "ExcelWrite",
                        ABRConstants.EXTRACT_FIELD,
                        2,
                        "ExcelWrite" + ":" + comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getInstructionId(),
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("GO TO")) {
                addInstruction(
                        "GOTO",
                        "GOTO",
                        ABRConstants.GOTO,
                        1,
                        comboBoxBlocks.getValue().getText(),
                        null, // Block Order Number as VarId
                        comboBoxBlocks.getValue().getInstructionId(), // BLOCK ID as Parent Id
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("IF")) {
                addInstruction(
                        "IF",
                        "IF",
                        ABRConstants.IF,
                        1,
                        "IF",
                        null, // Block Order Number as VarId
                        null, // BLOCK ID as Parent Id
                        this.rowMoveDTO);
            }
        });

        // Add a listener to print the ID when the selection changes
        comboBoxWebPage.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ComboBoxVars>() {
            @Override
            public void changed(
                    ObservableValue<? extends ComboBoxVars> observable, ComboBoxVars oldValue, ComboBoxVars newValue) {
                if (newValue != null) {
                    loadJobVariables(newValue.getVarId());
                    reloadComboVars();
                    comboBoxVars.getSelectionModel().selectFirst();
                    if (comboBoxVars.getValue() != null) {
                        defineTextFlow(comboBoxInstruc.getValue().getValue());
                    }
                }
            }
        });

        cancelButton.setOnMouseClicked((e) -> {
            Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
            stage.close();
        });

        variableButton.setOnAction(e -> {
            if (this.rowMoveDTO != null && rowMoveDTO.getUpdatedRows().size() > 0) {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .info("creating variable for instruction Name "
                                + rowMoveDTO.getUpdatedRows().get(0).getInstructionName());
                ABRElementValueScene elementValueScene = new ABRElementValueScene(
                        rowMoveDTO.getBotJobId(),
                        //                        rowMoveDTO.getUpdatedRows().get(0).getInstructionId(),
                        //                        rowMoveDTO.getUpdatedRows().get(0).getInstructionName()
                        comboBoxWebPage.getValue().getVarId(),
                        comboBoxWebPage.getValue().getText(),
                        comboBoxWebPage.getValue().getValue());
                elementValueScene.showModal();
                loadJobVariables(comboBoxWebPage.getValue().getVarId());
                reloadComboVars();
                // Set ComboBox to first item
                comboBoxVars.getSelectionModel().selectFirst();
                if (comboBoxVars.getValue() != null) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());
                }

            } else {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .info("creating variable for instruction Name "
                                + comboBoxWebPage.getValue().getText());
                ABRElementValueScene elementValueScene = new ABRElementValueScene(
                        rowMoveDTO.getBotJobId(),
                        comboBoxWebPage.getValue().getVarId(),
                        comboBoxWebPage.getValue().getText(),
                        comboBoxWebPage.getValue().getValue());
                elementValueScene.showModal();
            }

            loadJobVariables(comboBoxWebPage.getValue().getVarId());
            reloadComboVars();
            // Set ComboBox to first item
            comboBoxVars.getSelectionModel().selectFirst();
            if (comboBoxVars.getValue() != null) {
                defineTextFlow(comboBoxInstruc.getValue().getValue());
            }
        });
    }

    private void defineTextFlow(String newValue) {

        String variableName = "NO VARIABLE";
        if (comboBoxVars != null && comboBoxVars.getValue() != null) {
            valueToBeChecked.setText(comboBoxVars.getValue().getValue());
            variableName = comboBoxVars.getValue().getText();
        } else {
            valueToBeChecked.setText("NO VARIABLES");
        }

        String webFieldName = "NO WEB FIELD";
        if (comboBoxVars != null && comboBoxVars.getValue() != null) {
            webFieldName = comboBoxWebPage.getValue().getText();
        }

        if (comboBoxWebPage != null && comboBoxWebPage.getValue() != null) {
            // Switch based on newValue and update variableText1 accordingly
            switch (newValue.toUpperCase()) {
                case ABRConstants.EXTRACT_FIELD:
                    regularText.setText("Excel Write: ");
                    variableText1.setText(" Excel Write value from ");
                    variableText2.setText(variableName);
                    break;
                case ABRConstants.GET_VALUE:
                    regularText.setText("GET: ");
                    variableText1.setText(" Get Variable value from ");
                    variableText2.setText(variableName);
                    break;
                case ABRConstants.SET_VALUE:
                    regularText.setText("SET: ");
                    variableText1.setText(" Set Web Field ");
                    variableText2.setText(webFieldName);
                    break;
                case ABRConstants.GOTO:
                    regularText.setText("GO TO: ");
                    variableText1.setText(" Go to The Block ");
                    variableText2.setText(comboBoxBlocks.getValue().getText());
                    break;
                case ABRConstants.IF:
                    regularText.setText("IF: ");
                    variableText1.setText(" IF ELSE ENDIF ");
                    variableText2.setText("SPECIAL COMMAND");
                    break;
                default:
                    regularText.setText("CHECK: ");
                    variableText1.setText(" Check variable ");
                    variableText2.setText(variableName);
                    break;
            }
        }
    }

    private void reloadComboVars() {
        if (variablesList != null && variablesList.size() > 0) {
            List<ComboBoxVars> variablesNames = variablesList.stream()
                    .map(variable -> new ComboBoxVars(
                            variable.getType().substring(0, 1) + variable.getName(),
                            variable.getValue(),
                            variable.getId(),
                            variable.getInstructionId()))
                    .collect(Collectors.toList());
            variablesItems.addAll(variablesNames);
        } else {
            variablesItems.add(new ComboBoxVars("no variables added", "", -1, -1));
        }
    }

    private void updateFields() {}

    private void clearFields() {}

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private boolean nameExists(String name) {
        for (VariableUserDTO dto : variablesList) {
            if (dto.getName().trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void initializeDatabase() {

        String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
        String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;

        File dbFile = new File(dbPath + ABRConstants.FILE_NAME_DB);
        if (!dbFile.exists()) {
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                try (Statement stmt = conn.createStatement()) {
                    String createTableSQL = "CREATE TABLE home_banking (" + "ID AUTOINCREMENT PRIMARY KEY, "
                            + "name TEXT, password TEXT, url TEXT, username TEXT, priority TEXT)";
                    stmt.executeUpdate(createTableSQL);
                }
                System.out.println(String.format("Database %s has bee created!", dbFile.getName()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(String.format("Database %s Already exist!", dbFile.getName()));
        }
    }

    private Integer loadNexIdData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM variable";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadJobVariables(int instructionId) {
        variablesItems.clear();
        variablesList.clear();
        String selectSQL = " SELECT vars.id, vars.type, vars.name, vars.value, COUNT(blk.variable_id) UsedVars "
                + " FROM variable vars "
                + " left join block_loop_instruction blk on blk.variable_id = vars.id "
                + " where bot_job_id = " + rowMoveDTO.getBotJobId()
                + " and  block_loop_instruction_id = " + instructionId
                + " group by vars.id, vars.type, vars.Name, vars.value ";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                Integer id = rs.getInt("ID");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String value = rs.getString("value");
                String usedVars = rs.getString("UsedVars");
                variablesList.add(
                        new VariableUserDTO(id, type, name, value, rowMoveDTO.getBotJobId(), instructionId, usedVars));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadBlockItems(List<BlockLoadDTO> blockLoadDTOList, int blockToAvoid) {
        blocksItems.clear();
        for (BlockLoadDTO block : blockLoadDTOList) {
            if (block.getId() != blockToAvoid)
                blocksItems.add(new ComboBoxVars(
                        block.getBlockOrderNumber() + "# " + block.getName(),
                        block.getName(),
                        block.getBlockOrderNumber(),
                        block.getId()));
        }
    }

    private void saveUserData(VariableUserDTO user) {
        // Generate a Unique-ID
        Integer hashCode = loadNexIdData() + 1;
        //        AlterSeq(hashCode);
        //        Integer hashCode = generateID();

        String insertSQL =
                "INSERT INTO variable (ID, type, Name, Value, bot_job_id, block_loop_instruction_id) VALUES ( "
                        + hashCode + ","
                        + "'" + user.getType() + "', "
                        + "'" + user.getName() + "', "
                        + "'" + user.getValue() + "', "
                        + "'" + user.getBotJobId() + "', "
                        + "'" + user.getInstructionId() + "')";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            System.out.println("Data saved successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateUserData(String id, VariableUserDTO user) {
        try {
            int userId = Integer.parseInt(id);

            String updateSQL = "UPDATE variable SET Name = '" + user.getName() + "', "
                    + " type = '" + user.getType() + "', "
                    + " value = '" + user.getValue() + "' "
                    + " WHERE ID = " + userId;
            try (Statement stmt =
                    ABRSharedResources.getInstance().getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    System.out.println("Data updated successfully.");
                } else {
                    System.out.println("No matching record found to update.");
                }
            } catch (SQLException e) {
                performAction.showAlert(
                        Alert.AlertType.ERROR,
                        "Error",
                        "MAX CHARACTERS LIMIT FOR ACCESS",
                        String.format(
                                "This '%s' \n cannot be updated with same name.\nError: %s",
                                user.getName(), e.getMessage()));
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format.");
        }
    }

    private void deleteUserData(String Id) {
        try {
            int variableId = Integer.parseInt(Id);
            String deleteSQL = "DELETE FROM variable WHERE ID = " + variableId;
            try (Statement stmt =
                    ABRSharedResources.getInstance().getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(deleteSQL);
                if (rowsAffected > 0) {
                    System.out.println("Data updated successfully.");
                } else {
                    System.out.println("No matching record found to update.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format.");
        }
    }

    @Override
    public void clearPane(Pane panel) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void showAlertTimer(Alert.AlertType alertType, String title, String header, String content) {
        executorService = Executors.newSingleThreadExecutor();
        alertToShow.setAlertType(alertType);
        alertToShow.setTitle(title);
        alertToShow.setHeaderText(header);
        alertToShow.setContentText(content);

        // Create a label to display the countdown
        Label countdownLabel = new Label(content);
        countdownLabel.setStyle("-fx-font-size: 22px;");
        countdownLabel.setVisible(true);
        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));
        alertToShow.getDialogPane().setContent(stackPane);

        executorService.execute(() -> {
            timeline.setCycleCount(SECONDS); // Run for SECONDS seconds
            timeline.play(); // Start the timeline

            // Show the alert on the JavaFX Application Thread
            javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
        });

        if (executorService != null) {
            remainingSeconds = SECONDS;
            executorService.shutdown();
        }
    }

    private void addInstruction(
            String name,
            String description,
            String actions,
            Integer onHold,
            String operation,
            Integer varId,
            Integer instructionId,
            RowMoveDTO rowMoveDTO) {

        // Create and show alert inside Platform.runLater
        BotJobDTO botJob = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, rowMoveDTO.getBotJobId());

        loadBlocksForBotJob(rowMoveDTO.getBotJobId());

        // Combine the texts using TextFlow

        Text extra = new Text(" value ");
        extra.setStyle("-fx-font-size: 18px;");

        if (actions.equalsIgnoreCase(ABRConstants.HOLD)
                || (actions.equalsIgnoreCase(ABRConstants.SCREEN))
                || actions.equalsIgnoreCase(ABRConstants.QUIT)) {
            extra.setText("");
            regularText.setText("");
            variableText1.setText(actions.equalsIgnoreCase(ABRConstants.HOLD) ? name : "Action:");
            variableText2.setText(name);
            valueToBeChecked.setText("");
        }

        // Create individual text elements with the necessary styling
        Text regularTextStyled = new Text(regularText.getText());
        regularTextStyled.setStyle("-fx-font-size: 18px; -fx-fill: black;");

        Text variableText1Styled = new Text(variableText1.getText());
        variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        Text arrowText = new Text(" -> ");
        arrowText.setStyle("-fx-font-size: 18px; -fx-fill: black;");

        Text variableText2Styled = new Text(variableText2.getText());
        variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");

        Text newTextStyled = new Text(valueToBeChecked.getText()); // Use the provided text
        newTextStyled.setStyle("-fx-font-size: 18px; -fx-fill: darkcyan;");

        // Create an HBox to hold the individual text elements
        HBox combinedTextContainer = new HBox();
        combinedTextContainer.setSpacing(5); // Add some spacing between the texts

        if (comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.SET_VALUE)
                || (comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.CHECK_VALUE))) {
            combinedTextContainer
                    .getChildren()
                    .addAll(
                            regularTextStyled,
                            variableText1Styled,
                            arrowText,
                            variableText2Styled,
                            extra,
                            newTextStyled);
        } else {
            combinedTextContainer
                    .getChildren()
                    .addAll(regularTextStyled, variableText1Styled, arrowText, variableText2Styled);
        }

        boolean alertResponse = performAction.showCombinedConfirmationDialog(
                "Add new Instruction",
                "Are you sure you want to Add the Instruction to the Bot-Job?",
                "",
                combinedTextContainer);

        if (alertResponse) {

            // Handle loop outside Platform.runLater to ensure multiple iterations
            int endifCount = actions.equalsIgnoreCase(ABRConstants.IF) ? 3 : 1;

            // Run the loop for adding multiple instructions
            String nextAction = null;
            int parentId = 0;
            for (int added = endifCount; added >= 1; added--) {

                boolean isShowAlert = added == 1;

                // Run the instruction add in a separate Task
                int newRowId = preFillInstruction(
                        nextAction == null ? name : nextAction,
                        nextAction == null ? description : nextAction,
                        nextAction == null ? actions : nextAction,
                        nextAction == null ? operation : nextAction,
                        onHold,
                        varId,
                        instructionId,
                        nextAction == null ? -1 : parentId,
                        rowMoveDTO,
                        botJob,
                        isShowAlert);

                if (Strings.isNullOrEmpty(nextAction)) {
                    nextAction = ABRConstants.ELSE;
                    parentId = newRowId;
                } else if (!Strings.isNullOrEmpty(nextAction) && nextAction.equals(ABRConstants.ELSE)) {
                    nextAction = ABRConstants.ENDIF;
                }
            }
        }
    }

    private boolean reorderInstructions(List<InstructionDTO> rowList) {
        int orderNumber = 1;

        // Iterate through the list and update the instructionOrderNumber
        for (InstructionDTO instruction : rowList) {
            instruction.setInstructionOrderNumber(orderNumber);
            orderNumber++; // Increment the order number for the next instruction
        }

        // Build the SQL update statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            // Loop through each instruction in the rowList
            for (InstructionDTO instruction : rowList) {
                // Increment the instructionOrderNumber by 1 for each instruction
                String updateSQL = "UPDATE block_loop_instruction SET  "
                        + " instruction_order_number = " + instruction.getInstructionOrderNumber()
                        + " WHERE id = " + instruction.getInstructionId()
                        + " AND block_id = " + instruction.getBlockId();

                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRNewCommandPane.class)
                            .warning(String.format(
                                    "preInsertStep - InstructionId: %s in BlockId: %s now has order number: %d",
                                    instruction.getInstructionId(),
                                    instruction.getBlockId(),
                                    instruction.getInstructionOrderNumber() + 1));
                } else {
                    ABRLogger.getInstance(ABRNewCommandPane.class)
                            .warning(String.format(
                                    "preInsertStep - No matching record found for BlockId: %d and InstructionId: %d",
                                    instruction.getBlockId(), instruction.getInstructionId()));
                }
            }

            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRNewCommandPane.class)
                    .severe(String.format("Error updating instruction order numbers.\nError: %s", e.getMessage()));
        }
        return false;
    }

    private boolean preInsertStep(RowMoveDTO rowMoveDTO, List<InstructionDTO> rowList) {
        // Check if the operation type is either "INSERT_BEFORE" or "INSERT_AFTER"
        String operationType = rowMoveDTO.getType();
        if ("INSERT_BEFORE".equals(operationType) || "INSERT_AFTER".equals(operationType)) {
            // Get the instruction order number from the first instruction in the updated rows
            int targetOrderNumber = rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber();

            // Check if the targetOrderNumber exists in the rowList
            boolean orderNumberExists = rowList.stream()
                    .anyMatch(instruction -> instruction.getInstructionOrderNumber() == targetOrderNumber);

            if (!orderNumberExists) {
                // If the target order number doesn't exist, return false without shifting
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .warning(String.format(
                                "preInsertStep - Target order number %d does not exist in the row list.",
                                targetOrderNumber));
                return false;
            }

            // Build the SQL update statement
            try (Statement stmt =
                    ABRSharedResources.getInstance().getConnection().createStatement()) {
                // Loop through each instruction in the rowList
                for (InstructionDTO instruction : rowList) {
                    // For "INSERT_BEFORE", shift instructions with an order number greater than or equal to the target
                    // For "INSERT_AFTER", shift instructions with an order number strictly greater than the target
                    boolean shouldShift = "INSERT_BEFORE".equals(operationType)
                            ? instruction.getInstructionOrderNumber() >= targetOrderNumber
                            : instruction.getInstructionOrderNumber() > targetOrderNumber;

                    if (shouldShift) {
                        // Increment the instructionOrderNumber by 1 for each instruction
                        String updateSQL = "UPDATE block_loop_instruction SET  "
                                + " instruction_order_number = " + (instruction.getInstructionOrderNumber() + 1)
                                + " WHERE id = " + instruction.getInstructionId()
                                + " AND block_id = " + instruction.getBlockId();

                        int rowsAffected = stmt.executeUpdate(updateSQL);
                        if (rowsAffected > 0) {
                            ABRLogger.getInstance(ABRNewCommandPane.class)
                                    .warning(String.format(
                                            "preInsertStep - InstructionId: %s in BlockId: %s now has order number: %d",
                                            instruction.getInstructionId(),
                                            instruction.getBlockId(),
                                            instruction.getInstructionOrderNumber() + 1));
                        } else {
                            ABRLogger.getInstance(ABRNewCommandPane.class)
                                    .warning(String.format(
                                            "preInsertStep - No matching record found for BlockId: %d and InstructionId: %d",
                                            instruction.getBlockId(), instruction.getInstructionId()));
                        }
                    }
                }
                return true;
            } catch (SQLException e) {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .severe(String.format("Error updating instruction order numbers.\nError: %s", e.getMessage()));
            }
        }
        return false;
    }

    public List<InstructionDTO> getInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<InstructionDTO> instructions = new ArrayList<>();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM block_loop_instruction WHERE block_id = " + blockId
                + " order by instruction_order_number ASC";

        // Execute the query and process the result set
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                // Assuming you have an Instruction class, populate it with data from the ResultSet
                InstructionDTO instruction = new InstructionDTO();
                instruction.setInstructionId(rs.getInt("id"));
                instruction.setInstructionName(rs.getString("name"));
                instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                instruction.setBlockId(rs.getInt("block_id"));
                instruction.setBlockOrderNumber(instruction.getBlockOrderNumber());
                instruction.setBotJobId(botJobId);

                instruction.setActions(rs.getString("actions"));
                instruction.setPath(rs.getString("path"));
                instruction.setDescription(rs.getString("description"));
                instruction.setOptional(rs.getInt("optional"));
                instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                instruction.setEncrypted(rs.getInt("encrypted"));
                instruction.setExportToABR(rs.getInt("export_to_abr"));

                // Add the instruction to the list
                instructions.add(instruction);
            }

            ABRLogger.getInstance(ABRNewCommandPane.class)
                    .info(String.format("Fetched %d instructions for Block ID %d:", instructions.size(), blockId));

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRNewCommandPane.class)
                    .severe(String.format(
                            "Error fetching instructions for Block ID %d. Error: %s: ", blockId, e.getMessage()));
        }

        return instructions;
    }

    public List<BlockLoadDTO> loadBlocksForBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT " + "b.id AS block_id, "
                + "b.block_order_number, "
                + "b.name AS block_name, "
                + "b.description AS block_description, "
                + "b.type_id, "
                + "bj.id AS bot_job_id, "
                + "bj.name AS bot_job_name "
                + "FROM bot_job bj "
                + "JOIN block b ON b.bot_job_id = bj.id "
                + "WHERE bj.id = "
                + botJobId + " " + // Use the botJobId directly in the query string
                "ORDER BY b.block_order_number ASC";

        // Initialize the necessary data structures
        blockLoadList.clear();
        Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();

        // Use Statement to execute the query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // Load the Block information
                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMap.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setBotJobId(rs.getInt("bot_job_id"));
                    blockDTO.setBotJobName(rs.getString("bot_job_name"));

                    blockMap.put(blockId, blockDTO);
                    blockLoadList.add(blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return blockLoadList;
    }

    private void runAddInstructionTask(
            String name,
            String description,
            String actions,
            String operation,
            Integer onHold,
            Integer varId,
            Integer instructionId,
            RowMoveDTO rowMoveDTO,
            BotJobDTO botJob,
            boolean isShowAlert) {

        List<InstructionDTO> rowList = getInstructionsByBlockId(rowMoveDTO.getBotJobId(), rowMoveDTO.getBlockId());

        reorderInstructions(rowList);

        preInsertStep(rowMoveDTO, rowList);

        List<BlockLoopInstructionLoadDTO> instructionList = null;
        List<BlockLoadDTO> matchingBlocks = null;

        if (rowMoveDTO != null && rowMoveDTO.getUpdatedRows().size() > 0) {
            int targetBlockId = rowMoveDTO.getUpdatedRows().get(0).getBlockId();

            matchingBlocks = blockLoadList.stream()
                    .filter(block -> block.getId() == targetBlockId)
                    .collect(Collectors.toList());
        }

        List<BlockLoadDTO> finalMatchingBlocks = matchingBlocks;
        List<InstructionDTO> finalInstructionList = rowList;
        Task<Void> waitTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    BlockLoopInstructionDTO instruction = new BlockLoopInstructionDTO();

                    instruction.setName(name);

                    instruction.setEncrypted(false);
                    instruction.setExportToABR(true);
                    if (rowMoveDTO != null && rowMoveDTO.getUpdatedRows().size() > 0) {
                        if ("INSERT_BEFORE".equals(rowMoveDTO.getType())) {
                            instruction.setInstructionOrderNumber(
                                    rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber());
                        } else {
                            instruction.setInstructionOrderNumber(
                                    rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber() + 1);
                        }
                    } else {
                        instruction.setInstructionOrderNumber(finalMatchingBlocks.size() + 1);
                    }
                    instruction.setOptional(false);

                    instruction.setVariableId(varId);
                    instruction.setParentId(instructionId);

                    instruction.setOperation(operation);
                    instruction.setActions(actions);
                    instruction.setDescription(description);

                    instruction.setActionCustomMaxWaitSec(30);
                    instruction.setOnHoldSeconds(onHold);
                    if (finalMatchingBlocks != null) {
                        instruction.setBlock(ABRSharedResources.getInstance()
                                .getEntityById(
                                        BlockDTO.class,
                                        finalMatchingBlocks.get(0).getId()));
                    } else {
                        instruction.setBlock(botJob.getBlocks().get(0));
                    }
                    instruction.setExportToABR(false);

                    // Wrap the persistence in a try-catch block
                    try {
                        ABRSharedResources.getInstance().addEntity(instruction, BlockLoopInstructionDTO.class);
                    } catch (Exception e) {
                        performAction.showAlert(
                                Alert.AlertType.ERROR,
                                "Error while saving instruction",
                                "Error while saving instruction",
                                "Error Inserting Instruction \n" + instruction.getName() + "\"!");

                        ABRLogger.getInstance(ABRNewCommandPane.class)
                                .severe(String.format("Error Adding new instruction.\nError: %s", e.getMessage()));
                    }

                    // Move the UI update to the JavaFX Application Thread
                    Platform.runLater(() -> {
                        // This makes insertion in a Roll after the Target Position
                        int targetOrderNumber =
                                rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber();
                        rowMoveDTO.getUpdatedRows().get(0).setInstructionOrderNumber(targetOrderNumber + 1);
                        if (isShowAlert) {
                            performAction.showAlert(
                                    Alert.AlertType.INFORMATION,
                                    "News Instruction Add",
                                    "Instruction Added",
                                    "Instruction " + instruction.getName() + " has been added successfully");
                        }
                    });
                } catch (Exception ex) {
                    ABRLogger.getInstance(ABRNewCommandPane.class)
                            .severe(String.format("Error Adding new instruction.\nError: %s", ex.getMessage()));

                    performAction.showAlert(
                            Alert.AlertType.ERROR,
                            "Error Add New Instruction",
                            "Not possible to inser new Operation",
                            ex.getMessage());
                }
                return null;
            }
        };

        new Thread(waitTask).start();
    }

    private int preFillInstruction(
            String name,
            String description,
            String actions,
            String operation,
            Integer onHold,
            Integer varId,
            Integer instructionId,
            Integer parentId,
            RowMoveDTO rowMoveDTO,
            BotJobDTO botJob,
            boolean isShowAlert) {

        List<InstructionDTO> rowList = getInstructionsByBlockId(rowMoveDTO.getBotJobId(), rowMoveDTO.getBlockId());

        reorderInstructions(rowList);

        preInsertStep(rowMoveDTO, rowList);

        List<BlockLoopInstructionLoadDTO> instructionList = null;
        List<BlockLoadDTO> matchingBlocks = null;

        if (rowMoveDTO != null && rowMoveDTO.getUpdatedRows().size() > 0) {
            int targetBlockId = rowMoveDTO.getUpdatedRows().get(0).getBlockId();

            matchingBlocks = blockLoadList.stream()
                    .filter(block -> block.getId() == targetBlockId)
                    .collect(Collectors.toList());
        }

        List<BlockLoadDTO> finalMatchingBlocks = matchingBlocks;
        List<InstructionDTO> finalInstructionList = rowList;
        BlockLoopInstructionDTO instruction = new BlockLoopInstructionDTO();

        instruction.setName(name);

        instruction.setEncrypted(false);
        instruction.setExportToABR(true);
        if (rowMoveDTO != null && rowMoveDTO.getUpdatedRows().size() > 0) {
            if ("INSERT_BEFORE".equals(rowMoveDTO.getType())) {
                instruction.setInstructionOrderNumber(
                        rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber());
            } else {
                instruction.setInstructionOrderNumber(
                        rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber() + 1);
            }
        } else {
            instruction.setInstructionOrderNumber(finalMatchingBlocks.size() + 1);
        }
        instruction.setOptional(false);

        instruction.setOperation(operation);
        instruction.setActions(actions);
        instruction.setDescription(description);

        instruction.setVariableId(varId);

        Integer nextId = loadNextIdInstructionData() + 1;

        if (actions.equalsIgnoreCase(ABRConstants.IF)) {
            instruction.setId(nextId);
            instruction.setParentId(nextId);
        } else if (actions.equalsIgnoreCase(ABRConstants.ELSE)) {
            instruction.setId(nextId);
            instruction.setParentId(parentId);
        } else if (actions.equalsIgnoreCase(ABRConstants.ENDIF)) {
            instruction.setId(nextId);
            instruction.setParentId(parentId);
        } else {
            instruction.setId(nextId);
            instruction.setParentId(instructionId);
        }

        instruction.setActionCustomMaxWaitSec(30);
        instruction.setOnHoldSeconds(onHold);
        if (finalMatchingBlocks != null) {
            instruction.setBlock(ABRSharedResources.getInstance()
                    .getEntityById(BlockDTO.class, finalMatchingBlocks.get(0).getId()));
        } else {
            instruction.setBlock(botJob.getBlocks().get(0));
        }
        instruction.setExportToABR(false);
        // Wrap the persistence in a try-catch block
        boolean response;

        try {
            response = insertInstruction(instruction);

            int targetOrderNumber = rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber();
            rowMoveDTO.getUpdatedRows().get(0).setInstructionOrderNumber(targetOrderNumber + 1);

            boolean finalResponse = response;
            Platform.runLater(() -> {
                if (isShowAlert) {
                    if (finalResponse) {

                        ABRLogger.getInstance(ABRViewBotJobPane.class)
                                .info(String.format(
                                        "\"Component\" Instruction: \"%s\"\nhas been added successfully!",
                                        instruction.getName()));
                        showAlertTimer(
                                Alert.AlertType.INFORMATION,
                                "Add Instruction",
                                "Instruction Added",
                                "Instruction \"" + instruction.getName() + "\" has been added successfully");
                    } else {

                        ABRLogger.getInstance(ABRViewBotJobPane.class)
                                .severe(String.format(
                                        "Error Add New \"Component\" Instruction: \"%s\"\nCannot be saved!",
                                        instruction.getName()));

                        showAlertTimer(
                                Alert.AlertType.ERROR,
                                "Error",
                                "Error Add New Instruction",
                                "Not possible to insert new Operation:  \"" + instruction.getName() + "\"");
                    }
                }
            });

            if (response) {
                return nextId;
            }

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class)
                    .severe("Cannot Insert Instruction\nError: " + e.getMessage());
        }

        return -1;
    }

    private boolean insertInstruction(BlockLoopInstructionDTO instructionDTO) throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            //            Integer nextId = loadNextIdInstructionData() + 1;

            String pathValue = (instructionDTO.getPath() != null) ? "'" + instructionDTO.getPath() + "'" : "null";

            // Build the SQL insert query

            String insertSQL = "INSERT INTO block_loop_instruction(\n" + "id, "
                    + "action_custom_max_wait_sec, "
                    + "actions, "
                    + "block_marked, "
                    + "default_val, "
                    + "description, "
                    + "encrypted, "
                    + "export_to_abr, "
                    + "instruction_order_number, "
                    + "name, "
                    + "on_hold_seconds, "
                    + "operation, "
                    + "optional, "
                    + "parent_id, "
                    + "path, "
                    + "variable_id, "
                    + "block_id)\n"
                    + "VALUES ("
                    + instructionDTO.getId()
                    + ", " + instructionDTO.getActionCustomMaxWaitSec()
                    + ", '" + instructionDTO.getActions() + "'"
                    + ", " + (instructionDTO.isBlockMarked() ? "true" : "false")
                    + ", '" + instructionDTO.getDefaultValue() + "'"
                    + ", '" + instructionDTO.getDescription() + "'"
                    + ", " + (instructionDTO.isEncrypted() ? 1 : 0)
                    + ", " + (instructionDTO.getExportToABR() ? 1 : 0)
                    + ", " + instructionDTO.getInstructionOrderNumber()
                    + ", '" + instructionDTO.getName() + "'"
                    + ", " + instructionDTO.getOnHoldSeconds()
                    + ", '" + instructionDTO.getOperation() + "'"
                    + ", " + (instructionDTO.isOptional() ? 1 : 0)
                    + ", " + instructionDTO.getParentId()
                    + ", " + pathValue
                    + ", " + instructionDTO.getVariableId()
                    + ", " + instructionDTO.getBlock().getId()
                    + ");";

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY\nid: %d\nName: %s\nActions: %s\nOperation: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return true;
            } else {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .warning(String.format(
                                "Instruction NOT SAVED\nid: %d\nName: %s\nActions: %s\nOperations: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return false;
            }
        }
    }

    private Integer loadNextIdInstructionData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block_loop_instruction";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class)
                    .severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
        }
        return null;
    }
}
