package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.HomeBankingListCell;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARAlertScene;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARConfigurationPane extends ARPane {

    private static final ARComponentBuilder builder = new ARComponentBuilder();

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;
    private String previousDB;

    public ARConfigurationPane(String previousDB) {
        this.previousDB = previousDB;
    }

    private static final ARNewHomeBankingScene arNewHomeBankingScene;
    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
        arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    }

    private ObservableList<HomeBankingLoadDTO> homeBankingList = FXCollections.observableArrayList();
    private ListView<HomeBankingLoadDTO> homeBankingListView;

    // UI Components
    Label title;
    Label pathExcelLabel;
    //    Label pathExportLabel;
    //    Label fileExportLabel;
    Label pathLogLabel;
    Label sizeLogLabel;
    Label reduceSearchLabel;
    Label pathJavaLabel;
    Label pathDBLabel;
    Label databaseLabel;
    Label socketPortLabel;
    //    Label blockLimitLabel;
    Label pathReportLabel;
    Label pathPriorityLabel;
    Label pathJavaFXLabel;
    Label pathEngineLabel;
    Label browserLabel;
    Label reloadDBLabel;
    Label migrationDBLabel;
    Label deleteAllDBLabel;
    Label insertSitesLabel;
    Label pathWebDriverLabel;

    TextField pathExcel;
    //    TextField pathExport;
    //    TextField fileExport;
    TextField pathLog;
    TextField sizeLog;
    TextField reduceSearch;
    TextField pathJava;
    TextField pathDB;
    TextField socketPort;
    //    TextField blockLimit;
    TextField pathReport;
    TextField pathPriority;
    TextField pathJavaFX;
    TextField pathEngine;
    TextField pathWebDriver;

    ChoiceBox<String> browserChoiceBox = new ChoiceBox<>();
    ChoiceBox<String> databaseChoiceBox = new ChoiceBox<>();
    ObservableList<String> browserList =
            FXCollections.observableArrayList(ARConstants.CHROME, ARConstants.EDGE, ARConstants.FIREFOX);
    ObservableList<String> databaseList =
            FXCollections.observableArrayList(ARConstants.ACCESS, ARConstants.POSTGRES, ARConstants.SQLSERVER);

    Button pathExcelButton;
    //    Button pathExportButton;
    Button pathLogButton;
    Button pathJavaButton;
    Button pathDBButton;
    Button pathReportButton;
    Button pathPriorityButton;
    Button pathJavaFXButton;
    Button pathEngineButton;
    Button pathWebDriverButton;

    Button reloadDBButton;
    Button migrationDBButton;
    Button deleteAllDBButton;
    Button addHomeBankingButton;

    VBox pathGroup;

    AnchorPane mainPane;

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

        title = new Label("Configuration");
        title.setMaxHeight(ARConstants.SPACE_L);
        title.setBackground(new Background(
                new BackgroundFill(Color.ROYALBLUE, new CornerRadii(ARConstants.SPACE_XS), Insets.EMPTY)));
        title.setTextFill(Color.WHITE);
        AnchorPane.setTopAnchor(title, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(title, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(title, ARConstants.SPACE_M);

        //        ButtonBar homeBankingActionGroup = new ButtonBar();
        addHomeBankingButton = builder.buildButton("Insert / Config Scan");
        //        homeBankingActionGroup.getButtons().addAll(addHomeBankingButton);

        //        ObservableList<HomeBankingDTO> homeBankingList =
        //                ARSharedResources.getInstance().getEntityList(HomeBankingDTO.class);

        homeBankingList.addAll(PerformDataBase.loadAllHomeBanking());
        homeBankingListView = new ListView<>(homeBankingList);
        homeBankingListView.setCellFactory(new ARCellFactory<>(HomeBankingListCell.class)::call);

        // Setting the preferred height for homeBankingListView
        homeBankingListView.setPrefHeight(100); // Set the height to 50px

        // Add homeBankingListView to a VBox if needed (optional, not mandatory for height adjustment)
        VBox homeBankingContainer = new VBox(homeBankingListView);
        //        homeBankingContainer.setSpacing(2); // O

        pathExcelLabel = new Label("Excel Path:");
        pathExcel = createPathTextField(ARPropertyEnum.FOLDER_PATH_EXCEL);
        pathExcelButton = createPathButton();
        AnchorPane excelGroup = new AnchorPane(pathExcel, pathExcelButton);

        //        pathExportLabel = new Label("Export Path:");
        //        pathExport = createPathTextField(ARPropertyEnum.FOLDER_PATH_EXPORT);
        //        pathExportButton = createPathButton();
        //        fileExportLabel = new Label("File Name");
        //        fileExport = createPathTextField(ARPropertyEnum.FILE_NAME_EXPORT);
        //        AnchorPane exportGroup = new AnchorPane(pathExport, pathExportButton);

        //        GridPane gridPaneExport = new GridPane();
        //        //        gridPaneLog.setVgap(10);
        //        gridPaneExport.setHgap(10);
        //        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        //        ColumnConstraints colExp1 = new ColumnConstraints();
        //        colExp1.setPercentWidth(65);
        //
        //        ColumnConstraints colExp2 = new ColumnConstraints();
        //        colExp2.setPercentWidth(30);
        //
        //        ColumnConstraints colExp3 = new ColumnConstraints();
        //        colExp3.setPercentWidth(5);
        //
        //        gridPaneExport.getColumnConstraints().addAll(colExp1, colExp2, colExp3);

        //        // Add labels in the first row
        //        gridPaneExport.add(pathExportLabel, 0, 0);
        //        gridPaneExport.add(fileExportLabel, 1, 0);
        //
        //        // Add text fields in the second row
        //        gridPaneExport.add(pathExport, 0, 1);
        //        gridPaneExport.add(fileExport, 1, 1);
        //
        //        // Add button in the second row, third column
        //        gridPaneExport.add(pathExportButton, 2, 1);
        //
        //        // Set margin for pathLogButton to create spacing from right border
        //        GridPane.setMargin(pathExportButton, new Insets(0, 0, 0, 5));

        // LOGs
        pathLogLabel = new Label("Log Path:");
        pathLog = createPathTextField(ARPropertyEnum.FOLDER_PATH_LOG);
        pathLogButton = createPathButton();
        sizeLogLabel = new Label("Max Size Log");
        sizeLog = createPathTextField(ARPropertyEnum.MAX_LOG_SIZE);
        reduceSearchLabel = new Label("Limit Max Search");
        reduceSearch = createPathTextField(ARPropertyEnum.REDUCE_SEARCH_CRITERIA);

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
        pathDB = createPathTextField(ARPropertyEnum.FOLDER_PATH_DB);
        pathDBButton = createPathButton();

        socketPortLabel = new Label("Socket Port");
        socketPort = createPathTextField(ARPropertyEnum.PORT_SOCKET);
        String portSocket = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PORT_SOCKET);
        if (Strings.isNullOrEmpty(portSocket)) {
            socketPort.setText("8080");
        }

        //        blockLimitLabel = new Label("Block Exec. Limit");
        //        blockLimit = createPathTextField(ARPropertyEnum.BLOCK_EXEC_LIMIT);
        //        String processReach = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.BLOCK_EXEC_LIMIT);
        //        if (Strings.isNullOrEmpty(processReach)) {
        //            blockLimit.setText("50");
        //        }

        GridPane gridPaneDB = new GridPane();
        gridPaneDB.setHgap(10);

        // Set column constraints for pathDB (80%), dbType (15%), socketPort (15%) and pathDBButton (5%)
        ColumnConstraints col1DB = new ColumnConstraints();
        col1DB.setPercentWidth(65);

        ColumnConstraints col2DB = new ColumnConstraints();
        col2DB.setPercentWidth(30);

        ColumnConstraints col3DB = new ColumnConstraints();
        col3DB.setPercentWidth(5);

        gridPaneDB.getColumnConstraints().addAll(col1DB, col2DB, col3DB);

        // Add labels in the first row
        gridPaneDB.add(pathDBLabel, 0, 0);
        gridPaneDB.add(socketPortLabel, 1, 0);
        //        gridPaneDB.add(blockLimitLabel, 2, 0);

        // Add text fields in the second row
        gridPaneDB.add(pathDB, 0, 1);
        //        gridPaneDB.add(databaseChoiceBox, 1, 1);
        gridPaneDB.add(socketPort, 1, 1);
        //        gridPaneDB.add(blockLimit, 2, 1);

        // Add button in the second row, third column
        gridPaneDB.add(pathDBButton, 3, 1);

        // Set margin for pathDBButton to create spacing from right border
        GridPane.setMargin(pathDBButton, new Insets(0, 0, 0, 5));

        GridPane gridPaneButton = new GridPane();
        gridPaneButton.setHgap(2);

        // Set column constraints for each column to take up 33.33% of the grid width
        ColumnConstraints col1Button = new ColumnConstraints();
        col1Button.setPercentWidth(16);

        ColumnConstraints col2Button = new ColumnConstraints();
        col2Button.setPercentWidth(16);

        ColumnConstraints col3Button = new ColumnConstraints();
        col3Button.setPercentWidth(16);

        ColumnConstraints col4Button = new ColumnConstraints();
        col4Button.setPercentWidth(16);

        ColumnConstraints col5Button = new ColumnConstraints();
        col5Button.setPercentWidth(16);

        ColumnConstraints col6Button = new ColumnConstraints();
        col6Button.setPercentWidth(16);

        gridPaneButton
                .getColumnConstraints()
                .addAll(col1Button, col2Button, col3Button, col4Button, col5Button, col6Button);

        browserLabel = new Label("Browser");
        databaseLabel = new Label("DB Type");

        migrationDBLabel = new Label("Migrate DB");

        reloadDBLabel = new Label("Reload DB");
        deleteAllDBLabel = new Label("Delete ALL DB");
        insertSitesLabel = new Label("Insert Sites");

        migrationDBButton = builder.buildButton("Migrate");
        migrationDBButton.setMaxHeight(ARConstants.SPACE_XXS);

        migrationDBLabel.setVisible(true);
        migrationDBButton.setVisible(true);

        reloadDBButton = builder.buildButton("Reload Configs");
        reloadDBButton.setMaxHeight(ARConstants.SPACE_L);

        deleteAllDBButton = builder.buildButton("Delete DB");
        deleteAllDBButton.setMaxHeight(ARConstants.SPACE_L);
        deleteAllDBButton.setStyle("-fx-background-color: lightcoral; -fx-text-fill: blue;");

        browserChoiceBox.setItems(browserList);
        databaseChoiceBox.setItems(databaseList);

        // Add labels in the first row
        gridPaneButton.add(browserLabel, 0, 0);
        gridPaneButton.add(databaseLabel, 1, 0);
        gridPaneButton.add(reloadDBLabel, 2, 0);
        gridPaneButton.add(migrationDBLabel, 3, 0);
        gridPaneButton.add(deleteAllDBLabel, 4, 0);
        gridPaneButton.add(insertSitesLabel, 5, 0);

        // Add components in the second row, each occupying 25% of the width
        gridPaneButton.add(browserChoiceBox, 0, 1);
        gridPaneButton.add(databaseChoiceBox, 1, 1);
        gridPaneButton.add(reloadDBButton, 2, 1);
        gridPaneButton.add(migrationDBButton, 3, 1);
        gridPaneButton.add(deleteAllDBButton, 4, 1);
        gridPaneButton.add(addHomeBankingButton, 5, 1);

        //        AnchorPane logGroup = new AnchorPane(pathLog, sizeLog, pathLogButton);
        pathJavaLabel = new Label("Java Path:");
        pathJava = createPathTextField(ARPropertyEnum.FOLDER_PATH_JAVA);
        pathJavaButton = createPathButton();
        AnchorPane javaGroup = new AnchorPane(pathJava, pathJavaButton);

        pathReportLabel = new Label("Report Path:");
        pathReport = createPathTextField(ARPropertyEnum.FOLDER_PATH_REPORT);
        pathReportButton = createPathButton();
        AnchorPane reportGroup = new AnchorPane(pathReport, pathReportButton);

        pathPriorityLabel = new Label("Priority Path:");
        pathPriority = createPathTextField(ARPropertyEnum.FOLDER_PATH_PRIORITY);
        pathPriorityButton = createPathButton();
        AnchorPane priorityGroup = new AnchorPane(pathPriority, pathPriorityButton);

        pathJavaFXLabel = new Label("JavaFX Path:");
        pathJavaFX = createPathTextField(ARPropertyEnum.FOLDER_PATH_JAVA_FX);
        pathJavaFXButton = createPathButton();
        AnchorPane javaFXGroup = new AnchorPane(pathJavaFX, pathJavaFXButton);

        pathEngineLabel = new Label("Engine Path:");
        pathEngine = createPathTextField(ARPropertyEnum.PATH_ENGINE);
        pathEngineButton = createPathButton();
        AnchorPane engineGroup = new AnchorPane(pathEngine, pathEngineButton);

        pathWebDriverLabel = new Label("Web Driver Path:");
        pathWebDriver = createPathTextField(ARPropertyEnum.PATH_WEBDRIVER);
        pathWebDriverButton = createPathButton();
        AnchorPane driverGroup = new AnchorPane(pathWebDriver, pathWebDriverButton);

        pathGroup = new VBox(
                pathExcelLabel,
                excelGroup,
                //                gridPaneExport,
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

        AnchorPane.setTopAnchor(pathGroup, ARConstants.SPACE_L + ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(pathGroup, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(pathGroup, ARConstants.SPACE_M);

        mainPane = new AnchorPane(title, pathGroup);
    }

    @Override
    public void initUIBehaviour() {

        try (Connection conn = performDataBase.getConnection()) {
            List<BotJobLoadDTO> botJobLoadList = performDataBase.loadAllBotJobs();

            List<InstructionLoadDTO> instList = null;

            for (BotJobLoadDTO botJobLoadDTO : botJobLoadList) {

                instList = performDataBase.instructionsToDuplicate(
                        conn,
                        botJobLoadDTO.getHomeBankingId(),
                        botJobLoadDTO.getId(),
                        -1,
                        "instruction",
                        "block"); // instruction
                break;
            }

            if ((instList != null && instList.size() > 0) || botJobLoadList.size() == 0) {
                migrationDBLabel.setVisible(false);
                migrationDBButton.setVisible(false);
            }
        } catch (SQLException ignore) {
            System.out.println("Check if It Was Migrated! - Not Migrate Columns found!");
        }

        //        homeBankingGroup
        //                .maxHeightProperty()
        //                .bind(mainPane.heightProperty()
        //                        .subtract(title.heightProperty())
        //                        .subtract(pathGroup.heightProperty())
        //                        .subtract(reloadDBButton.heightProperty())
        //                        .subtract(ARConstants.SPACE_M * 2)
        //                        .subtract(ARConstants.SPACE_L * 2));
        addHomeBankingButton.setOnMouseClicked(e -> {
            arNewHomeBankingScene.initialize(homeBankingList);
            arNewHomeBankingScene.showModal();
        });

        pathExcelButton.setOnMouseClicked(e -> openChooserFor(pathExcel, true));
        //        pathExportButton.setOnMouseClicked(e -> openChooserFor(pathExport, true));
        pathLogButton.setOnMouseClicked(e -> openChooserFor(pathLog, true));
        // pathExtRefButton.setOnMouseClicked(e -> openChooserFor(pathExtRef, true));
        pathJavaButton.setOnMouseClicked(e -> openChooserFor(pathJava, true));
        pathDBButton.setOnMouseClicked(e -> openChooserFor(pathDB, true));
        pathReportButton.setOnMouseClicked(e -> openChooserFor(pathReport, true));
        pathPriorityButton.setOnMouseClicked(e -> openChooserFor(pathPriority, true));
        pathJavaFXButton.setOnMouseClicked(e -> openChooserFor(pathJavaFX, true));
        pathEngineButton.setOnMouseClicked(e -> openChooserFor(pathEngine, true));
        pathWebDriverButton.setOnMouseClicked(e -> openChooserFor(pathWebDriver, false));
        browserChoiceBox.setValue(ARPropertyManager.getInstance().getProperty(ARPropertyEnum.BROWSER));

        if (ARPropertyManager.getInstance().getProperty(ARPropertyEnum.DATABASE_TYPE) == null) {
            databaseChoiceBox.setValue("Access");
        } else {
            databaseChoiceBox.setValue(ARPropertyManager.getInstance().getProperty(ARPropertyEnum.DATABASE_TYPE));
        }

        reloadDBButton.setOnMouseClicked(e -> saveConfigurations());
        migrationDBButton.setOnMouseClicked(e -> runMigrateScripts());
        deleteAllDBButton.setOnMouseClicked(e -> deleteAllDB());
    }

    private void runMigrateScripts() {
        String dataBaseType = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.DATABASE_TYPE);

        Label newInstruction =
                new Label("DB MIGRATION\nDatabase Selected: \"" + dataBaseType + "\" \nRelease : \"v2.6f Beta Test\"");
        newInstruction.setStyle("-fx-font-size: 18px; -fx-text-fill: red;");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Are you sure you want to EXECUTE MIGRATION DB (\"" + dataBaseType + "\")?");
        alert.getDialogPane().setContent(newInstruction);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {

            try (Connection conn = performDataBase.getConnection()) {
                List<BotJobLoadDTO> botJobLoadList = performDataBase.loadAllBotJobs();

                String[] tablesMigration = {
                    "block", "block_loop_instruction", "instruction", "instruction_reference", "reference", "variable"
                };
                ErrorMessage errorMessage = null;
                for (BotJobLoadDTO botJobLoadDTO : botJobLoadList) {
                    // tablesMigration = {"block", "block_loop_instruction", "instruction", "instruction_reference",
                    // "reference"};
                    //                    errorMessage = performDataBase.migration2_6f(
                    //                            conn, botJobLoadDTO.getId(), botJobLoadDTO.getId(), tablesMigration);
                    //
                    //                    if (errorMessage != null) {
                    //                        break;
                    //                    }
                }

                performDataBase.dropTablesMigrationScriptsv2_7f();

                if (errorMessage == null) {
                    showAlertTimer(
                            Alert.AlertType.INFORMATION,
                            "Migration DB Scripts Success!",
                            "The Block Component job has been successfully created!",
                            "Database",
                            databaseChoiceBox.getValue(),
                            null,
                            null);

                    Platform.runLater(() -> {
                        migrationDBLabel.setVisible(false);
                        migrationDBButton.setVisible(false);
                    });

                } else {
                    String errorType = "Database error";
                    String errorDetail = "Verify  [INSERT] or [UPDATE] or [SELECT]";

                    String detailedMessage = "Type: " + errorType + "\nDetail: " + errorDetail;

                    showAlertTimer(
                            Alert.AlertType.ERROR,
                            errorMessage.getErrorTitle(),
                            errorMessage.getErrorHeader(),
                            detailedMessage,
                            "Migration DB Scripts error",
                            databaseChoiceBox.getValue(),
                            null);
                }

            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }

            //            int rowsAffected = performDataBase.migrationScriptsv2_6f();
            //            if (rowsAffected < 0) {
            //                performMessage.errorMessage(
            //                        "Migration DB Scripts error",
            //                        "Cannot perform  Migration for the Database",
            //                        databaseChoiceBox.getValue(),
            //                        null,
            //                        null,
            //                        0);
            //            } else {
            //                performMessage.showCustomModalDialog(
            //                        "Migration DB Scripts Success!",
            //                        String.format("Perform Migration on %s records", rowsAffected),
            //                        "Database",
            //                        databaseChoiceBox.getValue(),
            //                        null,
            //                        false,
            //                        null,
            //                        0);
            //            }
        }
    }

    private void saveConfigurations() {
        boolean validfields = true;
        if (Strings.isNullOrEmpty(pathExcel.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Excel Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        //        if (Strings.isNullOrEmpty(pathExport.getText())) {
        //            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Export Path must be filed!",
        // ButtonType.OK);
        //            validfields = false;
        //        }
        //        if (Strings.isNullOrEmpty(fileExport.getText())) {
        //            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "File Name Export must be filed!",
        // ButtonType.OK);
        //            validfields = false;
        //        }

        if (Strings.isNullOrEmpty(pathLog.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Log Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(sizeLog.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Max Size Log must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathJava.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Java Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(socketPort.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Socket Port must be filed!", ButtonType.OK);
            validfields = false;
        }

        //        if (Strings.isNullOrEmpty(blockLimit.getText())) {
        //            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Process Limit must be filed!",
        // ButtonType.OK);
        //            validfields = false;
        //        }

        if (Strings.isNullOrEmpty(pathDB.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Database Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathReport.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Reports Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathPriority.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Priority Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathJavaFX.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "JavaFX Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathEngine.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "AR Engine Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathWebDriver.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Web Driver Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (validfields) {

            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.BROWSER.getValue(), browserChoiceBox.getValue());
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), databaseChoiceBox.getValue());
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.FOLDER_PATH_DB.getValue(), pathDB.getText());
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.FOLDER_PATH_EXCEL.getValue(), pathExcel.getText());
            //            ARPropertyManager.getInstance()
            //                    .setProperty(ARPropertyEnum.FOLDER_PATH_EXPORT.getValue(), pathExport.getText());
            //            ARPropertyManager.getInstance()
            //                    .setProperty(ARPropertyEnum.FILE_NAME_EXPORT.getValue(), fileExport.getText());
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.FOLDER_PATH_JAVA.getValue(), pathJava.getText());
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.FOLDER_PATH_JAVA_FX.getValue(), pathJavaFX.getText());
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.FOLDER_PATH_LOG.getValue(), pathLog.getText());
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.FOLDER_PATH_LOG.getValue(), pathLog.getText());
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.FOLDER_PATH_PRIORITY.getValue(), pathPriority.getText());
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.FOLDER_PATH_REPORT.getValue(), pathReport.getText());
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.MAX_LOG_SIZE.getValue(), sizeLog.getText());
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.PATH_ENGINE.getValue(), pathEngine.getText());
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.PATH_WEBDRIVER.getValue(), pathWebDriver.getText());
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), socketPort.getText());
            //            ARPropertyManager.getInstance()
            //                    .setProperty(ARPropertyEnum.BLOCK_EXEC_LIMIT.getValue(), blockLimit.getText());
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.REDUCE_SEARCH_CRITERIA.getValue(), reduceSearch.getText());

            /*ARPropertyManager.getInstance().setProperty(
            ARPropertyEnum.WEBDRIVER_EXT_REFERENCE.getValue(), pathExtRef.getText()); */
            // ARSharedResources.getInstance().changeDbConnection();

            homeBankingList.clear();
            homeBankingList.addAll(PerformDataBase.loadAllHomeBanking());
            homeBankingListView = new ListView<>(homeBankingList);

            performDataBase.changeDbConnection();

            new ARAlertScene(
                    Alert.AlertType.INFORMATION,
                    "Configuration saved",
                    "The configuration has been saved and the data has been reloaded",
                    ButtonType.OK);
        }
    }

    private void deleteAllDB() {
        String dataBaseType = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.DATABASE_TYPE);

        Label newInstruction = new Label("DELETE ALL JOB DETAILS\nDatabase Selected: \"" + dataBaseType + "\"");
        newInstruction.setStyle("-fx-font-size: 18px; -fx-text-fill: red;");
        ;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Are you sure you want to DELETE ALL JOB TABLES ROWS (\"" + dataBaseType + "\")?");
        alert.getDialogPane().setContent(newInstruction);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            if (deleteAllJobDetails(dataBaseType)) {
                // ARSharedResources.getInstance().changeDbConnection();
                new ARAlertScene(
                        Alert.AlertType.INFORMATION,
                        "All Job Details has been deleted!",
                        "The All Instructions and Job Details has been deleted and the data has been reloaded",
                        ButtonType.OK);

            } else {
                // ARSharedResources.getInstance().changeDbConnection();
                new ARAlertScene(
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
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {

            // Execute each statement individually
            stmt.executeUpdate("DELETE FROM job_run_report;");
            stmt.executeUpdate("DELETE FROM variable;");
            stmt.executeUpdate("DELETE FROM reference;");
            stmt.executeUpdate("DELETE FROM instruction;");
            stmt.executeUpdate("DELETE FROM block;");
            stmt.executeUpdate("DELETE FROM bot_job;");

            stmt.executeUpdate("DELETE FROM component_reference;");
            stmt.executeUpdate("DELETE FROM component_instruction;");
            stmt.executeUpdate("DELETE FROM component_block;");

            // Drop sequences if they exist
            if (!dataBaseType.equalsIgnoreCase("ACCESS")) {
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockLoopInstructionSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"botJobSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"variableSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"instructionReferenceSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"excelReportSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockLoopInstructionSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"complexInstructionSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"configurationSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"homeBankingSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockSeq\";");
                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"idgen\";");
            }
            ARLogger.getInstance(ARWebDriver.class)
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
            ARLogger.getInstance(ARWebDriver.class)
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

    private TextField createPathTextField(ARPropertyEnum property) {
        TextField textField = new TextField();
        textField.setText(ARPropertyManager.getInstance().getProperty(property));
        AnchorPane.setTopAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(textField, ARConstants.SPACE_XL);
        AnchorPane.setLeftAnchor(textField, ARConstants.SPACE_ZERO);
        return textField;
    }

    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_DIRECTORY, ARConstants.SPACE_M, new Insets(5D));
        button.setMaxWidth(ARConstants.SPACE_L);
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
