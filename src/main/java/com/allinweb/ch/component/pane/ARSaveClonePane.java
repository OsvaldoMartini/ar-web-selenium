package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeUrlDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.io.*;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ARSaveClonePane extends ARPane {

    protected static volatile ARSaveClonePane instance;

    // Private constructor to prevent instantiation
    private ARSaveClonePane() {
        // Initialize if necessary
        super();
    }

    public static ARSaveClonePane getInstance() {
        if (instance == null) {
            synchronized (ARSaveClonePane.class) {
                if (instance == null) {
                    instance = new ARSaveClonePane();
                }
            }
        }
        return instance;
    }

    public void initialize(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList) {
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobList = botJobList;

        if (nameField != null) {
            nameField.setText(selecBotJobDTO.getName().trim());
            descriptionField.setText(selecBotJobDTO.getDescription().trim());
            newUrl.setText(selecBotJobDTO.getHomeBankingLoadDTO().getUrl());
        }
    }

    private static final ARPropertyManager arPropertyManager;
    private static final PerformMessage performMessage;

    private static final PerformLists performLists;
    private static final PerformDataBase performDataBase;

    // Static block to initialize
    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
        performLists = PerformLists.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    private BotJobLoadDTO selecBotJobDTO;
    private List<BotJobLoadDTO> botJobList;
    // UI

    private Label nameLabel;
    private Label descriptionLabel;
    private Label defaultURLLabel;

    private TextField nameField;
    private TextField descriptionField;
    private TextField newUrl;

    private Button cloneBotJobButton;

    private Pane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Labels
        nameLabel = new Label("Name of new Bot Job:");
        descriptionLabel = new Label("Description:");
        defaultURLLabel = new Label("URL Bot Job OR INSERT NEW");

        String labelStyle = "-fx-text-fill: blue; -fx-font-weight: bold; -fx-font-size: 14;";
        nameLabel.setStyle(labelStyle);
        descriptionLabel.setStyle(labelStyle);
        defaultURLLabel.setStyle(labelStyle);

        // Text fields
        nameField = new TextField(selecBotJobDTO.getName().trim());
        descriptionField = new TextField("Description");
        newUrl = new TextField(selecBotJobDTO.getHomeBankingLoadDTO().getUrl());

        nameField.setPrefWidth(400);
        descriptionField.setPrefWidth(400);
        newUrl.setPrefWidth(400);

        // Button
        cloneBotJobButton = new Button("Clone Bot Job");
        cloneBotJobButton.setPrefWidth(200);
        cloneBotJobButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        // VBoxes for each field group
        VBox nameBox = new VBox(5, nameLabel, nameField);
        VBox descriptionBox = new VBox(5, descriptionLabel, descriptionField);
        VBox urlBox = new VBox(5, defaultURLLabel, newUrl);

        // Centered button
        HBox buttonBox = new HBox(cloneBotJobButton);
        buttonBox.setAlignment(Pos.CENTER);

        // Main vertical layout
        VBox formVBox = new VBox(20); // spacing between sections
        formVBox.setAlignment(Pos.TOP_LEFT);
        formVBox.setPadding(new Insets(20));
        formVBox.getChildren().addAll(nameBox, descriptionBox, urlBox, buttonBox);

        // Anchor the VBox
        AnchorPane.setTopAnchor(formVBox, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(formVBox, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(formVBox, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(formVBox, ARConstants.SPACE_M);

        mainPane = new AnchorPane(formVBox);
    }

    @Override
    public void initUIBehaviour() {

        cloneBotJobButton.setOnMouseClicked(e -> {
            String newBotJobName = nameField.getText().trim();
            String newDescription = descriptionField.getText().trim();

            // Clean Spaces
            Platform.runLater(() -> nameField.setText(newBotJobName));

            if (Strings.isNullOrEmpty(nameField.getText().trim())) {
                performMessage.errorMessage(
                        "Select a new Bot Job name", "There is NOT a name defined", null, null, null, 0);
                return;
            }

            BotJobLoadDTO existBotJob = botJobList.stream()
                    .filter(botJob -> botJob.getName().equals(newBotJobName))
                    .findFirst()
                    .orElse(null); //

            if (existBotJob != null) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists",
                        "The name you have entered is already in use.",
                        "Please choose a different name and try again.",
                        null,
                        null,
                        0);
                return;
            }

            //            String excelPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
            //            String originalFilePath =
            //                    excelPath + "\\" + selecBotJobDTO.getName().trim() + ".xlsx";
            //            String newFilePath = excelPath + "\\" + newBotJobName + ".xlsx";

            ExcelUtils.createExcelDataFile(selecBotJobDTO, newBotJobName);

            //            boolean excelCreation = checkFilesExist(originalFilePath, newFilePath);
            //            if (!excelCreation) {
            //                performMessage.errorMessage(
            //                        "Error Duplicating File Excel",
            //                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Excel File
            // Name:</span> ❌",
            //                        "<span style='color: #E65100; font-weight: bold;'>" + newBotJobName + ".xlsx" +
            // "</span> ",
            //                        null,
            //                        null,
            //                        0);
            //
            //                return;
            //            }

            if (!Strings.isNullOrEmpty(newUrl.getText())) {

                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                if (performLists.getListHomeUrl().isEmpty()) {
                    performDataBase.loadHomeUrls(null);
                }

                List<HomeUrlDTO> filteredHomeUrl = performLists.getHomeUrlsByBankId(
                        selecBotJobDTO.getHomeBankingLoadDTO().getId());

                if (!newUrl.getText()
                        .trim()
                        .equals(selecBotJobDTO.getHomeBankingLoadDTO().getUrl())) {

                    // Check if homeURLList contains a HomeUrlDTO with matching id and url
                    Optional<HomeUrlDTO> matchHomeUrl = filteredHomeUrl.stream()
                            .filter(homeUrl -> homeUrl.getId() != null
                                    && selecBotJobDTO.getHomeBankingId().equals(homeUrl.getHomeBankingId())
                                    && newUrl.getText().trim().equals(homeUrl.getUrl()))
                            .findFirst();

                    if (matchHomeUrl.isPresent()) {
                        HomeUrlDTO matchedHomeUrl = matchHomeUrl.get();
                        // Do something with matchedHomeUrl
                        System.out.println("Found matching HomeUrlDTO: id=" + matchedHomeUrl.getId() + ", url="
                                + matchedHomeUrl.getUrl());

                        cloneBotJobSteps(matchedHomeUrl, newBotJobName, newDescription, stage);

                    } else {
                        System.out.println("No matching HomeUrlDTO found.");

                        ErrorMessage errorMessage = performDataBase.createNewHomeUrl(
                                selecBotJobDTO.getHomeBankingId(),
                                newUrl.getText().trim());
                        if (errorMessage == null) {
                            // After the Insert
                            int newHomeUrlId = performDataBase.getNewHomeUrlId();

                            HomeUrlDTO homeUrlDTO = new HomeUrlDTO();
                            homeUrlDTO.setId(newHomeUrlId);
                            homeUrlDTO.setHomeBankingId(selecBotJobDTO.getHomeBankingId());
                            homeUrlDTO.setUrl(newUrl.getText().trim());

                            cloneBotJobSteps(homeUrlDTO, newBotJobName, newDescription, stage);
                        } else {
                            performMessage.errorMessage(
                                    "Insert New Environment Failed ❌",
                                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>"
                                            + errorMessage.getErrorTitle() + "</span>",
                                    "<span style='color: #E65100; font-weight: bold;'>" + errorMessage.getErrorHeader()
                                            + "</span>",
                                    "<span style='font-style: italic;'>" + errorMessage.getErrorMessage() + "</span>",
                                    null,
                                    0);
                        }
                    }

                } else {

                    // Check if homeURLList contains a HomeUrlDTO with matching id and url
                    Optional<HomeUrlDTO> matchHomeUrl = filteredHomeUrl.stream()
                            .filter(homeUrl -> homeUrl.getId() != null
                                    && selecBotJobDTO.getHomeBankingId().equals(homeUrl.getHomeBankingId())
                                    && newUrl.getText().trim().equals(homeUrl.getUrl()))
                            .findFirst();

                    if (matchHomeUrl.isPresent()) {
                        HomeUrlDTO matchedHomeUrl = matchHomeUrl.get();
                        // Do something with matchedHomeUrl
                        System.out.println("Found matching HomeUrlDTO: id=" + matchedHomeUrl.getId() + ", url="
                                + matchedHomeUrl.getUrl());

                        cloneBotJobSteps(matchedHomeUrl, newBotJobName, newDescription, stage);
                    }
                }

            } else {
                performMessage.errorMessage(
                        "URL Field cannot be empty",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>There is NOT a URL defined</span>",
                        null,
                        null,
                        null,
                        0);
            }
        });
    }

    private void cloneBotJobSteps(HomeUrlDTO homeUrlDTO, String newBotJobName, String newDescription, Stage stage) {
        ErrorMessage errorMessage =
                performDataBase.cloneBotJob(homeUrlDTO, selecBotJobDTO.getId(), newBotJobName, newDescription);

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneBlock(selecBotJobDTO.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneInstructions(selecBotJobDTO.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneVariables(selecBotJobDTO.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneUpdateInstruction(selecBotJobDTO.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneReferences(selecBotJobDTO.getId());
        }

        if (errorMessage == null) {
            Integer newBotJobId = performDataBase.getNewBotBojId(selecBotJobDTO.getId());

            performMessage.showCustomModalDialogDragWin11(
                    "Success: Bot Job Cloned",
                    "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Bot Job Cloned Successful!</span> ✅",
                    "<span style='color: #1976D2;'>New Bot Job Details:</span>",
                    "<span style='font-weight: bold;'>ID:</span> " + newBotJobId + "<br>"
                            + "<span style='font-weight: bold;'>Name:</span> '" + newBotJobName + "'",
                    "<span style='font-style: italic;'>Description: " + newDescription + "</span>",
                    false,
                    "OK",
                    null,
                    0);

        } else {

            Integer newBotJobId = performDataBase.getNewBotBojId(selecBotJobDTO.getId());
            if (newBotJobId != null) {
                performDataBase.deleteBotJob(newBotJobId);
            }

            String errorType = "Database error";
            String errorDetail = "Verify  [INSERT] or [UPDATE] or [SELECT]";

            performMessage.errorMessage(
                    "Error Encountered",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                    "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> " + errorType,
                    "<span style='font-style: italic;'>Detail:</span> " + errorDetail,
                    null,
                    0);
        }
        //            } else {
        //                performMessage.errorMessage(
        //                        "Access Denied",
        //                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Access
        // Denied!</span> 🔒",
        //                        "<span style='color: #E65100;'>Cannot access the file.</span>",
        //                        "<span style='font-style: italic;'>Verify if the file is currently open in another
        // application.</span>",
        //                        "<span style='font-style: italic;'>Please close the file in other applications and try
        // again.</span>",
        //                        0);
        //            }

        ARLogger.getInstance(ARSaveClonePane.class).finer("ARSaveClonePane Close()");
        Platform.runLater(() -> {
            stage.close();
        });
    }

    private boolean checkFilesExist(String originalFilePath, String newFilePath) {
        File originalFile = new File(originalFilePath);
        File newFile = new File(newFilePath);

        // Check if original file exists
        if (!originalFile.exists()) {
            performMessage.errorMessage(
                    "Excel File Missing",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Original Excel file does not exist!</span>",
                    "<span style='color: #E65100; font-weight: bold;'>Attempted to read:</span> <span style='font-weight: bold;'>"
                            + originalFilePath + "</span>",
                    "<span style='font-style: italic;'>Please ensure the file exists and the application has read permissions.</span>",
                    null,
                    0);
            return false;
        }

        // Check if new file exists
        if (!newFile.exists()) {
            performMessage.errorMessage(
                    "Excel File Missing",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Target Excel file does not exist!</span>",
                    "<span style='color: #E65100; font-weight: bold;'>Expected path:</span> <span style='font-weight: bold;'>"
                            + newFilePath + "</span>",
                    "<span style='font-style: italic;'>Please ensure the file exists and the application has read/write permissions.</span>",
                    null,
                    0);
            return false;
        }

        return true;
    }
}
