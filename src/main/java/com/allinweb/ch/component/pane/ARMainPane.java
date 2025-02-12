package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARAlertScene;
import com.allinweb.ch.component.scene.ARConfigurationScene;
import com.allinweb.ch.component.scene.ARInfoScene;
import com.allinweb.ch.component.scene.ARNewBotJobScene;
import com.allinweb.ch.component.scene.ARSaveCloneScene;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.ConfigUserDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;

public class ARMainPane extends ARPane {

    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final ARConfigurationScene arConfigurationScene;
    private static final ARNewBotJobScene arNewBotJobScene;

    // Static block to initialize
    static {
        arNewBotJobScene = ARNewBotJobScene.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        arConfigurationScene = ARConfigurationScene.getInstance();
    }

    private static final ARComponentBuilder builder = new ARComponentBuilder();
    private Properties properties = new Properties();

    // UI components
    Button newBotJobButton;
    Button cloneBotJobButton;
    // Button viewBotJobButton;
    Button configureButton;
    Button infoButton;
    Button editBotJobButton;
    Button launchBotJobButton;
    Button exitButton;
    HBox buttonPane;
    VBox panelPane;

    GridPane header = new GridPane();

    ListView<BotJobLoadDTO> viewBotJobListView = new ListView<>();

    public ARMainPane() {
        arNewBotJobScene.initialize(viewBotJobListView);

        String pathDB = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_DB);
        if (pathDB == null || pathDB.isBlank()) {
            arConfigurationScene.show();
            new ARAlertScene(
                    Alert.AlertType.WARNING,
                    "Configuration Needed",
                    "Please configure the application before use.",
                    ButtonType.OK);
        }
    }

    private void loadConfigDB() {

        ARSharedResources resources = ARSharedResources.getInstance();
        if (resources != null) {
            List<ConfigUserDTO> configurationList =
                    ARSharedResources.getInstance().loadUserConfig();
            setProperty(ARPropertyEnum.FOLDER_PATH_EXCEL.getValue(), "");
            setProperty(ARPropertyEnum.FOLDER_PATH_LOG.getValue(), "");
            setProperty(ARPropertyEnum.FOLDER_PATH_EXPORT.getValue(), "");
            //            setProperty(ARPropertyEnum.FILE_NAME_EXPORT.getValue(), "");
            setProperty(
                    ARPropertyEnum.FOLDER_PATH_JAVA.getValue(),
                    ARConstants.CURRENT_PATH + ARConstants.DEFAULT_PATH_JAVA);
            setProperty(
                    ARPropertyEnum.FOLDER_PATH_JAVA_FX.getValue(),
                    ARConstants.CURRENT_PATH + ARConstants.DEFAULT_PATH_JAVA_FX);
            setProperty(ARPropertyEnum.FOLDER_PATH_DB.getValue(), "");
            setProperty(ARPropertyEnum.FOLDER_PATH_REPORT.getValue(), "");
            setProperty(ARPropertyEnum.PATH_ENGINE.getValue(), ARConstants.CURRENT_PATH);
            setProperty(ARPropertyEnum.LOG_LEVEL.getValue(), Level.ALL.getName());
            setProperty(ARPropertyEnum.BROWSER.getValue(), ARConstants.CHROME);
            setProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC.getValue(), "60");
            setProperty(ARPropertyEnum.WEBDRIVER_INTERACTION_TIMEOUT_SEC.getValue(), "60");
            setProperty(ARPropertyEnum.DEFAULT_INSTRUCTION_STOP_SECONDS.getValue(), "15");
            setProperty(
                    ARPropertyEnum.WEBDRIVER_EXT_REFERENCE.getValue(),
                    "test-id='web-banking-payment-core.payment-details.external-reference'");
        }
    }

    @Override
    public void initUIComponents() {
        newBotJobButton = builder.buildButton(
                "New", ARConstants.SPACE_M, ARConstants.ICON_NEW, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        cloneBotJobButton = builder.buildButton(
                "Clone Job", ARConstants.SPACE_M, ARConstants.ICON_SAVE, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        configureButton = builder.buildButton(
                "Config", ARConstants.SPACE_M, ARConstants.ICON_CONFIG, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        infoButton = builder.buildButton(
                "Info", ARConstants.SPACE_M, ARConstants.ICON_INFO, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        editBotJobButton = builder.buildButton(
                "Open Job", ARConstants.SPACE_L, ARConstants.ICON_EDIT, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        launchBotJobButton = builder.buildButton(
                "Launch", ARConstants.SPACE_L, ARConstants.ICON_PLAY, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        exitButton = builder.buildButton(
                "Exit", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));

        buttonPane = new HBox(
                newBotJobButton,
                cloneBotJobButton,
                configureButton,
                infoButton,
                launchBotJobButton,
                editBotJobButton,
                exitButton);
        buttonPane.maxHeight(ARConstants.SPACE_L);
        AnchorPane.setTopAnchor(buttonPane, ARConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(buttonPane, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(buttonPane, ARConstants.SPACE_ZERO);
        buttonPane.setAlignment(Pos.TOP_CENTER);

        initHeader();

        //        ObservableList<BotJobLoadDTO> botJobList =
        // ARSharedResources.getInstance().getEntityList(BotJobDTO.class);
        ObservableList<BotJobLoadDTO> botJobList = FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
        viewBotJobListView.setItems(botJobList);
        viewBotJobListView.setCellFactory(new ARCellFactory<>(BotJobListCell.class)::call);

        //        viewBotJobListView.setMaxSize(800D, 580D);

        panelPane = new VBox(buttonPane, header, viewBotJobListView);
        VBox.setMargin(viewBotJobListView, new Insets(0, 10D, 10D, 10D));
        VBox.setVgrow(viewBotJobListView, Priority.ALWAYS);
        HBox.setHgrow(viewBotJobListView, Priority.ALWAYS);

        AnchorPane.setTopAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(panelPane, ARConstants.SPACE_ZERO);
    }

    @Override
    public void initUIBehaviour() {
        newBotJobButton.setOnMouseClicked(e -> {
            arNewBotJobScene.showModal();
            ObservableList<BotJobLoadDTO> botJobList =
                    FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
            viewBotJobListView.setItems(botJobList);
        });

        cloneBotJobButton.setOnMouseClicked(e -> {
            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
            if (selecBotJobDTO != null) {
                new ARSaveCloneScene(selecBotJobDTO, performDataBase.loadAllBotJobs()).showModal();
                ObservableList<BotJobLoadDTO> botJobList =
                        FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
                viewBotJobListView.setItems(botJobList);
                viewBotJobListView.refresh();

            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                return;
            }
        });
        /*viewBotJobButton.setOnMouseClicked(
                e -> new ARViewBotJobListScene().show()
        );*/
        configureButton.setOnMouseClicked(e -> {
            arConfigurationScene.showModal();
            performDataBase.changeDbConnection();
            ObservableList<BotJobLoadDTO> botJobList =
                    FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
            viewBotJobListView.setItems(botJobList);
        });
        infoButton.setOnMouseClicked(e -> new ARInfoScene().showModal());
        exitButton.setOnMouseClicked(e -> {
            //            Platform.exit();
            System.exit(0);
        });

        editBotJobButton.setOnMouseClicked(e -> {
            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();

            if (selecBotJobDTO != null) {
                try {
                    Platform.runLater(() -> {
                        new ARViewBotJobScene(selecBotJobDTO).show();
                        // new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName()).show();
                    });

                } catch (Exception e2) {
                    new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName() + "  " + e2.getMessage())
                            .show(); // TODO: handle exception
                }
                // new ARMoveBlockScene(selecBotJobDTO.getBlocks().get(0));
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                return;
            }
        });

        launchBotJobButton.setOnMouseClicked(e -> {
            {
                var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
                if (selecBotJobDTO != null) {
                    ARPropertyManager managerProps = ARPropertyManager.getInstance();
                    String enginePath = managerProps.getProperty(ARPropertyEnum.PATH_ENGINE) + "\\AR_Web_Engine.jar";
                    String excelPath = managerProps.getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL);
                    excelPath = excelPath + "\\" + selecBotJobDTO.getName() + ".xlsx";
                    if (!new File(excelPath).exists()) {
                        new ARAlertScene(
                                Alert.AlertType.WARNING,
                                "Missing file excel",
                                "Please generate and compile the data of the file excel first before launching the bot job",
                                ButtonType.OK);
                    }

                    String[] command = new String[] {
                        "cmd.exe",
                        "/c",
                        ".\\java\\bin\\java.exe",
                        "-jar",
                        "\"" + enginePath + "\"",
                        "execute/j",
                        String.valueOf(selecBotJobDTO.getHomeBankingLoadDTO().getId()),
                        String.valueOf(selecBotJobDTO.getId()),
                        "\"" + excelPath + "\"",
                        "-c",
                        ARPropertyManager.getConfigurationFileName()
                    };
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.directory(new File(ARConstants.CURRENT_PATH));
                    String logPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_LOG);
                    File output = new File(logPath + "\\engine_debug_log_output.log");
                    File error = new File(logPath + "\\engine_debug_log_error.log");
                    File input = new File(logPath + "\\engine_debug_log_input.log");
                    List<File> files = new ArrayList<>();
                    files.add(output);
                    files.add(error);
                    files.add(input);
                    for (File file : files) {
                        if (!file.exists()) {
                            try {
                                file.createNewFile();
                            } catch (IOException ex) {
                                ARLogger.getInstance(ARScannedElementPane.class).fine("Error : " + ex);
                            }
                        }
                    }
                    processBuilder.redirectOutput(output);
                    processBuilder.redirectError(error);
                    processBuilder.redirectInput(input);
                    try {
                        processBuilder.start();
                    } catch (IOException ex) {
                        ARLogger.getInstance(ARScannedElementPane.class).fine("Error : " + ex);
                    }
                } else {
                    performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                    return;
                }
            }
        });
    }

    private void initHeader() {
        header.setMaxHeight(ARConstants.SPACE_M);
        ColumnConstraints con = new ColumnConstraints();
        con.setPercentWidth(25);
        con.setHgrow(Priority.ALWAYS);
        con.setHalignment(HPos.LEFT);
        header.getColumnConstraints().add(con);
        header.getColumnConstraints().add(con);
        header.getColumnConstraints().add(con);
        ColumnConstraints con2 = new ColumnConstraints();
        con2.setPercentWidth(25);
        con2.setHgrow(Priority.ALWAYS);
        con2.setHalignment(HPos.CENTER);
        header.getColumnConstraints().add(con2);
        VBox.setMargin(header, new Insets(5D, 10D, 5D, 10D));
        header.add(new Label("Name"), 0, 0);
        header.add(new Label("Description"), 1, 0);
        header.add(new Label("Environment"), 2, 0);
        header.add(new Label("Actions"), 3, 0);
    }

    public void setProperty(String propertyName, String value) {
        this.properties.setProperty(propertyName, value);
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(panelPane);
    }
}
