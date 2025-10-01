package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.FormatOption;
import com.allinweb.ch.component.model.InstructionLoad;
import com.allinweb.ch.component.model.SplitDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARExcelFilePane extends ARPane {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARExcelFilePane instance;
    private final Gson gson = new Gson();
    // UI Components
    Label titleLabel;
    Label blockNameLabel;
    Label pathExportLabel;
    Label fileExportLabel;
    Label fileTypeLabel;
    Label delimeterCSVLabel;
    TextField pathExport;
    TextField fileExport;
    ObservableList<String> filetypeList =
            FXCollections.observableArrayList(ARConstants.FILE_FORMAT_EXCEL, ARConstants.FILE_FORMAT_CSV);
    ChoiceBox<String> fileTypeChoiceBox = new ChoiceBox<>();
    ComboBox<FormatOption> comboBoxCSVColumns;
    Button pathExportButton;
    Button pathDeleteButton;
    Button saveButton;
    Button cancelButton;
    VBox pathGroup;
    AnchorPane mainPane;
    double buttonWidth = 200;
    String excelPath;
    String directory;
    String fileName;
    String delimiter;
    private Stage modalStage;
    private SplitDTO splitDTO;
    private String sessionId;
    // Private constructor to prevent instantiation
    private ARExcelFilePane() {

        super();
    }

    public static ARExcelFilePane getInstance() {
        if (instance == null) {
            synchronized (ARExcelFilePane.class) {
                if (instance == null) {
                    instance = new ARExcelFilePane();
                }
            }
        }
        return instance;
    }

    public void initialize(String sessionId, SplitDTO splitDTO, Stage modalStage) {
        this.sessionId = sessionId;
        this.splitDTO = splitDTO;
        this.modalStage = modalStage;

        if (titleLabel != null) {
            titleLabel.setText(
                    "Block name: #" + this.splitDTO.getBlockOrderNumber() + "-" + this.splitDTO.getBlockName());
        }

        if (fileExport != null && pathExport != null) {
            extractPathAndFileName();

            pathExport.setText(directory);
            fileExport.setText(fileName);
        }

        if (comboBoxCSVColumns != null && !Strings.isNullOrEmpty(splitDTO.getExportFile())) {
            // Update the checkboxes based on the selected user's type
            String[] fileParts = splitDTO.getExportFile().split(":");
            if (fileParts.length > 2 && fileParts[2].equals("|")) {
                comboBoxCSVColumns.getSelectionModel().selectLast();
            } else {
                comboBoxCSVColumns.getSelectionModel().selectFirst();
            }
        }
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Create the title label
        // Create the "Block Name :" label
        blockNameLabel = new Label("Block Name :");
        blockNameLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: blue;"); // Blue font color

        // Create the title label
        titleLabel = new Label("#" + this.splitDTO.getBlockOrderNumber() + "-" + this.splitDTO.getBlockName());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: darkgreen;"); // Green dark

        HBox titleBox = new HBox(10, blockNameLabel, titleLabel); // Put labels in an HBox
        titleBox.setAlignment(Pos.CENTER); // Align the labels in the center of the HBox

        pathExportLabel = new Label("Export Path:");

        extractPathAndFileName();

        pathExport = createPathTextField(directory);
        pathExportButton = createPathButton();
        pathDeleteButton = createDeleteButton();
        fileExportLabel = new Label("File Name");
        fileExport = createPathTextField(fileName);
        //        AnchorPane exportGroup = new AnchorPane(pathExport, pathExportButton);
        fileTypeLabel = new Label("File Type");
        delimeterCSVLabel = new Label("Delimiter");

        fileTypeChoiceBox.setItems(filetypeList);

        if (!fileName.toLowerCase().endsWith(".csv")) {
            fileTypeChoiceBox.getSelectionModel().selectFirst();
        } else {
            fileTypeChoiceBox.getSelectionModel().selectLast();
        }

        // Create ComboBox for number format
        comboBoxCSVColumns = new ComboBox<>();
        comboBoxCSVColumns
                .getItems()
                .addAll(new FormatOption("Comma: \",\"", ","), new FormatOption("Pipe \"|\"", "|"));
        comboBoxCSVColumns.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
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

        comboBoxCSVColumns.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
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

        if (comboBoxCSVColumns != null && !Strings.isNullOrEmpty(splitDTO.getExportFile())) {
            // Update the checkboxes based on the selected user's type
            if (delimiter.equals("|")) {
                comboBoxCSVColumns.getSelectionModel().selectLast();
            } else {
                comboBoxCSVColumns.getSelectionModel().selectFirst();
            }
        }

        GridPane gridPaneExport = new GridPane();
        //        gridPaneLog.setVgap(10);
        gridPaneExport.setHgap(5);
        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        ColumnConstraints colExp1 = new ColumnConstraints();
        colExp1.setPercentWidth(40);

        ColumnConstraints colExp2 = new ColumnConstraints();
        colExp2.setPercentWidth(20);

        ColumnConstraints colExp3 = new ColumnConstraints();
        colExp3.setPercentWidth(10);

        ColumnConstraints colExp4 = new ColumnConstraints();
        colExp4.setPercentWidth(20);

        ColumnConstraints colExp5 = new ColumnConstraints();
        colExp5.setPercentWidth(5);

        ColumnConstraints colExp6 = new ColumnConstraints();
        colExp6.setPercentWidth(5);

        gridPaneExport.getColumnConstraints().addAll(colExp1, colExp2, colExp3, colExp4, colExp5, colExp6);

        // Add LABELS in the first row
        gridPaneExport.add(pathExportLabel, 0, 0);
        gridPaneExport.add(fileExportLabel, 1, 0);
        gridPaneExport.add(fileTypeLabel, 2, 0);
        gridPaneExport.add(delimeterCSVLabel, 3, 0);

        // Add text FIELDS in the second row
        gridPaneExport.add(pathExport, 0, 1);
        gridPaneExport.add(fileExport, 1, 1);
        gridPaneExport.add(fileTypeChoiceBox, 2, 1);
        gridPaneExport.add(comboBoxCSVColumns, 3, 1);

        // Add button in the second row, third column
        gridPaneExport.add(pathExportButton, 4, 1);

        // Add button in the second row, fourth column
        gridPaneExport.add(pathDeleteButton, 5, 1);

        // Set margin for pathLogButton to create spacing from right border
        GridPane.setMargin(pathExportButton, new Insets(0, 0, 0, 5));

        GridPane gridPaneButton = new GridPane();
        gridPaneButton.setHgap(5);

        // Set column constraints for each column to take up 33.33% of the grid width
        ColumnConstraints col1Button = new ColumnConstraints();
        col1Button.setPercentWidth(20);

        ColumnConstraints col2Button = new ColumnConstraints();
        col2Button.setPercentWidth(20);

        ColumnConstraints col3Button = new ColumnConstraints();
        col3Button.setPercentWidth(20);

        ColumnConstraints col4Button = new ColumnConstraints();
        col4Button.setPercentWidth(20);

        gridPaneButton.getColumnConstraints().addAll(col1Button, col2Button, col3Button, col4Button);

        String css = getClass().getResource("/button.css").toExternalForm();

        saveButton = builder.buildButton("OK", ARConstants.SPACE_L, Insets.EMPTY);
        saveButton.getStyleClass().add("ok-button");

        cancelButton = builder.buildButton("Close", ARConstants.SPACE_L, Insets.EMPTY);
        cancelButton.getStyleClass().add("cancel-button");

        // Create HBox for instruction and cancel buttons
        HBox instructionButtonsRow = new HBox(10, saveButton, cancelButton);
        saveButton.setPrefWidth(buttonWidth);
        cancelButton.setPrefWidth(buttonWidth);
        instructionButtonsRow.setAlignment(Pos.BASELINE_RIGHT); // Align buttons to the right

        pathGroup = new VBox(gridPaneExport, gridPaneButton);

        // Combine all HBoxes into a VBox for vertical alignment
        VBox vbox = new VBox(20);
        vbox.getChildren()
                .addAll(
                        titleBox, // Add the title label at the top
                        pathGroup, // ComboBoxes row
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

    private void extractPathAndFileName() {
        directory = "";
        fileName = "";

        excelPath =
                splitDTO.getExportFile() != null && !splitDTO.getExportFile().isEmpty() ? splitDTO.getExportFile() : "";
        String[] fileParts = excelPath.split(":");
        delimiter = ",";

        if (fileParts.length > 2) {
            delimiter = fileParts[2];
            excelPath = excelPath.replace(":,", "").replace(":|", "");
        }

        if (!excelPath.isEmpty()) {
            try {

                Path path = Paths.get(excelPath);
                directory = path.getParent() != null ? path.getParent().toString() : ""; // Get the directory path
                fileName = path.getFileName() != null ? path.getFileName().toString() : ""; // Get the file name

                if (fileName.equalsIgnoreCase("No Excel Export File")) {
                    fileName = "";
                }

                log.info("Identified Directory: " + directory);
                log.info("Identified File Name: " + fileName);
            } catch (Exception ex) {
                log.error("Excel Path  \nError: " + ex.getMessage());
            }

        } else {
            log.info("No export file path provided.");
        }
    }

    @Override
    public void initUIBehaviour() {
        pathExportButton.setOnMouseClicked(e -> openChooserFor(pathExport, modalStage, true));
        pathDeleteButton.setOnMouseClicked(e -> {
            pathExport.setText("");
            fileExport.setText("");
            saveConfigurations();
        });

        saveButton.setOnMouseClicked(e -> saveConfigurations());
        cancelButton.setOnMouseClicked((e) -> {
            log.info("ARExcelFilePane cancelButton");
            Platform.runLater(() -> {
                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                stage.close();
            });
        });
    }

    private void saveConfigurations() {

        String exportFile = "";

        String delimiter = "|";
        FormatOption selected = comboBoxCSVColumns.getValue();
        if (selected != null) {
            delimiter = selected.getValue(); // "US" or "EU"
        }

        if (Strings.isNullOrEmpty(pathExport.getText())) {
            fileExport.setText("");
        } else {
            String filePath = fileExport.getText().trim();
            fileExport.setText(filePath);

            String fileName = fileExport.getText().trim();

            if (Strings.isNullOrEmpty(fileName)) {
                performMessage.errorMessage("File Name  Is Empty!", "Type  the File Name!", null, null, null, 0);
                return;
            }

            // Find the last dot (.) in the file name to identify an extension
            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                // Remove the existing extension
                fileName = fileName.substring(0, lastDotIndex);
            }
            // Append .xlsx extension
            fileName += fileTypeChoiceBox.getValue();
            // Set the corrected file name back to the text field
            fileExport.setText(fileName);

            exportFile = pathExport.getText() + "/" + fileExport.getText();
        }

        exportFile = exportFile.replace("\\", "/");

        String blockTable = "block";
        String updateAction = "updateInstructions";
        int whereId = splitDTO.getBotJobId();

        if ((sessionId != null && sessionId.matches(".*componentTasks.*"))) {
            blockTable = "component_block";
            whereId = splitDTO.getHomeBankingId();
            updateAction = "componentsUpdate";
        }

        exportFile = exportFile + ":" + delimiter;

        ErrorMessage errorMessage =
                performDataBase.updateBlockExportFile(blockTable, whereId, splitDTO.getBlockId(), exportFile);

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        if (errorMessage == null) {
            performLists.updateMemoryBlockExcelExport(blockTable, whereId, splitDTO.getBlockId(), exportFile);

            String jsonData = "[]";
            List<BotJobLoadDTO> listToSend =
                    blockTable.equals("block") ? performLists.getListBotJob() : performLists.getListBotJobComp();

            if (!listToSend.isEmpty()) {
                List<InstructionLoad> instructions = performLists.buildJsonViewData(listToSend);
                jsonData = gson.toJson(instructions);
            }
            webSocketSessionManager.sendMessageJson(splitDTO.getHomeBankingId(), sessionId, jsonData, updateAction);
        }

        performMessage.showCustomModalDialogDragWin11(
                "Export File: ",
                exportFile,
                errorMessage == null ? "Bot-Job Updated successfully!" : "Bot-Job NOT Update!\"",
                null,
                null,
                false,
                "OK",
                null,
                300);
    }

    private TextField createPathTextField(String text) {
        TextField textField = new TextField();
        textField.setText(text);
        AnchorPane.setTopAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(textField, ARConstants.SPACE_XL);
        AnchorPane.setLeftAnchor(textField, ARConstants.SPACE_ZERO);
        return textField;
    }

    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_EXCEL2, ARConstants.SPACE_M, new Insets(5D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private Button createDeleteButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_CROSS2, ARConstants.SPACE_M, new Insets(5D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private void openChooserFor(TextField field, Stage ownerStage, boolean isDirectory) {
        String folderBase = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        if (Strings.isNullOrEmpty(folderBase)) {
            folderBase = System.getProperty("user.dir");
        }

        File startingPoint = new File(folderBase);
        String chosenPath =
                isDirectory ? openDirectoryChooserFor(startingPoint, ownerStage) : openFileChooserFor(startingPoint);
        if (!Strings.isNullOrEmpty(chosenPath)) {
            field.setText(chosenPath);
        }
    }

    private String openDirectoryChooserFor(File startingDirectory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }

    private String openDirectoryChooserFor(File startingDirectory, Stage ownerStage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(startingDirectory);

        // Make sure the dialog is shown in front of the provided stage
        File chosenPath = chooser.showDialog(ownerStage);
        return chosenPath != null ? chosenPath.getAbsolutePath() : null;
    }

    private String openFileChooserFor(File startingDirectory) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showOpenDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }
}
