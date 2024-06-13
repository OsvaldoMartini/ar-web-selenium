package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.dto.BankingDTO;
import com.allinweb.ch.component.model.dto.JobDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.DatabaseUserDTO;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.persistence.JobUserDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

public class ABRNewHomeBankingPane extends ABRPane {

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";

    //    private static final String FILE_NAME =
    //            "D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\user_data.mdb";
    //    private static final String FILE_NAME2 =
    //            "D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\database.mdb";
    //    private static final String DB_URL_1 =
    //            "jdbc:ucanaccess:////D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\user_data.mdb";
    //    //    private static final String DB_URL_BANKING =
    //    //
    // "jdbc:ucanaccess://D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\database.mdb;memory=false;newDatabaseVersion=V2010";
    //    private static final String DB_URL_2 =
    //            "jdbc:ucanaccess://D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\database.mdb";

    private Connection conn = null;
    private ObservableList<DatabaseUserDTO> databaseList = FXCollections.observableArrayList();
    private ObservableList<JobUserDTO> jobUserList = FXCollections.observableArrayList();
    private TableView<DatabaseUserDTO> tableView = new TableView<>();

    private List<BankingDTO> dtoList;
    private int currentIndex = 0;

    private Pane mainPane;

    private boolean isNewState = false;

    public ABRNewHomeBankingPane() {
        super();
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {

        // Initialize database
        initializeDatabase();
        loadUserData();
        // Create labels
        Label idLabel = new Label("ID:");
        Label nameLabel = new Label("Name:");
        Label urlLabel = new Label("Url:");
        Label priorityLabel = new Label("Priority:");
        Label jobsLabel = new Label("Total of Jobs:");
        Label searchConfigLabel = new Label("Scan Config:");

        // Create text fields
        TextField idField = new TextField();
        idField.setEditable(false);
        idField.setStyle("-fx-control-inner-background: FFDA33; -fx-pref-width: 50px;");
        //        idField.setPrefWidth(200); // Set the preferred width
        idField.setPrefHeight(30);
        TextField nameField = new TextField();
        nameField.requestFocus();
        TextField urlField = new TextField();
        TextArea priorityField = new TextArea();
        TextField jobsField = new TextField(); // Hidden field
        jobsField.setEditable(false);
        jobsField.setStyle("-fx-control-inner-background: FFDA33;");
        jobsField.setPrefWidth(50); // Set the preferred width
        jobsField.setPrefHeight(30);
        priorityField.setPrefRowCount(3); // Set preferred row count for the TextArea

        TextArea searchConfigField = new TextArea();
        searchConfigField.setPrefRowCount(3); // Set preferred row count for the TextArea

        // Create submit button
        Button submitButton = new Button("Submit");
        submitButton.setOnAction(event -> {
            DatabaseUserDTO user = new DatabaseUserDTO(
                    null,
                    nameField.getText().trim(),
                    urlField.getText().trim(),
                    priorityField.getText(),
                    searchConfigField.getText());

            if (nameExists(nameField.getText().trim())) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Env Name Alread Existe",
                        String.format("This '%s' cannot be inserted with same name.\n", nameField.getText()));
                return;
            }

            if (nameField.getText().trim().isEmpty()
                    || urlField.getText().trim().isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Name and URL Cannot be Empty", "Name and URL Cannot be Empty");
                return;
            }

            saveUserData(user);
            loadUserData();
        });

        // Create update button
        Button updateButton = new Button("Update");
        updateButton.setOnAction(event -> {
            String id = idField.getText();
            DatabaseUserDTO user = new DatabaseUserDTO(
                    id, nameField.getText(), urlField.getText(), priorityField.getText(), searchConfigField.getText());
            updateUserData(id, user);
            loadUserData();
        });

        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(event -> {
            String id = idField.getText();
            if (Integer.parseInt(jobsField.getText()) > 0) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Action Remove Error",
                        String.format(
                                "This '%s' cannot be deleted.\nIt has %s Jobs",
                                nameField.getText(), jobsField.getText()));
                return;
            }

            deleteUserData(id);
            loadUserData();
        });

        Button templateButton = new Button("Template");
        templateButton.setOnAction(event -> {
            StringBuilder priorities = new StringBuilder();
            priorities.append("#numero priorità, categoria, identificativo" + System.lineSeparator());
            priorities.append("1,attribute,test-id" + System.lineSeparator());
            priorities.append("2,xpath,xpath" + System.lineSeparator());
            priorities.append("3,coordinates,coordinates" + System.lineSeparator());
            priorityField.setText(priorities.toString());

            StringBuilder searchCriteria = new StringBuilder();
            //            searchCriteria.append("#numero priorità, categoria, criterioricerca" +
            // System.lineSeparator());
            searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
            searchCriteria.append(
                    "2,ByChained,By.tagName:input,By.className:mat-mdc-input-element" + System.lineSeparator());
            searchCriteria.append(
                    "3,ByChained,By.xpath://*[contains(@idCOMMA \"mat-input\")]" + System.lineSeparator());
            searchCriteria.append("4,ByTagName,input" + System.lineSeparator());
            searchCriteria.append("5,ByTagName,button" + System.lineSeparator());
            searchCriteria.append("6,ByChained,By.cssSelector:[id^=\"mat-input\"]" + System.lineSeparator());

            //            searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
            //            searchCriteria.append("2,ByChained,By.tagName:input" + System.lineSeparator());
            //            searchCriteria.append("3,ByChained,By.tagName:button" + System.lineSeparator());
            //            searchCriteria.append("4,ByTagName,button,label,a" + System.lineSeparator());
            //            searchCriteria.append("5,ByTagName,input" + System.lineSeparator());
            searchConfigField.setText(searchCriteria.toString());
            loadUserData();
        });

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
        //        gridPane.add(priorityField, 1, 3);

        gridPane.add(jobsLabel, 0, 5);
        gridPane.add(jobsField, 1, 5);

        gridPane.add(searchConfigLabel, 0, 6);
        gridPane.add(searchConfigField, 1, 6, 1, 2); // Span the TextArea over 2 rows

        HBox buttonsBox = new HBox(10, submitButton, updateButton, deleteButton, templateButton);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.setSpacing(10); // Horizontal spacing between buttons

        gridPane.add(buttonsBox, 0, 8, 2, 1);

        //        gridPane.add(submitButton, 0, 5);
        //        gridPane.add(updateButton, 1, 5);
        //        gridPane.add(deleteButton, 2, 5);
        HBox hBoxGridPane = new HBox(gridPane);
        hBoxGridPane.setAlignment(Pos.CENTER);
        hBoxGridPane.setSpacing(10); // Horizontal spacing around the gridPane

        // Configure TableView
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

        tableView.getColumns().addAll(idColumn, jobColumn, nameColumn, urlColumn, priorityColumn, searchConfigColumn);
        tableView.setItems(databaseList);

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
            } else {
                // If no row is selected, clear the text fields
                idField.clear();
                nameField.clear();
                urlField.clear();
                priorityField.clear();
                jobsField.clear();
                searchConfigField.clear();
                // Clear other fields as needed
            }
        });

        // Create main layout
        //        VBox vbox = new VBox(gridPane, tableView);

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
        //        tableViewContainer.setPrefHeight(300); // Adjust the preferred height as needed
        tableViewContainer.setPrefWidth(950); // Adjust the preferred height as needed

        mainPane = new AnchorPane(vbox);
    }

    private List<BankingDTO> loadFromDB() {

        ABRSharedResources.getInstance().refreshEntity(null, HomeBankingDTO.class);

        List<BankingDTO> dtoList = new ArrayList<>();

        List<HomeBankingDTO> listHomeBankingDTO =
                ABRSharedResources.getInstance().getEntityList(HomeBankingDTO.class);

        // Iterate through the result set and populate the DTO list
        for (HomeBankingDTO homeBankingDTO : listHomeBankingDTO) {
            List<JobDTO> listJobsDto = new ArrayList<>();
            for (BotJobDTO botJobDTO : homeBankingDTO.getBotJobs()) {
                JobDTO jobsDto = new JobDTO(botJobDTO.getName(), botJobDTO.getDescription(), new ArrayList<>());
                listJobsDto.add(jobsDto);
            }

            dtoList.add(new BankingDTO(
                    homeBankingDTO.getId(),
                    homeBankingDTO.getName(),
                    homeBankingDTO.getUrl(),
                    homeBankingDTO.getPriority(),
                    listJobsDto.size(),
                    listJobsDto));
        }
        return dtoList;
    }

    @Override
    public void initUIBehaviour() {}

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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private boolean nameExists(String name) {
        for (DatabaseUserDTO dto : databaseList) {
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

    private void initializeDatabase() {

        String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
        String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;

        File dbFile = new File(dbPath + ABRConstants.FILE_NAME_DB);
        if (!dbFile.exists()) {
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                try (Statement stmt = conn.createStatement()) {
                    String createTableSQL = "CREATE TABLE home_banking (" + "ID AUTOINCREMENT PRIMARY KEY, "
                            + "name TEXT, password TEXT, url TEXT, username TEXT, priority TEXT)";
                    stmt.executeUpdate(createTableSQL);
                }
                System.out.println(String.format("Database %s has bee created!", dbFile.getName()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(String.format("Database %s Already exist!", dbFile.getName()));
        }
    }

    private Connection getConnection() {
        if (conn == null) {
            String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
            String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
            try {
                conn = DriverManager.getConnection(dbUrl);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return conn;
    }

    private void loadUserData() {
        databaseList.clear();
        String selectSQL = " SELECT ID, Name, Url, priority, COUNT(bot.ID) Jobs, searchConfig, username, password "
                + " FROM home_banking bank "
                + " left join bot_job bot on bot.home_banking_id = bank.id "
                + " group by bank.ID, bank.Name, bank.Url, bank.priority, bank.searchConfig, bank.username, bank.password ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String jobs = rs.getString("Jobs");
                String name = rs.getString("Name");
                String url = rs.getString("Url");
                String priority = rs.getString("Priority");
                String searchConfig = rs.getString("searchConfig");
                String username = rs.getString("username");
                String password = rs.getString("password");
                databaseList.add(new DatabaseUserDTO(id, jobs, name, url, priority, searchConfig, username, password));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private Integer loadNexIdData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM home_banking";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveUserData(DatabaseUserDTO user) {
        // Generate a Unique-ID
        Integer hashCode = loadNexIdData() + 1;
        //        AlterSeq(hashCode);
        //        Integer hashCode = generateID();
        String priority = Strings.isNullOrEmpty(user.getPriority()) ? "" : user.getPriority();
        String searchConfig = Strings.isNullOrEmpty(user.getSearchConfig()) ? "" : user.getSearchConfig();

        String insertSQL =
                "INSERT INTO home_banking (ID, Name, Url, priority, searchConfig, username, password) VALUES ( "
                        + hashCode + ","
                        + "'" + user.getName() + "', "
                        + "'" + user.getUrl() + "', "
                        + "'" + priority + "', "
                        + "'" + searchConfig + "', "
                        + "'" + user.getUsername() + "', "
                        + "'" + user.getPassword() + "')";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            System.out.println("Data saved successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateUserData(String id, DatabaseUserDTO user) {
        try {
            int userId = Integer.parseInt(id);

            String priority = Strings.isNullOrEmpty(user.getPriority()) ? "" : user.getPriority();
            String searchConfig = Strings.isNullOrEmpty(user.getSearchConfig()) ? "" : user.getSearchConfig();
            String updateSQL = "UPDATE home_banking SET Name = '" + user.getName() + "', "
                    + " Url = '" + user.getUrl() + "', "
                    + " Priority = '" + priority + "', "
                    + " searchConfig = '" + searchConfig + "' "
                    + " WHERE ID = " + userId;
            try (Statement stmt = getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(updateSQL);
                if (rowsAffected > 0) {
                    System.out.println("Data updated successfully.");
                } else {
                    System.out.println("No matching record found to update.");
                }
            } catch (SQLException e) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "MAX CARACTERES LIMIT FOR ACCESS",
                        String.format(
                                "This '%s' \n cannot be updated with same name.\nError: %s",
                                searchConfig, e.getMessage()));
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format.");
        }
    }

    private void deleteUserData(String Id) {
        try {
            int honeBankingId = Integer.parseInt(Id);
            String deleteSQL = "DELETE FROM home_banking WHERE ID = " + honeBankingId;
            try (Statement stmt = getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(deleteSQL);
                if (rowsAffected > 0) {
                    System.out.println("Data updated successfully.");
                } else {
                    System.out.println("No matching record found to update.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format.");
        }
    }

    @Override
    public void clearPane(Pane panel) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
