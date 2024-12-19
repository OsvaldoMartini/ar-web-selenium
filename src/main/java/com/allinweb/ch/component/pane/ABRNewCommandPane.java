package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRElementValueScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
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

    private static final PerformDataBase performDataBase;
    private static final PerformActions performAction;

    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
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

    //    private HBox labelRow;

    private HBox boxCombos;
    private VBox commandBox;
    private VBox varsBox;
    private VBox webFieldsBox;
    private VBox blocksBox;
    private VBox addNewsBox;

    private HBox comboBoxesRow;
    private HBox variableButtonRow;
    private HBox buttonBox;
    private HBox instructionButtonsRow;
    private VBox vboxAll;

    Label commandLabel;
    Label botJobVarsLabel;
    Label webPageLabel;
    Label blocksLabel;
    Label addNewsLabel;

    Text timesText;
    Text loopText;
    Text regularText1;
    Text regularText2;
    Text regularText3;
    Text regularText4;
    Text variableText1;
    Text variableText2;
    Text variableText3;
    TextFlow textFlow;
    Text blankText;

    TextField nameField;

    private Button variableButton;

    private Button addPauseButton;
    private Button addWaitButton30;
    private Button addWaitButton15;
    private Button addWaitButton5;
    private Button addWaitButton2;
    private Button addCloseActionButton;
    private Button addScreenButton;

    double buttonWidth = 200;
    double blockAddNewsWidth = 200;
    double comboOperatorWidth = 50;
    double comboTimesWidth = 70;
    double comboLoopsWidth = 80;

    Button addNewInstructionButton;
    Button cancelButton;

    private ComboBox<ComboBoxImage> comboBoxInstruc;
    private ObservableList<ComboBoxImage> itemsInstructions = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxVars;
    private ObservableList<ComboBoxVars> variablesItems = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxTimes;
    private ObservableList<ComboBoxVars> timesItems = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxLoops;
    private ObservableList<ComboBoxVars> loopsItems = FXCollections.observableArrayList();

    private ObservableList<VariableUserDTO> variablesList = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxWebPage;
    private ObservableList<ComboBoxVars> webPageItems;

    private List<BotJobLoadDTO> botJobLoadList;

    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
    private ComboBox<ComboBoxVars> comboBoxAllBlocks;
    private ObservableList<ComboBoxVars> allBlocksItems = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxBlocks;
    private ObservableList<ComboBoxVars> blocksItems = FXCollections.observableArrayList();

    private ComboBox<ComboBoxOperator> comboBoxOperator;
    private ObservableList<ComboBoxOperator> operatorsItems = FXCollections.observableArrayList();

    public ABRNewCommandPane(
            RowMoveDTO rowMoveDTO, List<BotJobLoadDTO> botJobLoadList, ObservableList<ComboBoxVars> webPageItems) {
        this.rowMoveDTO = rowMoveDTO;
        this.botJobLoadList = botJobLoadList;
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
        String firstAction = "";
        if (rowMoveDTO.getUpdatedRows().size() > 0) {
            firstAction = rowMoveDTO.getUpdatedRows().get(0).getActions();
        } else {
            firstAction = rowMoveDTO.getType();
        }

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
            if (!rowMoveDTO.isBetween()) {

                itemsInstructions.add(new ComboBoxImage("IF", new Image(ABRConstants.ICON_IF_ELSE), ABRConstants.IF));
            }

            itemsInstructions.add(new ComboBoxImage("GO TO", new Image(ABRConstants.ICON_GOTO), ABRConstants.GOTO));
            itemsInstructions.add(
                    new ComboBoxImage("ExcelWrite", new Image(ABRConstants.ICON_EXCEL), ABRConstants.EXTRACT_FIELD));

            itemsInstructions.add(
                    new ComboBoxImage("Refresh", new Image(ABRConstants.ICON_REFRESH_ONLY), ABRConstants.REFRESH_ONLY));
            itemsInstructions.add(
                    new ComboBoxImage("Loop", new Image(ABRConstants.ICON_REFRESH_LOOP), ABRConstants.LOOP));
            itemsInstructions.add(new ComboBoxImage(
                    "Refresh Loop", new Image(ABRConstants.ICON_REFRESH_LOOP), ABRConstants.REFRESH_LOOP));

        } catch (Exception ex) {
            ABRLogger.getInstance(ABRNewCommandPane.class)
                    .severe("Error creating \"DropBox Instructions\"\nError: " + ex.getMessage());
        }

        try {
            operatorsItems = FXCollections.observableArrayList(
                    new ComboBoxOperator("Equals", new Image(ABRConstants.ICON_EQUAL), "="),
                    new ComboBoxOperator("Greater", new Image(ABRConstants.ICON_GREATER), ">"),
                    new ComboBoxOperator("!=", new Image(ABRConstants.ICON_DIFFERENT), "!="));
        } catch (Exception ex) {
            ABRLogger.getInstance(ABRNewCommandPane.class)
                    .severe("Error creating \"DropBox Operators\"\nError: " + ex.getMessage());
        }

        if (itemsInstructions.isEmpty() || itemsInstructions.size() == 0) {
            itemsInstructions.add(
                    new ComboBoxImage("No Instructions", new Image(ABRConstants.ICON_BLANK), ABRConstants.NO_VALUE));
        }
        if (operatorsItems.size() == 0) {
            operatorsItems.add(
                    new ComboBoxOperator("No Operators", new Image(ABRConstants.ICON_BLANK), ABRConstants.NO_VALUE));
        }

        if (webPageItems.size() == 0) {
            webPageItems.add(new ComboBoxVars("No Web Fields", ABRConstants.NO_VALUE, -1, -1));
        }

        if (this.webPageItems != null && this.webPageItems.size() > 0) {
            loadJobVariables(webPageItems.get(0).getVarId());
        }

        if (this.botJobLoadList != null && this.botJobLoadList.size() > 0) {
            for (BotJobLoadDTO botJobLoadDTO : this.botJobLoadList) {
                loadBlockItems(botJobLoadDTO.getBlockLoadDTOList(), rowMoveDTO.getBlockId());
            }
            for (BotJobLoadDTO botJobLoadDTO : this.botJobLoadList) {}

            for (BotJobLoadDTO botJobLoadDTO : this.botJobLoadList) {
                loadAllBlockItems(botJobLoadDTO.getBlockLoadDTOList());
            }
        } else {
            allBlocksItems.add(new ComboBoxVars("#1 Default Block", "Default Block", 1, 1));
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
        blocksLabel = new Label("Block Destination");
        addNewsLabel = new Label("Block to Add the New Instruction");

        timesText = new Text("Times");
        timesText.setStyle("-fx-font-size: 14px; -fx-fill: blue;");
        loopText = new Text("Loop");
        loopText.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

        textFlow = new TextFlow();
        // Create regular Text for the first part of the label
        regularText1 = new Text("Variable to SET : ");
        regularText2 = new Text("");
        regularText3 = new Text("");
        regularText4 = new Text("");

        // Create Text for the variable part and set the color to red
        variableText1 = new Text("");
        variableText2 = new Text("");
        variableText3 = new Text("");
        variableText1.setFill(Color.BLUE); // Set font color to blue
        variableText2.setFill(Color.RED); // Set font color to red

        blankText = new Text("  ");

        textFlow.getChildren().addAll(regularText1, variableText1, variableText2, variableText3);

        timesItems.add(new ComboBoxVars("5s", "5", -1, -1));
        timesItems.add(new ComboBoxVars("10s", "10", -1, -1));
        timesItems.add(new ComboBoxVars("20s", "20", -1, -1));
        timesItems.add(new ComboBoxVars("30s", "30", -1, -1));
        timesItems.add(new ComboBoxVars("40s", "40", -1, -1));
        timesItems.add(new ComboBoxVars("50s", "50", -1, -1));
        timesItems.add(new ComboBoxVars("60s", "60", -1, -1));

        comboBoxTimes = new ComboBox<>(timesItems);
        comboBoxTimes.setPrefWidth(50);
        // Set cell factory to display images and text
        comboBoxTimes.setButtonCell(new ListCell<>() {
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
        comboBoxTimes.setCellFactory(param -> new ListCell<>() {
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
        comboBoxTimes.getSelectionModel().selectFirst();

        loopsItems.add(new ComboBoxVars("5 x", "5", -1, -1));
        loopsItems.add(new ComboBoxVars("10 x", "10", -1, -1));
        loopsItems.add(new ComboBoxVars("20 x", "20", -1, -1));
        loopsItems.add(new ComboBoxVars("30 x", "30", -1, -1));
        comboBoxLoops = new ComboBox<>(loopsItems);
        comboBoxLoops.setPrefWidth(60);
        // Set cell factory to display images and text
        comboBoxLoops.setButtonCell(new ListCell<>() {
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
        comboBoxLoops.setCellFactory(param -> new ListCell<>() {
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
        comboBoxLoops.getSelectionModel().selectFirst();

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
        comboBoxBlocks.setPrefWidth(buttonWidth);
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

        comboBoxAllBlocks = new ComboBox<>(allBlocksItems);
        comboBoxAllBlocks.setPrefWidth(50);
        comboBoxAllBlocks.setButtonCell(new ListCell<>() {
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
        comboBoxAllBlocks.setCellFactory(param -> new ListCell<>() {
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

        if (rowMoveDTO.getBlockId() > -1) {
            // Get the blockId to match
            int targetBlockId = rowMoveDTO.getBlockId();

            // Iterate through items in comboBoxAllBlocks
            for (ComboBoxVars item : comboBoxAllBlocks.getItems()) {
                if (item.getInstructionId() != null && item.getInstructionId() == targetBlockId) {
                    comboBoxAllBlocks.getSelectionModel().select(item); // Select the matching item
                }
            }
        } else {
            // If blockId is not valid, select the first item
            comboBoxAllBlocks.getSelectionModel().selectFirst();
        }

        defineTextFlow(comboBoxInstruc.getValue().getValue());

        addNewInstructionButton = componentBuilder.buildButton("OK", ABRConstants.SPACE_L, Insets.EMPTY);
        addNewInstructionButton.getStyleClass().add("ok-button");

        cancelButton = componentBuilder.buildButton("Close", ABRConstants.SPACE_L, Insets.EMPTY);
        cancelButton.getStyleClass().add("cancel-button");

        variableButton = componentBuilder.buildButton(
                "Variables", ABRConstants.SPACE_L, ABRConstants.ICON_VARIABLES, ABRConstants.SPACE_M, Insets.EMPTY);

        addPauseButton = componentBuilder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_PAUSE, ABRConstants.SPACE_M, new Insets(5));
        addWaitButton30 = componentBuilder.buildButton(
                "30s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addWaitButton15 = componentBuilder.buildButton(
                "15s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addWaitButton5 = componentBuilder.buildButton(
                "5s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addWaitButton2 = componentBuilder.buildButton(
                "2s", ABRConstants.SPACE_L, ABRConstants.ICON_WAIT, ABRConstants.SPACE_M, new Insets(5));

        addCloseActionButton = componentBuilder.buildButton(
                "Add Close Browser",
                ABRConstants.SPACE_L,
                ABRConstants.ICON_CROSS,
                ABRConstants.SPACE_M,
                new Insets(5));
        addScreenButton = componentBuilder.buildButton(
                "Add Screenshot", ABRConstants.SPACE_L, ABRConstants.ICON_SCREEN, ABRConstants.SPACE_M, new Insets(5));

        // Create a new HBox for the new buttons
        buttonBox = new HBox(10); // 10 is the spacing between buttons

        buttonBox
                .getChildren()
                .addAll(
                        addPauseButton,
                        addWaitButton30,
                        addWaitButton15,
                        addWaitButton5,
                        addWaitButton2,
                        addCloseActionButton,
                        addScreenButton);

        buttonBox.setAlignment(Pos.BASELINE_LEFT); // Align buttons to the left

        // Create an HBox and add all three labels into the same row
        // labelRow = new HBox(10); // 10 is the spacing between the labels
        // labelRow.getChildren().addAll(commandLabel, botJobVarsLabel, webPageLabel);
        // labelRow.setAlignment(Pos.BASELINE_LEFT); // Align the labels to the left

        // Create HBox for comboBoxes
        comboBoxesRow = new HBox(10);

        comboBoxInstruc.setPrefWidth(buttonWidth);
        comboBoxVars.setPrefWidth(buttonWidth);
        comboBoxWebPage.setPrefWidth(buttonWidth);
        comboBoxAllBlocks.setPrefWidth(buttonWidth);

        // Handle the visibility of comboBoxBlocks
        comboBoxBlocks.setVisible(false);
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

        commandBox = new VBox(commandLabel, comboBoxInstruc);
        varsBox = new VBox(botJobVarsLabel, comboBoxVars);
        webFieldsBox = new VBox(webPageLabel, comboBoxWebPage);
        addNewsBox = new VBox(addNewsLabel, comboBoxAllBlocks);
        blocksBox = new VBox(blocksLabel, comboBoxBlocks);

        comboBoxesRow.getChildren().addAll(commandBox, varsBox, webFieldsBox);

        variableButton.setPrefWidth(buttonWidth - 50);

        comboBoxOperator.setVisible(false);
        comboBoxOperator.setManaged(false);

        // Create a listener (optional) to toggle visibility dynamically
        comboBoxOperator.visibleProperty().addListener((obs, oldValue, newValue) -> {
            comboBoxOperator.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                comboBoxOperator.setPrefWidth(comboOperatorWidth); // Restore width when visible
            }
        });

        comboBoxTimes.setVisible(false);
        comboBoxTimes.setManaged(false);

        // Create a listener (optional) to toggle visibility dynamically
        comboBoxTimes.visibleProperty().addListener((obs, oldValue, newValue) -> {
            comboBoxTimes.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                comboBoxTimes.setPrefWidth(comboTimesWidth); // Restore width when visible
            }
        });

        comboBoxLoops.setVisible(false);
        comboBoxLoops.setManaged(false);

        // Create a listener (optional) to toggle visibility dynamically
        comboBoxLoops.visibleProperty().addListener((obs, oldValue, newValue) -> {
            comboBoxLoops.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                comboBoxLoops.setPrefWidth(comboLoopsWidth); // Restore width when visible
            }
        });

        // Create an HBox for the variable button
        variableButtonRow = new HBox(10, variableButton, comboBoxOperator, textFlow);
        variableButtonRow.setAlignment(Pos.BASELINE_LEFT); // Align variableButton to the left

        // Create HBox for instruction and cancel buttons
        instructionButtonsRow = new HBox(10, addNewInstructionButton, cancelButton);
        addNewInstructionButton.setPrefWidth(buttonWidth);
        cancelButton.setPrefWidth(buttonWidth);
        instructionButtonsRow.setAlignment(Pos.BASELINE_RIGHT); // Align buttons to the right

        // Combine all HBoxes into a VBox for vertical alignment
        vboxAll = new VBox(20);
        vboxAll.getChildren()
                .addAll(
                        // labelRow, // Web Page Label row
                        comboBoxesRow, // ComboBoxes row
                        variableButtonRow, // Variable Button row
                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                        addNewsBox,
                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                        );

        String css = getClass().getResource("/button.css").toExternalForm();

        vboxAll.setAlignment(Pos.CENTER);
        vboxAll.setPadding(new Insets(10)); // Padding around the VBox

        // Adjust VBox properties for better alignment
        VBox.setVgrow(vboxAll, Priority.ALWAYS);

        // Use AnchorPane to ensure the VBox resizes with the window
        mainPane = new AnchorPane(vboxAll);
        mainPane.getStylesheets().add(css);

        AnchorPane.setTopAnchor(vboxAll, 0.0);
        AnchorPane.setBottomAnchor(vboxAll, 0.0);
        AnchorPane.setLeftAnchor(vboxAll, 0.0);
        AnchorPane.setRightAnchor(vboxAll, 0.0);
    }

    private void clearData() {
        nameField.clear();
        //        valueToBeChecked.clear();
    }

    @Override
    public void initUIBehaviour() {
        addPauseButton.setOnAction(
                e -> addInstruction("PAUSE", "PAUSE Action", ABRConstants.PAUSE, 0, "", null, null, rowMoveDTO));
        addWaitButton30.setOnAction(e -> addInstruction(
                "Wait 30second(s)", "Waiting action", ABRConstants.HOLD, 30, "", null, null, rowMoveDTO));
        addWaitButton15.setOnAction(e -> addInstruction(
                "Wait 15second(s)", "Waiting action", ABRConstants.HOLD, 15, "", null, null, rowMoveDTO));
        addWaitButton5.setOnAction(e ->
                addInstruction("Wait 5second(s)", "Waiting action", ABRConstants.HOLD, 5, "", null, null, rowMoveDTO));
        addWaitButton2.setOnAction(e ->
                addInstruction("Wait 2second(s)", "Waiting action", ABRConstants.HOLD, 2, "", null, null, rowMoveDTO));
        addCloseActionButton.setOnAction(e ->
                addInstruction("Close Browser", "Close Browser", ABRConstants.QUIT, 0, "", null, null, rowMoveDTO));

        addScreenButton.setOnAction(e -> addInstruction(
                "Screenshot Browser", "Screenshot Browser", ABRConstants.SCREEN, 0, "", null, null, rowMoveDTO));

        comboBoxOperator.setVisible(false);
        comboBoxTimes.setVisible(false);
        comboBoxLoops.setVisible(false);

        // Add a listener to comboBoxInstruc to handle selection changes
        comboBoxInstruc.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Set the visibility of comboBoxOperator based on the selected value
                if (ABRConstants.CHECK_VALUE.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    //                    textFlow.setPrefWidth(buttonWidth + 100);

                    //                    botJobVarsLabel.setText("Bot-Job Variable");
                    botJobVarsLabel.setVisible(true);
                    webPageLabel.setVisible(true);
                    comboBoxOperator.setVisible(true);
                    comboBoxWebPage.setVisible(true);
                    comboBoxAllBlocks.setVisible(true);

                    variableButton.setVisible(true);

                    comboBoxVars.setVisible(true);
                    comboBoxVars.setPrefWidth(buttonWidth);
                    comboBoxBlocks.setVisible(false);
                    comboBoxTimes.setVisible(false);
                    comboBoxLoops.setVisible(false);

                    try {
                        variableButtonRow.getChildren().clear();
                        vboxAll.getChildren().clear();

                        // labelRow.getChildren().clear();
                        // labelRow.getChildren().addAll(commandLabel, botJobVarsLabel, webPageLabel);
                        // labelRow.setAlignment(Pos.BASELINE_LEFT);

                        comboBoxesRow.getChildren().clear();
                        comboBoxesRow.getChildren().addAll(commandBox, varsBox, webFieldsBox);

                        variableButtonRow = new HBox(10, variableButton, comboBoxOperator, textFlow);

                        vboxAll.getChildren()
                                .addAll(
                                        // labelRow, // Web Page Label row
                                        comboBoxesRow, // ComboBoxes row
                                        variableButtonRow, // Variable Button row
                                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                                        addNewsBox,
                                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                                        );

                        // labelRow.requestLayout();
                        vboxAll.requestLayout();
                        mainPane.requestLayout();

                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }

                } else if (ABRConstants.GOTO.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    //                    textFlow.setPrefWidth(buttonWidth + 100);

                    //                    botJobVarsLabel.setText("Block Destination");
                    botJobVarsLabel.setVisible(true);
                    webPageLabel.setVisible(false);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(false);
                    comboBoxAllBlocks.setVisible(true);

                    variableButton.setVisible(false);

                    comboBoxVars.setVisible(false);
                    comboBoxBlocks.setVisible(true);
                    comboBoxBlocks.setPrefWidth(buttonWidth);
                    comboBoxTimes.setVisible(false);
                    comboBoxLoops.setVisible(true);

                    try {
                        variableButtonRow.getChildren().clear();
                        vboxAll.getChildren().clear();

                        // labelRow.getChildren().clear();
                        // labelRow.getChildren().addAll(commandLabel);
                        // labelRow.setAlignment(Pos.BASELINE_LEFT);

                        comboBoxesRow.getChildren().clear();
                        comboBoxesRow.getChildren().addAll(commandBox, blocksBox);

                        variableButtonRow = new HBox(10, blankText, loopText, comboBoxLoops, textFlow);

                        vboxAll.getChildren()
                                .addAll(
                                        // labelRow, // Web Page Label row
                                        comboBoxesRow, // ComboBoxes row
                                        variableButtonRow, // Variable Button row
                                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                                        addNewsBox,
                                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                                        );

                        // labelRow.requestLayout();
                        vboxAll.requestLayout();
                        mainPane.requestLayout();

                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }

                } else if (ABRConstants.REFRESH_ONLY.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    //                    textFlow.setPrefWidth(buttonWidth + 100);

                    botJobVarsLabel.setVisible(false);
                    webPageLabel.setVisible(false);
                    comboBoxBlocks.setVisible(false);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(false);
                    comboBoxAllBlocks.setVisible(true);

                    variableButton.setVisible(false);
                    comboBoxVars.setVisible(false);
                    comboBoxTimes.setVisible(false);
                    comboBoxLoops.setVisible(false);

                    try {
                        variableButtonRow.getChildren().clear();
                        vboxAll.getChildren().clear();

                        // labelRow.getChildren().clear();
                        // labelRow.getChildren().addAll(commandLabel);
                        // labelRow.setAlignment(Pos.BASELINE_LEFT);

                        comboBoxesRow.getChildren().clear();
                        comboBoxesRow.getChildren().addAll(commandBox);

                        variableButtonRow = new HBox(10, blankText, textFlow);

                        vboxAll.getChildren()
                                .addAll(
                                        // labelRow, // Web Page Label row
                                        comboBoxesRow, // ComboBoxes row
                                        variableButtonRow, // Variable Button row
                                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                                        addNewsBox,
                                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                                        );

                        // labelRow.requestLayout();
                        vboxAll.requestLayout();
                        mainPane.requestLayout();

                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }

                } else if (ABRConstants.LOOP.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    //                    textFlow.setPrefWidth(buttonWidth + 100);

                    //                    botJobVarsLabel.setText("Bot-Job Variable");
                    botJobVarsLabel.setVisible(false);
                    webPageLabel.setVisible(true);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(true);
                    comboBoxAllBlocks.setVisible(true);

                    variableButton.setVisible(false);

                    comboBoxVars.setVisible(false);
                    comboBoxVars.setPrefWidth(buttonWidth);
                    comboBoxBlocks.setVisible(false);

                    comboBoxTimes.setVisible(false);
                    comboBoxLoops.setVisible(true);

                    try {
                        variableButtonRow.getChildren().clear();
                        vboxAll.getChildren().clear();

                        // labelRow.getChildren().clear();
                        // labelRow.getChildren().addAll(commandLabel, webPageLabel);
                        // labelRow.setAlignment(Pos.BASELINE_LEFT);

                        comboBoxesRow.getChildren().clear();
                        comboBoxesRow.getChildren().addAll(commandBox, webFieldsBox);

                        variableButtonRow = new HBox(10, blankText, loopText, comboBoxLoops, textFlow);

                        vboxAll.getChildren()
                                .addAll(
                                        // labelRow, // Web Page Label row
                                        comboBoxesRow, // ComboBoxes row
                                        variableButtonRow, // Variable Button row
                                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                                        addNewsBox,
                                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                                        );

                        // labelRow.requestLayout();
                        vboxAll.requestLayout();
                        mainPane.requestLayout();

                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }

                } else if (ABRConstants.REFRESH_LOOP.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    //                    textFlow.setPrefWidth(buttonWidth + 100);

                    //                    botJobVarsLabel.setText("Bot-Job Variable");
                    botJobVarsLabel.setVisible(false);
                    webPageLabel.setVisible(true);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(true);
                    comboBoxAllBlocks.setVisible(true);

                    variableButton.setVisible(false);

                    comboBoxVars.setVisible(false);
                    comboBoxVars.setPrefWidth(buttonWidth);
                    comboBoxBlocks.setVisible(false);

                    comboBoxTimes.setVisible(true);
                    comboBoxLoops.setVisible(true);

                    try {
                        variableButtonRow.getChildren().clear();
                        vboxAll.getChildren().clear();

                        // labelRow.getChildren().clear();
                        // labelRow.getChildren().addAll(commandLabel, webPageLabel);
                        // labelRow.setAlignment(Pos.BASELINE_LEFT);

                        comboBoxesRow.getChildren().clear();
                        comboBoxesRow.getChildren().addAll(commandBox, webFieldsBox);

                        variableButtonRow =
                                new HBox(10, blankText, timesText, comboBoxTimes, loopText, comboBoxLoops, textFlow);

                        vboxAll.getChildren()
                                .addAll(
                                        // labelRow, // Web Page Label row
                                        comboBoxesRow, // ComboBoxes row
                                        variableButtonRow, // Variable Button row
                                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                                        addNewsBox,
                                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                                        );

                        // labelRow.requestLayout();
                        vboxAll.requestLayout();
                        mainPane.requestLayout();

                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }

                } else if (ABRConstants.IF.equalsIgnoreCase(newValue.getValue())) {
                    defineTextFlow(comboBoxInstruc.getValue().getValue());

                    textFlow.setVisible(true);
                    //                    textFlow.setPrefWidth(buttonWidth + 100);

                    botJobVarsLabel.setVisible(false);
                    webPageLabel.setVisible(false);
                    comboBoxBlocks.setVisible(false);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(false);
                    comboBoxAllBlocks.setVisible(true);
                    variableButton.setVisible(false);
                    comboBoxVars.setVisible(false);
                    comboBoxTimes.setVisible(false);
                    comboBoxLoops.setVisible(false);

                    try {
                        variableButtonRow.getChildren().clear();
                        vboxAll.getChildren().clear();

                        // labelRow.getChildren().clear();
                        // labelRow.getChildren().addAll(commandLabel);
                        // labelRow.setAlignment(Pos.BASELINE_LEFT);

                        comboBoxesRow.getChildren().clear();
                        comboBoxesRow.getChildren().addAll(commandBox);

                        variableButtonRow = new HBox(10, blankText, textFlow);

                        vboxAll.getChildren()
                                .addAll(
                                        // labelRow, // Web Page Label row
                                        comboBoxesRow, // ComboBoxes row
                                        variableButtonRow, // Variable Button row
                                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                                        addNewsBox,
                                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                                        );

                        // labelRow.requestLayout();
                        vboxAll.requestLayout();
                        mainPane.requestLayout();

                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }

                } else {
                    defineTextFlow(newValue.getValue());

                    textFlow.setVisible(true);
                    //                    textFlow.setPrefWidth(buttonWidth);

                    //                    botJobVarsLabel.setText("Bot-Job Variable");
                    botJobVarsLabel.setVisible(true);
                    webPageLabel.setVisible(true);
                    comboBoxOperator.setVisible(false);
                    comboBoxWebPage.setVisible(true);
                    comboBoxAllBlocks.setVisible(true);
                    variableButton.setVisible(true);

                    comboBoxVars.setVisible(true);
                    comboBoxVars.setPrefWidth(buttonWidth);
                    comboBoxBlocks.setVisible(false);

                    comboBoxTimes.setVisible(false);
                    comboBoxLoops.setVisible(false);

                    try {
                        variableButtonRow.getChildren().clear();
                        vboxAll.getChildren().clear();

                        // labelRow.getChildren().clear();
                        // labelRow.getChildren().addAll(commandLabel, botJobVarsLabel, webPageLabel);
                        // labelRow.setAlignment(Pos.BASELINE_LEFT);

                        comboBoxesRow.getChildren().clear();
                        comboBoxesRow.getChildren().addAll(commandBox, varsBox, webFieldsBox);

                        variableButtonRow = new HBox(10, variableButton, textFlow);

                        vboxAll.getChildren()
                                .addAll(
                                        // labelRow, // Web Page Label row
                                        comboBoxesRow, // ComboBoxes row
                                        variableButtonRow, // Variable Button row
                                        buttonBox, // Button Box (addWaitButton30, addWaitButton15, etc.)
                                        addNewsBox,
                                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                                        );

                        // labelRow.requestLayout();
                        vboxAll.requestLayout();
                        mainPane.requestLayout();

                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
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

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxOperator.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Set the visibility of comboBoxOperator based on the selected value
                defineTextFlow(comboBoxInstruc.getValue().getValue());
            }
        });

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxTimes.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Set the visibility of comboBoxOperator based on the selected value
                defineTextFlow(comboBoxInstruc.getValue().getValue());
            }
        });

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxLoops.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Set the visibility of comboBoxOperator based on the selected value
                defineTextFlow(comboBoxInstruc.getValue().getValue());
            }
        });

        this.addNewInstructionButton.setOnMouseClicked((e) -> {
            // Check if the current selected index is greater than the first index

            if (!comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.IF)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.GOTO)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.REFRESH_ONLY)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.LOOP)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ABRConstants.REFRESH_LOOP)) {

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
                //                variableText3.setText(variableValue);
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
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Refresh")) {
                addInstruction("Refresh", "Refresh", ABRConstants.REFRESH_ONLY, 10, "", null, null, this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Loop")) {
                addInstruction(
                        "LOOP",
                        "LOOP",
                        ABRConstants.LOOP,
                        2,
                        comboBoxLoops.getValue().getValue(),
                        null,
                        comboBoxWebPage.getValue().getVarId(),
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Refresh Loop")) {
                addInstruction(
                        "Refresh Loop",
                        "Refresh Loop",
                        ABRConstants.REFRESH_LOOP,
                        2,
                        comboBoxTimes.getValue().getValue() + ":"
                                + comboBoxLoops.getValue().getValue(),
                        null,
                        comboBoxWebPage.getValue().getVarId(),
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("GO TO")) {
                addInstruction(
                        "GOTO",
                        "GOTO",
                        ABRConstants.GOTO,
                        1,
                        comboBoxLoops.getValue().getValue(),
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

            ABRSharedResources.getInstance().changeDbConnection();
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

        // Add a listener to print the ID when the selection changes
        //        comboBoxAllBlocks.getSelectionModel().selectedItemProperty().addListener(new
        // ChangeListener<ComboBoxVars>() {
        //            @Override
        //            public void changed(
        //                    ObservableValue<? extends ComboBoxVars> observable, ComboBoxVars oldValue, ComboBoxVars
        // newValue) {
        //                if (newValue != null) {
        //                    loadJobVariables(newValue.getVarId());
        //                    reloadComboVars();
        //                    comboBoxVars.getSelectionModel().selectFirst();
        //                    if (comboBoxVars.getValue() != null) {
        //                        defineTextFlow(comboBoxInstruc.getValue().getValue());
        //                    }
        //                }
        //            }
        //        });

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
                        rowMoveDTO,
                        //                        rowMoveDTO.getUpdatedRows().get(0).getInstructionId(),
                        //                        rowMoveDTO.getUpdatedRows().get(0).getInstructionName()
                        comboBoxWebPage.getValue().getVarId(),
                        comboBoxWebPage.getValue().getText(),
                        comboBoxWebPage.getValue().getValue(),
                        comboBoxInstruc.getValue().getValue());
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
                        rowMoveDTO,
                        comboBoxWebPage.getValue().getVarId(),
                        comboBoxWebPage.getValue().getText(),
                        comboBoxWebPage.getValue().getValue(),
                        comboBoxInstruc.getValue().getValue());
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
        String variableValue = "NO VARIABLE";

        if (comboBoxVars != null && comboBoxVars.getValue() != null) {
            variableValue = comboBoxVars.getValue().getValue();
            variableName = comboBoxVars.getValue().getText();
        }

        String webFieldName = "NO WEB FIELD";
        if (comboBoxVars != null && comboBoxVars.getValue() != null) {
            webFieldName = comboBoxWebPage.getValue().getText();
        }

        if (comboBoxWebPage != null && comboBoxWebPage.getValue() != null) {
            // Switch based on newValue and update variableText1 accordingly
            switch (newValue.toUpperCase()) {
                case ABRConstants.EXTRACT_FIELD:
                    regularText1.setText(" Excel Write from variable: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(variableName);
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(false);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(false);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText1);
                    textFlow.requestLayout();

                    break;
                case ABRConstants.GET_VALUE:
                    regularText1.setText(" GET Web field: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(comboBoxWebPage.getValue().getText());
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" and PUT on Variable: ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText(variableName);
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText1, regularText2, variableText2);
                    textFlow.requestLayout();

                    break;
                case ABRConstants.SET_VALUE:
                    regularText1.setText("SET Web field: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(webFieldName);
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" with the value of: ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText(variableValue);
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText1, regularText2, variableText2);
                    textFlow.requestLayout();
                    break;
                case ABRConstants.GOTO:
                    regularText1.setText("GO TO Block : ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(comboBoxBlocks.getValue().getText());
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" Limit: ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText(comboBoxLoops.getValue().getText());
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText3.setText(" Times");
                    regularText3.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(true);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren()
                            .addAll(regularText1, variableText1, regularText2, variableText2, regularText3);
                    textFlow.requestLayout();

                    break;
                case ABRConstants.REFRESH_ONLY:
                    regularText1.setText("Refresh: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText("Refresh Current Web Page");
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(false);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(false);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText1);
                    textFlow.requestLayout();
                    break;
                case ABRConstants.LOOP:
                    regularText1.setText("Jump To Parent: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(webFieldName);
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" Limit: ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText(comboBoxLoops.getValue().getText());
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText3.setText(" Times");
                    regularText3.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(true);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren()
                            .addAll(regularText1, variableText1, regularText2, variableText2, regularText3);
                    textFlow.requestLayout();

                    break;
                case ABRConstants.REFRESH_LOOP:
                    regularText1.setText("Jump To Parent: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(webFieldName);
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" Refresh in: ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText(comboBoxTimes.getValue().getText() + " ");
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText3.setText(" Limit: ");
                    regularText3.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText3.setText(comboBoxLoops.getValue().getText() + " ");
                    variableText3.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText4.setText(" Times");
                    regularText4.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(true);
                    regularText4.setVisible(true);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(true);

                    textFlow.getChildren().clear();
                    textFlow.getChildren()
                            .addAll(
                                    regularText1,
                                    variableText1,
                                    regularText2,
                                    variableText2,
                                    regularText3,
                                    variableText3,
                                    regularText4);
                    textFlow.requestLayout();

                    break;
                case ABRConstants.IF:
                    regularText1.setText("{ IF -> ELSE } ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(" OR ");
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" { ELSE -> ENDIF }");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText(" SPECIAL BLOCKS");
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText1, regularText2, variableText2);
                    textFlow.requestLayout();

                    break;
                case ABRConstants.CHECK_VALUE:
                    regularText1.setText("CHECK Variable: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(variableName);
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" " + comboBoxOperator.getValue().getText() + " ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText(variableValue);
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText1, regularText2, variableText2);
                    textFlow.requestLayout();

                    break;
                default:
                    regularText1.setText("No Selection");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText("");
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText("");
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(true);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(true);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText1, regularText2, variableText2);
                    textFlow.requestLayout();

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
        if (blockLoadDTOList.size() > 1) {
            for (BlockLoadDTO block : blockLoadDTOList) {
                if (block.getId() != blockToAvoid)
                    blocksItems.add(new ComboBoxVars(
                            block.getBlockOrderNumber() + "# " + block.getName(),
                            block.getName(),
                            block.getBlockOrderNumber(),
                            block.getId()));
            }
        }
    }

    private void loadAllBlockItems(List<BlockLoadDTO> blockLoadDTOList) {
        allBlocksItems.clear();
        if (blockLoadDTOList.size() > 1) {
            allBlocksItems.add(new ComboBoxVars("Select the Block", "", -1, -1));
        }
        for (BlockLoadDTO block : blockLoadDTOList) {
            allBlocksItems.add(new ComboBoxVars(
                    block.getBlockOrderNumber() + "# " + block.getName().trim(),
                    block.getName().trim(),
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

        int blockId = comboBoxAllBlocks.getValue().getInstructionId();
        String blockName = comboBoxAllBlocks.getValue().getText();
        rowMoveDTO.setBlockId(blockId);
        rowMoveDTO.setBlockName(blockName);
        if (blockId < 0) {

            Text variableText1Styled = new Text("Select the block you wan to Add New Command!");
            variableText1Styled.setStyle("-fx-font-size: 16px; -fx-fill: red;");

            VBox combinedTextContainer = new VBox();
            combinedTextContainer.setSpacing(5); // Add some sp

            combinedTextContainer.getChildren().add(variableText1Styled);

            performAction.showAlertCombinedVBOX(
                    Alert.AlertType.ERROR, "Block Not Selected", "Select the Block!", null, combinedTextContainer);
            return;
        }

        // Create and show alert inside Platform.runLater

        if (this.botJobLoadList.size() == 0) {
            this.botJobLoadList = loadBotJobs(rowMoveDTO.getBotJobId());
        }

        // Combine the texts using TextFlow

        Text extra = new Text("Action: ");
        extra.setStyle("-fx-font-size: 14px; -fx-fill: blue;");
        extra.setVisible(true);

        if (actions.equalsIgnoreCase(ABRConstants.HOLD)
                || actions.equalsIgnoreCase(ABRConstants.PAUSE)
                || (actions.equalsIgnoreCase(ABRConstants.SCREEN))
                || actions.equalsIgnoreCase(ABRConstants.QUIT)) {
            regularText1.setText("");
            regularText2.setText("");
            regularText3.setText("");
            regularText4.setText("");
            variableText1.setText("");
            variableText2.setText(name);
            variableText3.setText("");
        }

        // Create individual text elements with the necessary styling
        Text regularTextCopy1 = new Text(regularText1.getText());
        regularTextCopy1.setStyle(regularText1.getStyle());
        regularTextCopy1.setVisible(regularText1.isVisible());

        Text regularTextCopy2 = new Text(regularText2.getText());
        regularTextCopy2.setStyle(regularText2.getStyle());
        regularTextCopy2.setVisible(regularText2.isVisible());

        Text regularTextCopy3 = new Text(regularText3.getText());
        regularTextCopy3.setStyle(regularText3.getStyle());
        regularTextCopy3.setVisible(regularText3.isVisible());

        Text regularTextCopy4 = new Text(regularText4.getText());
        regularTextCopy4.setStyle(regularText4.getStyle());
        regularTextCopy4.setVisible(regularText4.isVisible());

        Text variableText1Copy = new Text(variableText1.getText());
        variableText1Copy.setStyle(variableText1.getStyle());
        variableText1Copy.setVisible((variableText1.isVisible()));

        Text variableText2Copy = new Text(variableText2.getText());
        variableText2Copy.setStyle(variableText2.getStyle());
        variableText2Copy.setVisible((variableText2.isVisible()));

        Text variableText3Copy = new Text(variableText3.getText());
        variableText3Copy.setStyle(variableText3.getStyle());
        variableText3Copy.setVisible((variableText3.isVisible()));

        if (regularTextCopy1.getText().trim().length() == 0) {
            regularTextCopy1.setText("");
        }
        if (regularTextCopy2.getText().trim().length() == 0) {
            regularTextCopy2.setText("");
        }

        if (regularTextCopy3.getText().trim().length() == 0) {
            regularTextCopy3.setText("");
        }
        if (regularTextCopy4.getText().trim().length() == 0) {
            regularTextCopy4.setText("");
        }

        if (variableText1Copy.getText().trim().length() == 0) {
            variableText1Copy.setText("");
        }
        if (variableText2Copy.getText().trim().length() == 0) {
            variableText2Copy.setText("");
        }

        if (variableText3Copy.getText().trim().length() == 0) {
            variableText3Copy.setText("");
        }

        // Create an HBox to hold the individual text elements
        HBox combinedTextContainer = new HBox();
        combinedTextContainer.setSpacing(5); // Add some spacing between the texts

        Text blockNameLabel = new Text("Block : ");
        blockNameLabel.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        Text blockNameText = new Text(blockName);
        blockNameText.setStyle("-fx-font-size: 18px; -fx-fill: green;");

        HBox blockNameBox = new HBox();
        blockNameBox.getChildren().addAll(blockNameLabel, blockNameText);

        HBox allMsgHor = new HBox();
        allMsgHor.setSpacing(5);
        allMsgHor
                .getChildren()
                .addAll(
                        extra,
                        regularTextCopy1,
                        variableText1Copy,
                        regularTextCopy2,
                        variableText2Copy,
                        regularTextCopy3,
                        variableText3Copy,
                        regularTextCopy4);

        VBox allMsgVer = new VBox();
        allMsgVer.getChildren().addAll(blockNameBox, allMsgHor);

        combinedTextContainer.getChildren().addAll(allMsgVer);

        boolean alertResponse = performAction.showCombinedConfirmation(
                "Add new Instruction",
                "Are you sure you want to Add the Instruction to the Bot-Job?",
                "",
                combinedTextContainer);

        if (alertResponse) {

            BotJobLoadDTO botJobLoad = null;
            if (botJobLoadList.size() > 0) {
                botJobLoad = botJobLoadList.get(0);
            } else {
                this.botJobLoadList = loadBotJobs(rowMoveDTO.getBotJobId());
                botJobLoad = botJobLoadList.get(0);
            }

            // Handle loop outside Platform.runLater to ensure multiple iterations
            int endifCount = actions.equalsIgnoreCase(ABRConstants.IF) ? 3 : 1;

            // Run the loop for adding multiple instructions
            String nextAction = null;
            int parentId = 0;
            for (int added = endifCount; added >= 1; added--) {

                boolean isShowAlert = added == 1;

                // Run the instruction add in a separate Task
                int newRowId = performDataBase.preFillInstruction(
                        nextAction == null ? name : nextAction,
                        nextAction == null ? description : nextAction,
                        nextAction == null ? actions : nextAction,
                        nextAction == null ? operation : nextAction,
                        onHold,
                        varId,
                        instructionId,
                        nextAction == null ? -1 : parentId,
                        rowMoveDTO,
                        botJobLoad,
                        isShowAlert);

                if (newRowId > 0) {
                    showAlertTimer(
                            Alert.AlertType.INFORMATION,
                            "Add Instruction",
                            "Instruction Added",
                            "Instruction \"" + name + "\" has been added successfully");
                } else {
                    showAlertTimer(
                            Alert.AlertType.ERROR,
                            "Error",
                            "Error Add New Instruction",
                            "Not possible to insert new Operation:  \"" + name + "\"");
                }

                if (Strings.isNullOrEmpty(nextAction)) {
                    nextAction = ABRConstants.ELSE;
                    parentId = newRowId;
                } else if (!Strings.isNullOrEmpty(nextAction) && nextAction.equals(ABRConstants.ELSE)) {
                    nextAction = ABRConstants.ENDIF;
                }
            }
        }
        defineTextFlow(comboBoxInstruc.getValue().getValue());
    }

    public List<BotJobLoadDTO> loadBotJobs(int botJobId) {
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
        botJobLoadList.clear();
        Map<Integer, BotJobLoadDTO> blockMap = new HashMap<>();

        // Use Statement to execute the query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // Load the Block information
                int blockId = rs.getInt("block_id");
                BotJobLoadDTO blockDTO = blockMap.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BotJobLoadDTO();
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setBotJobId(rs.getInt("bot_job_id"));
                    blockDTO.setBotJobName(rs.getString("bot_job_name"));

                    blockMap.put(blockId, blockDTO);
                    botJobLoadList.add(blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return botJobLoadList;
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

        List<InstructionDTO> rowList =
                performDataBase.getInstructionsByBlockId(rowMoveDTO.getBotJobId(), rowMoveDTO.getBlockId());

        performDataBase.reorderInstructions(rowList);

        performDataBase.preInsertStep(rowMoveDTO, rowList);

        List<BlockLoopInstructionLoadDTO> instructionList = null;
        List<BotJobLoadDTO> matchingBlocks = null;

        if (rowMoveDTO != null && rowMoveDTO.getUpdatedRows().size() > 0) {
            int targetBlockId = rowMoveDTO.getUpdatedRows().get(0).getBlockId();

            matchingBlocks = botJobLoadList.stream()
                    .filter(block -> block.getId() == targetBlockId)
                    .collect(Collectors.toList());
        }

        List<BotJobLoadDTO> finalMatchingBlocks = matchingBlocks;
        List<InstructionDTO> finalInstructionList = rowList;
        Task<Void> waitTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    BlockLoopInstructionDTO instruction = new BlockLoopInstructionDTO();

                    instruction.setName(name);

                    instruction.setEncrypted(false);
                    instruction.setExportToABR(false);
                    instruction.setActive(true);

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
