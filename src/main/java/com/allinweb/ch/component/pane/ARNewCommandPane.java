package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementIcon;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARElementValueScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.application.Platform;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARNewCommandPane extends ARPane {

    // Lists for tables
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final ARElementValueScene arElementValueScene = ARElementValueScene.getInstance();
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARNewCommandPane instance;
    private final Gson gson = new Gson();
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
    TextField gotoField;
    Pattern validInt = Pattern.compile("\\d{0,4}");
    UnaryOperator<TextFormatter.Change> filter = change -> {
        String newText = change.getControlNewText();

        // Must match regex
        if (!validInt.matcher(newText).matches()) {
            return null;
        }

        // Allow empty while typing
        if (newText.isEmpty()) {
            return change;
        }

        try {
            int value = Integer.parseInt(newText);
            // Only accept values between 1 and 9999
            if (value >= 1 && value <= 9999) {
                return change;
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    };
    double buttonWidth = 200;
    double comboOperatorWidth = 50;
    double comboTimesWidth = 70;
    double comboLoopsWidth = 80;
    //    boolean variablesDisable = false;
    boolean blockIdChanged = false;
    Button addNewInstructionButton;
    Button cancelButton;
    private Stage stage;
    private List<String> allowedActions = Arrays.asList(
            WebElementIcon.SET_VALUE.getValue().toUpperCase(),
            WebElementIcon.GET_VALUE.getValue().toUpperCase(),
            WebElementIcon.CHECK_VALUE.getValue().toUpperCase(),
            WebElementIcon.PDF_CHECK.getValue().toUpperCase(),
            WebElementIcon.CSV_CHECK.getValue().toUpperCase(),
            WebElementIcon.NEXT_ENTER.getValue().toUpperCase(),
            WebElementIcon.SWIPE_UP.getValue().toUpperCase(),
            WebElementIcon.SWIPE_DOWN.getValue().toUpperCase(),
            WebElementIcon.GOTO.getValue().toUpperCase(),
            WebElementIcon.EXCEL_GOTO.getValue().toUpperCase(),
            WebElementIcon.EXTRACT_FIELD.getValue().toUpperCase(),
            WebElementIcon.REFRESH_ONLY.getValue().toUpperCase(),
            WebElementIcon.LOOP.getValue().toUpperCase(),
            WebElementIcon.REFRESH_LOOP.getValue().toUpperCase());
    private boolean firstLoad = false;
    private boolean loadeAllCompleted = false;
    // Postgres
    private Connection conn = null;
    private SplitDTO splitDTO;
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
    private Button variableButton;
    //    private Button addExcelNextRowButton;
    private Button addPauseButton;
    private Button addWaitButton30;
    private Button addWaitButton15;
    private Button addWaitButton5;
    private Button addWaitButton2;
    private Button addCloseActionButton;
    private Button addScreenButton;
    private Button refreshWebButton;
    private ComboBox<ComboBoxImage> comboBoxInstruc;
    private ObservableList<ComboBoxImage> itemsInstructions = FXCollections.observableArrayList();
    private ComboBox<ComboBoxVars> comboBoxVars;
    private ObservableList<ComboBoxVars> variablesItems = FXCollections.observableArrayList();
    private ComboBox<FormatOption> comboBoxTimes;
    private ObservableList<FormatOption> timesItems = FXCollections.observableArrayList();
    private ComboBox<FormatOption> comboBoxLoops;
    private ObservableList<FormatOption> loopsItems = FXCollections.observableArrayList();
    private HBox webBoxWebFields;
    private ComboBox<ComboBoxImage> comboBoxWebFields;
    private ObservableList<ComboBoxImage> filteredPageItems = FXCollections.observableArrayList();
    private List<BlockOptions> listOptions;
    private ComboBox<BlockOptions> comboBoxAllBlocks;
    private ComboBox<BlockOptions> comboBoxBlocksGoto;
    private ComboBox<ComboBoxOperator> comboBoxOperator;
    private ObservableList<ComboBoxOperator> operatorsItems = FXCollections.observableArrayList();

    // Private constructor to prevent instantiation
    private ARNewCommandPane() {}

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

    // Helper method for distinct by text
    private static Predicate<BlockOptions> distinctByText() {
        Set<String> seen = new HashSet<>();
        return b -> seen.add(b.getText());
    }

    // Helper method for distinct by text AND blockOrderNumber
    private static Predicate<BlockOptions> distinctByTextAndId() {
        Set<String> seen = new HashSet<>();
        return b -> {
            // Combine text and blockOrderNumber as a unique key
            String key = b.getText() + "#" + b.getBlockId();
            return seen.add(key);
        };
    }

    public void initialize(SplitDTO splitDTO) {
        this.splitDTO = splitDTO;

        String tableName = this.splitDTO.getSessionId().equals("componentTasks") ? "home_banking" : "bot_job";
        String blockTable = this.splitDTO.getSessionId().equals("componentTasks") ? "component_block" : "block";
        int whereId = -1;
        if (this.splitDTO.getSessionId().equals("componentTasks")) {
            whereId = splitDTO.getHomeBankingId() != null ? splitDTO.getHomeBankingId() : -1;
        } else {
            whereId = splitDTO.getBotJobId() != null ? splitDTO.getBotJobId() : -1;
        }

        ErrorMessage errorMessage = performDataBase.loadWebPageFields(whereId, tableName);
        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        this.filteredPageItems.clear();
        this.filteredPageItems.addAll(performLists.getListWebPageItems().stream()
                //                .filter(item -> !"button".equals(item.getTagType()) && !"a".equals(item.getTagType()))
                .map(item -> new ComboBoxImage(
                        item.getText(),
                        getImageForTagType(item.getTagType()),
                        item.getValue(),
                        item.getBlockId(),
                        item.getInstructionId(),
                        item.getOrderNumber()))
                .toList());

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
            itemsInstructions.add(new ComboBoxImage(
                    "PDF Check", new Image(ARConstants.ICON_CHECK), ARConstants.PDF_CHECK, -1, -1, -1));
            itemsInstructions.add(new ComboBoxImage(
                    "CSV Check", new Image(ARConstants.ICON_CHECK), ARConstants.CSV_CHECK, -1, -1, -1));
            itemsInstructions.add(new ComboBoxImage(
                    "NEXT/ENTER", new Image(ARConstants.ICON_NEXT_ENTER), ARConstants.NEXT_ENTER, -1, -1, -1));
            itemsInstructions.add(new ComboBoxImage(
                    "SWIPE UP", new Image(ARConstants.ICON_SWIPE_UP), ARConstants.SWIPE_UP, -1, -1, -1));
            itemsInstructions.add(new ComboBoxImage(
                    "SWIPE DOWN", new Image(ARConstants.ICON_SWIPE_DOWN), ARConstants.SWIPE_DOWN, -1, -1, -1));

            // Add "IF" only if it does not meet the exclusion conditions
            //            if (splitDTO.getIsBetween() != null && !splitDTO.getIsBetween()) {

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

            log.error("Error creating \"DropBox Instructions\"\nError: " + ex.getMessage());
        }

        try {
            operatorsItems = FXCollections.observableArrayList(
                    new ComboBoxOperator("Equals", new Image(ARConstants.ICON_EQUAL), "="),
                    new ComboBoxOperator("Greater", new Image(ARConstants.ICON_GREATER), ">"),
                    new ComboBoxOperator("Less", new Image(ARConstants.ICON_LESS), "<"),
                    new ComboBoxOperator("!=", new Image(ARConstants.ICON_DIFFERENT), "!="),
                    new ComboBoxOperator("Contains", new Image(ARConstants.ICON_CONTAINS), "contains"));
        } catch (Exception ex) {

            log.error("Error creating \"DropBox Operators\"\nError: " + ex.getMessage());
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

        String varTable = splitDTO.getSessionId().equals("componentTasks") ? "component_variable" : "variable";

        if (this.filteredPageItems != null && !this.filteredPageItems.isEmpty()) {
            variablesItems.clear();

            String instrTable = varTable.equals("variable") ? "instruction" : "component_instruction";

            InstructionLoad instructionLoad = performLists.getInstructionById(
                    instrTable, whereId, filteredPageItems.get(0).getInstructionId());

            if (instructionLoad != null) {
                errorMessage = performDataBase.loadAllVariablesByCriteria(
                        varTable, whereId, instructionLoad.getId(), instructionLoad.getName());
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
        }

        List<BlockOptions> listOptions = performLists.loadComboOptions(blockTable, "NewCommandPane");
        if (listOptions.isEmpty()) {
            // If list is empty, populate AllBlocks with a default block
            ObservableList<BlockOptions> defaultAll = FXCollections.observableArrayList(
                    new BlockOptions("#1 Default Block", "Default Block", -1, -1, -1));
            if (comboBoxAllBlocks != null) {
                comboBoxAllBlocks.setItems(defaultAll);
                comboBoxAllBlocks.getSelectionModel().selectFirst();
            }

            // And also for Goto combo
            ObservableList<BlockOptions> defaultGoto =
                    FXCollections.observableArrayList(new BlockOptions("no blocks added", "", -1, -1, -1));
            if (comboBoxBlocksGoto != null) {
                comboBoxBlocksGoto.setItems(defaultGoto);
                comboBoxBlocksGoto.getSelectionModel().selectFirst();
            }
        }

        if (loadeAllCompleted) {
            setCombosAndLabels(varTable, whereId);
            titleDescription();
        }
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        addNewInstructionButton = builder.buildButton("OK", ARConstants.SPACE_L, Insets.EMPTY);
        addNewInstructionButton.getStyleClass().add("ok-button");

        cancelButton = builder.buildButton("Close", ARConstants.SPACE_L, Insets.EMPTY);
        cancelButton.getStyleClass().add("cancel-button");

        variableButton = builder.buildButton(
                "Variables", ARConstants.SPACE_L, ARConstants.ICON_VARIABLES, ARConstants.SPACE_M, Insets.EMPTY);

        //        variableButton.setDisable(variablesDisable);

        //        addExcelNextRowButton = builder.buildButton(
        //                "Data Next Row", ARConstants.SPACE_L, ARConstants.ICON_EXCEL2, ARConstants.SPACE_M, new
        // Insets(5));
        addPauseButton = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_PAUSE, ARConstants.SPACE_M, new Insets(5));
        addWaitButton30 = builder.buildButton(
                "30s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addWaitButton15 = builder.buildButton(
                "15s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addWaitButton5 = builder.buildButton(
                "5s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addWaitButton2 = builder.buildButton(
                "2s", ARConstants.SPACE_L, ARConstants.ICON_WAIT, ARConstants.SPACE_M, new Insets(5));

        addCloseActionButton = builder.buildButton(
                "Add Close Browser", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, new Insets(5));
        addScreenButton = builder.buildButton(
                "Add Screenshot", ARConstants.SPACE_L, ARConstants.ICON_SCREEN, ARConstants.SPACE_M, new Insets(5));

        refreshWebButton = createPathButton();

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

        timesItems.add(new FormatOption("1s", "1"));
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

        // Inside your UI initialization
        TextFormatter<String> textFormatter = new TextFormatter<>(filter);
        gotoField = new TextField();
        gotoField.setTextFormatter(textFormatter);
        gotoField.setPromptText("10");
        gotoField.setPrefWidth(50D);
        gotoField.setVisible(false); // hidden by default
        gotoField.setManaged(false); // Ensure it does not t

        loopsItems.add(new FormatOption("1 x", "1"));
        loopsItems.add(new FormatOption("5 x", "5"));
        loopsItems.add(new FormatOption("10 x", "10"));
        loopsItems.add(new FormatOption("20 x", "20"));
        loopsItems.add(new FormatOption("30 x", "30"));
        loopsItems.add(new FormatOption("other", "other"));
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

        comboBoxBlocksGoto = new ComboBox<>();
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

        comboBoxAllBlocks = new ComboBox<>();
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
                        //                        addExcelNextRowButton,
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

        webBoxWebFields = new HBox();
        webBoxWebFields.setSpacing(0); // No spacing, use margins instead
        HBox.setMargin(comboBoxWebFields, new Insets(0, 3, 0, 0)); // Right margin of 3 pixels
        HBox.setMargin(refreshWebButton, new Insets(0, 3, 0, 0)); // Right margin of 3 pixels
        webBoxWebFields.getChildren().addAll(comboBoxWebFields, refreshWebButton);

        commandBox = new VBox(commandLabel, comboBoxInstruc);
        varsBox = new VBox(botJobVarsLabel, comboBoxVars);
        webFieldsBox = new VBox(webPageLabel, webBoxWebFields);
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
                        buttonBox,
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

        reloadCombosBlocks();
        loadeAllCompleted = true;
        String varTable = "variable";
        int whereId = splitDTO.getBotJobId();
        if (splitDTO.getSessionId().equals("componentTasks")) {
            varTable = "component_variable";
            whereId = splitDTO.getHomeBankingId();
        }
        setCombosAndLabels(varTable, whereId);
    }

    private void titleDescription() {
        InstructionLoad firstInstruction = SplitDTO.mapSplitToInstruction(splitDTO);

        String operation = performMessage.renderInstructionActions(firstInstruction);

        // Create individual text elements with the necessary styling
        currentActionText1 = new Text(splitDTO.getType().replace("_", " "));
        currentActionText1.setStyle("-fx-font-size: 14px; -fx-fill: green;");

        currentActionText2 = new Text(" Instruction: ");
        currentActionText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

        currentActionText3 = new Text(firstInstruction.getName());
        currentActionText3.setStyle("-fx-font-size: 14px; -fx-fill: green;");

        //        currentActionText4 = new Text(operation);
        //        currentActionText4.setStyle("-fx-font-size: 14px; -fx-fill: green;");

        currentActionText5 = new Text(" on Block Name: ");
        currentActionText5.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

        currentActionText6 = new Text(splitDTO.getBlockName());
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

    private void setCombosAndLabels(String tableName, int whereId) {
        if (splitDTO.getType().equals("EDIT_OPERATION")) {
            String actions = splitDTO.getInstructionName();
            String[] operations = splitDTO.getOperation() != null
                    ? splitDTO.getOperation().split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)
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
                reloadComboVars(tableName, whereId, comboBoxWebFields.getValue().getInstructionId(), false, -1);
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
            reloadComboVars(tableName, whereId, comboBoxWebFields.getValue().getInstructionId(), false, -1);
            comboBoxVars.getSelectionModel().selectFirst();
            comboBoxOperator.getSelectionModel().selectFirst();

            comboBoxBlocksGoto.getSelectionModel().selectFirst();
            comboBoxTimes.getSelectionModel().selectFirst();
            comboBoxLoops.getSelectionModel().selectFirst();

            recallMessages(comboBoxInstruc.getValue().getValue());
        }

        if (splitDTO.getBlockId() > -1) {
            // Get the blockId to match
            int targetBlockId = splitDTO.getBlockId();

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
        //        addExcelNextRowButton.setOnAction(
        //                e -> insertNewInstruction("NEXT ROW", "EXCEL NEXT ROW", ARConstants.NEXT_ROW, 0, "", null,
        // null, null));
        addPauseButton.setOnAction(
                e -> insertNewInstruction("PAUSE", "PAUSE Action", ARConstants.PAUSE, 0, "", null, null, null));
        addWaitButton30.setOnAction(e ->
                insertNewInstruction("Wait 30second(s)", "Waiting action", ARConstants.HOLD, 30, "", null, null, null));
        addWaitButton15.setOnAction(e ->
                insertNewInstruction("Wait 15second(s)", "Waiting action", ARConstants.HOLD, 15, "", null, null, null));
        addWaitButton5.setOnAction(e ->
                insertNewInstruction("Wait 5second(s)", "Waiting action", ARConstants.HOLD, 5, "", null, null, null));
        addWaitButton2.setOnAction(e ->
                insertNewInstruction("Wait 2second(s)", "Waiting action", ARConstants.HOLD, 2, "", null, null, null));
        addCloseActionButton.setOnAction(
                e -> insertNewInstruction("Close Browser", "Close Browser", ARConstants.QUIT, 0, "", null, null, null));

        addScreenButton.setOnAction(e -> insertNewInstruction(
                "Screenshot Browser", "Screenshot Browser", ARConstants.SCREEN, 0, "", null, null, null));

        refreshWebButton.setOnMouseClicked(e -> {
            String tableName = splitDTO.getSessionId().equals("componentTasks") ? "home_banking" : "bot_job";
            int whereId = splitDTO.getSessionId().equals("componentTasks")
                    ? splitDTO.getHomeBankingId()
                    : splitDTO.getBotJobId();
            updateFields(tableName, whereId);
        });

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
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.NEXT_ENTER)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.SWIPE_UP)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.SWIPE_DOWN)
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
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.NEXT_ENTER)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.SWIPE_UP)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.SWIPE_DOWN)
                    && !comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.REFRESH_ONLY)) {
                if (comboBoxAllBlocks.getValue() != null
                        && comboBoxWebFields.getValue() != null
                        && !comboBoxAllBlocks
                                .getValue()
                                .getBlockId()
                                .equals(comboBoxWebFields.getValue().getBlockId())) {

                    String outsideBlock = listOptions.stream()
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
                    && listOptions.size() == 1
                    && (comboBoxBlocksGoto.getValue().getBlockId() == -1)) {

                performMessage.errorMessage(
                        "Error", "No Blocks Defined", "It must have ate least Two Blocks defined ", null, null, 0);

                return;
            }

            if ((comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.GOTO)
                            || comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.EXCEL_GOTO))
                    && (comboBoxBlocksGoto.getValue().getBlockId() == -1)) {

                performMessage.errorMessage(
                        "Error", "No Blocks Selected", "It must select the Block Destination ", null, null, 0);

                return;
            }

            if (splitDTO.getInstructionOrderNumber()
                            <= comboBoxWebFields.getValue().getOrderNumber()
                    && (comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.REFRESH_LOOP)
                            || comboBoxInstruc.getValue().getValue().equalsIgnoreCase(ARConstants.LOOP))) {
                // It Forces to LOOP or REFRESH_LOOP BE AFTER THE Instruction
                if (splitDTO.getType().equals("INSERT_AFTER")) {
                    splitDTO.setInstructionOrderNumber(
                            comboBoxWebFields.getValue().getOrderNumber());
                } else {
                    splitDTO.setInstructionOrderNumber(
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
                        //                        comboBoxVars.getValue().getText().substring(1) + ":" +
                        // setValueTo,
                        comboBoxWebFields.getValue().getValue() + ":" + setValueTo,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("GetValue")) {
                insertNewInstruction(
                        "GetValue",
                        "GetValue",
                        ARConstants.GET_VALUE,
                        1,
                        //                        comboBoxVars.getValue().getText().substring(1) + ":"+
                        // comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxWebFields.getValue().getValue() + ":"
                                + comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null);
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
                        comboBoxVars.getValue().getText().toUpperCase() + ":"
                                + comboBoxOperator.getValue().getOperator() + ":" + checkValueFor,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("PDF CHECK")) {
                String checkValueFor =
                        Strings.isNullOrEmpty(comboBoxVars.getValue().getValue())
                                ? "EMPTY"
                                : comboBoxVars.getValue().getValue();

                insertNewInstruction(
                        "PDF CHECK",
                        "PDF CHECK",
                        ARConstants.PDF_CHECK,
                        1,
                        comboBoxVars.getValue().getText().toUpperCase() + ":"
                                + comboBoxOperator.getValue().getOperator() + ":" + checkValueFor,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("CSV CHECK")) {
                String checkValueFor =
                        Strings.isNullOrEmpty(comboBoxVars.getValue().getValue())
                                ? "EMPTY"
                                : comboBoxVars.getValue().getValue();

                insertNewInstruction(
                        "CSV CHECK",
                        "CSV CHECK",
                        ARConstants.CSV_CHECK,
                        1,
                        comboBoxVars.getValue().getText().toUpperCase() + ":"
                                + comboBoxOperator.getValue().getOperator() + ":" + checkValueFor,
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("excelWrite")) {
                insertNewInstruction(
                        "ExcelWrite",
                        "ExcelWrite",
                        ARConstants.EXTRACT_FIELD,
                        2,
                        comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxVars.getValue().getVarId(),
                        comboBoxVars.getValue().getParentId(),
                        null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Refresh")) {
                insertNewInstruction("Refresh", "Refresh", ARConstants.REFRESH_ONLY, 10, "", null, null, null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Loop")) {
                String loopValue =
                        "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                ? (Strings.isNullOrEmpty(gotoField.getText())
                                        ? "10"
                                        : gotoField.getText().trim())
                                : comboBoxLoops.getValue().getValue();

                insertNewInstruction(
                        "LOOP",
                        "LOOP",
                        ARConstants.LOOP,
                        2,
                        comboBoxTimes.getValue().getValue() + ":" + loopValue,
                        null,
                        comboBoxWebFields.getValue().getInstructionId(),
                        null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("Refresh Loop")) {

                String loopValue =
                        "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                ? (Strings.isNullOrEmpty(gotoField.getText())
                                        ? "10"
                                        : gotoField.getText().trim())
                                : comboBoxLoops.getValue().getValue();

                insertNewInstruction(
                        "Refresh Loop",
                        "Refresh Loop",
                        ARConstants.REFRESH_LOOP,
                        2,
                        comboBoxTimes.getValue().getValue() + ":" + loopValue,
                        null,
                        comboBoxWebFields.getValue().getInstructionId(),
                        null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("GOTO")) {

                String gotoValue =
                        "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                ? (Strings.isNullOrEmpty(gotoField.getText())
                                        ? "10"
                                        : gotoField.getText().trim())
                                : comboBoxLoops.getValue().getValue();

                insertNewInstruction(
                        "GOTO",
                        "GOTO",
                        ARConstants.GOTO,
                        1,
                        gotoValue,
                        null, // Block Order Number as VarId
                        null,
                        comboBoxBlocksGoto.getValue().getBlockId() // BLOCK ID as Parent Block Id
                        );
            } else if (comboBoxInstruc.getValue().getValue().equalsIgnoreCase("NEXT_ENTER")) {

                insertNewInstruction("NEXT_ENTER", "NEXT_ENTER", ARConstants.NEXT_ENTER, 1, null, null, null, null);
            } else if (comboBoxInstruc.getValue().getValue().equalsIgnoreCase("SWIPE_UP")) {

                String swipeTimes =
                        "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                ? (Strings.isNullOrEmpty(gotoField.getText())
                                        ? "10"
                                        : gotoField.getText().trim())
                                : comboBoxLoops.getValue().getValue();

                insertNewInstruction("SWIPE_UP", "SWIPE_UP", ARConstants.SWIPE_UP, 1, swipeTimes, null, null, null);
            } else if (comboBoxInstruc.getValue().getValue().equalsIgnoreCase("SWIPE_DOWN")) {

                String swipeTimes =
                        "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                ? (Strings.isNullOrEmpty(gotoField.getText())
                                        ? "10"
                                        : gotoField.getText().trim())
                                : comboBoxLoops.getValue().getValue();

                insertNewInstruction(
                        "SWIPE_DOWN", "SWIPE_DOWN", ARConstants.SWIPE_DOWN, 1, swipeTimes, null, null, null);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("EXCEL GOTO")) {
                String previous = splitDTO.getType();
                insertNewInstruction(
                        "EXCEL GOTO",
                        "EXCEL GOTO",
                        ARConstants.EXCEL_GOTO,
                        1,
                        "1",
                        null, // Block Order Number as VarId
                        null,
                        comboBoxBlocksGoto.getValue().getBlockId() // BLOCK ID as Parent Block Id
                        );
                splitDTO.setType(previous);
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("IF")) {
                insertNewInstruction(
                        "IF",
                        "IF",
                        ARConstants.IF,
                        1,
                        "IF",
                        null, // Block Order Number as VarId
                        null, // Parent Id null
                        null // Parent Block Id null
                        );
            }

            //            PerformDataBase..changeDbConnection(previousDB);
        });

        cancelButton.setOnMouseClicked((e) -> {
            log.info("ARNewCommandPane Close()");
            Platform.runLater(() -> {
                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                stage.close();
            });
        });

        variableButton.setOnAction(e -> {
            String varTable = "variable";
            int whereId = splitDTO.getBotJobId();
            if (splitDTO.getSessionId().equals("componentTasks")) {
                varTable = "component_variable";
                whereId = splitDTO.getHomeBankingId();
            }

            if (this.splitDTO != null) {

                log.info("creating variable for instruction Name " + splitDTO.getInstructionName());

                String varName = prepareVarName();

                reloadComboVars(varTable, whereId, comboBoxWebFields.getValue().getInstructionId(), false, -1);
                // Set ComboBox to first item
                comboBoxVars.getSelectionModel().selectFirst();
                if (!firstLoad) {
                    recallMessages(comboBoxInstruc.getValue().getValue());
                }

                callInitializeElementValueScene(varName);
                arElementValueScene.showModal();
            } else {

                log.info("creating variable for instruction Name "
                        + comboBoxWebFields.getValue().getText());

                String varName = prepareVarName();
                callInitializeElementValueScene(varName);
            }
            reloadComboVars(varTable, whereId, comboBoxWebFields.getValue().getInstructionId(), false, -1);
            // Set ComboBox to first item
            comboBoxVars.getSelectionModel().selectFirst();
            if (comboBoxVars.getValue() != null) {
                recallMessages(comboBoxInstruc.getValue().getValue());
            }
        });

        //        comboBoxWebFields.getSelectionModel().selectedItemProperty().addListener(new
        // ChangeListener<ComboBoxImage>() {
        //            @Override
        //            public void changed(
        //                    ObservableValue<? extends ComboBoxImage> observable,
        //                    ComboBoxImage oldValue,
        //                    ComboBoxImage newValue) {
        //                if (newValue != null) {
        //                    if (!firstLoad)  {
        //                        reloadComboVars(tableName, whereId, newValue.getInstructionId(), false, -1);
        //                        comboBoxVars.getSelectionModel().selectFirst();
        //                        recallMessages(comboBoxInstruc.getValue().getValue());
        //                    }
        //                }
        //            }
        //        });

        // Add a listener to print the ID when the selection changes
        comboBoxWebFields.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals("No Web Fields")) {
                if (!firstLoad) {
                    String varTable = "variable";
                    int whereId = splitDTO.getBotJobId();
                    if (splitDTO.getSessionId().equals("componentTasks")) {
                        varTable = "component_variable";
                        whereId = splitDTO.getHomeBankingId();
                    }
                    reloadComboVars(varTable, whereId, newValue.getInstructionId(), false, -1);
                    comboBoxVars.getSelectionModel().selectFirst();
                    recallMessages(comboBoxInstruc.getValue().getValue());
                }
            }
        });

        // Add a listener to comboBoxInstruc to handle selection changes
        comboBoxInstruc.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    if (newValue.getValue().equals("EXCEL GOTO")) {
                        loadBlockGoto(-99);
                    } else {
                        if (comboBoxBlocksGoto != null) {
                            loadBlockGoto(comboBoxAllBlocks.getValue().getBlockId());
                        } else {
                            loadBlockGoto(-99);
                        }
                    }

                    recallMessages(comboBoxInstruc.getValue().getValue());
                    if (comboBoxVars.getValue() != null
                            && comboBoxVars.getValue().getVarId() > -1) {
                        arElementValueScene.setTableRowById(
                                comboBoxVars.getValue().getVarId());
                    } else {
                        String varName = prepareVarName();
                        callInitializeElementValueScene(varName);
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

                        if (!arElementValueScene.closeCalled) {
                            String varName = prepareVarName();
                            callInitializeElementValueScene(varName);
                        }

                        arElementValueScene.setTableRowById(
                                comboBoxVars.getValue().getVarId());
                    } else {
                        String varName = prepareVarName();
                        callInitializeElementValueScene(varName);
                    }
                }
            }
        });

        // Add a listener to comboBoxBlocks to handle selection changes
        comboBoxAllBlocks.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (!firstLoad) {
                    if (splitDTO.getActions() != null && splitDTO.getActions().equals("EXCEL GOTO")) {
                        loadBlockGoto(-99);
                    } else {
                        loadBlockGoto(newValue.getBlockId());
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

        // Listener for comboBoxLoops selection
        comboBoxLoops.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Show/hide the nameField when "other" is selected
                boolean isOther = "other".equalsIgnoreCase(newValue.getValue());
                gotoField.setVisible(isOther);
                gotoField.setManaged(isOther);

                if (isOther) {
                    gotoField.setText("10"); // reset when selecting "other"
                    gotoField.requestFocus(); // optional: auto-focus for typing
                }

                // Call recallMessages when not firstLoad
                if (!firstLoad) {
                    var selectedInstruc = comboBoxInstruc.getValue();
                    if (selectedInstruc != null) {
                        recallMessages(selectedInstruc.getValue());
                    }
                }
            }
        });

        // Listener for nameField typing (fires recallMessages when user changes text)
        gotoField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!firstLoad) {
                var selectedInstruc = comboBoxInstruc.getValue();
                if (selectedInstruc != null) {
                    recallMessages(selectedInstruc.getValue());
                }
            }
        });
    }

    private void callInitializeElementValueScene(String varName) {
        if (!comboBoxVars.getItems().isEmpty() && comboBoxVars.getValue() != null) {
            arElementValueScene.initialize(
                    splitDTO,
                    comboBoxVars.getValue().getVarId(),
                    varName,
                    comboBoxVars.getValue().getValue(),
                    comboBoxWebFields.getValue().getInstructionId(),
                    comboBoxWebFields.getValue().getText(),
                    comboBoxInstruc.getValue().getValue());
        }
        //        arElementValueScene.showModal();
    }

    private String prepareVarName() {
        String varName;

        if (comboBoxVars.getValue() != null
                && comboBoxVars.getValue().getVarId() != null
                && comboBoxVars.getValue().getVarId() == -1) {
            varName = comboBoxWebFields.getValue().getValue();
        } else if (comboBoxVars.getValue() != null && comboBoxVars.getValue().getText() != null) {
            varName = comboBoxVars.getValue().getText();
        } else {
            varName = comboBoxWebFields.getValue() != null
                            && comboBoxWebFields.getValue().getValue() != null
                    ? comboBoxWebFields.getValue().getValue()
                    : "CHANGE ME";
        }
        if (varName.contains("$") || varName.contains("#")) {
            varName = varName.substring(1);
        }
        return varName;
    }

    private void recallMessages(String valueEdit) {
        // Set the visibility of comboBoxOperator based on the selected value
        if (ARConstants.CHECK_VALUE.equalsIgnoreCase(valueEdit)
                || ARConstants.PDF_CHECK.equalsIgnoreCase(valueEdit)
                || ARConstants.CSV_CHECK.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Bot-Job Variable");
            botJobVarsLabel.setVisible(true);
            webPageLabel.setVisible(true);
            comboBoxOperator.setVisible(true);
            comboBoxWebFields.setVisible(true);
            webBoxWebFields.setVisible(true);
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
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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
            webBoxWebFields.setVisible(false);
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

                variableButtonRow = new HBox(10, blankText, loopText, comboBoxLoops, gotoField, textFlow);

                vboxAll.getChildren()
                        .addAll(
                                operationSelected,
                                // labelRow, // Web Page Label row
                                comboBoxesRow, // ComboBoxes row
                                variableButtonRow, // Variable Button row
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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
            webBoxWebFields.setVisible(false);

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
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
            }

        } else if (ARConstants.NEXT_ENTER.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Block Destination");
            botJobVarsLabel.setVisible(false);
            webPageLabel.setVisible(false);
            comboBoxBlocksGoto.setVisible(false);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(false);
            webBoxWebFields.setVisible(false);
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
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
            }

        } else if (ARConstants.SWIPE_UP.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Block Destination");
            botJobVarsLabel.setVisible(false);
            webPageLabel.setVisible(false);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(false);
            webBoxWebFields.setVisible(false);
            comboBoxAllBlocks.setVisible(true);

            variableButton.setVisible(false);

            comboBoxVars.setVisible(false);
            comboBoxBlocksGoto.setVisible(false);
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
                comboBoxesRow.getChildren().addAll(commandBox);

                variableButtonRow = new HBox(10, blankText, timesText, comboBoxLoops, gotoField, textFlow);

                vboxAll.getChildren()
                        .addAll(
                                operationSelected,
                                // labelRow, // Web Page Label row
                                comboBoxesRow, // ComboBoxes row
                                variableButtonRow, // Variable Button row
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
            }

        } else if (ARConstants.SWIPE_DOWN.equalsIgnoreCase(valueEdit)) {
            defineTextFlow(comboBoxInstruc.getValue().getValue());

            textFlow.setVisible(true);
            //                    textFlow.setPrefWidth(buttonWidth + 100);

            //                    botJobVarsLabel.setText("Block Destination");
            botJobVarsLabel.setVisible(false);
            webPageLabel.setVisible(false);
            comboBoxOperator.setVisible(false);
            comboBoxWebFields.setVisible(false);
            webBoxWebFields.setVisible(false);
            comboBoxAllBlocks.setVisible(true);

            variableButton.setVisible(false);

            comboBoxVars.setVisible(false);
            comboBoxBlocksGoto.setVisible(false);
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
                comboBoxesRow.getChildren().addAll(commandBox);

                variableButtonRow = new HBox(10, blankText, timesText, comboBoxLoops, gotoField, textFlow);

                vboxAll.getChildren()
                        .addAll(
                                operationSelected,
                                // labelRow, // Web Page Label row
                                comboBoxesRow, // ComboBoxes row
                                variableButtonRow, // Variable Button row
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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
            webBoxWebFields.setVisible(false);
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
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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
            webBoxWebFields.setVisible(true);
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
                        new HBox(10, blankText, timesText, comboBoxTimes, loopText, comboBoxLoops, gotoField, textFlow);

                vboxAll.getChildren()
                        .addAll(
                                operationSelected,
                                // labelRow, // Web Page Label row
                                comboBoxesRow, // ComboBoxes row
                                variableButtonRow, // Variable Button row
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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
            webBoxWebFields.setVisible(true);
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
                        new HBox(10, blankText, timesText, comboBoxTimes, loopText, comboBoxLoops, gotoField, textFlow);

                vboxAll.getChildren()
                        .addAll(
                                operationSelected,
                                // labelRow, // Web Page Label row
                                comboBoxesRow, // ComboBoxes row
                                variableButtonRow, // Variable Button row
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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
            webBoxWebFields.setVisible(false);
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
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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
            webBoxWebFields.setVisible(true);
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
                                buttonBox,
                                addNewsBox,
                                instructionButtonsRow // Add Instruction and Cancel Buttons row
                                );

                // labelRow.requestLayout();
                vboxAll.requestLayout();
                mainPane.requestLayout();

            } catch (Exception ex) {
                log.info(ex.getMessage());
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

                    String gotoValue =
                            "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                    ? Strings.isNullOrEmpty(gotoField.getText())
                                            ? "10"
                                            : gotoField.getText().trim()
                                    : comboBoxLoops.getValue().getText();

                    variableText2.setText(gotoValue);
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
                case ARConstants.NEXT_ENTER:
                    regularText1.setText("Device NEXT/ENTER command");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    //                    variableText1.setText(comboBoxBlocksGoto.getValue().getText());
                    //                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");
                    //
                    //                    regularText2.setText(" Limit: ");
                    //                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");
                    //
                    //                    String gotoValue =
                    //                            "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                    //                                    ? Strings.isNullOrEmpty(gotoField.getText())
                    //                                    ? "10"
                    //                                    : gotoField.getText().trim()
                    //                                    : comboBoxLoops.getValue().getText();

                    //                    variableText2.setText(gotoValue);
                    //                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    //                    regularText3.setText(" Times");
                    //                    regularText3.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(false);
                    regularText3.setVisible(false);
                    regularText4.setVisible(false);

                    variableText1.setVisible(false);
                    variableText2.setVisible(false);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren()
                            .addAll(regularText1, variableText1, regularText2, variableText2, regularText3);
                    textFlow.requestLayout();

                    break;
                case ARConstants.SWIPE_UP:
                    regularText1.setText("Device SWIPE UP : ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    //                    variableText1.setText(comboBoxBlocksGoto.getValue().getText());
                    //                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    //                    regularText2.setText(" Times: ");
                    //                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    String swipeTimes =
                            "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                    ? Strings.isNullOrEmpty(gotoField.getText())
                                            ? "10"
                                            : gotoField.getText().trim()
                                    : comboBoxLoops.getValue().getText();

                    variableText2.setText(swipeTimes);
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText3.setText(" Times");
                    regularText3.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(false);
                    regularText3.setVisible(true);
                    regularText4.setVisible(false);

                    variableText1.setVisible(false);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText2, regularText3);
                    textFlow.requestLayout();

                    break;
                case ARConstants.SWIPE_DOWN:
                    regularText1.setText("Device SWIPE UP : ");
                    regularText1.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    //                    variableText1.setText(comboBoxBlocksGoto.getValue().getText());
                    //                    variableText1.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    //                    regularText2.setText(" Times: ");
                    //                    regularText2.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    swipeTimes =
                            "other".equalsIgnoreCase(comboBoxLoops.getValue().getValue())
                                    ? Strings.isNullOrEmpty(gotoField.getText())
                                            ? "10"
                                            : gotoField.getText().trim()
                                    : comboBoxLoops.getValue().getText();

                    variableText2.setText(swipeTimes);
                    variableText2.setStyle("-fx-font-size: 14px; -fx-fill: red;");

                    regularText3.setText(" Times");
                    regularText3.setStyle("-fx-font-size: 14px; -fx-fill: blue;");

                    regularText1.setVisible(true);
                    regularText2.setVisible(false);
                    regularText3.setVisible(true);
                    regularText4.setVisible(false);

                    variableText1.setVisible(false);
                    variableText2.setVisible(true);
                    variableText3.setVisible(false);

                    textFlow.getChildren().clear();
                    textFlow.getChildren().addAll(regularText1, variableText2, regularText3);
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
                case ARConstants.PDF_CHECK:
                    regularText1.setText("PDF CHECK Variable: ");
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
                case ARConstants.CSV_CHECK:
                    regularText1.setText("CSV CHECK Variable: ");
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

    public void reloadComboVars(String varTable, int whereId, int instructionId, boolean selectLast, int variableId) {

        // Always bind to the same observable list instance
        //        if (comboBoxVars.getItems() != variablesItems) {
        comboBoxVars.setItems(variablesItems);
        //        }

        variablesItems.clear();

        // Load variables for the instruction
        String instrTable = "variable".equals(varTable) ? "instruction" : "component_instruction";
        InstructionLoad instructionLoad = performLists.getInstructionById(instrTable, whereId, instructionId);

        if (instructionLoad != null) {
            ErrorMessage errorMessage = performDataBase.loadAllVariablesByCriteria(
                    varTable, whereId, instructionLoad.getId(), instructionLoad.getName());
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }

        List<VariableUserDTO> vars = performLists.getListVariablesUser();

        // ✅ If no variables, ALWAYS show the placeholder and select it
        if (vars == null || vars.isEmpty()) {
            variablesItems.add(new ComboBoxVars("no variables added", "", -1, -1, -1, -1, null, -1, null));
            comboBoxVars.getSelectionModel().selectFirst();
            return; // important: stop here
        }

        // Populate the combobox items
        for (VariableUserDTO v : vars) {
            String type = v.getType();
            String prefix = (type != null && !type.isEmpty()) ? type.substring(0, 1) : "";
            String displayName = prefix + v.getName();

            variablesItems.add(new ComboBoxVars(
                    displayName, v.getValue(), -1, -1, v.getParentId(), v.getId(), null, -1, v.getLocalFormat()));
        }

        // Selection logic
        if (selectLast && variableId == -1) {
            comboBoxVars.getSelectionModel().selectLast();
            return;
        }

        if (selectLast && variableId > -1) {
            for (int i = 0; i < variablesItems.size(); i++) {
                Integer id = variablesItems.get(i).getVarId();
                if (id != null && id == variableId) {
                    comboBoxVars.getSelectionModel().select(i);
                    return;
                }
            }
            comboBoxVars.getSelectionModel().selectFirst();
            return;
        }

        comboBoxVars.getSelectionModel().selectFirst();
    }

    private void updateFields(String tableName, int whereId) {
        ErrorMessage errorMessage = performDataBase.loadWebPageFields(whereId, tableName);
        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        this.filteredPageItems.clear();
        this.filteredPageItems.addAll(performLists.getListWebPageItems().stream()
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
            filteredPageItems.add(new ComboBoxImage(
                    "No Web Fields", new Image(ARConstants.ICON_BLANK), ARConstants.NO_VALUE, -1, -1, -1));
        }

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
        for (VariableUserDTO dto : performLists.getListVariablesUser()) {
            if (dto.getName().trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void loadBlockGoto(int blockToAvoid) {
        if (comboBoxBlocksGoto == null) {
            return; // Exit early if comboBoxBlocksGoto is not initialized
        }

        Platform.runLater(() -> {
            ObservableList<BlockOptions> filtered = FXCollections.observableArrayList();

            if (!listOptions.isEmpty()) {
                // Apply filtering
                List<BlockOptions> tempList = listOptions.stream()
                        .filter(option -> {
                            if (comboBoxInstruc.getValue() != null
                                    && comboBoxInstruc.getValue().getText().equalsIgnoreCase("EXCEL GOTO")) {
                                return true;
                            } else return !option.getBlockId().equals(blockToAvoid);
                        })
                        .distinct() // optional: may combine with the custom predicate
                        .collect(Collectors.toList());

                // Apply distinctByTextAndId
                //                List<BlockOptions> distinctList =
                //                        tempList.stream().filter(distinctByTextAndId()).collect(Collectors.toList());

                if (tempList.isEmpty()) {
                    tempList.add(new BlockOptions("no blocks available", "", -1, -1, -1));
                }

                filtered.addAll(tempList);
            } else {
                filtered.add(new BlockOptions("no blocks added", "", -1, -1, -1));
            }

            comboBoxBlocksGoto.setItems(filtered);
            comboBoxBlocksGoto.getSelectionModel().selectFirst();
        });
    }

    private void loadAllBlocks() {
        if (comboBoxAllBlocks != null) {
            Platform.runLater(() -> {
                comboBoxAllBlocks.setItems(FXCollections.observableArrayList(listOptions));

                // Select target block
                if (splitDTO.getBlockId() > -1) {
                    int targetBlockId = splitDTO.getBlockId();
                    comboBoxAllBlocks.getItems().stream()
                            .filter(item -> item.getBlockId() != null && item.getBlockId() == targetBlockId)
                            .findFirst()
                            .ifPresentOrElse(
                                    item -> comboBoxAllBlocks
                                            .getSelectionModel()
                                            .select(item),
                                    () -> comboBoxAllBlocks.getSelectionModel().selectFirst());
                } else {
                    comboBoxAllBlocks.getSelectionModel().selectFirst();
                }
            });
        }
    }

    @Override
    public void clearPane(Pane panel) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.info(e.getMessage());
            }
        }
    }

    private void insertNewInstruction(
            String name,
            String description,
            String actions,
            Integer onHold,
            String operation,
            Integer variableId,
            Integer parentId,
            Integer parentBlockId) {

        Integer blockId = comboBoxAllBlocks.getValue().getBlockId();
        String blockName = comboBoxAllBlocks.getValue().getText();

        this.splitDTO.setBlockName(blockName);

        if (!this.splitDTO.getBlockId().equals(blockId)) {
            blockIdChanged = true;
            this.splitDTO.setBlockId(blockId);
        } else {
            blockIdChanged = false;
        }

        // This will make the EXCEL GOTO TO BE RELOCATES JUST AS INFO
        // EXCEL GOTO IS GOING TO BE RENDERED DIFFERENTLY ON GridItems
        if (parentBlockId != null && actions.equals("EXCEL GOTO")) {
            blockId = comboBoxBlocksGoto.getValue().getBlockId();
            blockName = comboBoxBlocksGoto.getValue().getText();
            this.splitDTO.setBlockId(blockId);
            this.splitDTO.setBlockName(blockName);
            this.splitDTO.setBlockId(parentBlockId);
            this.splitDTO.setParentBlockId(parentBlockId);

            String tableName =
                    this.splitDTO.getSessionId().equals("componentTasks") ? "component_instruction" : "instruction";
            int whereId = this.splitDTO.getSessionId().equals("componentTasks")
                    ? this.splitDTO.getHomeBankingId()
                    : this.splitDTO.getBotJobId();
            try {
                List<InstructionLoad> excelDataGoto = performDBEngine.loadExcelGotoBlock(whereId, tableName);

                // THIS IS VERY IMPORTANT BECAUSE JUST ALLOWS ONLY ONE "EXCEL GOTO" PER BOT JOB
                if (!excelDataGoto.isEmpty()) {
                    this.splitDTO.setType("EDIT_OPERATION");
                    this.splitDTO.setInstructionId(excelDataGoto.get(0).getId());

                } else {
                    this.splitDTO.setType("INSERT_AFTER");
                }
            } catch (Exception error) {
                log.warn("Error reading 'EXCEL GOTO' instructions: " + error.getMessage());
            }

        } else {
            // Parent Id
            this.splitDTO.setVariableId(variableId);
            this.splitDTO.setParentId(parentId);
            this.splitDTO.setParentBlockId(parentBlockId);
        }

        this.splitDTO.setVariableId(variableId);

        // This prevents if was deleted when in EDIT _OPERATION
        if (this.splitDTO.getType().equals("EDIT_OPERATION")) {
            if (!performDataBase.instructionIdExists(this.splitDTO.getInstructionId())) {
                this.splitDTO.setType("INSERT_AFTER");
            }
        }

        if (blockId < -1) {
            performMessage.errorMessage("Block Not Selected", "Select the Block!", null, null, null, 0);
            return;
        }

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

        // Run the instruction add in a separate Task
        ErrorMessage errorMessage = performDataBase.preFillNewInstruction(
                name, description, actions, operation, onHold, this.splitDTO, blockIdChanged);

        if (errorMessage == null) {

            String tableName;
            Integer whereId;

            tableName = "instruction";
            whereId = this.splitDTO.getBotJobId();
            this.splitDTO.setOperationId("updateInstructions");

            // Check componentTasks case
            if ("componentTasks".equalsIgnoreCase(this.splitDTO.getSessionId())) {
                tableName = "component_instruction";
                whereId = this.splitDTO.getHomeBankingId();
                this.splitDTO.setOperationId("componentsUpdate");

                errorMessage = performDataBase.loadComponentsComplete(
                        this.splitDTO.getHomeBankingId(), this.splitDTO.getBotJobId(), this.splitDTO.getBotJobName());

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }

            } else {
                errorMessage = performDBEngine.loadCompleteJobs(this.splitDTO.getBotJobId());

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }

            List<BotJobLoadDTO> listBotJobs =
                    tableName.equals("instruction") ? performLists.getListBotJob() : performLists.getListBotJobComp();

            String jsonData = "[]";
            if (!listBotJobs.isEmpty()) {
                List<InstructionLoad> instructions = performLists.buildJsonViewData(listBotJobs);
                jsonData = gson.toJson(instructions);
            }

            webSocketSessionManager.sendMessageJson(
                    this.splitDTO.getHomeBankingId(),
                    this.splitDTO.getSessionId(),
                    jsonData,
                    this.splitDTO.getOperationId());

            //            if (!splitDTO.getType().equals("EDIT_OPERATION")) {
            //                updateFields();
            //            }
        }

        //            if (Strings.isNullOrEmpty(nextAction)) {
        //                nextAction = ARConstants.ELSE;
        //                // parentId = newRowId;
        //            } else if (!Strings.isNullOrEmpty(nextAction) && nextAction.equals(ARConstants.ELSE)) {
        //                nextAction = ARConstants.ENDIF;
        //            }
        //        }
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

            Integer instrunctionId = splitDTO.getParentId() != null ? splitDTO.getParentId() : -1;

            Integer variableId = splitDTO.getVariableId() != null ? splitDTO.getVariableId() : -1;

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

            String varTable = "variable";
            int whereId = splitDTO.getBotJobId();
            if (splitDTO.getSessionId().equals("componentTasks")) {
                varTable = "component_variable";
                whereId = splitDTO.getHomeBankingId();
            }

            reloadComboVars(varTable, whereId, instrunctionId, false, -1);

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
                for (int i = 0; i < listOptions.size(); i++) {
                    if (listOptions.get(i).getBlockId().equals(splitDTO.getParentBlockId())) {
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
                    // Check if operations[0] is an integer
                    try {
                        int value = Integer.parseInt(operations[0]);
                        // Select last item ("other") and set value in nameField
                        comboBoxLoops.getSelectionModel().select(loopsItems.size() - 1);
                        gotoField.setText(String.valueOf(value));
                    } catch (NumberFormatException e) {
                        // Not an integer → select first item
                        comboBoxLoops.getSelectionModel().selectFirst();
                        gotoField.setText("10");
                    }
                }
            }

            indexGeneric = -1;
            if (instrValue.equals("EXCEL GOTO")) {
                for (int i = 0; i < listOptions.size(); i++) {
                    if (listOptions.get(i).getBlockId().equals(splitDTO.getParentBlockId())) {
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
                    // Check if operations[0] is an integer
                    try {
                        int value = Integer.parseInt(operations[0]);
                        // Select last item ("other") and set value in nameField
                        comboBoxLoops.getSelectionModel().select(loopsItems.size() - 1);
                        gotoField.setText(String.valueOf(value));
                    } catch (NumberFormatException e) {
                        // Not an integer → select first item
                        comboBoxLoops.getSelectionModel().selectFirst();
                        gotoField.setText("10");
                    }
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

    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    public ErrorMessage reloadDBBlocks(int whereId, String tableName) {
        try {
            // Handle INSERT_NEW case
            listOptions = performLists.loadComboOptions(tableName, "NewCommandPane");
            if (splitDTO != null && "INSERT_NEW".equals(splitDTO.getType()) && listOptions.size() == 1) {
                splitDTO.setBlockId(listOptions.get(0).getBlockId());
            }

            // Handle GOTO case
            if (splitDTO != null) {
                if ("EXCEL GOTO".equals(splitDTO.getActions())) {
                    loadBlockGoto(-99);
                } else {
                    loadBlockGoto(splitDTO.getBlockId());
                }
            }

            loadAllBlocks();
            return null;
        } catch (Exception error) {
            log.error("Error :" + error.getMessage());
            return new ErrorMessage(
                    "Error in Reload DB Blocks", "Error during loading reloadDBBlocks", error.getMessage());
        }
    }

    public void reloadCombosBlocks() {
        if (listOptions != null) {
            if (splitDTO != null) {
                if ("EXCEL GOTO".equals(splitDTO.getActions())) {
                    loadBlockGoto(-99);
                } else {
                    loadBlockGoto(splitDTO.getBlockId());
                }
            }
            loadAllBlocks();
        }
    }

    // Allow the stage to be set from outside when pane is shown
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    // 🔹 Method to close the window
    public void closePane() {
        if (this.stage != null) {
            Platform.runLater(() -> {
                this.stage.close();
                instance = null; // optional reset for singleton
            });
        }
    }
}
