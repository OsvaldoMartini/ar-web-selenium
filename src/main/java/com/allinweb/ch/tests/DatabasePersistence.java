package com.allinweb.ch.tests;

import com.allinweb.ch.persistence.DatabaseUserDTO;
import com.allinweb.ch.persistence.JobUserDTO;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DatabasePersistence extends Application {

    private static final String FILE_NAME =
            "D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\user_data.mdb";
    private static final String FILE_NAME2 =
            "D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\database.mdb";
    private static final String DB_URL_1 =
            "jdbc:ucanaccess:////D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\user_data.mdb";
    //    private static final String DB_URL_BANKING =
    // "jdbc:ucanaccess://D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\database.mdb;memory=false;newDatabaseVersion=V2010";
    private static final String DB_URL_2 =
            "jdbc:ucanaccess://D:\\Projects\\abr-web-selenium\\abr-web-selenium-files\\ABRWeb\\database.mdb";
    private Connection conn = null;
    private ObservableList<DatabaseUserDTO> databaseList = FXCollections.observableArrayList();
    private ObservableList<JobUserDTO> jobUserList = FXCollections.observableArrayList();
    private TableView<DatabaseUserDTO> tableView = new TableView<>();

    // Postgres
    private static final boolean POSTGRES_DB = false;
    private static final String CONNECTION_POSTGRES = "jdbc:postgresql://";
    private static final String DB_HOST = "localhost"; // or your PostgreSQL server address
    private static final String DB_PORT = "5432"; // default PostgreSQL port
    private static final String DB_NAME = "abr_web"; // your database name
    private static final String USERNAME = "postgres"; // your database username
    private static final String PASSWORD = "martini"; // your database password

    @Override
    public void start(Stage primaryStage) {
        // Initialize database IF IS ACCESS TO BE USED
        if (!POSTGRES_DB) {
            initializeDatabase();
        }
        loadUserData();
        // Create labels
        Label idLabel = new Label("ID:");
        Label nameLabel = new Label("Name:");
        Label urlLabel = new Label("Url:");
        Label priorityLabel = new Label("Priority:");
        Label jobsLabel = new Label("Total of Jobs:");

        // Create text fields
        TextField idField = new TextField();
        idField.setEditable(false);
        idField.setStyle("-fx-control-inner-background: #D3D3D3; -fx-pref-width: 50px;");
        //        idField.setPrefWidth(200); // Set the preferred width
        idField.setPrefHeight(30);
        TextField nameField = new TextField();
        nameField.setStyle("-fx-control-inner-background: FFDA33; -fx-pref-width: 50px;");
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

        TextArea optionsConfigField = new TextArea();
        optionsConfigField.setPrefRowCount(3); // Set preferred row count for the TextArea

        // Create submit button
        Button submitButton = new Button("Insert");
        submitButton.setOnAction(event -> {
            DatabaseUserDTO user = new DatabaseUserDTO(
                    null,
                    nameField.getText(),
                    urlField.getText(),
                    priorityField.getText(),
                    searchConfigField.getText(),
                    optionsConfigField.getText());

            if (nameExists(nameField.getText())) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Env Name Alread Existe",
                        String.format("This '%s' cannot be inserted with same name.\n", nameField.getText()));
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
                    id,
                    nameField.getText(),
                    urlField.getText(),
                    priorityField.getText(),
                    searchConfigField.getText(),
                    optionsConfigField.getPromptText());
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

        // Create layout and add components
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10); // Horizontal gap between columns
        gridPane.setVgap(10); // Vertical gap between rows

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

        HBox buttonsBox = new HBox(submitButton, updateButton, deleteButton);
        buttonsBox.setAlignment(Pos.CENTER);
        gridPane.add(buttonsBox, 0, 6, 3, 1);

        //        gridPane.add(submitButton, 0, 5);
        //        gridPane.add(updateButton, 1, 5);
        //        gridPane.add(deleteButton, 2, 5);
        HBox hBoxGridPane = new HBox(gridPane);
        hBoxGridPane.setAlignment(Pos.CENTER);

        // Configure TableView
        TableColumn<DatabaseUserDTO, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<DatabaseUserDTO, String> jobColumn = new TableColumn<>("Jobs");
        jobColumn.setCellValueFactory(new PropertyValueFactory<>("jobs"));

        TableColumn<DatabaseUserDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<DatabaseUserDTO, String> urlColumn = new TableColumn<>("Url");
        urlColumn.setCellValueFactory(new PropertyValueFactory<>("url"));

        TableColumn<DatabaseUserDTO, String> priorityColumn = new TableColumn<>("Priority");
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
            } else {
                // If no row is selected, clear the text fields
                idField.clear();
                nameField.clear();
                urlField.clear();
                priorityField.clear();
                jobsField.clear();
                // Clear other fields as needed
            }
        });

        // Create main layout
        //        VBox vbox = new VBox(gridPane, tableView);
        VBox vbox = new VBox(20, hBoxGridPane, tableView); // Added spacing between GridPane and TableView
        vbox.setAlignment(Pos.CENTER); // Center-align the VBox content

        // Create scene and set on stage
        Scene scene = new Scene(vbox, 600, 600);
        primaryStage.setTitle("Home Banking Data Persistence");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Request focus on the nameField after the scene is shown
        Platform.runLater(() -> nameField.requestFocus());
    }

    private void initializeDatabase() {
        File dbFile = new File(FILE_NAME2);
        if (!dbFile.exists()) {
            try (Connection conn = DriverManager.getConnection(DB_URL_2 + ";newDatabaseVersion=V2010")) {
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

    // Simple connection for tests
    private Connection getConnection() {
        if (!POSTGRES_DB) {
            if (conn == null) {
                try {
                    conn = DriverManager.getConnection(DB_URL_2);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return conn;
        } else {

            if (conn == null) {
                String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
                try {
                    conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return conn;
        }
    }

    private void loadUserData() {
        databaseList.clear();
        String selectSQL = " SELECT ID, Name, Url, priority, COUNT(bot.ID) Jobs,username, password "
                + " FROM home_banking bank "
                + " left join bot_job bot on bot.home_banking_id = bank.id "
                + " group by bank.ID, bank.Name, bank.Url, bank.priority, bank.username, bank.password ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String jobs = rs.getString("Jobs");
                String name = rs.getString("Name");
                String url = rs.getString("Url");
                String priority = rs.getString("Priority");
                if (Strings.isNullOrEmpty(priority) || priority.equalsIgnoreCase("null")) {
                    priority = "";
                }
                String searchConfig = rs.getString("searchConfig");
                if (Strings.isNullOrEmpty(searchConfig) || searchConfig.equalsIgnoreCase("null")) {
                    searchConfig = "";
                }
                String optionsConfig = rs.getString("optionsConfig");
                if (Strings.isNullOrEmpty(optionsConfig) || searchConfig.equalsIgnoreCase("null")) {
                    searchConfig = "";
                }

                String username = rs.getString("username");
                String password = rs.getString("password");
                databaseList.add(new DatabaseUserDTO(
                        id, jobs, name, url, priority, searchConfig, optionsConfig, username, password));
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

    private String AlterSeq(Integer id) {
        String selectSQL = "ALTER SEQUENCE homeBankingSeq RESTART WITH " + id;
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {

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
        String insertSQL =
                "INSERT INTO home_banking (ID, Name, Url, priority, searchConfig, username, password) VALUES ( "
                        + hashCode + ","
                        + "'" + user.getName() + "', "
                        + "'" + user.getUrl() + "', "
                        + "'" + user.getPriority() + "', "
                        + "'" + user.getSearchConfig() + "', "
                        + "'" + user.getUsername() + "', "
                        + "'" + user.getPassword() + "')";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            System.out.println("Data saved successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Integer generateID() {
        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();

        // Define the date-time formatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        // Format the current date and time
        String formattedDateTime = now.format(formatter);

        // Convert the formatted string to an integer
        return Integer.parseInt(formattedDateTime);
    }

    private void updateUserData(String id, DatabaseUserDTO user) {
        try {
            int userId = Integer.parseInt(id);
            String updateSQL = "UPDATE home_banking SET Name = '" + user.getName() + "', "
                    + " Url = '" + user.getUrl() + "', "
                    + " Priority = '" + user.getPriority() + "', "
                    + " searchConfig = '" + user.getSearchConfig() + "' "
                    + " WHERE ID = " + userId;
            try (Statement stmt = getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(updateSQL);
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
    public void stop() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean nameExists(String name) {
        for (DatabaseUserDTO dto : databaseList) {
            if (dto.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
