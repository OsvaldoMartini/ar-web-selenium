package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.HomeBankingListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRNewHomeBankingScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ABRConfigurationPane extends ABRPane {

    private static final ABRComponentBuilder builder = new ABRComponentBuilder();

    // UI Components
    Label title;
    Label pathExcelLabel;
    Label pathExportLabel;
    Label pathLogLabel;
    Label sizeLogLabel;
    Label reduceSearchLabel;
    Label pathJavaLabel;
    Label pathDBLabel;
    Label databaseLabel;
    Label socketPortLabel;
    Label blockLimitLabel;
    Label pathReportLabel;
    Label pathPriorityLabel;
    Label pathJavaFXLabel;
    Label pathEngineLabel;
    Label browserLabel;
    Label reloadDBLabel;
    Label deleteAllDBLabel;
    Label insertSitesLabel;
    Label pathWebDriverLabel;

    TextField pathExcel;
    TextField pathExport;
    TextField pathLog;
    TextField sizeLog;
    TextField reduceSearch;
    TextField pathJava;
    TextField pathDB;
    TextField socketPort;
    TextField blockLimit;
    TextField pathReport;
    TextField pathPriority;
    TextField pathJavaFX;
    TextField pathEngine;
    TextField pathWebDriver;

    ChoiceBox<String> browserChoiceBox = new ChoiceBox<>();
    ChoiceBox<String> databaseChoiceBox = new ChoiceBox<>();
    ObservableList<String> browserList =
            FXCollections.observableArrayList(ABRConstants.CHROME, ABRConstants.EDGE, ABRConstants.FIREFOX);
    ObservableList<String> databaseList =
            FXCollections.observableArrayList(ABRConstants.ACCESS, ABRConstants.POSTGRES, ABRConstants.SQLSERVER);

    Button pathExcelButton;
    Button pathExportButton;
    Button pathLogButton;
    Button pathJavaButton;
    Button pathDBButton;
    Button pathReportButton;
    Button pathPriorityButton;
    Button pathJavaFXButton;
    Button pathEngineButton;
    Button pathWebDriverButton;

    Button saveButton;
    Button deleteAllDBButton;
    Button addHomeBankingButton;

    ListView<HomeBankingDTO> homeBankingListView;

    VBox pathGroup;

    AnchorPane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        title = new Label("Configuration");
        title.setMaxHeight(ABRConstants.SPACE_L);
        title.setBackground(new Background(
                new BackgroundFill(Color.ROYALBLUE, new CornerRadii(ABRConstants.SPACE_XS), Insets.EMPTY)));
        title.setTextFill(Color.WHITE);
        AnchorPane.setTopAnchor(title, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(title, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(title, ABRConstants.SPACE_M);

        //        ButtonBar homeBankingActionGroup = new ButtonBar();
        addHomeBankingButton = builder.buildButton("Insert / Update / Config Scan");
        //        homeBankingActionGroup.getButtons().addAll(addHomeBankingButton);

        ObservableList<HomeBankingDTO> homeBankingList =
                ABRSharedResources.getInstance().getEntityList(HomeBankingDTO.class);
        homeBankingListView = new ListView<>(homeBankingList);
        homeBankingListView.setCellFactory(new ABRCellFactory<>(HomeBankingListCell.class)::call);
        // Setting the preferred height for homeBankingListView
        homeBankingListView.setPrefHeight(100); // Set the height to 50px

        // Add homeBankingListView to a VBox if needed (optional, not mandatory for height adjustment)
        VBox homeBankingContainer = new VBox(homeBankingListView);
        //        homeBankingContainer.setSpacing(2); // O

        pathExcelLabel = new Label("Excel Path:");
        pathExcel = createPathTextField(ABRPropertyEnum.FOLDER_PATH_EXCEL);
        pathExcelButton = createPathButton();
        AnchorPane excelGroup = new AnchorPane(pathExcel, pathExcelButton);

        pathExportLabel = new Label("Export Path:");
        pathExport = createPathTextField(ABRPropertyEnum.FOLDER_PATH_EXPORT);
        pathExportButton = createPathButton();
        AnchorPane exportGroup = new AnchorPane(pathExport, pathExportButton);

        // LOGs
        pathLogLabel = new Label("Log Path:");
        pathLog = createPathTextField(ABRPropertyEnum.FOLDER_PATH_LOG);
        pathLogButton = createPathButton();
        sizeLogLabel = new Label("Max Size Log");
        sizeLog = createPathTextField(ABRPropertyEnum.MAX_LOG_SIZE);
        reduceSearchLabel = new Label("Limit Max Search");
        reduceSearch = createPathTextField(ABRPropertyEnum.REDUCE_SEARCH_CRITERIA);

        GridPane gridPaneLog = new GridPane();
        //        gridPaneLog.setVgap(10);
        gridPaneLog.setHgap(10);
        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(65);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(15);

        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(15);

        ColumnConstraints col4 = new ColumnConstraints();
        col4.setPercentWidth(5);

        gridPaneLog.getColumnConstraints().addAll(col1, col2, col3, col4);

        // Add labels in the first row
        gridPaneLog.add(pathLogLabel, 0, 0);
        gridPaneLog.add(sizeLogLabel, 1, 0);
        gridPaneLog.add(reduceSearchLabel, 2, 0);

        // Add text fields in the second row
        gridPaneLog.add(pathLog, 0, 1);
        gridPaneLog.add(sizeLog, 1, 1);
        gridPaneLog.add(reduceSearch, 2, 1);

        // Add button in the second row, third column
        gridPaneLog.add(pathLogButton, 3, 1);

        // Set margin for pathLogButton to create spacing from right border
        GridPane.setMargin(pathLogButton, new Insets(0, 0, 0, 5));

        // DB Type
        pathDBLabel = new Label("Database Path:");
        pathDB = createPathTextField(ABRPropertyEnum.FOLDER_PATH_DB);
        pathDBButton = createPathButton();

        socketPortLabel = new Label("Socket Port");
        socketPort = createPathTextField(ABRPropertyEnum.PORT_SOCKET);
        String portSocket = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.PORT_SOCKET);
        if (Strings.isNullOrEmpty(portSocket)) {
            socketPort.setText("8080");
        }

        blockLimitLabel = new Label("Block Exec. Limit");
        blockLimit = createPathTextField(ABRPropertyEnum.BLOCK_EXEC_LIMIT);
        String processReach = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BLOCK_EXEC_LIMIT);
        if (Strings.isNullOrEmpty(processReach)) {
            blockLimit.setText("50");
        }

        GridPane gridPaneDB = new GridPane();
        gridPaneDB.setHgap(10);

        // Set column constraints for pathDB (80%), dbType (15%), socketPort (15%) and pathDBButton (5%)
        ColumnConstraints col1DB = new ColumnConstraints();
        col1DB.setPercentWidth(65);

        ColumnConstraints col2DB = new ColumnConstraints();
        col2DB.setPercentWidth(15);

        ColumnConstraints col3DB = new ColumnConstraints();
        col3DB.setPercentWidth(15);

        ColumnConstraints col4DB = new ColumnConstraints();
        col4DB.setPercentWidth(5);

        gridPaneDB.getColumnConstraints().addAll(col1DB, col2DB, col3DB, col4DB);

        // Add labels in the first row
        gridPaneDB.add(pathDBLabel, 0, 0);
        gridPaneDB.add(socketPortLabel, 1, 0);
        gridPaneDB.add(blockLimitLabel, 2, 0);

        // Add text fields in the second row
        gridPaneDB.add(pathDB, 0, 1);
        //        gridPaneDB.add(databaseChoiceBox, 1, 1);
        gridPaneDB.add(socketPort, 1, 1);
        gridPaneDB.add(blockLimit, 2, 1);

        // Add button in the second row, third column
        gridPaneDB.add(pathDBButton, 3, 1);

        // Set margin for pathDBButton to create spacing from right border
        GridPane.setMargin(pathDBButton, new Insets(0, 0, 0, 5));

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

        ColumnConstraints col5Button = new ColumnConstraints();
        col5Button.setPercentWidth(20);

        gridPaneButton.getColumnConstraints().addAll(col1Button, col2Button, col3Button, col4Button, col5Button);

        browserLabel = new Label("Browser");
        databaseLabel = new Label("DB Type");

        reloadDBLabel = new Label("Reload DB");
        deleteAllDBLabel = new Label("Delete ALL DB");
        insertSitesLabel = new Label("Insert Sites");

        saveButton = builder.buildButton("Reload Configs");
        saveButton.setMaxHeight(ABRConstants.SPACE_L);

        deleteAllDBButton = builder.buildButton("Delete DB");
        deleteAllDBButton.setMaxHeight(ABRConstants.SPACE_L);
        deleteAllDBButton.setStyle("-fx-background-color: lightcoral; -fx-text-fill: blue;");

        browserChoiceBox.setItems(browserList);
        databaseChoiceBox.setItems(databaseList);

        // Add labels in the first row
        gridPaneButton.add(browserLabel, 0, 0);
        gridPaneButton.add(databaseLabel, 1, 0);
        gridPaneButton.add(reloadDBLabel, 2, 0);
        gridPaneButton.add(deleteAllDBLabel, 3, 0);
        gridPaneButton.add(insertSitesLabel, 4, 0);

        // Add components in the second row, each occupying 25% of the width
        gridPaneButton.add(browserChoiceBox, 0, 1);
        gridPaneButton.add(databaseChoiceBox, 1, 1);
        gridPaneButton.add(saveButton, 2, 1);
        gridPaneButton.add(deleteAllDBButton, 3, 1);
        gridPaneButton.add(addHomeBankingButton, 4, 1);

        //        AnchorPane logGroup = new AnchorPane(pathLog, sizeLog, pathLogButton);
        pathJavaLabel = new Label("Java Path:");
        pathJava = createPathTextField(ABRPropertyEnum.FOLDER_PATH_JAVA);
        pathJavaButton = createPathButton();
        AnchorPane javaGroup = new AnchorPane(pathJava, pathJavaButton);

        pathReportLabel = new Label("Report Path:");
        pathReport = createPathTextField(ABRPropertyEnum.FOLDER_PATH_REPORT);
        pathReportButton = createPathButton();
        AnchorPane reportGroup = new AnchorPane(pathReport, pathReportButton);

        pathPriorityLabel = new Label("Priority Path:");
        pathPriority = createPathTextField(ABRPropertyEnum.FOLDER_PATH_PRIORITY);
        pathPriorityButton = createPathButton();
        AnchorPane priorityGroup = new AnchorPane(pathPriority, pathPriorityButton);

        pathJavaFXLabel = new Label("JavaFX Path:");
        pathJavaFX = createPathTextField(ABRPropertyEnum.FOLDER_PATH_JAVA_FX);
        pathJavaFXButton = createPathButton();
        AnchorPane javaFXGroup = new AnchorPane(pathJavaFX, pathJavaFXButton);

        pathEngineLabel = new Label("Engine Path:");
        pathEngine = createPathTextField(ABRPropertyEnum.PATH_ENGINE);
        pathEngineButton = createPathButton();
        AnchorPane engineGroup = new AnchorPane(pathEngine, pathEngineButton);

        pathWebDriverLabel = new Label("Web Driver Path:");
        pathWebDriver = createPathTextField(ABRPropertyEnum.PATH_WEBDRIVER);
        pathWebDriverButton = createPathButton();
        AnchorPane driverGroup = new AnchorPane(pathWebDriver, pathWebDriverButton);

        pathGroup = new VBox(
                pathExcelLabel,
                excelGroup,
                pathExportLabel,
                exportGroup,
                gridPaneLog,
                gridPaneDB,
                pathReportLabel,
                reportGroup,
                pathPriorityLabel,
                priorityGroup,
                pathJavaLabel,
                javaGroup,
                pathJavaFXLabel,
                javaFXGroup,
                pathEngineLabel,
                engineGroup,
                pathWebDriverLabel,
                driverGroup,
                gridPaneButton,
                homeBankingContainer);

        AnchorPane.setTopAnchor(pathGroup, ABRConstants.SPACE_L + ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(pathGroup, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(pathGroup, ABRConstants.SPACE_M);

        mainPane = new AnchorPane(title, pathGroup);
    }

    @Override
    public void initUIBehaviour() {
        //        homeBankingGroup
        //                .maxHeightProperty()
        //                .bind(mainPane.heightProperty()
        //                        .subtract(title.heightProperty())
        //                        .subtract(pathGroup.heightProperty())
        //                        .subtract(saveButton.heightProperty())
        //                        .subtract(ABRConstants.SPACE_M * 2)
        //                        .subtract(ABRConstants.SPACE_L * 2));
        addHomeBankingButton.setOnMouseClicked(e -> new ABRNewHomeBankingScene().show());
        pathExcelButton.setOnMouseClicked(e -> openChooserFor(pathExcel, true));
        pathExportButton.setOnMouseClicked(e -> openChooserFor(pathExport, true));
        pathLogButton.setOnMouseClicked(e -> openChooserFor(pathLog, true));
        // pathExtRefButton.setOnMouseClicked(e -> openChooserFor(pathExtRef, true));
        pathJavaButton.setOnMouseClicked(e -> openChooserFor(pathJava, true));
        pathDBButton.setOnMouseClicked(e -> openChooserFor(pathDB, true));
        pathReportButton.setOnMouseClicked(e -> openChooserFor(pathReport, true));
        pathPriorityButton.setOnMouseClicked(e -> openChooserFor(pathPriority, true));
        pathJavaFXButton.setOnMouseClicked(e -> openChooserFor(pathJavaFX, true));
        pathEngineButton.setOnMouseClicked(e -> openChooserFor(pathEngine, true));
        pathWebDriverButton.setOnMouseClicked(e -> openChooserFor(pathWebDriver, false));
        browserChoiceBox.setValue(ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BROWSER));
        databaseChoiceBox.setValue(ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.DATABASE_TYPE));

        saveButton.setOnMouseClicked(e -> saveConfigurations());
        deleteAllDBButton.setOnMouseClicked(e -> deleteAllDB());
    }

    private void saveConfigurations() {
        boolean validfields = true;
        if (Strings.isNullOrEmpty(pathExcel.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Excel Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathExport.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Export Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathLog.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Log Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(sizeLog.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Max Size Log must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathJava.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Java Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(socketPort.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Socket Port must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(blockLimit.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Process Limit must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathDB.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Database Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathReport.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Reports Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathPriority.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Priority Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathJavaFX.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "JavaFX Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathEngine.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "ABR Engine Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathWebDriver.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Web Driver Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (validfields) {

            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.BROWSER.getValue(), browserChoiceBox.getValue());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.DATABASE_TYPE.getValue(), databaseChoiceBox.getValue());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.FOLDER_PATH_DB.getValue(), pathDB.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL.getValue(), pathExcel.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_EXPORT.getValue(), pathExport.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_JAVA.getValue(), pathJava.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_JAVA_FX.getValue(), pathJavaFX.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.FOLDER_PATH_LOG.getValue(), pathLog.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.FOLDER_PATH_LOG.getValue(), pathLog.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_PRIORITY.getValue(), pathPriority.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_REPORT.getValue(), pathReport.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.MAX_LOG_SIZE.getValue(), sizeLog.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.PATH_ENGINE.getValue(), pathEngine.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.PATH_WEBDRIVER.getValue(), pathWebDriver.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.PORT_SOCKET.getValue(), socketPort.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.BLOCK_EXEC_LIMIT.getValue(), blockLimit.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.REDUCE_SEARCH_CRITERIA.getValue(), reduceSearch.getText());

            /*ABRPropertyManager.getInstance().setProperty(
            ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE.getValue(), pathExtRef.getText()); */
            ABRSharedResources.getInstance().changeDbConnection();
            new ABRAlertScene(
                    Alert.AlertType.INFORMATION,
                    "Configuration saved",
                    "The configuration has been saved and the data has been reloaded",
                    ButtonType.OK);
        }
    }

    private void deleteAllDB() {
        String dataBaseType = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.DATABASE_TYPE);

        Label newInstruction = new Label("DELETE ALL JOB DETAILS\nDatabase Selected: \"" + dataBaseType + "\"");
        newInstruction.setStyle("-fx-font-size: 18px;");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Are you sure you want to DELETE ALL JOB TABLES ROWS (\"" + dataBaseType + "\")?");
        alert.getDialogPane().setContent(newInstruction);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            if (deleteAllJobDetails(dataBaseType)) {
                ABRSharedResources.getInstance().changeDbConnection();
                new ABRAlertScene(
                        Alert.AlertType.INFORMATION,
                        "All Job Details has been deleted!",
                        "The All Instructions and Job Details has been deleted and the data has been reloaded",
                        ButtonType.OK);

            } else {
                ABRSharedResources.getInstance().changeDbConnection();
                new ABRAlertScene(
                        Alert.AlertType.ERROR,
                        "\"" + dataBaseType + "\" Problems\nNot possible  to delete All Job Details!",
                        "\"" + dataBaseType + "\" Problems!\n"
                                                        + "The Instructions and Job Details cannot be deleted and the data has been reloaded\n"
                                                        + dataBaseType
                                                != null
                                        && dataBaseType.equalsIgnoreCase("ACCESS")
                                ? dataBaseType + " database Recommendation:\nDelete interelly \"database.mdb\" file!"
                                : "",
                        ButtonType.OK);
            }
        }
    }

    private boolean deleteAllJobDetails(String dataBaseType) {
        // Build the SQL delete statement
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Execute each statement individually
            stmt.executeUpdate("DELETE FROM job_run_report;");
            stmt.executeUpdate("DELETE FROM variable;");
            stmt.executeUpdate("DELETE FROM instruction_reference;");
            stmt.executeUpdate("DELETE FROM block_loop_instruction;");
            stmt.executeUpdate("DELETE FROM block;");
            stmt.executeUpdate("DELETE FROM bot_job;");

            stmt.executeUpdate("DELETE FROM saved_instruction_reference;");
            stmt.executeUpdate("DELETE FROM saved_block_loop_instruction;");
            stmt.executeUpdate("DELETE FROM saved_blocks;");

            // Drop sequences if they exist
            if (!dataBaseType.equalsIgnoreCase("ACCESS")) {
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockLoopInstructionSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"botJobSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"variableSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"instructionReferenceSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"excelReportSeq\";");
            }
            ABRLogger.getInstance(ABRWebDriver.class)
                    .info("All Rows DELETED for:\n"
                            + "ExcelReportDTO;\n"
                            + "Variables;\n"
                            + "Instructions References;\n"
                            + "Instructions;\n"
                            + "Blocks;\n"
                            + "Bot Jobs;\n"
                            + "Saved Components;\n"
                            + "Sequences dropped.");

            return true;

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(dataBaseType + " Problems:\n"
                            + "Not Possible delete the  Rows was for these tables:\n"
                            + "ExcelReportDTO;\n"
                            + "Variables;\n"
                            + "Instructions References;\n"
                            + "Instructions;\n"
                            + "Blocks;\n"
                            + "Bot Jobs;\n"
                            + "Saved Components;\n"
                            + "Sequences Not dropped\n"
                            + e.getMessage());
        }
        return false;
    }

    private TextField createPathTextField(ABRPropertyEnum property) {
        TextField textField = new TextField();
        textField.setText(ABRPropertyManager.getInstance().getProperty(property));
        AnchorPane.setTopAnchor(textField, ABRConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(textField, ABRConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(textField, ABRConstants.SPACE_XL);
        AnchorPane.setLeftAnchor(textField, ABRConstants.SPACE_ZERO);
        return textField;
    }

    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_DIRECTORY, ABRConstants.SPACE_M, new Insets(5D));
        button.setMaxWidth(ABRConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private void openChooserFor(TextField field, boolean isDirectory) {
        File startingPoint = new File(System.getProperty("user.dir"));
        String chosenPath = isDirectory ? openDirectoryChooserFor(startingPoint) : openFileChooserFor(startingPoint);
        field.setText(chosenPath);
    }

    private String openDirectoryChooserFor(File startingDirectory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }

    private String openFileChooserFor(File startingDirectory) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showOpenDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }
}
