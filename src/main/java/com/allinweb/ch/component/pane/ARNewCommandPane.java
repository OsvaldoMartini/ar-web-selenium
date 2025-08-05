package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementIcon;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockOptions;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.FormatOption;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.model.VariableUserDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARElementValueScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class ARNewCommandPane extends ARPane {

    protected static volatile ARNewCommandPane instance;

    // Private constructor to prevent instantiation
    private ARNewCommandPane() {
        // Initialize if necessary
    }

    public static ARNewCommandPane getInstance() {
        if (instance == null) {
            synchronized (ARNewCommandPane.class) {
                if (instance == null) {
                    instance = new ARNewCommandPane();
                }
            }
        }
        return instance;
    }

    private static final WebSocketSessionManager webSocketSessionManager;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final ARElementValueScene arElementValueScene;

    // Static block to initialize
    static {
        webSocketSessionManager = WebSocketSessionManager.getInstance();
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
        arElementValueScene = ARElementValueScene.getInstance();
    }

    private List<String> allowedActions = Arrays.asList(
            WebElementIcon.SET_VALUE.getValue().toUpperCase(),
            WebElementIcon.GET_VALUE.getValue().toUpperCase(),
            WebElementIcon.CHECK_VALUE.getValue().toUpperCase(),
            WebElementIcon.GOTO.getValue().toUpperCase(),
            WebElementIcon.EXCEL_GOTO.getValue().toUpperCase(),
            WebElementIcon.EXTRACT_FIELD.getValue().toUpperCase(),
            WebElementIcon.REFRESH_ONLY.getValue().toUpperCase(),
            WebElementIcon.LOOP.getValue().toUpperCase(),
            WebElementIcon.REFRESH_LOOP.getValue().toUpperCase());

    private boolean firstLoad = false;
    private boolean loadeAllCompleted = false;
    private final Gson gson = new Gson();

    private final ARComponentBuilder componentBuilder = new ARComponentBuilder();

    // Postgres
    private Connection conn = null;

    private RowMoveDTO rowMoveDTO;
    private Pane mainPane;

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
    Label addBlocksLabel;

    TextFlow operationSelected;
    TextFlow textFlow;

    Text currentActionText1;
    Text currentActionText2;
    Text currentActionText3;
    Text currentActionText4;
    Text currentActionText5;
    Text currentActionText6;

    Text timesText;
    Text loopText;
    Text regularText1;
    Text regularText2;
    Text regularText3;
    Text regularText4;
    Text variableText1;
    Text variableText2;
    Text variableText3;
    Text blankText;

    TextField nameField;

    private Button variableButton;

    private Button addExcelNextRowButton;
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
    boolean variablesDisable = false;

    boolean blockIdChanged = false;

    Button addNewInstructionButton;
    Button cancelButton;

    private List<BotJobLoadDTO> botJobLoadList = new ArrayList<>();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();

    private ComboBox<ComboBoxImage> comboBoxInstruc;
    private ObservableList<ComboBoxImage> itemsInstructions = FXCollections.observableArrayList();

    private ObservableList<VariableUserDTO> variablesList = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxVars;
    private ObservableList<ComboBoxVars> variablesItems = FXCollections.observableArrayList();

    private ComboBox<FormatOption> comboBoxTimes;
    private ObservableList<FormatOption> timesItems = FXCollections.observableArrayList();

    private ComboBox<FormatOption> comboBoxLoops;
    private ObservableList<FormatOption> loopsItems = FXCollections.observableArrayList();

    private ComboBox<ComboBoxImage> comboBoxWebFields;
    private ObservableList<ComboBoxVars> webPageItems;
    private ObservableList<ComboBoxImage> filteredPageItems = FXCollections.observableArrayList();

    private ComboBox<BlockOptions> comboBoxAllBlocks;
    private ObservableList<BlockOptions> allBlocksItems = FXCollections.observableArrayList();

    private ComboBox<BlockOptions> comboBoxBlocksGoto;
    private ObservableList<BlockOptions> blocksGotoItems = FXCollections.observableArrayList();

    private ComboBox<ComboBoxOperator> comboBoxOperator;
    private ObservableList<ComboBoxOperator> operatorsItems = FXCollections.observableArrayList();

    public void initialize(RowMoveDTO rowMoveDTO) {

        if (rowMoveDTO.getSessionId().equals("componentTasks")) {
            this.webPageItems = performDataBase.loadCompWebPageFields(rowMoveDTO.getHomeBankingId());
        } else {
            this.webPageItems = performDataBase.loadWebPageFields(rowMoveDTO.getBotJobId());
        }

        this.rowMoveDTO = rowMoveDTO;

        this.filteredPageItems.clear();
        this.filteredPageItems.addAll(webPageItems.stream()
                //                .filter(item -> !"button".equals(item.getTagType()) && !"a".equals(item.getTagType()))
                .map(item -> new ComboBoxImage(
                        item.getText(),
                        getImageForTagType(item.getTagType()),
                        item.getValue(),
                        item.getBlockId(),
                        item.getInstructionId(),
                        item.getOrderNumber()))
                .toList());

        if (filteredPageItems.isEmpty()) {
            variablesDisable = true;
        }

        // Initialize itemsInstructions list conditionally
        try {
            itemsInstructions = FXCollections.observableArrayList();
            //            itemsInstructions.add(
            //                    new ComboBoxImage("Select", new Image(ARConstants.ICON_BLANK), ARConstants.NO_VALUE));
            itemsInstructions.add(new ComboBoxImage(
                    "SetValue", new Image(ARConstants.ICON_SET_VALUE_BTN), ARConstants.SET_VALUE, -1, -1, -1));
            itemsInstructions.add(new ComboBoxImage(
                    "GetValue", new Image(ARConstants.ICON_GET_VALUE_BTN), ARConstants.GET_VALUE, -1, -1, -1));
            itemsInstructions.add(new ComboBoxImage(
                    "CheckValue", new Image(ARConstants.ICON_CHECK), ARConstants.CHECK_VALUE, -1, -1, -1));

            // Add "IF" only if it does not meet the exclusion conditions
            //            if (rowMoveDTO.getIsBetween() != null && !rowMoveDTO.getIsBetween()) {

            itemsInstructions.add(
                    new ComboBoxImage("IF", new Image(ARConstants.ICON_IF_ELSE), ARConstants.IF, -1, -1, -1));
            //            }
            itemsInstructions.add(
                    new ComboBoxImage("GOTO", new Image(ARConstants.ICON_GOTO), ARConstants.GOTO, -1, -1, -1));

            itemsInstructions.add(new ComboBoxImage(
                    "Excel GOTO", new Image(ARConstants.ICON_EXCEL_GOTO), ARConstants.EXCEL_GOTO, -1, -1, -1));

            itemsInstructions.add(new ComboBoxImage(
                    "ExcelWrite", new Image(ARConstants.ICON_EXCEL), ARConstants.EXTRACT_FIELD, -1, -1, -1));

            itemsInstructions.add(new ComboBoxImage(
                    "Refresh", new Image(ARConstants.ICON_REFRESH_ONLY), ARConstants.REFRESH_ONLY, -1, -1, -1));
            itemsInstructions.add(
                    new ComboBoxImage("Loop", new Image(ARConstants.ICON_REFRESH_LOOP), ARConstants.LOOP, -1, -1, -1));
            itemsInstructions.add(new ComboBoxImage(
                    "Refresh Loop", new Image(ARConstants.ICON_REFRESH_LOOP), ARConstants.REFRESH_LOOP, -1, -1, -1));

        } catch (Exception ex) {
            ARLogger.getInstance(ARNewCommandPane.class)
                    .severe("Error creating \"DropBox Instructions\"\nError: " + ex.getMessage());
        }

        try {
            operatorsItems = FXCollections.observableArrayList(
                    new ComboBoxOperator("Equals", new Image(ARConstants.ICON_EQUAL), "="),
                    new ComboBoxOperator("Greater", new Image(ARConstants.ICON_GREATER), ">"),
                    new ComboBoxOperator("Less", new Image(ARConstants.ICON_LESS), "<"),
                    new ComboBoxOperator("!=", new Image(ARConstants.ICON_DIFFERENT), "!="));
        } catch (Exception ex) {
            ARLogger.getInstance(ARNewCommandPane.class)
                    .severe("Error creating \"DropBox Operators\"\nError: " + ex.getMessage());
        }

        if (itemsInstructions.isEmpty() || itemsInstructions.size() == 0) {
            itemsInstructions.add(new ComboBoxImage(
                    "No Instructions", new Image(ARConstants.ICON_BLANK), ARConstants.NO_VALUE, -1, -1, -1));
        }
        if (operatorsItems.size() == 0) {
            operatorsItems.add(
                    new ComboBoxOperator("No Operators", new Image(ARConstants.ICON_BLANK), ARConstants.NO_VALUE));
        }

        if (filteredPageItems.isEmpty()) {
            filteredPageItems.add(new ComboBoxImage(
                    "No Web Fields", new Image(ARConstants.ICON_BLANK), ARConstants.NO_VALUE, -1, -1, -1));
        }

        if (this.filteredPageItems != null && this.filteredPageItems.size() > 0) {
            variablesItems.clear();

            if (rowMoveDTO.getSessionId().equals("componentTasks")) {
                this.variablesList = performDataBase.loadAllCompVariablesByCriteria(
                        rowMoveDTO.getHomeBankingId(), filteredPageItems.get(0).getInstructionId());
            } else {
                this.variablesList = performDataBase.loadAllVariablesByCriteria(
                        rowMoveDTO.getBotJobId(), filteredPageItems.get(0).getInstructionId());
            }
        }

        if (rowMoveDTO.getSessionId().equals("componentTasks")) {
            this.blockLoadList = performDataBase.loadCompBlocksByHomeId(
                    rowMoveDTO.getHomeBankingId(), rowMoveDTO.getBotJobId(), rowMoveDTO.getBotJobName());
        } else {
            this.blockLoadList = performDataBase.loadBlocksByBotJobId(rowMoveDTO.getBotJobId());
        }

        if (this.blockLoadList != null && !this.blockLoadList.isEmpty()) {
            //            for (BotJobLoadDTO botJobLoadDTO : this.botJobLoadList) {

            if (rowMoveDTO.getUpdatedRows().get(0).getActions() != null
                    && rowMoveDTO.getUpdatedRows().get(0).getActions().equals("EXCEL GOTO")) {
                loadBlockItems(blockLoadList, -99);
            } else {
                loadBlockItems(blockLoadList, rowMoveDTO.getBlockId());
            }

            //            }

            //            for (BotJobLoadDTO botJobLoadDTO : this.botJobLoadList) {
            loadAllBlockItems(blockLoadList);
            //            }
        } else {
            allBlocksItems.add(new BlockOptions("#1 Default Block", "Default Block", 1, 1));
        }

        if (loadeAllCompleted) {
            setCombosAndLabels();
            titleDescription();
        }
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        addNewInstructionButton = componentBuilder.buildButton("OK", ARConstants.SPACE_L, Insets.EMPTY);
        addNewInstructionButton.getStyleClass().add("ok-button");

        cancelButton = componentBuilder.buildButton("Close", ARConstants.SPACE_L, Insets.EMPTY);
        cancelButton.getStyleClass().add("cancel-button");

        variableButton = componentBuilder.buildButton(
                "Variables", ARConstants.SPACE_L, ARConstants.ICON_VARIABLES, ARConstants.SPACE_M, Insets.EMPTY);

        variableButton.setDisable(variablesDisable);

        addExcelNextRowButton = componentBuilder.buildButton(
                "Data Next Row", ARConstants.SPACE_L, ARConstants.ICON_EXCEL2, ARConstants.SPACE_M, new Insets(5));
        addPauseButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_PAUSE, ARConstants.SPACE_M, new Insets(5));
        addWaitButton30 = componentBuilder.buildButton(
                "30s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addWaitButton15 = componentBuilder.buildButton(
                "15s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addWaitButton5 = componentBuilder.buildButton(
                "5s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addWaitButton2 = componentBuilder.buildButton(
                "2s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addCloseActionButton = componentBuilder.buildButton(
                "Add Close Browser", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, new Insets(5));
        addScreenButton = componentBuilder.buildButton(
                "Add Screenshot", ARConstants.SPACE_L, ARConstants.ICON_SCREEN, ARConstants.SPACE_M, new Insets(5));

        // Create a new HBox for the new buttons
        buttonBox = new HBox(10); // 10 is the spacing between buttons

        // Create labels
        commandLabel = new Label("Command:");
        botJobVarsLabel = new Label("Bot-Job Variable");
        webPageLabel = new Label("WebPage Field");
        blocksLabel = new Label("Block Destination");
        addBlocksLabel = new Label("Block to Add the New Instruction");

        timesText = new Text("Times");
        timesText.setStyle("-fx-font-size: 14px; -fx-fill: blue;");
        loopText = new Text("Loop");
        loopText.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

        textFlow = new TextFlow();
        operationSelected = new TextFlow();

        titleDescription();

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

        timesItems.add(new FormatOption("5s", "5"));
        timesItems.add(new FormatOption("10s", "10"));
        timesItems.add(new FormatOption("20s", "20"));
        timesItems.add(new FormatOption("30s", "30"));
        timesItems.add(new FormatOption("40s", "40"));
        timesItems.add(new FormatOption("50s", "50"));
        timesItems.add(new FormatOption("60s", "60"));

        comboBoxTimes = new ComboBox<>(timesItems);
        comboBoxTimes.setPrefWidth(50);
        // Set cell factory to display images and text
        comboBoxTimes.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK);
                }
            }
        });

        // Set cell factory for dropdown list rendering
        comboBoxTimes.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK);
                }

                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });

        loopsItems.add(new FormatOption("5 x", "5"));
        loopsItems.add(new FormatOption("10 x", "10"));
        loopsItems.add(new FormatOption("20 x", "20"));
        loopsItems.add(new FormatOption("30 x", "30"));
        comboBoxLoops = new ComboBox<>(loopsItems);
        comboBoxLoops.setPrefWidth(60);
        // Set button cell to display selected item properly
        comboBoxLoops.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK);
                }
            }
        });

        // Set cell factory for dropdown list rendering
        comboBoxLoops.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK);
                }

                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });

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

        comboBoxBlocksGoto = new ComboBox<>(blocksGotoItems);
        if (blocksGotoItems.size() == 0) {
            blocksGotoItems.add(new BlockOptions("no blocks added", "", -1, -1));
        }
        comboBoxBlocksGoto.setPrefWidth(buttonWidth);
        comboBoxBlocksGoto.getSelectionModel().selectFirst();
        comboBoxBlocksGoto.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(BlockOptions item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });

        comboBoxBlocksGoto.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(BlockOptions item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });

        comboBoxBlocksGoto.getSelectionModel().selectFirst();

        comboBoxWebFields = new ComboBox<>(filteredPageItems);
        comboBoxWebFields.setPrefWidth(50);
        comboBoxWebFields.setButtonCell(new ListCell<>() {
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
        comboBoxWebFields.setCellFactory(param -> new ListCell<>() {
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

        comboBoxAllBlocks = new ComboBox<>(allBlocksItems);
        comboBoxAllBlocks.setPrefWidth(50);
        comboBoxAllBlocks.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(BlockOptions item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });

        comboBoxAllBlocks.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(BlockOptions item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });

        //        setCombosAndLabels();

        //        defineTextFlow(comboBoxInstruc.getValue().getValue());

        buttonBox
                .getChildren()
                .addAll(
                        addExcelNextRowButton,
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
        comboBoxWebFields.setPrefWidth(buttonWidth);
        comboBoxAllBlocks.setPrefWidth(buttonWidth);

        // Handle the visibility of comboBoxBlocks
        comboBoxBlocksGoto.setVisible(false);
        comboBoxBlocksGoto.setManaged(false); // Ensure it does not take up space when hidden

        // Create a listener (optional) to toggle visibility dynamically
        comboBoxBlocksGoto.visibleProperty().addListener((obs, oldValue, newValue) -> {
            comboBoxBlocksGoto.setManaged(newValue); // Set managed based on visibility
            if (newValue) {
                comboBoxBlocksGoto.setPrefWidth(buttonWidth); // Restore width when visible
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
        webFieldsBox = new VBox(webPageLabel, comboBoxWebFields);
        addNewsBox = new VBox(addBlocksLabel, comboBoxAllBlocks);
        blocksBox = new VBox(blocksLabel, comboBoxBlocksGoto);

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
                        operationSelected,
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

        loadeAllCompleted = true;
        setCombosAndLabels();
    }

    private void titleDescription() {
        InstructionLoadDTO firstInstruction = rowMoveDTO.getUpdatedRows().get(0);
        String operation = performMessage.renderInstructionActions(firstInstruction);

        // Create individual text elements with the necessary styling
        currentActionText1 = new Text(rowMoveDTO.getType().replace("_", " "));
        currentActionText1.setStyle("-fx-font-size: 14px; -fx-fill: green;");

        currentActionText2 = new Text(" Instruction: ");
        currentActionText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

        currentActionText3 = new Text(firstInstruction.getInstructionName());
        currentActionText3.setStyle("-fx-font-size: 14px; -fx-fill: green;");

        //        currentActionText4 = new Text(operation);
        //        currentActionText4.setStyle("-fx-font-size: 14px; -fx-fill: green;");

        currentActionText5 = new Text(" on Block Name: ");
        currentActionText5.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

        currentActionText6 = new Text(rowMoveDTO.getBlockName());
        currentActionText6.setStyle("-fx-font-size: 14px; -fx-fill: green;");

        operationSelected.getChildren().clear();
        operationSelected
                .getChildren()
                .addAll(
                        currentActionText1,
                        currentActionText2,
                        currentActionText3,
                        //                        currentActionText4,
                        currentActionText5,
                        currentActionText6);

        operationSelected.requestLayout();
    }

    private void setCombosAndLabels() {
        if (rowMoveDTO.getType().equals("EDIT_OPERATION")) {
            String actions = rowMoveDTO.getUpdatedRows().get(0).getInstructionName();
            String[] operations = rowMoveDTO.getUpdatedRows().get(0).getOperation() != null
                    ? rowMoveDTO
                            .getUpdatedRows()
                            .get(0)
                            .getOperation()
                            .split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)
                    : null;

            if (allowedActions.contains(actions.toUpperCase())) {
                firstLoad = true;
                comboBoxBlocksGoto.getSelectionModel().selectFirst();
                comboBoxTimes.getSelectionModel().selectFirst();
                comboBoxLoops.getSelectionModel().selectFirst();

                setSelectedIndexByValue(actions, operations);
            } else {
                comboBoxInstruc.getSelectionModel().selectFirst();
                comboBoxWebFields.getSelectionModel().selectFirst();
                reloadComboVars(comboBoxWebFields.getValue().getInstructionId(), false, -1);
                comboBoxVars.getSelectionModel().selectFirst();
                comboBoxOperator.getSelectionModel().selectFirst();

                comboBoxBlocksGoto.getSelectionModel().selectFirst();
                comboBoxTimes.getSelectionModel().selectFirst();
                comboBoxLoops.getSelectionModel().selectFirst();
                recallMessages(comboBoxInstruc.getValue().getValue());
            }
        } else {
            comboBoxInstruc.getSelectionModel().selectFirst();
            comboBoxWebFields.getSelectionModel().selectFirst();
            reloadComboVars(comboBoxWebFields.getValue().getInstructionId(), false, -1);
            comboBoxVars.getSelectionModel().selectFirst();
            comboBoxOperator.getSelectionModel().selectFirst();

            comboBoxBlocksGoto.getSelectionModel().selectFirst();
            comboBoxTimes.getSelectionModel().selectFirst();
            comboBoxLoops.getSelectionModel().selectFirst();

            recallMessages(comboBoxInstruc.getValue().getValue());
        }

        if (rowMoveDTO.getBlockId() > -1) {
            // Get the blockId to match
            int targetBlockId = rowMoveDTO.getBlockId();

            // Iterate through items in comboBoxAllBlocks
            for (BlockOptions item : comboBoxAllBlocks.getItems()) {
                if (item.getBlockId() != null && item.getBlockId() == targetBlockId) {
                    comboBoxAllBlocks.getSelectionModel().select(item); // Select the matching item
                }
            }
        } else {
            // If blockId is not valid, select the first item
            comboBoxAllBlocks.getSelectionModel().selectFirst();
        }
    }

    private void clearData() {
        nameField.clear();
        //        valueToBeChecked.clear();
    }

    @Override
    public void initUIBehaviour() {
        addExcelNextRowButton.setOnAction(e -> insertNewInstruction(
                "NEXT ROW", "EXCEL NEXT ROW", ARConstants.NEXT_ROW, 0, "", null, null, null, rowMoveDTO));
        addPauseButton.setOnAction(e ->
                insertNewInstruction("PAUSE", "PAUSE Action", ARConstants.PAUSE, 0, "", null, null, null, rowMoveDTO));
        addWaitButton30.setOnAction(e -> insertNewInstruction(
                "Wait 30second(s)", "Waiting action", ARConstants.HOLD, 30, "", null, null, null, rowMoveDTO));
        addWaitButton15.setOnAction(e -> insertNewInstruction(
                "Wait 15second(s)", "Waiting action", ARConstants.HOLD, 15, "", null, null, null, rowMoveDTO));
        addWaitButton5.setOnAction(e -> insertNewInstruction(
                "Wait 5second(s)", "Waiting action", ARConstants.HOLD, 5, "", null, null, null, rowMoveDTO));
        addWaitButton2.setOnAction(e -> insertNewInstruction(
                "Wait 2second(s)", "Waiting action", ARConstants.HOLD, 2, "", null, null, null, rowMoveDTO));
        addCloseActionButton.setOnAction(e -> insertNewInstruction(
                "Close Browser", "Close Browser", ARConstants.QUIT, 0, "", null, null, null, rowMoveDTO));

        addScreenButton.setOnAction(e -> insertNewInstruction(
                "Screenshot Browser", "Screenshot Browser", ARConstants.SCREEN, 0, "", null, null, null, rowMoveDTO));

        comboBoxOperator.setVisible(false);
        comboBoxTimes.setVisible(false);
        comboBoxLoops.setVisible(false);

        this.addNewInstructionButton.setOnMouseClicked((e) -> {
            // Check if the current selected index is greater than the first index

            if (!comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.IF)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.GOTO)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.EXCEL_GOTO)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.REFRESH_ONLY)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.REFRESH_LOOP)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.LOOP)
                    && comboBoxVars.getValue() != null
                    && comboBoxVars.getValue().getVarId() < 0) {
                performMessage.errorMessage(
                        "Variables Not Defined!",
                        "No variables have been created!",
                        "Please define a variable for: \""
                                + comboBoxWebFields.getValue().getText() + "\".",
                        null,
                        null,
                        0);
                return;
            }

            if (!comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.IF)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.GOTO)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.EXCEL_GOTO)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.REFRESH_ONLY)
                    && comboBoxInstruc.getSelectionModel().getSelectedIndex() < 0) {
                performMessage.errorMessage(
                        "No Web Fields Defined",
                        "Missing Web Fields (Web Elements)!",
                        "Web Elements are required to insert operations.",
                        null,
                        null,
                        0);

                return;
            }

            if (!comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.IF)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.GOTO)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.EXCEL_GOTO)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.REFRESH_ONLY)) {
                if (comboBoxAllBlocks.getValue() != null
                        && comboBoxWebFields.getValue() != null
                        && !comboBoxAllBlocks
                                .getValue()
                                .getBlockId()
                                .equals(comboBoxWebFields.getValue().getBlockId())) {

                    String outsideBlock = allBlocksItems.stream()
                            .filter(f -> f.getBlockId()
                                    .equals(comboBoxWebFields.getValue().getBlockId()))
                            .map(BlockOptions::getText)
                            .findFirst()
                            .orElse(null);

                    performMessage.errorMessage(
                            "Web Field Outside of Scope",
                            "Selected Block: " + comboBoxAllBlocks.getValue().getText(),
                            "The Web Field belongs to Block: " + outsideBlock,
                            "Referencing Web Fields outside of their designated block is not allowed.",
                            null,
                            0);

                    return;
                }
            }

            if ((comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.GOTO)
                            || comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.EXCEL_GOTO))
                    && blocksGotoItems.size() == 1
                    && (comboBoxBlocksGoto.getValue().getBlockId() == -1)) {

                performMessage.errorMessage(
                        "Error", "No Blocks Defined", "It must have ate least Two Blocks defined ", null, null, 0);

                return;
            }

            if (rowMoveDTO.getUpdatedRows().get(0).getInstructionOrderNumber()
                            <= comboBoxWebFields.getValue().getOrderNumber()
                    && (comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.REFRESH_LOOP)
                            || comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.LOOP))) {
                // It Forces to LOOP or REFRESH_LOOP BE AFTER THE Instruction
                if (rowMoveDTO.getType().equals("INSERT_AFTER")) {
                    rowMoveDTO
                            .getUpdatedRows()
                            .get(0)
                            .setInstructionOrderNumber(
                                    comboBoxWebFields.getValue().getOrderNumber());
                } else {
                    rowMoveDTO
                            .getUpdatedRows()
                            .get(0)
                            .setInstructionOrderNumber(
                                    comboBoxWebFields.getValue().getOrderNumber() + 1);
                }
            }

            if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("SetValue")) {
                String setValueTo =
                        Strings.isNullOrEmpty(comboBoxVars.getValue().getValue())
                                ? "EMPTY"
                                : comboBoxVars.getValue().getValue();
                //                variableText3.setText(variableValue);
                insertNewInstruction(
                        "SetValue",
                        "SetValue",
                        ARConstants.SET_VALUE,
                        1,
                        //                        comboBoxVars.getValue().getText().substring(1).toLowerCase() + ":" +
                        // setValueTo,
                        comboBoxWebFields.getValue().getValue().toLowerCase() + ":" + setValueTo,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null,
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("GetValue")) {
                insertNewInstruction(
                        "GetValue",
                        "GetValue",
                        ARConstants.GET_VALUE,
                        1,
                        //                        comboBoxVars.getValue().getText().substring(1).toLowerCase() + ":"+
                        // comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxWebFields.getValue().getValue() + ":"
                                + comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null,
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("CheckValue")) {
                String checkValueFor =
                        Strings.isNullOrEmpty(comboBoxVars.getValue().getValue())
                                ? "EMPTY"
                                : comboBoxVars.getValue().getValue();

                insertNewInstruction(
                        "CheckValue",
                        "Check Value",
                        ARConstants.CHECK_VALUE,
                        1,
                        comboBoxVars.getValue().getText().toLowerCase() + ":"
                                + comboBoxOperator.getValue().getOperator() + ":" + checkValueFor,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null,
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("excelWrite")) {
                insertNewInstruction(
                        "ExcelWrite",
                        "ExcelWrite",
                        ARConstants.EXTRACT_FIELD,
                        2,
                        comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null,
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Refresh")) {
                insertNewInstruction(
                        "Refresh", "Refresh", ARConstants.REFRESH_ONLY, 10, "", null, null, null, this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Loop")) {
                insertNewInstruction(
                        "LOOP",
                        "LOOP",
                        ARConstants.LOOP,
                        2,
                        comboBoxTimes.getValue().getValue() + ":"
                                + comboBoxLoops.getValue().getValue(),
                        null,
                        comboBoxWebFields.getValue().getInstructionId(),
                        null,
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Refresh Loop")) {
                insertNewInstruction(
                        "Refresh Loop",
                        "Refresh Loop",
                        ARConstants.REFRESH_LOOP,
                        2,
                        comboBoxTimes.getValue().getValue() + ":"
                                + comboBoxLoops.getValue().getValue(),
                        null,
                        comboBoxWebFields.getValue().getInstructionId(),
                        null,
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("GOTO")) {
                insertNewInstruction(
                        "GOTO",
                        "GOTO",
                        ARConstants.GOTO,
                        1,
                        comboBoxLoops.getValue().getValue(),
                        null, // Block Order Number as VarId
                        null,
                        comboBoxBlocksGoto.getValue().getBlockId(), // BLOCK ID as Parent Block Id
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("EXCEL GOTO")) {
                insertNewInstruction(
                        "EXCEL GOTO",
                        "EXCEL GOTO",
                        ARConstants.EXCEL_GOTO,
                        1,
                        "1",
                        null, // Block Order Number as VarId
                        null,
                        comboBoxBlocksGoto.getValue().getBlockId(), // BLOCK ID as Parent Block Id
                        this.rowMoveDTO);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("IF")) {
                insertNewInstruction(
                        "IF",
                        "IF",
                        ARConstants.IF,
                        1,
                        "IF",
                        null, // Block Order Number as VarId
                        null, // BLOCK ID as Parent Id
                        null,
                        this.rowMoveDTO);
            }

            //            PerformDataBase..changeDbConnection(previousDB);
        });

        // Add a listener to print the ID when the selection changes
        comboBoxWebFields.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ComboBoxImage>() {
            @Override
            public void changed(
                    ObservableValue<? extends ComboBoxImage> observable,
                    ComboBoxImage oldValue,
                    ComboBoxImage newValue) {
                if (newValue != null) {
                    if (!firstLoad) {
                        reloadComboVars(newValue.getInstructionId(), false, -1);
                        comboBoxVars.getSelectionModel().selectFirst();
                        recallMessages(comboBoxInstruc.getValue().getValue());
                    }
                }
            }
        });

        cancelButton.setOnMouseClicked((e) -> {
            ARLogger.getInstance(ARNewCommandPane.class).finer("ARNewCommandPane Close()");
            Platform.runLater(() -> {
                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                stage.close();
            });
        });

        variableButton.setOnAction(e -> {
            if (this.rowMoveDTO != null && !rowMoveDTO.getUpdatedRows().isEmpty()) {
                ARLogger.getInstance(ARNewCommandPane.class)
                        .info("creating variable for instruction Name "
                                + rowMoveDTO.getUpdatedRows().get(0).getInstructionName());
                arElementValueScene.initialize(
                        rowMoveDTO,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getValue(),
                        comboBoxWebFields.getValue().getInstructionId(),
                        comboBoxWebFields.getValue().getText(),
                        comboBoxWebFields.getValue().getValue(),
                        comboBoxInstruc.getValue().getValue());
                arElementValueScene.showModal();

                reloadComboVars(comboBoxWebFields.getValue().getInstructionId(), false, -1);
                // Set ComboBox to first item
                comboBoxVars.getSelectionModel().selectFirst();
                if (!firstLoad) {
                    recallMessages(comboBoxInstruc.getValue().getValue());
                }

            } else {
                ARLogger.getInstance(ARNewCommandPane.class)
                        .info("creating variable for instruction Name "
                                + comboBoxWebFields.getValue().getText());

                arElementValueScene.initialize(
                        rowMoveDTO,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getValue(),
                        comboBoxWebFields.getValue().getInstructionId(),
                        comboBoxWebFields.getValue().getText(),
                        comboBoxWebFields.getValue().getValue(),
                        comboBoxInstruc.getValue().getValue());
                arElementValueScene.showModal();
            }
            reloadComboVars(comboBoxWebFields.getValue().getInstructionId(), false, -1);
            // Set ComboBox to first item
            comboBoxVars.getSelectionModel().selectFirst();
            if (comboBoxVars.getValue() != null) {
                recallMessages(comboBoxInstruc.getValue().getValue());
            }
        });

        // Add a listener to comboBoxInstruc to handle selection changes
        comboBoxInstruc.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    if (ARConstants.EXCEL_GOTO.equalsIgnoreCase(String.valueOf(newValue.getValue()))) {
                        loadBlockItems(blockLoadList, -99);
                    } else {
                        loadBlockItems(blockLoadList, rowMoveDTO.getBlockId());
                    }
                    recallMessages(comboBoxInstruc.getValue().getValue());
                    if (comboBoxVars.getValue() != null
                            && comboBoxVars.getValue().getVarId() > -1) {
                        arElementValueScene.setTableRowById(
                                comboBoxVars.getValue().getVarId());
                    } else {
                        arElementValueScene.initialize(
                                rowMoveDTO,
                                comboBoxVars.getValue().getVarId(),
                                comboBoxVars.getValue().getValue(),
                                comboBoxWebFields.getValue().getInstructionId(),
                                comboBoxWebFields.getValue().getText(),
                                comboBoxWebFields.getValue().getValue(),
                                comboBoxInstruc.getValue().getValue());
                    }
                }
            }
        });

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxVars.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    recallMessages(comboBoxInstruc.getValue().getValue());
                    if (comboBoxVars.getValue() != null
                            && comboBoxVars.getValue().getVarId() > -1) {
                        arElementValueScene.setTableRowById(
                                comboBoxVars.getValue().getVarId());
                    } else {
                        arElementValueScene.initialize(
                                rowMoveDTO,
                                comboBoxVars.getValue().getVarId(),
                                comboBoxVars.getValue().getValue(),
                                comboBoxWebFields.getValue().getInstructionId(),
                                comboBoxWebFields.getValue().getText(),
                                comboBoxWebFields.getValue().getValue(),
                                comboBoxInstruc.getValue().getValue());
                    }
                }
            }
        });

        // Add a listener to comboBoxBlocks to handle selection changes
        comboBoxAllBlocks.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    if (rowMoveDTO.getUpdatedRows().get(0).getActions() != null
                            && rowMoveDTO.getUpdatedRows().get(0).getActions().equals("EXCEL GOTO")) {
                        loadBlockItems(blockLoadList, -99);
                    } else {
                        loadBlockItems(blockLoadList, newValue.getBlockId());
                    }

                    recallMessages(comboBoxInstruc.getValue().getValue());
                }
            }
        });

        // Add a listener to comboBoxBlocksGoto to handle selection changes
        comboBoxBlocksGoto.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    recallMessages(comboBoxInstruc.getValue().getValue());
                }
            }
        });

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxOperator.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    recallMessages(comboBoxInstruc.getValue().getValue());
                }
            }
        });

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxTimes.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    recallMessages(comboBoxInstruc.getValue().getValue());
                }
            }
        });

        // Add a listener to comboBoxVars to handle selection changes
        comboBoxLoops.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    recallMessages(comboBoxInstruc.getValue().getValue());
                }
            }
        });
    }

    private void recallMessages(String valueEdit) {
        // Set the visibility of comboBoxOperator based on the selected value
        if (ARConstants.CHECK_VALUE.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Bot-Job Variable");
            botJobVarsLabel.setVisible(true);
            webPageLabel.setVisible(true);
            comboBoxOperator.setVisible(true);
            comboBoxWebFields.setVisible(true);
            comboBoxAllBlocks.setVisible(true);

            variableButton.setVisible(true);

            comboBoxVars.setVisible(true);
            comboBoxVars.setPrefWidth(buttonWidth);
            comboBoxBlocksGoto.setVisible(false);
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
                                operationSelected,
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

        } else if (ARConstants.GOTO.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Block Destination");
            botJobVarsLabel.setVisible(true);
            webPageLabel.setVisible(false);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(false);
            comboBoxAllBlocks.setVisible(true);

            variableButton.setVisible(false);

            comboBoxVars.setVisible(false);
            comboBoxBlocksGoto.setVisible(true);
            comboBoxBlocksGoto.setPrefWidth(buttonWidth);
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
                                operationSelected,
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

        } else if (ARConstants.EXCEL_GOTO.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Block Destination");
            botJobVarsLabel.setVisible(true);
            webPageLabel.setVisible(false);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(false);

            comboBoxAllBlocks.setVisible(true);

            variableButton.setVisible(false);

            comboBoxVars.setVisible(false);
            comboBoxBlocksGoto.setVisible(true);
            comboBoxBlocksGoto.setPrefWidth(buttonWidth);
            comboBoxTimes.setVisible(false);
            comboBoxLoops.setVisible(false);

            try {
                variableButtonRow.getChildren().clear();
                vboxAll.getChildren().clear();

                // labelRow.getChildren().clear();
                // labelRow.getChildren().addAll(commandLabel);
                // labelRow.setAlignment(Pos.BASELINE_LEFT);

                comboBoxesRow.getChildren().clear();
                comboBoxesRow.getChildren().addAll(commandBox, blocksBox);

                variableButtonRow = new HBox(10, blankText, textFlow);

                vboxAll.getChildren()
                        .addAll(
                                operationSelected,
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

        } else if (ARConstants.REFRESH_ONLY.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            botJobVarsLabel.setVisible(false);
            webPageLabel.setVisible(false);
            comboBoxBlocksGoto.setVisible(false);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(false);
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
                                operationSelected,
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

        } else if (ARConstants.LOOP.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Bot-Job Variable");
            botJobVarsLabel.setVisible(false);
            webPageLabel.setVisible(true);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(true);
            comboBoxAllBlocks.setVisible(true);

            variableButton.setVisible(false);

            comboBoxVars.setVisible(false);
            comboBoxVars.setPrefWidth(buttonWidth);
            comboBoxBlocksGoto.setVisible(false);

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
                                operationSelected,
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

        } else if (ARConstants.REFRESH_LOOP.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Bot-Job Variable");
            botJobVarsLabel.setVisible(false);
            webPageLabel.setVisible(true);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(true);
            comboBoxAllBlocks.setVisible(true);

            variableButton.setVisible(false);

            comboBoxVars.setVisible(false);
            comboBoxVars.setPrefWidth(buttonWidth);
            comboBoxBlocksGoto.setVisible(false);

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
                                operationSelected,
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

        } else if (ARConstants.IF.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            botJobVarsLabel.setVisible(false);
            webPageLabel.setVisible(false);
            comboBoxBlocksGoto.setVisible(false);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(false);
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
                                operationSelected,
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
            defineTextFlow(valueEdit);

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth);

            //                    botJobVarsLabel.setText("Bot-Job Variable");
            botJobVarsLabel.setVisible(true);
            webPageLabel.setVisible(true);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(true);
            comboBoxAllBlocks.setVisible(true);
            variableButton.setVisible(true);

            comboBoxVars.setVisible(true);
            comboBoxVars.setPrefWidth(buttonWidth);
            comboBoxBlocksGoto.setVisible(false);

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
                                operationSelected,
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

    private void defineTextFlow(String newValue) {

        String variableName = "NO VARIABLE";
        String variableValue = "NO VARIABLE";

        if (comboBoxVars != null && comboBoxVars.getValue() != null) {
            variableValue = comboBoxVars.getValue().getValue();
            variableName = comboBoxVars.getValue().getText();
        }

        String webFieldName = "NO WEB FIELD";
        if (comboBoxVars != null && comboBoxVars.getValue() != null) {
            webFieldName = comboBoxWebFields.getValue().getText();
        } else if (comboBoxWebFields.getValue() != null
                && !Strings.isNullOrEmpty(comboBoxWebFields.getValue().getText())) {
            webFieldName = comboBoxWebFields.getValue().getText();
        }

        if (comboBoxWebFields != null && comboBoxWebFields.getValue() != null) {
            // Switch based on newValue and update variableText1 accordingly
            switch (newValue.toUpperCase()) {
                case ARConstants.EXTRACT_FIELD:
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
                case ARConstants.GET_VALUE:
                    regularText1.setText(" GET Web field: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(comboBoxWebFields.getValue().getText());
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
                case ARConstants.SET_VALUE:
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
                case ARConstants.GOTO:
                    regularText1.setText("GO TO Block : ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(comboBoxBlocksGoto.getValue().getText());
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
                case ARConstants.EXCEL_GOTO:
                    regularText1.setText("EXCEL GO TO Block : ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(comboBoxBlocksGoto.getValue().getText());
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" Limit: ");
                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText2.setText("1");
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText3.setText(" Time");
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
                case ARConstants.REFRESH_ONLY:
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
                case ARConstants.LOOP:
                    regularText1.setText("Loop Parent: ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    variableText1.setText(webFieldName);
                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText2.setText(" Jump in: ");
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
                case ARConstants.REFRESH_LOOP:
                    regularText1.setText("Loop Parent: ");
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
                case ARConstants.IF:
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
                case ARConstants.CHECK_VALUE:
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

    public void reloadComboVars(int instructionId, boolean selectLast, int variableId) {
        variablesItems.clear();
        if (rowMoveDTO.getSessionId().equals("componentTasks")) {
            this.variablesList =
                    performDataBase.loadAllCompVariablesByCriteria(rowMoveDTO.getHomeBankingId(), instructionId);
        } else {
            this.variablesList = performDataBase.loadAllVariablesByCriteria(rowMoveDTO.getBotJobId(), instructionId);
        }

        if (variablesList != null && variablesList.size() > 0) {
            List<ComboBoxVars> variablesNames = variablesList.stream()
                    .map(variable -> new ComboBoxVars(
                            variable.getType().substring(0, 1) + variable.getName(),
                            variable.getValue(),
                            -1,
                            -1,
                            variable.getParentId(),
                            variable.getId(),
                            null,
                            -1,
                            variable.getLocalFormat()))
                    .collect(Collectors.toList());
            variablesItems.addAll(variablesNames);

            if (selectLast && variableId == -1) {
                comboBoxVars.getSelectionModel().selectLast();
            } else if (selectLast && variableId > -1) {
                int indexGeneric = -1;
                for (int i = 0; i < variablesItems.size(); i++) {
                    if (variablesItems.get(i).getVarId().equals(variableId)) {
                        comboBoxVars.getSelectionModel().select(i);
                        indexGeneric = i;
                        break;
                    }
                }

                if (indexGeneric == -1) {
                    comboBoxVars.getSelectionModel().selectFirst();
                }
            }

        } else {
            variablesItems.add(new ComboBoxVars("no variables added", "", -1, -1, -1, -1, null, -1, null));
            if (selectLast) {
                comboBoxVars.getSelectionModel().selectFirst();
            }
        }
    }

    private void updateFields() {
        if (rowMoveDTO.getSessionId().equals("componentTasks")) {
            this.webPageItems = performDataBase.loadCompWebPageFields(rowMoveDTO.getHomeBankingId());
        } else {
            this.webPageItems = performDataBase.loadWebPageFields(rowMoveDTO.getBotJobId());
        }

        this.filteredPageItems.clear();
        this.filteredPageItems.addAll(this.webPageItems.stream()
                //                .filter(item -> !"button".equals(item.getTagType()) && !"a".equals(item.getTagType()))
                .map(item -> new ComboBoxImage(
                        item.getText(),
                        getImageForTagType(item.getTagType()),
                        item.getValue(),
                        item.getBlockId(),
                        item.getInstructionId(),
                        item.getOrderNumber()))
                .toList());
        comboBoxWebFields.getSelectionModel().selectFirst();
    }

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

    private void loadBlockItems(List<BlockLoadDTO> blockLoadDTOList, int blockToAvoid) {
        blocksGotoItems.clear();
        if (blockLoadDTOList.size() > 1) {
            for (BlockLoadDTO block : blockLoadDTOList) {
                if (block.getId() != blockToAvoid)
                    blocksGotoItems.add(new BlockOptions(
                            block.getBlockOrderNumber() + "# " + block.getName(),
                            block.getName(),
                            block.getBlockOrderNumber(),
                            block.getId()));
            }
        }
        if (comboBoxBlocksGoto != null) {
            comboBoxBlocksGoto.setItems(blocksGotoItems);
            comboBoxBlocksGoto.getSelectionModel().selectFirst();
        }
    }

    private void loadAllBlockItems(List<BlockLoadDTO> blockLoadDTOList) {
        allBlocksItems.clear();
        if (blockLoadDTOList.size() > 1) {
            allBlocksItems.add(new BlockOptions("Select the Block", "", -1, -1));
        }
        for (BlockLoadDTO block : blockLoadDTOList) {
            allBlocksItems.add(new BlockOptions(
                    block.getBlockOrderNumber() + "# " + block.getName().trim(),
                    block.getName().trim(),
                    block.getBlockOrderNumber(),
                    block.getId()));
        }
    }

    @Override
    public void clearPane(Pane panel) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private void insertNewInstruction(
            String name,
            String description,
            String actions,
            Integer onHold,
            String operation,
            Integer varId,
            Integer instructionId,
            Integer parentBlockId,
            RowMoveDTO rowMoveDTO) {

        Integer blockId = comboBoxAllBlocks.getValue().getBlockId();
        String blockName = comboBoxAllBlocks.getValue().getText();

        // This will make the EXCEL GOTO TO BE RELOCATES JUST AS INFO
        // EXCEL GOTO IS GOING TO BE RENDERED DIFFERENTLY ON GridItems
        if (parentBlockId != null && actions.equals("EXCEL GOTO")) {
            blockId = comboBoxBlocksGoto.getValue().getBlockId();
            blockName = comboBoxBlocksGoto.getValue().getText();
            rowMoveDTO.setBlockId(blockId);
            rowMoveDTO.setBlockName(blockName);
            rowMoveDTO.getUpdatedRows().get(0).setBlockId(parentBlockId);
            rowMoveDTO.setParentBlockId(parentBlockId);

            List<InstructionLoadDTO> excelDataGoto =
                    performDataBase.loadExcelGotoBlock(rowMoveDTO.getHomeBankingId(), rowMoveDTO.getBotJobId());

            // THIS IS VERY IMPORTANT BECAUSE JUST ALOW ONLY ONE "EXCEL GOTO" PER BOT JOB
            if (!excelDataGoto.isEmpty()) {
                rowMoveDTO.setType("EDIT_OPERATION");
                rowMoveDTO
                        .getUpdatedRows()
                        .get(0)
                        .setInstructionId(excelDataGoto.get(0).getId());
            } else {
                rowMoveDTO.setType("INSERT_AFTER");
            }
        } else {
            rowMoveDTO.setBlockId(blockId);
            rowMoveDTO.setBlockName(blockName);
        }

        if (!rowMoveDTO.getBlockId().equals(blockId)) {
            blockIdChanged = true;
        } else {
            blockIdChanged = false;
        }

        // This prevents if was deleted when in EDIT _OPERATION
        if (rowMoveDTO.getType().equals("EDIT_OPERATION")) {
            if (!performDataBase.instructionIdExists(
                    rowMoveDTO.getUpdatedRows().get(0).getInstructionId())) {
                rowMoveDTO.setType("INSERT_AFTER");
            }
        }

        if (blockId < 0) {
            performMessage.errorMessage("Block Not Selected", "Select the Block!", null, null, null, 0);
            return;
        }

        // Create and show alert inside Platform.runLater

        //        if (this.botJobLoad.getBlockLoadDTOList() == null) {
        //            this.botJobLoadList = performDataBase.loadBotJobComplete(rowMoveDTO.getBotJobId());
        //            if (this.botJobLoadList.size() > 0) {
        //                this.botJobLoad.setBlockLoadDTOList(this.botJobLoadList.get(0).getBlockLoadDTOList());
        //            }
        //        } else if (this.botJobLoad.getBlockLoadDTOList() != null
        //                && this.botJobLoad.getBlockLoadDTOList().size() == 0) {
        //            this.botJobLoadList = performDataBase.loadBotJobComplete(rowMoveDTO.getBotJobId());
        //            if (this.botJobLoadList.size() > 0) {
        //                this.botJobLoad.setBlockLoadDTOList(this.botJobLoadList.get(0).getBlockLoadDTOList());
        //            }
        //        }

        // Combine the texts using TextFlow

        Text extra = new Text("Action: ");
        extra.setStyle("-fx-font-size: 14px; -fx-fill: blue;");
        extra.setVisible(true);

        if (actions.equalsIgnoreCase(ARConstants.HOLD)
                || actions.equalsIgnoreCase(ARConstants.PAUSE)
                || (actions.equalsIgnoreCase(ARConstants.SCREEN))
                || actions.equalsIgnoreCase(ARConstants.QUIT)
                || actions.equalsIgnoreCase(ARConstants.NEXT_ROW)) {
            regularText1.setText("");
            regularText2.setText("");
            regularText3.setText("");
            regularText4.setText("");
            variableText1.setText("");
            variableText2.setText(name);
            variableText2.setVisible(true);
            variableText3.setText("");
        }

        // Handle loop outside Platform.runLater to ensure multiple iterations
        int endifCount = actions.equalsIgnoreCase(ARConstants.IF) ? 3 : 1;

        // Run the loop for adding multiple instructions
        String nextAction = null;
        int parentId = 0;
        for (int added = endifCount; added >= 1; added--) {

            boolean isShowAlert = added == 1;

            // Run the instruction add in a separate Task
            ErrorMessage errorMessage = performDataBase.preFillInstruction(
                    nextAction == null ? name : nextAction,
                    nextAction == null ? description : nextAction,
                    nextAction == null ? actions : nextAction,
                    nextAction == null ? operation : nextAction,
                    onHold,
                    varId,
                    instructionId,
                    nextAction == null ? -1 : parentId,
                    rowMoveDTO,
                    isShowAlert,
                    rowMoveDTO.getType().equals("EDIT_OPERATION"),
                    blockIdChanged);

            if (errorMessage == null) {

                String tableName = "instruction";
                if (rowMoveDTO.getSessionId().equals("componentTasks")) {
                    tableName = "component_instruction";
                    rowMoveDTO.setOperationId("componentsUpdate");
                    this.botJobLoadList = performDataBase.loadComponentsComplete(
                            rowMoveDTO.getHomeBankingId(), rowMoveDTO.getBotJobId(), rowMoveDTO.getBotJobName());
                } else {
                    rowMoveDTO.setOperationId("updateInstructions");
                    this.botJobLoadList = performDataBase.loadCompleteJobs(rowMoveDTO.getBotJobId());
                }

                String jsonData = "[]";
                if (!botJobLoadList.isEmpty()) {
                    List<InstructionLoadDTO> instructions =
                            performDataBase.buildJsonViewData(botJobLoadList, tableName);
                    jsonData = gson.toJson(instructions);
                }

                webSocketSessionManager.sendMessageJson(
                        rowMoveDTO.getHomeBankingId(),
                        rowMoveDTO.getSessionId(),
                        jsonData,
                        rowMoveDTO.getOperationId());

                if (!rowMoveDTO.getType().equals("EDIT_OPERATION")) {
                    updateFields();
                }
            }

            if (Strings.isNullOrEmpty(nextAction)) {
                nextAction = ARConstants.ELSE;
                // parentId = newRowId;
            } else if (!Strings.isNullOrEmpty(nextAction) && nextAction.equals(ARConstants.ELSE)) {
                nextAction = ARConstants.ENDIF;
            }
        }
        //        }
        //        defineTextFlow(comboBoxInstruc.getValue().getValue());
    }

    public void setSelectedIndexByValue(String instrValue, String[] operations) {
        Platform.runLater(() -> {
            int indexGeneric = -1;

            for (int i = 0; i < itemsInstructions.size(); i++) {
                if (itemsInstructions.get(i).getText().equalsIgnoreCase(instrValue)) {
                    comboBoxInstruc.getSelectionModel().select(i);
                    indexGeneric = i;
                    break;
                }
            }

            if (indexGeneric == -1) {
                comboBoxInstruc.getSelectionModel().selectFirst();
            }

            indexGeneric = -1;
            //            if (instrValue.equals("GetValue")
            //                    || instrValue.equals("SetValue")
            //                    || instrValue.equals("CheckValue")
            //                    || instrValue.equals("ExcelWrite")) {

            Integer instrunctionId = rowMoveDTO.getUpdatedRows().get(0).getParentId() != null
                    ? rowMoveDTO.getUpdatedRows().get(0).getParentId()
                    : -1;

            Integer variableId = rowMoveDTO.getUpdatedRows().get(0).getVariableId() != null
                    ? rowMoveDTO.getUpdatedRows().get(0).getVariableId()
                    : -1;

            String operValue = "";
            if (instrValue.equals("CheckValue") && operations.length == 3) {
                operValue = operations[1];
            }

            indexGeneric = -1;
            // Always Must Have a WebField
            for (int i = 0; i < filteredPageItems.size(); i++) {
                if (filteredPageItems.get(i).getInstructionId().equals(instrunctionId)) {
                    comboBoxWebFields.getSelectionModel().select(i);
                    indexGeneric = i;
                    break;
                }
            }

            reloadComboVars(instrunctionId, false, -1);

            if (indexGeneric == -1) {
                comboBoxWebFields.getSelectionModel().selectFirst();
            }

            for (int i = 0; i < variablesItems.size(); i++) {
                if (variablesItems.get(i).getVarId().equals(variableId)) {
                    comboBoxVars.getSelectionModel().select(i);
                    indexGeneric = i;
                    break;
                }
            }

            if (indexGeneric == -1) {
                comboBoxVars.getSelectionModel().selectFirst();
            }

            indexGeneric = -1;
            for (int i = 0; i < operatorsItems.size(); i++) {
                if (operatorsItems.get(i).getOperator().equals(operValue)) {
                    comboBoxOperator.getSelectionModel().select(i);
                    indexGeneric = i;
                    break;
                }
            }

            if (indexGeneric == -1) {
                comboBoxOperator.getSelectionModel().selectFirst();
            }
            //            }

            indexGeneric = -1;
            if (instrValue.equals("LOOP") || instrValue.equals("REFRESH_LOOP")) {

                for (int i = 0; i < timesItems.size(); i++) {
                    if (timesItems.get(i).getValue().equals(operations[0])) {
                        comboBoxTimes.getSelectionModel().select(i);
                        indexGeneric = i;
                        break;
                    }
                }

                if (indexGeneric == -1) {
                    comboBoxTimes.getSelectionModel().selectFirst();
                }

                for (int i = 0; i < loopsItems.size(); i++) {
                    if (loopsItems.get(i).getValue().equals(operations[1])) {
                        comboBoxLoops.getSelectionModel().select(i);
                        indexGeneric = i;
                        break;
                    }
                }

                if (indexGeneric == -1) {
                    comboBoxLoops.getSelectionModel().selectFirst();
                }
            }

            indexGeneric = -1;
            if (instrValue.equals("GOTO")) {
                for (int i = 0; i < blocksGotoItems.size(); i++) {
                    if (blocksGotoItems.get(i).getValue().equals(operations[0])) {
                        comboBoxBlocksGoto.getSelectionModel().select(i);
                        indexGeneric = i;
                        break;
                    }
                }

                if (indexGeneric == -1) {
                    comboBoxBlocksGoto.getSelectionModel().selectFirst();
                }

                indexGeneric = -1;
                for (int i = 0; i < loopsItems.size(); i++) {
                    if (loopsItems.get(i).getValue().equals(operations[0])) {
                        comboBoxLoops.getSelectionModel().select(i);
                        indexGeneric = i;
                        break;
                    }
                }

                if (indexGeneric == -1) {
                    comboBoxLoops.getSelectionModel().selectFirst();
                }
            }

            indexGeneric = -1;
            if (instrValue.equals("EXCEL GOTO")) {
                for (int i = 0; i < blocksGotoItems.size(); i++) {
                    if (blocksGotoItems
                            .get(i)
                            .getBlockId()
                            .equals(rowMoveDTO.getUpdatedRows().get(0).getParentBlockId())) {
                        comboBoxBlocksGoto.getSelectionModel().select(i);
                        indexGeneric = i;
                        break;
                    }
                }

                if (indexGeneric == -1) {
                    comboBoxBlocksGoto.getSelectionModel().selectFirst();
                }

                indexGeneric = -1;
                for (int i = 0; i < loopsItems.size(); i++) {
                    if (loopsItems.get(i).getValue().equals(operations[0])) {
                        comboBoxLoops.getSelectionModel().select(i);
                        indexGeneric = i;
                        break;
                    }
                }

                if (indexGeneric == -1) {
                    comboBoxLoops.getSelectionModel().selectFirst();
                }
            }

            firstLoad = false;
            recallMessages(comboBoxInstruc.getValue().getValue());
        });
    }

    private Image getImageForTagType(String tagType) {
        if ("input".equals(tagType)) {
            return new Image(ARConstants.ICON_INSERT);
        } else if ("label".equals(tagType)) {
            return new Image(ARConstants.ICON_OUTPUT);
        } else if ("button".equals(tagType)) {
            return new Image(ARConstants.ICON_CLICK);
        } else if ("a".equals(tagType)) {
            return new Image(ARConstants.ICON_LINK);
        }
        return null; // Default case if tagType is neither "input" nor "output"
    }
}
