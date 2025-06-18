package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
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
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ARExcelFilePane extends ARPane {

    protected static volatile ARExcelFilePane instance;

    // Private constructor to prevent instantiation
    private ARExcelFilePane() {
        // Initialize if necessary
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

    private Stage modalStage;
    private BlockDetailsDTO blockExcelDTO;
    private String sessionId;

    public void initialize(String sessionId, BlockDetailsDTO blockExcelDTO, Stage modalStage) {
        this.sessionId = sessionId;
        this.blockExcelDTO = blockExcelDTO;
        this.modalStage = modalStage;

        if (titleLabel != null) {
            titleLabel.setText("Block name: #"
                    + this.blockExcelDTO.getBlockOrderNumber()
                    + "-"
                    + this.blockExcelDTO.getBlockName());
        }
    }

    private final Gson gson = new Gson();

    private static final ARPropertyManager arPropertyManager;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final WebSocketSessionManager webSocketSessionManager;

    // Static block to initialize
    static {
        arPropertyManager = ARPropertyManager.getInstance();
        webSocketSessionManager = WebSocketSessionManager.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    private static final ARComponentBuilder builder = new ARComponentBuilder();

    // UI Components
    Label titleLabel;
    Label blockNameLabel;
    Label pathExportLabel;
    Label fileExportLabel;
    Label fileTypeLabel;

    TextField pathExport;
    TextField fileExport;

    ObservableList<String> filetypeList =
            FXCollections.observableArrayList(ARConstants.FILE_FORMAT_EXCEL, ARConstants.FILE_FORMAT_CSV);
    ChoiceBox<String> fileTypeChoiceBox = new ChoiceBox<>();

    Button pathExportButton;
    Button pathDeleteButton;

    Button saveButton;
    Button cancelButton;

    VBox pathGroup;

    AnchorPane mainPane;

    double buttonWidth = 200;

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
        titleLabel =
                new Label("#" + this.blockExcelDTO.getBlockOrderNumber() + "-" + this.blockExcelDTO.getBlockName());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: darkgreen;"); // Green dark

        HBox titleBox = new HBox(10, blockNameLabel, titleLabel); // Put labels in an HBox
        titleBox.setAlignment(Pos.CENTER); // Align the labels in the center of the HBox

        pathExportLabel = new Label("Export Path:");

        String excelPath = blockExcelDTO.getExportFile() != null
                        && !blockExcelDTO.getExportFile().isEmpty()
                ? blockExcelDTO.getExportFile()
                : "";

        String directory = "";
        String fileName = "";
        if (!excelPath.isEmpty()) {
            try {

                Path path = Paths.get(excelPath);
                directory = path.getParent() != null ? path.getParent().toString() : ""; // Get the directory path
                fileName = path.getFileName() != null ? path.getFileName().toString() : ""; // Get the file name

                if (fileName.equalsIgnoreCase("No Excel Export File")) {
                    fileName = "";
                }

                ARLogger.getInstance(ARExcelFilePane.class).info("Identified Directory: " + directory);
                ARLogger.getInstance(ARExcelFilePane.class).info("Identified File Name: " + fileName);
            } catch (Exception ex) {
                ARLogger.getInstance(ARExcelFilePane.class).severe("Excel Path  \nError: " + ex.getMessage());
            }

        } else {
            ARLogger.getInstance(ARExcelFilePane.class).info("No export file path provided.");
        }

        pathExport = createPathTextField(directory);
        pathExportButton = createPathButton();
        pathDeleteButton = createDeleteButton();
        fileExportLabel = new Label("File Name");
        fileExport = createPathTextField(fileName);
        //        AnchorPane exportGroup = new AnchorPane(pathExport, pathExportButton);
        fileTypeLabel = new Label("File Type");

        fileTypeChoiceBox.setItems(filetypeList);

        if (!fileName.toLowerCase().endsWith(".csv")) {
            fileTypeChoiceBox.getSelectionModel().selectFirst();
        } else {
            fileTypeChoiceBox.getSelectionModel().selectLast();
        }

        GridPane gridPaneExport = new GridPane();
        //        gridPaneLog.setVgap(10);
        gridPaneExport.setHgap(5);
        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        ColumnConstraints colExp1 = new ColumnConstraints();
        colExp1.setPercentWidth(50);

        ColumnConstraints colExp2 = new ColumnConstraints();
        colExp2.setPercentWidth(30);

        ColumnConstraints colExp3 = new ColumnConstraints();
        colExp3.setPercentWidth(10);

        ColumnConstraints colExp4 = new ColumnConstraints();
        colExp4.setPercentWidth(5);

        ColumnConstraints colExp5 = new ColumnConstraints();
        colExp5.setPercentWidth(5);

        gridPaneExport.getColumnConstraints().addAll(colExp1, colExp2, colExp3, colExp4, colExp5);

        // Add LABELS in the first row
        gridPaneExport.add(pathExportLabel, 0, 0);
        gridPaneExport.add(fileExportLabel, 1, 0);
        gridPaneExport.add(fileTypeLabel, 2, 0);

        // Add text FIELDS in the second row
        gridPaneExport.add(pathExport, 0, 1);
        gridPaneExport.add(fileExport, 1, 1);
        gridPaneExport.add(fileTypeChoiceBox, 2, 1);

        // Add button in the second row, third column
        gridPaneExport.add(pathExportButton, 3, 1);

        // Add button in the second row, fourth column
        gridPaneExport.add(pathDeleteButton, 4, 1);

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
            ARLogger.getInstance(ARExcelFilePane.class).finer("ARExcelFilePane cancelButton");
            Platform.runLater(() -> {
                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                stage.close();
            });
        });
    }

    private void saveConfigurations() {

        String exportFile = "";

        if (Strings.isNullOrEmpty(pathExport.getText())) {
            fileExport.setText("");
        } else {
            String filePath = fileExport.getText().trim();
            fileExport.setText(filePath);

            String fileName = fileExport.getText().trim(); // Get the trimmed input

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

        String tableTarget = "block";
        if ((sessionId != null && sessionId.matches(".*componentTasks.*"))) {
            tableTarget = "component_block";
        }

        boolean updateBlock = performDataBase.updateBlockExportFile(
                tableTarget, blockExcelDTO.getBotJobId(), blockExcelDTO.getBlockId(), exportFile);

        List<BotJobLoadDTO> botJobLoadList;
        if ((sessionId != null && sessionId.matches(".*botJobTasks.*"))) {
            botJobLoadList = performDataBase.loadCompleteJobs(blockExcelDTO.getBotJobId());
            String jsonData = "[]";
            if (!botJobLoadList.isEmpty()) {
                List<InstructionLoadDTO> blockLoopInstructions =
                        performDataBase.buildJsonViewData(botJobLoadList, "instruction");
                jsonData = gson.toJson(blockLoopInstructions);
            }
            webSocketSessionManager.sendMessageJson(
                    blockExcelDTO.getHomeBankingId(), sessionId, jsonData, "updateInstructions");

        } else if ((sessionId != null && sessionId.matches(".*componentTasks.*"))) {
            botJobLoadList = performDataBase.loadComponentsComplete(
                    blockExcelDTO.getHomeBankingId(), blockExcelDTO.getBotJobId(), blockExcelDTO.getBotJobName());
            String jsonData = "[]";
            if (!botJobLoadList.isEmpty()) {
                List<InstructionLoadDTO> instructions =
                        performDataBase.buildJsonViewData(botJobLoadList, "component_instruction");
                jsonData = gson.toJson(instructions);
            }
            //            webSocketSessionManager.sendMessageJson(sessionId, jsonData,
            // "componentsUpdate");

            webSocketSessionManager.sendMessageJson(
                    blockExcelDTO.getHomeBankingId(), "componentTasks", jsonData, "componentsUpdate");
            //            webSocketSessionManager.broadcastMessageToAll(
            //                    blockExcelDTO.getHomeBankingId(), "componentTasks", jsonData,
            // "componentsUpdate");
        }

        performMessage.showCustomModalDialogDragWin11(
                "Export File: ",
                exportFile,
                updateBlock ? "Bot-Job Updated successfully!" : "Bot-Job NOT Update!\"",
                null,
                null,
                false,
                "OK",
                null,
                300);
    }

    private TextField createPathTextField(ARPropertyEnum property) {
        TextField textField = new TextField();
        textField.setText(arPropertyManager.getProperty(property));
        AnchorPane.setTopAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(textField, ARConstants.SPACE_XL);
        AnchorPane.setLeftAnchor(textField, ARConstants.SPACE_ZERO);
        return textField;
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
