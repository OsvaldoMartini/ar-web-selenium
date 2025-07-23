package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BankingDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
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

    private Button submitButton;
    private Button updateButton;
    private Button deleteButton;
    private Button templateButton;

    // Create labels
    private Label idLabel;
    private Label nameLabel;
    private Label urlLabel;
    private Label priorityLabel;
    private Label jobsLabel;
    private Label searchConfigLabel;
    private Label optionsConfigLabel;

    private TextField idField;
    private TextField nameField;
    private TextField urlField;
    private TextArea priorityField;
    private TextField jobsField;
    private TextArea searchConfigField;
    private TextArea optionsConfigField;

    // Regular expression for a basic URL validation (improved)
    private static final String URL_REGEX =
            "^((https?|ftp|file)://)?([\\da-z.-]+)\\.([a-z.]{2,6})(:\\d+)?(/\\w .-]*)?/?$";

    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE);

    private Connection conn = null;
    private TableView<DatabaseUserDTO> tableView = new TableView<>();

    private List<BankingDTO> dtoList;

    private Pane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        performDataBase.loadAllHomeBankingBotJob();
        updateHomeBankList(performDataBase.getDatabaseList());

        // Create labels
        idLabel = new Label("ID:");
        nameLabel = new Label("Name:");
        urlLabel = new Label("Url:");
        priorityLabel = new Label("Priority:");
        jobsLabel = new Label("Total of Jobs:");
        searchConfigLabel = new Label("Scan Config:");
        optionsConfigLabel = new Label("WebDriver Options:");
        // Create text fields
        idField = new TextField();
        idField.setEditable(false);
        idField.setStyle("-fx-control-inner-background: D3D3D3; -fx-pref-width: 50px;");
        //        idField.setPrefWidth(200); // Set the preferred width
        idField.setPrefHeight(30);
        nameField = new TextField();
        nameField.setStyle("-fx-control-inner-background: FFDA33;");
        nameField.requestFocus();
        urlField = new TextField();
        urlField.setStyle("-fx-control-inner-background: FFDA33;");
        priorityField = new TextArea();
        priorityField.setStyle("-fx-control-inner-background: FFDA33;");
        jobsField = new TextField(); // Hidden field
        jobsField.setEditable(false);
        jobsField.setStyle("-fx-control-inner-background: D3D3D3;");
        jobsField.setPrefWidth(50); // Set the preferred width
        jobsField.setPrefHeight(30);
        priorityField.setPrefRowCount(3); // Set preferred row count for the TextArea

        searchConfigField = new TextArea();
        searchConfigField.setPrefRowCount(3); // Set preferred row count for the TextArea
        searchConfigField.setStyle("-fx-control-inner-background: FFDA33;");

        optionsConfigField = new TextArea();
        optionsConfigField.setPrefRowCount(3); // Set preferred row count for the TextArea
        optionsConfigField.setStyle("-fx-control-inner-background: FFDA33;");

        // Create submit button
        submitButton = new Button("Insert");

        // Create update button
        updateButton = new Button("Update");
        deleteButton = new Button("Delete");
        templateButton = new Button("Template");

        // Create layout and add components
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10); // Horizontal gap between columns
        gridPane.setVgap(10); // Vertical gap between rows
        gridPane.setPadding(new Insets(10)); // Padding around the gridPane

        // Add components to the grid
        gridPane.add(idLabel, 0, 0);
        gridPane.add(idField, 1, 0);

        gridPane.add(nameLabel, 0, 1);
        gridPane.add(nameField, 1, 1);

        gridPane.add(urlLabel, 0, 2);
        gridPane.add(urlField, 1, 2);

        gridPane.add(priorityLabel, 0, 3);
        gridPane.add(priorityField, 1, 3, 1, 2); // Span the TextArea over 2 rows

        gridPane.add(jobsLabel, 0, 5);
        gridPane.add(jobsField, 1, 5);

        gridPane.add(searchConfigLabel, 0, 6);
        gridPane.add(searchConfigField, 1, 6);

        gridPane.add(optionsConfigLabel, 0, 7);
        gridPane.add(optionsConfigField, 1, 7);

        HBox buttonsBox = new HBox(10, submitButton, updateButton, deleteButton, templateButton);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.setSpacing(10); // Horizontal spacing between buttons

        gridPane.add(buttonsBox, 0, 8, 2, 1);

        HBox hBoxGridPane = new HBox(gridPane);
        hBoxGridPane.setAlignment(Pos.CENTER);
        hBoxGridPane.setSpacing(10); // Horizontal spacing around the gridPane

        // Configure TableView
        tableView = new TableView<>();
        TableColumn<DatabaseUserDTO, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<DatabaseUserDTO, String> jobColumn = new TableColumn<>("Jobs");
        jobColumn.setCellValueFactory(new PropertyValueFactory<>("jobs"));

        TableColumn<DatabaseUserDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<DatabaseUserDTO, String> urlColumn = new TableColumn<>("Url");
        urlColumn.setCellValueFactory(new PropertyValueFactory<>("url"));

        TableColumn<DatabaseUserDTO, String> priorityColumn = new TableColumn<>("Priority Identifier");
        priorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));

        TableColumn<DatabaseUserDTO, String> searchConfigColumn = new TableColumn<>("Search Config");
        searchConfigColumn.setCellValueFactory(new PropertyValueFactory<>("searchConfig"));

        TableColumn<DatabaseUserDTO, String> optionsConfigColumn = new TableColumn<>("WebDriver Options");
        optionsConfigColumn.setCellValueFactory(new PropertyValueFactory<>("optionsConfig"));

        tableView
                .getColumns()
                .addAll(
                        idColumn,
                        jobColumn,
                        nameColumn,
                        urlColumn,
                        priorityColumn,
                        searchConfigColumn,
                        optionsConfigColumn);

        tableView.setItems(performDataBase.getDatabaseList());

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
                searchConfigField.setText(selectedUser.getSearchConfig()); // Update the hidden field
                optionsConfigField.setText(selectedUser.getOptionsConfig()); // Update the hidden field
            } else {
                // If no row is selected, clear the text fields
                idField.clear();
                nameField.clear();
                urlField.clear();
                priorityField.clear();
                jobsField.clear();
                searchConfigField.clear();
                optionsConfigField.clear();
                // Clear other fields as needed
            }
        });

        // Wrap the tableView in a VBox for more control
        VBox tableViewContainer = new VBox(tableView);
        tableViewContainer.setAlignment(Pos.BOTTOM_CENTER);
        tableViewContainer.setSpacing(10); // Spacing around the tableView
        tableViewContainer.setPadding(new Insets(10)); // Padding inside the tableView container

        // Create main layout
        VBox vbox = new VBox(20, hBoxGridPane, tableViewContainer);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(10)); // Padding around the VBox

        // Adjust VBox properties for better alignment
        VBox.setVgrow(tableViewContainer, Priority.ALWAYS);

        // Use AnchorPane to ensure the VBox resizes with the window
        //        AnchorPane mainPane = new AnchorPane(vbox);

        mainPane = new AnchorPane(vbox);
        AnchorPane.setTopAnchor(vbox, 0.0);
        AnchorPane.setBottomAnchor(vbox, 0.0);
        AnchorPane.setLeftAnchor(vbox, 0.0);
        AnchorPane.setRightAnchor(vbox, 0.0);
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
        submitButton.setOnAction(event -> {
            DatabaseUserDTO user = new DatabaseUserDTO(
                    null,
                    nameField.getText().trim(),
                    urlField.getText().trim(),
                    priorityField.getText(),
                    searchConfigField.getText(),
                    optionsConfigField.getText());

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

                        performMessage.errorMessage(
                                "Cannot Create New Environment ",
                                "Verify  [INSERT] or [UPDATE] or [SELECT]",
                                null,
                                null,
                                null,
                                0);
                    }

                } catch (SQLException error) {
                    System.out.println(error.getMessage());
                }
            }

            performDataBase.loadAllHomeBankingBotJob();
            updateHomeBankList(performDataBase.getDatabaseList());
        });

        updateButton.setOnAction(event -> {
            String id = idField.getText();
            DatabaseUserDTO user = new DatabaseUserDTO(
                    id,
                    nameField.getText(),
                    urlField.getText(),
                    priorityField.getText(),
                    searchConfigField.getText(),
                    optionsConfigField.getText());
            updateUserData(id, user);
            performDataBase.loadAllHomeBankingBotJob();
            updateHomeBankList(performDataBase.getDatabaseList());
        });
        deleteButton.setOnAction(event -> {
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
        templateButton.setOnAction(event -> {
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
            searchConfigField.setText(searchCriteria.toString());

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
            int honeBankingId = Integer.parseInt(Id);
            String deleteSQL = "DELETE FROM home_banking WHERE ID = " + honeBankingId;
            try (Statement stmt = performDataBase.getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(deleteSQL);
                if (rowsAffected > 0) {
                    ARLogger.getInstance(Thread.class).finer("Data deleted successfully.\n " + Id);
                } else {
                    ARLogger.getInstance(Thread.class).finer("Data NOT deleted successfully.\n " + Id);
                }
            } catch (SQLException e) {
                ARLogger.getInstance(Thread.class).finer("Error Deleting\n " + Id);
            }
        } catch (NumberFormatException e) {
            ARLogger.getInstance(Thread.class).finer("Invalid Format ID:\n " + Id);
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
