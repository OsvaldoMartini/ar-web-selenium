package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BankingDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.model.HomeUrlDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.DatabaseUserDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import lombok.Getter;

public class ARNewHomeBankingPane extends ARPane {

    protected static volatile ARNewHomeBankingPane instance;

    // Private constructor to prevent instantiation
    private ARNewHomeBankingPane() {
        // Initialize if necessary
        super();
    }

    public static ARNewHomeBankingPane getInstance() {
        if (instance == null) {
            synchronized (ARNewHomeBankingPane.class) {
                if (instance == null) {
                    instance = new ARNewHomeBankingPane();
                }
            }
        }
        return instance;
    }

    @Getter
    private ObservableList<HomeBankingLoadDTO> homeBankingList;

    public void initialize(ObservableList<HomeBankingLoadDTO> homeBankingList) {
        this.homeBankingList = homeBankingList;
        if (this.homeBankingList == null) {
            this.homeBankingList = FXCollections.observableArrayList();
        }
    }

    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    private Button insertORGButton;
    private Button updateORGButton;
    private Button deleteORGButton;
    private Button templateORGButton;

    private Button insertURLButton;
    private Button updateURLButton;
    private Button deleteURLButton;

    // Create labels
    private Label idLabel;
    private Label nameLabel;
    private Label urlLabel;
    private Label priorityLabel;
    private Label jobsLabel;
    private Label searchConfigLabel;
    private Label optionsConfigLabel;
    private Label organizationsLabel;
    private Label urlEnviromentLabel;

    private TextField idField;
    private TextField nameField;
    private TextField urlField;
    private TextArea priorityField;
    private TextField jobsField;
    private TextArea scanConfigField;
    private TextArea optionsConfigField;
    private TextField homeUrlIdField;
    private TextField homeUrlValueField;

    // Regular expression for a basic URL validation (improved)
    private static final String URL_REGEX =
            "^((https?|ftp|file)://)?([\\da-z.-]+)\\.([a-z.]{2,6})(:\\d+)?(/\\w .-]*)?/?$";

    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE);

    private Connection conn = null;
    private TableView<DatabaseUserDTO> tableView;
    private TableView<HomeUrlDTO> tableViewHomeUrl;
    private List<BankingDTO> dtoList;

    private Pane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Load initial data
        performDataBase.loadAllHomeBankingBotJob();
        // Assuming getDatabaseList() and getHomeUrlList() are populated by loadAllHomeBankingBotJob()
        // If not, you might need to call updateHomeBankList(someLoadedList) here.

        // --- 1. Initialize Labels ---
        idLabel = new Label("ID:");
        nameLabel = new Label("Name:");
        urlLabel = new Label("Url:");
        priorityLabel = new Label("Priority:"); // This will be "Next Row" conceptually
        jobsLabel = new Label("Total of Jobs:");
        searchConfigLabel = new Label("Scan Config:");
        optionsConfigLabel = new Label("WebDriver Options:");

        organizationsLabel = new Label("Organizations");
        urlEnviromentLabel = new Label("Environments"); // This label will be moved

        // --- 2. Initialize Text Fields and Text Areas ---
        // ID Field (Read-only, grey background)
        idField = new TextField();
        idField.setEditable(false);
        idField.setStyle("-fx-control-inner-background: #D3D3D3;");
        idField.setPrefWidth(50); // Max 50
        idField.setMaxWidth(50); // Max 50
        idField.setPrefHeight(28);

        // Name Field (Editable, yellow background)
        nameField = new TextField();
        nameField.setStyle("-fx-control-inner-background: #FFDA33;");
        nameField.setPrefWidth(150); // Max 150
        nameField.setMaxWidth(150); // Max 150
        nameField.setPrefHeight(28);
        nameField.requestFocus(); // Keep initial focus

        // URL Field (Editable, yellow background)
        urlField = new TextField();
        urlField.setStyle("-fx-control-inner-background: #FFDA33;");
        urlField.setPrefHeight(28);
        HBox.setHgrow(urlField, Priority.ALWAYS); // Allow this field to expand horizontally

        // Priority Field (TextArea, yellow background, multi-line)
        priorityField = new TextArea();
        priorityField.setStyle("-fx-control-inner-background: #FFDA33;");
        priorityField.setPrefRowCount(3); // Suggests initial rows
        priorityField.setWrapText(true); // Enable text wrapping
        priorityField.setPrefHeight(60); // Adjusted height for better multi-line display
        HBox.setHgrow(priorityField, Priority.ALWAYS); // Allow this field to expand horizontally

        // Jobs Field (Read-only, grey background)
        jobsField = new TextField();
        jobsField.setEditable(false);
        jobsField.setStyle("-fx-control-inner-background: #D3D3D3;");
        jobsField.setPrefWidth(50); // Max 50
        jobsField.setMaxWidth(50); // Max 50
        jobsField.setPrefHeight(28);

        // Search Config Field (TextArea, yellow background, multi-line)
        scanConfigField = new TextArea();
        scanConfigField.setStyle("-fx-control-inner-background: #FFDA33;");
        scanConfigField.setPrefRowCount(3);
        scanConfigField.setWrapText(true);
        scanConfigField.setPrefHeight(60);
        HBox.setHgrow(scanConfigField, Priority.ALWAYS); // Allow this field to expand horizontally

        // Options Config Field (TextArea, yellow background, multi-line)
        optionsConfigField = new TextArea();
        optionsConfigField.setStyle("-fx-control-inner-background: #FFDA33;");
        optionsConfigField.setPrefRowCount(3);
        optionsConfigField.setWrapText(true);
        optionsConfigField.setPrefHeight(60);
        HBox.setHgrow(optionsConfigField, Priority.ALWAYS); // Allow this field to expand horizontally

        // Home URL ID Field (Read-only, grey background)
        homeUrlIdField = new TextField();
        homeUrlIdField.setPromptText("Home URL ID");
        homeUrlIdField.setEditable(false);
        homeUrlIdField.setStyle("-fx-control-inner-background: #D3D3D3;");
        homeUrlIdField.setPrefWidth(80);
        homeUrlIdField.setPrefHeight(28);

        // Home URL Value Field (Editable, yellow background)
        homeUrlValueField = new TextField();
        homeUrlValueField.setPromptText("Home URL");
        homeUrlValueField.setStyle("-fx-control-inner-background: #FFDA33;");
        homeUrlValueField.setPrefHeight(28);
        HBox.setHgrow(homeUrlValueField, Priority.ALWAYS); // Allow this field to expand horizontally

        // --- 3. Initialize Buttons ---
        insertORGButton = new Button("Insert");
        updateORGButton = new Button("Update");
        deleteORGButton = new Button("Delete");
        templateORGButton = new Button("Template");

        insertURLButton = new Button("Insert URL");
        updateURLButton = new Button("Update URL");
        deleteURLButton = new Button("Delete URL");

        // --- 4. Layout for Organization Details Section ---
        // Create VBoxes for each label-field pair for consistent vertical alignment
        VBox idGroup = new VBox(2, idLabel, idField);
        idGroup.setAlignment(Pos.TOP_LEFT); // Align label and field to top left
        VBox nameGroup = new VBox(2, nameLabel, nameField);
        nameGroup.setAlignment(Pos.TOP_LEFT);
        VBox jobsGroup = new VBox(2, jobsLabel, jobsField);
        jobsGroup.setAlignment(Pos.TOP_LEFT);
        VBox urlGroup = new VBox(2, urlLabel, urlField); // URL group for the top row
        urlGroup.setAlignment(Pos.TOP_LEFT);

        VBox priorityGroup = new VBox(2, priorityLabel, priorityField);
        priorityGroup.setAlignment(Pos.TOP_LEFT);
        VBox searchConfigGroup = new VBox(2, searchConfigLabel, scanConfigField);
        searchConfigGroup.setAlignment(Pos.TOP_LEFT);
        VBox optionsConfigGroup = new VBox(2, optionsConfigLabel, optionsConfigField);
        optionsConfigGroup.setAlignment(Pos.TOP_LEFT);

        // HBox for the first row of fields (ID, Name, Total Jobs, URL)
        HBox topFieldsRow = new HBox(15, idGroup, nameGroup, jobsGroup, urlGroup);
        topFieldsRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(urlGroup, Priority.ALWAYS); // Ensure URL group expands

        // HBox for the second row of fields (Priority, Scan Config, WebDriver Options)
        HBox middleFieldsRow = new HBox(15, priorityGroup, searchConfigGroup, optionsConfigGroup);
        middleFieldsRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(priorityGroup, Priority.ALWAYS);
        HBox.setHgrow(searchConfigGroup, Priority.ALWAYS);
        HBox.setHgrow(optionsConfigGroup, Priority.ALWAYS);

        // HBox for the Organization buttons
        HBox orgButtonsBox = new HBox(10, insertORGButton, updateORGButton, deleteORGButton, templateORGButton);
        orgButtonsBox.setAlignment(Pos.CENTER_RIGHT);
        orgButtonsBox.setPadding(new Insets(0, 0, 2, 0)); // Padding below buttons before the grid

        // VBox to hold the section title, field rows, and buttons
        VBox orgDetailsContainer = new VBox(10); // Spacing between elements within this section
        orgDetailsContainer.setPadding(new Insets(15, 20, 2, 20)); // Padding around the container
        orgDetailsContainer.setStyle(
                "-fx-background-color: #f5f5f5; -fx-border-color: #ccc; -fx-border-width: 1px; -fx-border-radius: 5px;"); // Subtle background and border

        // Organizations Label at the very top of this section
        organizationsLabel.setStyle(
                "-fx-font-size: 1.2em; -fx-font-weight: bold; -fx-text-fill: #1565C0; -fx-padding: 0 0 10 0;");
        organizationsLabel.setMaxWidth(Double.MAX_VALUE);
        organizationsLabel.setAlignment(Pos.CENTER);

        // --- 5. TableView for Organizations ---
        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); // Columns fill available width

        // Define columns for Organizations Table
        TableColumn<DatabaseUserDTO, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setPrefWidth(50);
        idColumn.setMinWidth(30);
        idColumn.setMaxWidth(80); // Fixed width

        TableColumn<DatabaseUserDTO, String> jobColumn = new TableColumn<>("Jobs");
        jobColumn.setCellValueFactory(new PropertyValueFactory<>("jobs"));
        jobColumn.setPrefWidth(50);
        jobColumn.setMinWidth(30);
        jobColumn.setMaxWidth(80); // Fixed width

        TableColumn<DatabaseUserDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<DatabaseUserDTO, String> urlColumn = new TableColumn<>("Url");
        urlColumn.setCellValueFactory(new PropertyValueFactory<>("url"));

        // Removed table columns for priority, search config, options config as they are now in the detail section
        // TableColumn<DatabaseUserDTO, String> priorityColumn = new TableColumn<>("Priority Identifier");
        // priorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));
        // TableColumn<DatabaseUserDTO, String> searchConfigColumn = new TableColumn<>("Search Config");
        // searchConfigColumn.setCellValueFactory(new PropertyValueFactory<>("searchConfig"));
        // TableColumn<DatabaseUserDTO, String> optionsConfigColumn = new TableColumn<>("WebDriver Options");
        // optionsConfigColumn.setCellValueFactory(new PropertyValueFactory<>("optionsConfig"));

        tableView.getColumns().addAll(idColumn, jobColumn, nameColumn, urlColumn);
        tableView.setItems(performDataBase.getDatabaseList()); // Set data to the table

        orgDetailsContainer
                .getChildren()
                .addAll(
                        organizationsLabel,
                        topFieldsRow,
                        middleFieldsRow,
                        orgButtonsBox,
                        tableView
                );

        // --- 6. Home URL Details and Table (Environments Section) ---
        // HBox for Home URL fields and buttons
        VBox homeUrlDetailsContainer = new VBox(10); // Use VBox to stack label and then fields/buttons
        homeUrlDetailsContainer.setPadding(new Insets(10, 20, 2, 20)); // Padding
        homeUrlDetailsContainer.setStyle(
                "-fx-background-color: #f5f5f5; -fx-border-color: #ccc; -fx-border-width: 1px; -fx-border-radius: 5px;");

        // Environments Label moved inside this container, above the fields
        urlEnviromentLabel.setStyle(
                "-fx-font-size: 1.2em; -fx-font-weight: bold; -fx-text-fill: #1565C0; -fx-padding: 0 0 10 0;");
        urlEnviromentLabel.setMaxWidth(Double.MAX_VALUE);
        urlEnviromentLabel.setAlignment(Pos.CENTER);

        HBox fieldBox = new HBox(10, homeUrlIdField, homeUrlValueField); // Fields
        fieldBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(homeUrlValueField, Priority.ALWAYS); // Allow value field to take available space

        HBox buttonBox = new HBox(10, insertURLButton, updateURLButton, deleteURLButton); // Buttons
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // TableView for Home URLs
        tableViewHomeUrl = new TableView<>();
        tableViewHomeUrl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Define columns for Home URL Table
        TableColumn<HomeUrlDTO, Integer> homeUrlIdColumn = new TableColumn<>("ID");
        homeUrlIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        homeUrlIdColumn.setPrefWidth(50);
        homeUrlIdColumn.setMinWidth(30);
        homeUrlIdColumn.setMaxWidth(80); // Fixed width

        TableColumn<HomeUrlDTO, String> homeUrlColumn = new TableColumn<>("URL");
        homeUrlColumn.setCellValueFactory(new PropertyValueFactory<>("url"));
        homeUrlColumn.setMaxWidth(Double.MAX_VALUE); // Allow URL column to grow

        tableViewHomeUrl.getColumns().addAll(homeUrlIdColumn, homeUrlColumn);
        List<HomeUrlDTO> urls = performDataBase.loadAllHomeURL();
        tableViewHomeUrl.setItems(FXCollections.observableArrayList(urls));

        homeUrlDetailsContainer.getChildren().addAll(
                urlEnviromentLabel, // Add the label first
                fieldBox,
                buttonBox,
                tableViewHomeUrl
        );

        // --- 7. Main Layout VBox ---
        // This VBox will hold all major sections
        VBox rootVBox = new VBox(
                20, // Spacing between major sections (e.g., Org Details and Org Table)
                orgDetailsContainer,
                homeUrlDetailsContainer);
        rootVBox.setAlignment(Pos.TOP_CENTER); // Align content to top center of the window
        rootVBox.setPadding(new Insets(15)); // Overall padding around the entire content

        // Set vertical grow priority for tables to fill available space
        VBox.setVgrow(tableView, Priority.ALWAYS);
        VBox.setVgrow(tableViewHomeUrl, Priority.ALWAYS);

        // --- 8. Final Setup of the Root Pane (this class) ---
        // Add the rootVBox to the AnchorPane (this instance) and anchor it to all sides
        mainPane = new AnchorPane(rootVBox);
        AnchorPane.setTopAnchor(rootVBox, 0.0);
        AnchorPane.setBottomAnchor(rootVBox, 0.0);
        AnchorPane.setLeftAnchor(rootVBox, 0.0);
        AnchorPane.setRightAnchor(rootVBox, 0.0);

        // --- 9. Styling for Section Labels (all styling is now applied where labels are added to their containers) ---
        // No global styling for labels needed here anymore as they are styled when added to their specific containers.
    }

    private void updateHomeBankList(List<DatabaseUserDTO> databaseList) {
        homeBankingList.clear();

        for (DatabaseUserDTO user : databaseList) {
            HomeBankingLoadDTO homeBanking = new HomeBankingLoadDTO();

            homeBanking.setId(user.getId() != null ? Integer.parseInt(user.getId()) : null);
            homeBanking.setUrl(user.getUrl() != null ? user.getUrl() : null);
            homeBanking.setName(user.getName() != null ? user.getName() : null);
            homeBanking.setPriority(user.getPriority() != null ? user.getPriority() : null);
            homeBanking.setSearchConfig(user.getSearchConfig() != null ? user.getSearchConfig() : null);
            homeBanking.setOptionsConfig(user.getOptionsConfig() != null ? user.getOptionsConfig() : null);
            homeBanking.setUsername(user.getUsername() != null ? user.getUsername() : null);
            homeBanking.setPassword(user.getPassword() != null ? user.getPassword() : null);

            homeBankingList.add(homeBanking);
        }
    }

    public void updateTableBankingView() {
        performDataBase.loadAllHomeBankingBotJob();
        updateHomeBankList(performDataBase.getDatabaseList());
        tableView.setItems(performDataBase.getDatabaseList());
    }

    @Override
    public void initUIBehaviour() {
        insertORGButton.setOnAction(event -> {
            DatabaseUserDTO user = new DatabaseUserDTO(
                    null,
                    nameField.getText().trim(),
                    urlField.getText().trim(),
                    priorityField.getText(),
                    scanConfigField.getText(),
                    optionsConfigField.getText());

            if (Strings.isNullOrEmpty(priorityField.getText().trim())) {
                user.setPriority(fillUpTemplatePriority());
            }

            if (Strings.isNullOrEmpty(scanConfigField.getText().trim())) {
                user.setSearchConfig(fillUpTemplateScanConfig());
            }

            if (Strings.isNullOrEmpty(optionsConfigField.getText().trim())) {
                user.setOptionsConfig(fillUpTemplateWebDriver());
            }

            if (nameExists(nameField.getText().trim())) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Error",
                        "Env Name Already Exist",
                        String.format("This '%s' cannot be inserted with same name.\n", nameField.getText()));
                return;
            }

            if (nameField.getText().trim().isEmpty()
                    || urlField.getText().trim().isEmpty()) {
                showAlert(
                        Alert.AlertType.ERROR, "Error", "Name and URL Cannot be Empty", "Name and URL Cannot be Empty");
                return;
            }

            int newHomeId = saveUserData(user);

            if (newHomeId > -1) {
                try (Connection conn = performDataBase.getConnection()) {
                    int newHomeUrlId = performDataBase.loadNexHomeUrlData() + 1;

                    ErrorMessage errorMessage =
                            performDataBase.insertHomeUrlChild(conn, newHomeId, user.getUrl(), newHomeUrlId);

                    if (errorMessage != null) {
                        performMessage.errorMessage(
                                "Clone Bot Job Failed",
                                errorMessage.getErrorTitle(),
                                errorMessage.getErrorHeader(),
                                "Verify  [INSERT] or [UPDATE] or [SELECT]",
                                null,
                                0);
                    } else {
                        performMessage.showCustomModalDialogDragWin11(
                                "New Environment Created Successfully",
                                "<span style='color: #388E3C; font-weight: bold; font-size: 1.1em;'>The test environment has been successfully created for the organization.</span>",
                                "<span style='font-weight: bold; color: #1976D2;'>Organization: " + user.getName()
                                        + "</span>",
                                "<span style='color: #0288D1; font-weight: bold;'>This environment is now ready for testing and further configuration.</span>",
                                "<span style='font-style: italic; color: #1976D2;'>Environment URL: " + user.getUrl()
                                        + "</span>",
                                false,
                                "OK",
                                null,
                                0);
                    }

                } catch (Exception error) {
                    ARLogger.getInstance(PerformDataBase.class).severe("getConnection Error: " + error.getMessage());
                    String database = performDataBase.POSTGRES_DB ? "Postgress" : "Access";
                    performMessage.errorMessage(
                            "Database connection Failed",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the Database connection.</span>",
                            "<span style='font-weight: bold;'>" + database + "</span>.",
                            "<span style='color: #E65100; font-weight: bold;'>Please ensure the Database connections are correct.</span>",
                            "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                            0);
                }
            }

            performDataBase.loadAllHomeBankingBotJob();
            updateHomeBankList(performDataBase.getDatabaseList());
        });

        updateORGButton.setOnAction(event -> {
            String id = idField.getText();
            DatabaseUserDTO user = new DatabaseUserDTO(
                    id,
                    nameField.getText(),
                    urlField.getText(),
                    priorityField.getText(),
                    scanConfigField.getText(),
                    optionsConfigField.getText());
            updateUserData(id, user);
            performDataBase.loadAllHomeBankingBotJob();
            updateHomeBankList(performDataBase.getDatabaseList());
        });
        deleteORGButton.setOnAction(event -> {
            String id = idField.getText();
            if (Integer.parseInt(jobsField.getText()) > 0) {
                performMessage.errorMessage(
                        "Attempt to Delete",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The organization cannot be deleted:</span>",
                        "<span style='font-weight: bold;'>" + nameField.getText() + "</span>.",
                        "<span style='color: #E65100; font-weight: bold;'>Please delete the bot job(s) attached to it first.</span>",
                        "<span style='font-style: italic;'>Details: Total bot jobs attached: " + jobsField.getText()
                                + "</span>",
                        0);
                return;
            }

            ARConstants.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                    "Delete Confirmation",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Are you sure you want to delete this organization?</span>",
                    "<span style='font-weight: bold;'>" + nameField.getText() + "</span>",
                    null,
                    null,
                    false,
                    "Continue",
                    "Cancel",
                    0);

            if (respModal.equals(ARConstants.DialogModal.OK)) {
                deleteUserData(id);
                performDataBase.loadAllHomeBankingBotJob();
                updateHomeBankList(performDataBase.getDatabaseList());
            }
        });
        templateORGButton.setOnAction(event -> {
            StringBuilder priorities = new StringBuilder();
            priorities.append("#numero priorità, categoria, identificativo" + System.lineSeparator());
            priorities.append("1,xpath,currentXPath" + System.lineSeparator());
            priorities.append("2,attributeID,attributeID" + System.lineSeparator());
            priorities.append("3,attributeName,attributeName" + System.lineSeparator());
            priorities.append("4,searchAttribute,searchAttribute" + System.lineSeparator());
            priorities.append("5,coordinates,coordinates" + System.lineSeparator());
            priorities.append("6,attribute,test-id" + System.lineSeparator());
            //            priorMissing.append("7,attributes,allAttributes" + System.lineSeparator());
            priorityField.setText(priorities.toString());

            StringBuilder searchCriteria = new StringBuilder();
            //            searchCriteria.append("#numero priorità, categoria, criterioricerca" +
            // System.lineSeparator());
            searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
            //            searchCriteria.append(
            //                    "2,ByChained,By.tagName:input,By.className:mat-mdc-input-element" +
            // System.lineSeparator());
            //            searchCriteria.append(
            //                    "3,ByChained,By.xpath://*[contains(@idCOMMA \"mat-input\")]" +
            // System.lineSeparator());
            //            searchCriteria.append("4,ByTagName,input" + System.lineSeparator());
            //            searchCriteria.append("5,ByTagName,button" + System.lineSeparator());
            //            searchCriteria.append("6,ByChained,By.cssSelector:[id^=\"mat-input\"]" +
            // System.lineSeparator());

            //            searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
            //            searchCriteria.append("2,ByChained,By.tagName:input" + System.lineSeparator());
            //            searchCriteria.append("3,ByChained,By.tagName:button" + System.lineSeparator());
            //            searchCriteria.append("4,ByTagName,button,label,a" + System.lineSeparator());
            //            searchCriteria.append("5,ByTagName,input" + System.lineSeparator());
            scanConfigField.setText(searchCriteria.toString());

            // Proxy Example
            String argument1 = "arg:-disable-web-security";
            String argument2 = "arg:-disable-site-isolation-trials";
            String argument3 = "arg:-allow-running-insecure-content";
            String argument4 = "arg:-disable-features=IsolateOrigins,site-per-process";
            String argument5 = "arg:-disable-infobars";
            String argument6 = "#arg:-disable-dev-shm-usage";
            String proxyAddress = "#proxy:proxy_address:proxy_port";
            //            String browserLog = "#browser_log:active";
            //            String systemProps1 = "#systemProps:webdriver.chrome.logfile:logFolder";
            //            String systemProps2 = "#systemProps:webdriver.chrome.verboseLogging:true";
            StringBuilder optionsConfig = new StringBuilder();
            optionsConfig.append(argument1 + System.lineSeparator());
            optionsConfig.append(argument2 + System.lineSeparator());
            optionsConfig.append(argument3 + System.lineSeparator());
            optionsConfig.append(argument4 + System.lineSeparator());
            optionsConfig.append(argument5 + System.lineSeparator());
            optionsConfig.append(argument6 + System.lineSeparator());
            optionsConfig.append(proxyAddress + System.lineSeparator());
            //            optionsConfig.append(browserLog + System.lineSeparator());
            //            optionsConfig.append(systemProps1 + System.lineSeparator());
            //            optionsConfig.append(systemProps2 + System.lineSeparator());

            optionsConfigField.setText(optionsConfig.toString());

            //            loadAllHomeBankingBotJob();
            //            updateHomeBankList(databaseList);
        });

        insertURLButton.setOnAction(event -> {
            String homeBankIdStr = idField.getText().trim();
            String homeUrl = homeUrlValueField.getText().trim();

            // Check if fields are empty
            if (homeBankIdStr.isEmpty() || homeUrl.isEmpty()) {
                performMessage.errorMessage(
                        "Insert Home URL Failed",
                        "Missing Fields",
                        "You must select an Organization and fill the Home URL field.",
                        null,
                        null,
                        0);

                return;
            }

            try {
                int homeBankId = Integer.parseInt(homeBankIdStr);

                ErrorMessage errorMessage = performDataBase.insertNewHomeUrl(homeBankId, homeUrl);
                if (errorMessage != null) {
                    performMessage.errorMessage(
                            "Insert Home URL Failed",
                            errorMessage.getErrorTitle(),
                            errorMessage.getErrorHeader(),
                            errorMessage.getErrorMessage(),
                            null,
                            0);
                } else {
                    // ✅ Reload the table after successful insert
                    List<HomeUrlDTO> urls = performDataBase.loadAllHomeURLByHomeId(homeBankId);
                    tableViewHomeUrl.setItems(FXCollections.observableArrayList(urls));

                    homeUrlIdField.clear();
                    homeUrlValueField.clear();
                }

            } catch (SQLException e) {
                performMessage.errorMessage(
                        "Insert Home URL Failed",
                        "Database Error",
                        e.getMessage(),
                        "Verify [INSERT] or [UPDATE] or [SELECT]",
                        null,
                        0);
            }
        });

        updateURLButton.setOnAction(event -> {
            String homeBankIdStr = idField.getText().trim();
            String homeUrlIdStr = homeUrlIdField.getText().trim();
            String homeUrl = homeUrlValueField.getText().trim();

            // Validate fields
            if (homeBankIdStr.isEmpty() || homeUrl.isEmpty() || homeUrlIdStr.isEmpty()) {
                performMessage.errorMessage(
                        "Update Home URL Failed",
                        "Missing Fields",
                        "You must select an Organization and a Home URL row, and fill in the URL.",
                        null,
                        null,
                        0);

                return;
            }

            try {
                int homeBankId = Integer.parseInt(homeBankIdStr);
                int homeUrlId = Integer.parseInt(homeUrlIdStr);

                ErrorMessage errorMessage = performDataBase.updateHomeUrl(homeUrlId, homeBankId, homeUrl);
                if (errorMessage != null) {
                    performMessage.errorMessage(
                            "Update Home URL Failed",
                            errorMessage.getErrorTitle(),
                            errorMessage.getErrorHeader(),
                            errorMessage.getErrorMessage(),
                            null,
                            0);
                } else {
                    // ✅ Reload the table after successful update
                    List<HomeUrlDTO> urls = performDataBase.loadAllHomeURLByHomeId(homeBankId);
                    tableViewHomeUrl.setItems(FXCollections.observableArrayList(urls));

                    // Optionally clear the fields
                    homeUrlIdField.clear();
                    homeUrlValueField.clear();
                }

            } catch (SQLException | NumberFormatException e) {
                performMessage.errorMessage(
                        "Update Home URL Failed",
                        "Database Error",
                        e.getMessage(),
                        "Verify [INSERT] or [UPDATE] or [SELECT]",
                        null,
                        0);
            }
        });

        deleteURLButton.setOnAction(event -> {
            String homeBankIdStr = idField.getText().trim();
            String homeUrlIdStr = homeUrlIdField.getText().trim();
            String homeUrl = homeUrlValueField.getText().trim();

            // Validate required fields
            if (homeBankIdStr.isEmpty() || homeUrlIdStr.isEmpty() || homeUrl.isEmpty()) {
                performMessage.errorMessage(
                        "Delete Home URL Failed",
                        "Missing Fields",
                        "You must select an Organization and a Home URL row.",
                        null,
                        null,
                        0);
                return;
            }

            // Show confirmation modal
            ARConstants.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                    "Delete Confirmation",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Are you sure you want to delete this URL?</span>",
                    "<span style='font-weight: bold;'>" + homeUrl + "</span>",
                    null,
                    null,
                    false,
                    "Continue",
                    "Cancel",
                    0);

            if (!respModal.equals(ARConstants.DialogModal.OK)) {
                return;
            }

            try {
                int homeBankId = Integer.parseInt(homeBankIdStr);
                int homeUrlId = Integer.parseInt(homeUrlIdStr);

                ErrorMessage errorMessage = performDataBase.deleteHomeUrl(homeUrlId);
                if (errorMessage != null) {
                    performMessage.errorMessage(
                            "Delete Home URL Failed",
                            errorMessage.getErrorTitle(),
                            errorMessage.getErrorHeader(),
                            errorMessage.getErrorMessage(),
                            null,
                            0);

                } else {
                    // ✅ Refresh list
                    List<HomeUrlDTO> urls = performDataBase.loadAllHomeURLByHomeId(homeBankId);
                    tableViewHomeUrl.setItems(FXCollections.observableArrayList(urls));

                    homeUrlIdField.clear();
                    homeUrlValueField.clear();
                }

            } catch (SQLException | NumberFormatException e) {
                performMessage.errorMessage(
                        "Delete Home URL Failed",
                        "Database Error",
                        e.getMessage(),
                        "Verify [DELETE] operation",
                        null,
                        0);
            }
        });

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Get the selected UserDTO object
                DatabaseUserDTO selectedUser = tableView.getSelectionModel().getSelectedItem();

                // Set the values of the selected row to the text fields
                idField.setText(selectedUser.getId());
                nameField.setText(selectedUser.getName());
                urlField.setText(selectedUser.getUrl());
                priorityField.setText(selectedUser.getPriority());
                jobsField.setText(selectedUser.getJobs()); // Update the hidden field
                scanConfigField.setText(selectedUser.getSearchConfig()); // Update the hidden field
                optionsConfigField.setText(selectedUser.getOptionsConfig()); // Update the hidden field
            } else {
                // If no row is selected, clear the text fields
                idField.clear();
                nameField.clear();
                urlField.clear();
                priorityField.clear();
                jobsField.clear();
                scanConfigField.clear();
                optionsConfigField.clear();
                // Clear other fields as needed
            }
        });

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                DatabaseUserDTO selectedUser = tableView.getSelectionModel().getSelectedItem();
                // ... populate form fields

                // Load URLs related to the selected home banking ID
                List<HomeUrlDTO> urls = performDataBase.loadAllHomeURLByHomeId(Integer.parseInt(selectedUser.getId()));
                tableViewHomeUrl.setItems(FXCollections.observableArrayList(urls));
            } else {
                tableViewHomeUrl.getItems().clear();
            }
        });

        tableViewHomeUrl.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                homeUrlIdField.setText(String.valueOf(newSelection.getId()));
                homeUrlValueField.setText(newSelection.getUrl());
            } else {
                homeUrlIdField.clear();
                homeUrlValueField.clear();
            }
        });
    }

    private String fillUpTemplatePriority() {
        StringBuilder priorities = new StringBuilder();
        priorities.append("#numero priorità, categoria, identificativo" + System.lineSeparator());
        priorities.append("1,xpath,currentXPath" + System.lineSeparator());
        priorities.append("2,attributeID,attributeID" + System.lineSeparator());
        priorities.append("3,attributeName,attributeName" + System.lineSeparator());
        priorities.append("4,searchAttribute,searchAttribute" + System.lineSeparator());
        priorities.append("5,coordinates,coordinates" + System.lineSeparator());
        priorities.append("6,attribute,test-id" + System.lineSeparator());
        //            priorMissing.append("7,attributes,allAttributes" + System.lineSeparator());
        Platform.runLater(() -> priorityField.setText(priorities.toString()));

        return priorities.toString();
    }

    private String fillUpTemplateScanConfig() {
        StringBuilder searchCriteria = new StringBuilder();
        //            searchCriteria.append("#numero priorità, categoria, criterioricerca" +
        // System.lineSeparator());
        searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
        //            searchCriteria.append(
        //                    "2,ByChained,By.tagName:input,By.className:mat-mdc-input-element" +
        // System.lineSeparator());
        //            searchCriteria.append(
        //                    "3,ByChained,By.xpath://*[contains(@idCOMMA \"mat-input\")]" +
        // System.lineSeparator());
        //            searchCriteria.append("4,ByTagName,input" + System.lineSeparator());
        //            searchCriteria.append("5,ByTagName,button" + System.lineSeparator());
        //            searchCriteria.append("6,ByChained,By.cssSelector:[id^=\"mat-input\"]" +
        // System.lineSeparator());

        //            searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
        //            searchCriteria.append("2,ByChained,By.tagName:input" + System.lineSeparator());
        //            searchCriteria.append("3,ByChained,By.tagName:button" + System.lineSeparator());
        //            searchCriteria.append("4,ByTagName,button,label,a" + System.lineSeparator());
        //            searchCriteria.append("5,ByTagName,input" + System.lineSeparator());
        Platform.runLater(() -> scanConfigField.setText(searchCriteria.toString()));

        return searchCriteria.toString();
    }

    private String fillUpTemplateWebDriver() {
        // Proxy Example
        String argument1 = "arg:-disable-web-security";
        String argument2 = "arg:-disable-site-isolation-trials";
        String argument3 = "arg:-allow-running-insecure-content";
        String argument4 = "arg:-disable-features=IsolateOrigins,site-per-process";
        String argument5 = "arg:-disable-infobars";
        String argument6 = "#arg:-disable-dev-shm-usage";
        String proxyAddress = "#proxy:proxy_address:proxy_port";
        //            String browserLog = "#browser_log:active";
        //            String systemProps1 = "#systemProps:webdriver.chrome.logfile:logFolder";
        //            String systemProps2 = "#systemProps:webdriver.chrome.verboseLogging:true";
        StringBuilder optionsConfig = new StringBuilder();
        optionsConfig.append(argument1 + System.lineSeparator());
        optionsConfig.append(argument2 + System.lineSeparator());
        optionsConfig.append(argument3 + System.lineSeparator());
        optionsConfig.append(argument4 + System.lineSeparator());
        optionsConfig.append(argument5 + System.lineSeparator());
        optionsConfig.append(argument6 + System.lineSeparator());
        optionsConfig.append(proxyAddress + System.lineSeparator());
        //            optionsConfig.append(browserLog + System.lineSeparator());
        //            optionsConfig.append(systemProps1 + System.lineSeparator());
        //            optionsConfig.append(systemProps2 + System.lineSeparator());

        Platform.runLater(() -> optionsConfigField.setText(optionsConfig.toString()));

        return optionsConfig.toString();
    }

    private void updateFields() {}

    private void clearFields() {}

    private boolean showConfirmationDialog(String name) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation Dialog");
        alert.setHeaderText("Delete Confirmation");
        alert.setContentText("Are you sure you want to delete the record for '" + name + "'?");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private boolean nameExists(String name) {
        for (DatabaseUserDTO dto : performDataBase.getDatabaseList()) {
            if (dto.getName().trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private List<BankingDTO> createSampleData() {
        List<BankingDTO> dataList = new ArrayList<>();
        // Assuming you have a constructor in BankingDT0
        for (int i = 0; i < 10; i++) {
            dataList.add(new BankingDTO(i + 1, "Name " + i, "URL " + i, "Priority " + i, 5, new ArrayList<>()));
        }
        return dataList;
    }

    private Integer loadNexIdData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM home_banking";
        try (Statement stmt = performDataBase.getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private int saveUserData(DatabaseUserDTO user) {
        // Generate a Unique-ID
        Integer hashCode = loadNexIdData() + 1;
        //        AlterSeq(hashCode);
        //        Integer hashCode = generateID();
        String priority = Strings.isNullOrEmpty(user.getPriority()) ? "" : user.getPriority();
        String searchConfig = Strings.isNullOrEmpty(user.getSearchConfig()) ? "" : user.getSearchConfig();
        String optionsConfig = Strings.isNullOrEmpty(user.getSearchConfig()) ? "" : user.getOptionsConfig();

        String insertSQL =
                "INSERT INTO home_banking (ID, Name, Url, priority, search_config, options_config, username, password) VALUES ( "
                        + hashCode + ","
                        + "'" + user.getName() + "', "
                        + "'" + user.getUrl() + "', "
                        + "'" + priority + "', "
                        + "'" + searchConfig + "', "
                        + "'" + optionsConfig + "', "
                        + "'" + user.getUsername() + "', "
                        + "'" + user.getPassword() + "')";
        try (Statement stmt = performDataBase.getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            System.out.println("Data saved successfully.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return -1;
        }
        return hashCode;
    }

    private void updateUserData(String id, DatabaseUserDTO user) {

        try (Connection conn = performDataBase.getConnection()) {

            int userId = Integer.parseInt(id);

            // Replace newlines with "£" and handle null values
            String priority = Strings.isNullOrEmpty(user.getPriority())
                    ? ""
                    : user.getPriority().replace("\n", "£");
            String searchConfig = Strings.isNullOrEmpty(user.getSearchConfig())
                    ? ""
                    : user.getSearchConfig().replace("\n", "£");
            String optionsConfig = Strings.isNullOrEmpty(user.getOptionsConfig())
                    ? ""
                    : user.getOptionsConfig().replace("\n", "£");

            // Use placeholders (?) to prevent SQL injection
            String updateSQL =
                    "UPDATE home_banking SET Name = ?, Url = ?, Priority = ?, search_config = ?, options_config = ? WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(updateSQL)) {

                // Set parameters in the prepared statement
                stmt.setString(1, user.getName());
                stmt.setString(2, user.getUrl());
                stmt.setString(3, priority);
                stmt.setString(4, searchConfig);
                stmt.setString(5, optionsConfig);
                stmt.setInt(6, userId);

                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected > 0) {
                    //                    showAlert(Alert.AlertType.INFORMATION, "Success", "Updated", "Data updated
                    // successfully.");
                } else {
                    performMessage.errorMessage(
                            "Error",
                            "Id Not Found",
                            String.format("No matching record found to update Id: %d", userId),
                            null,
                            null,
                            0);
                }
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());

            performMessage.errorMessage("Error", "MAX CHARACTERS LIMIT FOR ACCESS", null, null, null, 0);
            //            showAlert(Alert.AlertType.ERROR, "Error", "MAX CHARACTERS LIMIT FOR ACCESS", null);
        }
    }

    private void deleteUserData(String Id) {
        try {
            int homeBankId = Integer.parseInt(Id);

            String deleteHomeUrlSQL = "DELETE FROM home_url WHERE home_banking_id = ?";
            String deleteHomeBankingSQL = "DELETE FROM home_banking WHERE id = ?";

            try (Connection conn = performDataBase.getConnection()) {
                // Optional: wrap in a transaction
                conn.setAutoCommit(false);

                try (PreparedStatement deleteHomeUrlStmt = conn.prepareStatement(deleteHomeUrlSQL);
                        PreparedStatement deleteHomeBankingStmt = conn.prepareStatement(deleteHomeBankingSQL)) {
                    deleteHomeUrlStmt.setInt(1, homeBankId);
                    deleteHomeBankingStmt.setInt(1, homeBankId);

                    int urlRows = deleteHomeUrlStmt.executeUpdate();
                    int bankRows = deleteHomeBankingStmt.executeUpdate();

                    conn.commit();

                    ARLogger.getInstance(Thread.class)
                            .finer("Deleted " + urlRows + " rows from home_url and " + bankRows
                                    + " rows from home_banking for ID: " + Id);
                } catch (SQLException error) {
                    conn.rollback(); // rollback if either delete fails
                    ARLogger.getInstance(Thread.class).finer("Error Deleting: " + error.getMessage());
                } finally {
                    conn.setAutoCommit(true); // restore auto-commit mode
                }
            } catch (SQLException connError) {
                ARLogger.getInstance(Thread.class).finer("Database connection error: " + connError.getMessage());
            }
        } catch (NumberFormatException error) {
            ARLogger.getInstance(Thread.class).finer("Invalid Format ID: " + Id);
        }
    }

    @Override
    public void clearPane(Pane panel) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Validates a URL using a regular expression and checks for a valid protocol.
     *
     * @param urlStr The URL string to validate.
     * @return true if the URL is valid, false otherwise.
     */
    public static boolean isValidUrl(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            return false;
        }

        String trimmedUrl = urlStr.trim();
        // Check for basic syntax using regex
        Matcher matcher = URL_PATTERN.matcher(trimmedUrl);
        if (!matcher.matches()) {
            return false;
        }

        // Further check using java.net.URL for protocol and general validity
        try {
            URL url = new URL(trimmedUrl);
            // Check if the protocol is valid.
            String protocol = url.getProtocol();
            if (protocol == null
                    || (!protocol.equals("http")
                            && !protocol.equals("https")
                            && !protocol.equals("ftp")
                            && !protocol.equals("file"))) {
                return false;
            }
            return true;
        } catch (MalformedURLException e) {
            return false; // Invalid URL
        }
    }
}
