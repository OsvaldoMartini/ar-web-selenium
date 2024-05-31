package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRConfigurationScene;
import com.allinweb.ch.component.scene.ABRInfoScene;
import com.allinweb.ch.component.scene.ABRNewBotJobScene;
import com.allinweb.ch.component.scene.ABRViewBotJobScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;

public class ABRMainPane extends ABRPane {

    private static final ABRComponentBuilder builder = new ABRComponentBuilder();

    // UI components
    Button newBotJobButton;
    // Button viewBotJobButton;
    Button configureButton;
    Button infoButton;
    Button editBotJobButton;
    Button launchBotJobButton;
    Button exitButton;
    HBox buttonPane;
    VBox panelPane;

    GridPane header = new GridPane();

    ListView<BotJobDTO> viewBotJobListView = new ListView<>();

    public ABRMainPane() {
        String pathDB = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
        /*String pathExcel = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL);
        String pathLog = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG);
        String pathReport = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_REPORT);*/
        if (pathDB.isBlank()) {
            new ABRConfigurationScene().show();
            new ABRAlertScene(
                    Alert.AlertType.WARNING,
                    "Configuration Needed",
                    "Please configure the application before use.",
                    ButtonType.OK);
        }
    }

    @Override
    public void initUIComponents() {
        newBotJobButton = builder.buildButton(
                "New", ABRConstants.SPACE_M, ABRConstants.ICON_NEW, ABRConstants.SPACE_M, new Insets(8, 10, 8, 10));
        configureButton = builder.buildButton(
                "Config",
                ABRConstants.SPACE_M,
                ABRConstants.ICON_CONFIG,
                ABRConstants.SPACE_M,
                new Insets(8, 10, 8, 10));
        infoButton = builder.buildButton(
                "Info", ABRConstants.SPACE_M, ABRConstants.ICON_INFO, ABRConstants.SPACE_M, new Insets(8, 10, 8, 10));
        editBotJobButton = builder.buildButton(
                "Edit", ABRConstants.SPACE_L, ABRConstants.ICON_EDIT, ABRConstants.SPACE_M, new Insets(8, 10, 8, 10));
        launchBotJobButton = builder.buildButton(
                "Launch", ABRConstants.SPACE_L, ABRConstants.ICON_PLAY, ABRConstants.SPACE_M, new Insets(8, 10, 8, 10));
        exitButton = builder.buildButton(
                "Exit", ABRConstants.SPACE_L, ABRConstants.ICON_CROSS, ABRConstants.SPACE_M, new Insets(8, 10, 8, 10));

        buttonPane = new HBox(
                newBotJobButton, configureButton, infoButton, launchBotJobButton, editBotJobButton, exitButton);
        buttonPane.maxHeight(ABRConstants.SPACE_L);
        AnchorPane.setTopAnchor(buttonPane, ABRConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(buttonPane, ABRConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(buttonPane, ABRConstants.SPACE_ZERO);
        buttonPane.setAlignment(Pos.TOP_CENTER);

        initHeader();

        ObservableList<BotJobDTO> botJobList = ABRSharedResources.getInstance().getEntityList(BotJobDTO.class);
        viewBotJobListView.setItems(botJobList);
        viewBotJobListView.setCellFactory(new ABRCellFactory<>(BotJobListCell.class)::call);

        viewBotJobListView.setMaxSize(800D, 580D);

        panelPane = new VBox(buttonPane, header, viewBotJobListView);
        VBox.setMargin(viewBotJobListView, new Insets(0, 10D, 10D, 10D));
        VBox.setVgrow(viewBotJobListView, Priority.ALWAYS);

        AnchorPane.setTopAnchor(panelPane, ABRConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(panelPane, ABRConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(panelPane, ABRConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(panelPane, ABRConstants.SPACE_ZERO);
    }

    @Override
    public void initUIBehaviour() {
        newBotJobButton.setOnMouseClicked(e -> new ABRNewBotJobScene().show());

        /*viewBotJobButton.setOnMouseClicked(
                e -> new ABRViewBotJobListScene().show()
        );*/
        configureButton.setOnMouseClicked(e -> new ABRConfigurationScene().show());
        infoButton.setOnMouseClicked(e -> new ABRInfoScene().show());
        exitButton.setOnMouseClicked(e -> Platform.exit());

        editBotJobButton.setOnMouseClicked(e -> {
            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();

            if (selecBotJobDTO != null) {
                try {
                    Platform.runLater(() -> {
                        new ABRViewBotJobScene(selecBotJobDTO.getId()).show();
                        // new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName()).show();
                    });

                } catch (Exception e2) {
                    new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName() + "  " + e2.getMessage())
                            .show(); // TODO: handle exception
                }
                // new ABRMoveBlockScene(selecBotJobDTO.getBlocks().get(0));
            }
        });

        launchBotJobButton.setOnMouseClicked(e -> {
            {
                var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
                if (selecBotJobDTO != null) {
                    ABRPropertyManager managerProps = ABRPropertyManager.getInstance();
                    String enginePath = managerProps.getProperty(ABRPropertyEnum.PATH_ENGINE) + "\\ABR_Web_Engine.jar";
                    String excelPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL);
                    excelPath = excelPath + "\\" + selecBotJobDTO.getName() + ".xlsx";
                    if (!new File(excelPath).exists()) {
                        new ABRAlertScene(
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
                        String.valueOf(selecBotJobDTO.getHomeBanking().getId()),
                        String.valueOf(selecBotJobDTO.getId()),
                        "\"" + excelPath + "\"",
                        "-c",
                        ABRPropertyManager.getConfigurationFileName()
                    };
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.directory(new File(ABRConstants.CURRENT_PATH));
                    String logPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG);
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
                                ABRLogger.getInstance(ABRScannedElementPane.class)
                                        .fine("Error : " + ex);
                            }
                        }
                    }
                    processBuilder.redirectOutput(output);
                    processBuilder.redirectError(error);
                    processBuilder.redirectInput(input);
                    try {
                        processBuilder.start();
                    } catch (IOException ex) {
                        ABRLogger.getInstance(ABRScannedElementPane.class).fine("Error : " + ex);
                    }
                }
            }
        });
    }

    private void initHeader() {
        header.setMaxHeight(ABRConstants.SPACE_M);
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

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(panelPane);
    }
}
