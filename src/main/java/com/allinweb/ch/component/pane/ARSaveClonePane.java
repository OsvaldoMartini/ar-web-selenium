package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.model.HomeUrlDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import lombok.extern.slf4j.Slf4j;  @Slf4j public class ARSaveClonePane extends ARPane {

    protected static volatile ARSaveClonePane instance;

    // Private constructor to prevent instantiation
    private ARSaveClonePane() {

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

    public void initialize(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.selectedBotJob = selecBotJobDTO;

        if (botJobName != null) {
            botJobName.setText(selecBotJobDTO.getName().trim());
            botJobDescription.setText(selecBotJobDTO.getDescription().trim());
            newUrl.setText(selecBotJobDTO.getHomeBankingLoadDTO().getUrl());
        }

        if (performLists.getListHomeBanking().isEmpty()) {
            performDBEngine.loadHomeBanking(null);
        }
        if (performLists.getListHomeUrl().isEmpty()) {
            performDBEngine.loadHomeUrls(null);
        }
    }

    private boolean isEnabledLicence;
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final ARNewHomeBankingScene arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();

    private BotJobLoadDTO selectedBotJob;
    //    private List<BotJobLoadDTO> botJobList;
    // UI

    private Label labelBotJobName;
    private Label descriptionLabel;
    private Label labelHomeBanking;

    private TextField botJobName;
    private TextField botJobDescription;
    private TextField newUrl;

    private Button cloneBotJobButton;
    private Button refreshEnvsButton;
    private Button insertSitesdButton;

    private ChoiceBox<HomeUrlDTO> homeURLChoiceBox;

    private Pane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        String labelStyle = "-fx-text-fill: blue; -fx-font-weight: bold; -fx-font-size: 14;";

        // Top description label for the pane
        Label paneTitleLabel = new Label("Clone Bot Jobs");
        paneTitleLabel.setStyle("-fx-text-fill: blue; -fx-font-weight: bold; -fx-font-size: 16;");
        paneTitleLabel.setMaxWidth(Double.MAX_VALUE);
        paneTitleLabel.setAlignment(Pos.CENTER);

        // Labels
        labelBotJobName = new Label("Name of new Bot Job:");
        labelBotJobName.setStyle(labelStyle);

        descriptionLabel = new Label("Description:");
        descriptionLabel.setStyle(labelStyle);

        // This is your urlEnviromentLabel (defaultURLLabel)
        labelHomeBanking = new Label("Enter a New URL or Choose from the List Below");
        labelHomeBanking.setStyle(
                "-fx-font-size: 1.2em; -fx-font-weight: bold; -fx-text-fill: #1565C0; -fx-padding: 0 0 10 0;");
        labelHomeBanking.setMaxWidth(Double.MAX_VALUE);
        labelHomeBanking.setAlignment(Pos.CENTER);

        // Text fields
        botJobName = new TextField(selectedBotJob.getName().trim());
        botJobName.setPrefWidth(400);

        botJobDescription = new TextField("Description");
        botJobDescription.setPrefWidth(400);

        newUrl = new TextField(selectedBotJob.getHomeBankingLoadDTO().getUrl());
        newUrl.setPrefWidth(250);

        // ChoiceBox + Button
        homeURLChoiceBox = new ChoiceBox<>();
        // Remove fixed width
        // homeURLChoiceBox.setPrefWidth(300);
        // homeURLChoiceBox.setMaxWidth(300);
        // homeURLChoiceBox.setMinWidth(300);

        // Apply CSS style for font size, padding, background, and text color
        homeURLChoiceBox.setStyle("-fx-font-size: 1.1em;" + "-fx-padding: 4 8 4 8;"
                + "-fx-background-radius: 5;"
                + "-fx-border-radius: 5;"
                + "-fx-text-fill: white;");

        //        homeURLChoiceBox.setDefaultButton(true);

        populateHomeUrlChoiceBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        Tooltip tooltip = new Tooltip("Select the target URL / environment for the Bot Job");
        homeURLChoiceBox.setTooltip(tooltip);

        refreshEnvsButton = createPathButton();

        // Side-by-side: ChoiceBox and refresh button
        HBox choiceAndRefreshBox = new HBox(5, homeURLChoiceBox, refreshEnvsButton);
        choiceAndRefreshBox.setAlignment(Pos.CENTER); // Center horizontally
        choiceAndRefreshBox.setPadding(new Insets(0, 0, 10, 0));

        // Allow ChoiceBox to grow horizontally
        HBox.setHgrow(homeURLChoiceBox, Priority.ALWAYS);
        homeURLChoiceBox.setMaxWidth(Double.MAX_VALUE);

        // Buttons
        cloneBotJobButton = new Button("Clone Bot Job");
        cloneBotJobButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        insertSitesdButton = new Button("Orgs / Environments");
        insertSitesdButton.setDefaultButton(true);
        HBox.setMargin(insertSitesdButton, new Insets(0, 0, 0, 20));

        // Buttons in one line
        HBox buttonsBox = new HBox(15, cloneBotJobButton, insertSitesdButton);
        buttonsBox.setAlignment(Pos.CENTER);

        // Organization label
        Label organizationLabel = new Label("Organization: ");
        organizationLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1em;");
        organizationLabel.setMinWidth(Region.USE_PREF_SIZE); // Prevent stretching

        // OrgName label to the right
        Label orgNameLabel = new Label(selectedBotJob.getHomeBankingLoadDTO().getName());
        orgNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em; -fx-text-fill: #1E90FF;"); // nice blue

        // Combine in an HBox
        HBox labelsBox = new HBox(10); // spacing of 10
        labelsBox.getChildren().addAll(organizationLabel, orgNameLabel);
        labelsBox.setAlignment(Pos.CENTER_LEFT); // align to left

        // HBox containing Organization label + newUrl TextField
        VBox organizationBox = new VBox(5, labelsBox, newUrl);
        organizationBox.setAlignment(Pos.CENTER_LEFT); // Align items to the left

        // URL details container (everything related to URL + buttons)
        VBox homeUrlDetailsContainer = new VBox(10);
        homeUrlDetailsContainer.setPadding(new Insets(10, 10, 10, 10));
        homeUrlDetailsContainer.setStyle(
                "-fx-background-color: #E8F5E9; -fx-border-color: #ccc; -fx-border-width: 1px; -fx-border-radius: 5px;");
        homeUrlDetailsContainer
                .getChildren()
                .addAll(labelHomeBanking, organizationBox, choiceAndRefreshBox, buttonsBox);

        // Main layout
        VBox mainLayout = new VBox(
                12,
                paneTitleLabel, // Top title
                labelBotJobName,
                botJobName,
                descriptionLabel,
                botJobDescription,
                homeUrlDetailsContainer);
        mainLayout.setPadding(new Insets(15));
        mainLayout.setFillWidth(true);

        AnchorPane.setTopAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(mainLayout, ARConstants.SPACE_M);

        mainPane = new AnchorPane(mainLayout);
    }

    @Override
    public void initUIBehaviour() {
        insertSitesdButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            if (performLists.getListHomeBanking().isEmpty()) {
                performDBEngine.loadHomeBanking(null);
            }
            if (performLists.getListHomeUrl().isEmpty()) {
                performDBEngine.loadHomeUrls(null);
            }

            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
            arNewHomeBankingScene.initialize(homeBank);
            Stage currentStage = (Stage) insertSitesdButton.getScene().getWindow();
            arNewHomeBankingScene.showModal(currentStage);
            // If homeURLChoiceBox was initialized, refresh its items
            if (homeURLChoiceBox != null) {
                populateHomeUrlChoiceBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
            }
        });

        refreshEnvsButton.setOnAction(e -> {
            // Reload from performLists after reloading from DB
            performDBEngine.loadHomeUrls(null);

            // If homeURLChoiceBox was initialized, refresh its items
            if (homeURLChoiceBox != null) {
                populateHomeUrlChoiceBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
            }
        });

        homeURLChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(HomeUrlDTO object) {
                if (object != null) {
                    if (object.getUrl() != null) {
                        return object.getOrgName() + " | " + object.getUrl();
                    } else {
                        return object.getOrgName();
                    }
                }
                return "";
            }

            @Override
            public HomeUrlDTO fromString(String string) {
                return null;
            }
        });

        homeURLChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                newUrl.setText(newVal.getUrl().trim());
            }
        });

        cloneBotJobButton.setOnMouseClicked(e -> {
            String newBotJobName = botJobName.getText().trim();
            String newDescription = botJobDescription.getText().trim();

            // Clean Spaces
            Platform.runLater(() -> botJobName.setText(newBotJobName));

            if (Strings.isNullOrEmpty(botJobName.getText().trim())) {
                performMessage.errorMessage(
                        "Select a new Bot Job name", "There is NOT a name defined", null, null, null, 0);
                return;
            }

            BotJobLoadDTO existBotJob = performLists.getQuickBotJobs().stream()
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

            ExcelUtils.createExcelDataFile(selectedBotJob, newBotJobName);

            if (!Strings.isNullOrEmpty(newUrl.getText())) {

                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                if (performLists.getListHomeUrl().isEmpty()) {
                    performDBEngine.loadHomeUrls(null);
                }

                List<HomeUrlDTO> filteredHomeUrl = performLists.getHomeUrlsByBankId(
                        selectedBotJob.getHomeBankingLoadDTO().getId());

                if (!newUrl.getText()
                        .trim()
                        .equals(selectedBotJob.getHomeBankingLoadDTO().getUrl())) {

                    // Check if homeURLList contains a HomeUrlDTO with matching id and url
                    Optional<HomeUrlDTO> matchHomeUrl = filteredHomeUrl.stream()
                            .filter(homeUrl -> homeUrl.getId() != null
                                    && selectedBotJob.getHomeBankingId().equals(homeUrl.getHomeBankingId())
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
                                selectedBotJob.getHomeBankingId(),
                                newUrl.getText().trim());
                        if (errorMessage == null) {
                            // After the Insert
                            int newHomeUrlId = performDataBase.getNewHomeUrlId();

                            HomeUrlDTO homeUrlDTO = new HomeUrlDTO();
                            homeUrlDTO.setId(newHomeUrlId);
                            homeUrlDTO.setHomeBankingId(selectedBotJob.getHomeBankingId());
                            homeUrlDTO.setUrl(newUrl.getText().trim());

                            cloneBotJobSteps(homeUrlDTO, newBotJobName, newDescription, stage);

                            if (homeURLChoiceBox != null) {
                                populateHomeUrlChoiceBox(
                                        selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
                            }

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
                                    && selectedBotJob.getHomeBankingId().equals(homeUrl.getHomeBankingId())
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
                performDataBase.cloneBotJob(homeUrlDTO, selectedBotJob.getId(), newBotJobName, newDescription);

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneBlock(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneInstructions(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneVariables(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneUpdateInstruction(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneReferences(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            Integer newBotJobId = performDataBase.getNewBotBojId(selectedBotJob.getId());

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

            Integer newBotJobId = performDataBase.getNewBotBojId(selectedBotJob.getId());
            if (newBotJobId != null) {
                performDataBase.deleteBotJobData(newBotJobId);
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

        log.info("ARSaveClonePane Close()");
        Platform.runLater(() -> {
            stage.close();
        });
    }

    private void populateHomeUrlChoiceBox(int homeBankId, int currentHomeUrlId) {
        // Clear old items
        homeURLChoiceBox.getItems().clear();

        List<HomeUrlDTO> homeUrlFiltered = performLists.getHomeUrlsByBankId(homeBankId);

        // If list is empty (no real envs), add "No Environment Defined"
        if (homeUrlFiltered.isEmpty()) {
            HomeUrlDTO noEnv = new HomeUrlDTO(-1, null, -1, "No Environment Defined");
            homeURLChoiceBox.getItems().add(noEnv);
            homeURLChoiceBox.setDisable(true);
        } else {
            homeURLChoiceBox.getItems().addAll(homeUrlFiltered);
            homeURLChoiceBox.setDisable(false);

            // Select the item matching currentHomeUrlId
            for (HomeUrlDTO item : homeUrlFiltered) {
                if (item.getId() == currentHomeUrlId) { // assuming getId() returns homeUrlId
                    homeURLChoiceBox.getSelectionModel().select(item);
                    break;
                }
            }
        }
    }

    // You mentioned this is the button creator, adapted here
    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private boolean checkLicense() {
        try {
            String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
            if (Strings.isNullOrEmpty(licensePath)) {
                licensePath = System.getProperty("user.dir");
            }

            LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

            String msgValid = "The license file is valid and the application is authorized for use.";
            String msgNextStep = "You can now proceed with normal application usage.";

            String msgColor = "#0277BD";
            if (!licenseStatus.equals(LicenceVal.VALID)) {
                msgValid = "The license file is not valid and the application is not authorized for use.";
                msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
                msgColor = "#C62828"; // Soft, elegant red tone

                performMessage.showCustomModalDialogDragWin11(
                        "License Status Verification",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>License status has been successfully verified.</span>",
                        "<span style='color: " + msgColor + "; font-weight: bold;'>" + msgValid + "</span>",
                        "<span style='font-style: italic;'>" + msgNextStep + "</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Current license status:</span> <span style='font-weight: bold;'>"
                                + licenseStatus.getStaus() + "</span>",
                        false,
                        "OK",
                        null,
                        0);
                return false;
            }
            return true;
        } catch (Exception error) {
            
                    log.error("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }
}
