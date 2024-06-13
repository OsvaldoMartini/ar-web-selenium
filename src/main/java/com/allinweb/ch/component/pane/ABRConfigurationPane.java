package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.HomeBankingListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRNewHomeBankingScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
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
    // Label pathExtRefLabel; //Added by morandi 15-04
    Label pathLogLabel;
    Label sizeLogLabel;
    Label pathJavaLabel;
    Label pathDBLabel;
    Label pathReportLabel;
    Label pathPriorityLabel;
    Label pathJavaFXLabel;
    Label pathEngineLabel;
    Label browserLabel;
    Label pathWebDriverLabel;

    TextField pathExcel;
    // TextField pathExtRef; //Added by morandi 15-04
    TextField pathLog;
    TextField sizeLog;
    TextField pathJava;
    TextField pathDB;
    TextField pathReport;
    TextField pathPriority;
    TextField pathJavaFX;
    TextField pathEngine;
    TextField pathWebDriver;

    ChoiceBox<String> browserChoiceBox = new ChoiceBox<>();

    Button pathExcelButton;
    Button pathLogButton;
    Button pathJavaButton;
    Button pathDBButton;
    Button pathReportButton;
    Button pathPriorityButton;
    Button pathJavaFXButton;
    Button pathEngineButton;
    Button pathWebDriverButton;

    Button addHomeBankingButton;
    Button saveButton;

    ListView<HomeBankingDTO> homeBankingListView;

    VBox pathGroup;
    VBox homeBankingGroup;

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

        ButtonBar homeBankingActionGroup = new ButtonBar();
        addHomeBankingButton = builder.buildButton("Insert / Update / Config Scan");
        homeBankingActionGroup.getButtons().addAll(addHomeBankingButton);

        ObservableList<HomeBankingDTO> homeBankingList =
                ABRSharedResources.getInstance().getEntityList(HomeBankingDTO.class);
        homeBankingListView = new ListView<>(homeBankingList);
        homeBankingListView.setCellFactory(new ABRCellFactory<>(HomeBankingListCell.class)::call);

        homeBankingGroup = new VBox(homeBankingActionGroup, homeBankingListView);
        AnchorPane.setBottomAnchor(homeBankingGroup, ABRConstants.SPACE_L + ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(homeBankingGroup, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(homeBankingGroup, ABRConstants.SPACE_M);

        pathExcelLabel = new Label("Excel Path:");
        pathExcel = createPathTextField(ABRPropertyEnum.FOLDER_PATH_EXCEL);
        pathExcelButton = createPathButton();
        AnchorPane excelGroup = new AnchorPane(pathExcel, pathExcelButton);
        pathLogLabel = new Label("Log Path:");
        pathLog = createPathTextField(ABRPropertyEnum.FOLDER_PATH_LOG);
        pathLogButton = createPathButton();
        sizeLogLabel = new Label("Max Size Log");
        sizeLog = createPathTextField(ABRPropertyEnum.MAX_LOG_SIZE);
        GridPane gridPaneLog = new GridPane();
        //        gridPaneLog.setVgap(10);
        gridPaneLog.setHgap(10);
        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(80);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(15);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(5);
        gridPaneLog.getColumnConstraints().addAll(col1, col2, col3);

        // Add labels in the first row
        gridPaneLog.add(pathLogLabel, 0, 0);
        gridPaneLog.add(sizeLogLabel, 1, 0);

        // Add text fields in the second row
        gridPaneLog.add(pathLog, 0, 1);
        gridPaneLog.add(sizeLog, 1, 1);

        // Add button in the second row, third column
        gridPaneLog.add(pathLogButton, 2, 1);

        // Set margin for pathLogButton to create spacing from right border
        GridPane.setMargin(pathLogButton, new Insets(0, 0, 0, 5));

        //        AnchorPane logGroup = new AnchorPane(pathLog, sizeLog, pathLogButton);
        pathJavaLabel = new Label("Java Path:");
        pathJava = createPathTextField(ABRPropertyEnum.FOLDER_PATH_JAVA);
        pathJavaButton = createPathButton();
        AnchorPane javaGroup = new AnchorPane(pathJava, pathJavaButton);
        pathDBLabel = new Label("Database Path:");
        pathDB = createPathTextField(ABRPropertyEnum.FOLDER_PATH_DB);
        pathDBButton = createPathButton();
        AnchorPane dbGroup = new AnchorPane(pathDB, pathDBButton);
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

        browserLabel = new Label("Browser");
        ObservableList<String> browserList =
                FXCollections.observableArrayList(ABRConstants.CHROME, ABRConstants.EDGE, ABRConstants.FIREFOX);
        browserChoiceBox.setItems(browserList);
        // Added by morandi 15-04-24
        /* pathExtRefLabel = new Label("CSS Ext. Reference:");
        pathExtRef = createPathTextField(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        pathExtRefButton = createPathButton(); */
        // AnchorPane refGroup = new AnchorPane(pathExtRef,pathExtRefButton);
        // END Add Morandi

        pathGroup = new VBox(
                pathExcelLabel,
                excelGroup,
                gridPaneLog,
                pathDBLabel,
                dbGroup,
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
                browserLabel,
                browserChoiceBox); // , pathExtRefLabel, pathExtRef);
        AnchorPane.setTopAnchor(pathGroup, ABRConstants.SPACE_L + ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(pathGroup, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(pathGroup, ABRConstants.SPACE_M);

        saveButton = builder.buildButton("Save to DB");
        saveButton.setMaxHeight(ABRConstants.SPACE_L);
        AnchorPane.setTopAnchor(
                saveButton,
                (pathGroup.getChildren().size() * (ABRConstants.SPACE_M + ABRConstants.SPACE_XS))
                        + (ABRConstants.SPACE_XL + ABRConstants.SPACE_SM));
        AnchorPane.setLeftAnchor(saveButton, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(saveButton, ABRConstants.SPACE_M);
        mainPane = new AnchorPane(title, pathGroup, saveButton, homeBankingGroup);
    }

    @Override
    public void initUIBehaviour() {
        homeBankingGroup
                .maxHeightProperty()
                .bind(mainPane.heightProperty()
                        .subtract(title.heightProperty())
                        .subtract(pathGroup.heightProperty())
                        .subtract(saveButton.heightProperty())
                        .subtract(ABRConstants.SPACE_M * 2)
                        .subtract(ABRConstants.SPACE_L * 2));
        addHomeBankingButton.setOnMouseClicked(e -> new ABRNewHomeBankingScene().show());
        pathExcelButton.setOnMouseClicked(e -> openChooserFor(pathExcel, true));
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

        saveButton.setOnMouseClicked(e -> saveConfigurations());
    }

    private void saveConfigurations() {
        boolean validfields = true;
        if (Strings.isNullOrEmpty(pathExcel.getText())) {
            new ABRAlertScene(Alert.AlertType.ERROR, "Field Blank", "Excel Path must be filed!", ButtonType.OK);
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

            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.FOLDER_PATH_LOG.getValue(), pathLog.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.MAX_LOG_SIZE.getValue(), sizeLog.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_JAVA.getValue(), pathJava.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.FOLDER_PATH_DB.getValue(), pathDB.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_REPORT.getValue(), pathReport.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_PRIORITY.getValue(), pathPriority.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_JAVA_FX.getValue(), pathJavaFX.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.PATH_ENGINE.getValue(), pathEngine.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.PATH_WEBDRIVER.getValue(), pathWebDriver.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.BROWSER.getValue(), browserChoiceBox.getValue());

            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL.getValue(), pathExcel.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.FOLDER_PATH_LOG.getValue(), pathLog.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.MAX_LOG_SIZE.getValue(), sizeLog.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_JAVA.getValue(), pathJava.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.FOLDER_PATH_DB.getValue(), pathDB.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_REPORT.getValue(), pathReport.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_PRIORITY.getValue(), pathPriority.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.FOLDER_PATH_JAVA_FX.getValue(), pathJavaFX.getText());
            ABRPropertyManager.getInstance().setProperty(ABRPropertyEnum.PATH_ENGINE.getValue(), pathEngine.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.PATH_WEBDRIVER.getValue(), pathWebDriver.getText());
            ABRPropertyManager.getInstance()
                    .setProperty(ABRPropertyEnum.BROWSER.getValue(), browserChoiceBox.getValue());

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
