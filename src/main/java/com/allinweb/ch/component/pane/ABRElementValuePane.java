package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.dto.BankingDTO;
import com.allinweb.ch.component.model.dto.JobDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.persistence.JobUserDTO;
import com.allinweb.ch.persistence.VariableUserDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
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

public class ABRElementValuePane extends ABRPane {

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";

    // Postgres
    private static final boolean POSTGRES_DB = true;
    private static final String CONNECTION_POSTGRES = "jdbc:postgresql://";
    private static final String DB_HOST = "localhost"; // or your PostgreSQL server address
    private static final String DB_PORT = "5432"; // default PostgreSQL port
    private static final String DB_NAME = "abr_web"; // your database name
    private static final String USERNAME = "postgres"; // your database username
    private static final String PASSWORD = "martini"; // your database password

    private Connection conn = null;
    private ObservableList<VariableUserDTO> databaseList = FXCollections.observableArrayList();
    private ObservableList<JobUserDTO> jobUserList = FXCollections.observableArrayList();
    private TableView<VariableUserDTO> tableView = new TableView<>();
    private ListView<BotJobDTO> viewVariablesListView;
    private int botJobId;

    private List<BankingDTO> dtoList;
    private int currentIndex = 0;

    private Pane mainPane;

    TextField idField;
    TextField nameField;
    TextField usedVarsField;
    CheckBox stringCheckBox;
    CheckBox numericCheckBox;
    Button updateButton;
    Button deleteButton;

    private boolean isNewState = false;

    public ABRElementValuePane(int botJobId) {
        this.botJobId = botJobId;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Initialize database IF IS ACCESS TO BE USED
        if (!POSTGRES_DB) {
            initializeDatabase();
        }
        loadUserData();

        // Create labels
        Label idLabel = new Label("ID:");
        Label nameLabel = new Label("Name:");
        Label typeLabel = new Label("Type");
        Label jobsLabel = new Label("Used Variables:");

        // Create text fields
        idField = new TextField();
        idField.setEditable(false);
        idField.setStyle("-fx-control-inner-background: D3D3D3; -fx-pref-width: 50px;");
        idField.setPrefHeight(30);

        nameField = new TextField();
        nameField.setStyle("-fx-control-inner-background: FFDA33;");
        nameField.requestFocus();

        usedVarsField = new TextField();
        usedVarsField.setEditable(false);
        usedVarsField.setStyle("-fx-control-inner-background: D3D3D3;");
        usedVarsField.setPrefWidth(50);
        usedVarsField.setPrefHeight(30);

        // Create checkboxes for type selection
        stringCheckBox = new CheckBox("$String");
        numericCheckBox = new CheckBox("#Numeric");

        // Ensure only one checkbox can be selected at a time
        stringCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                numericCheckBox.setSelected(false);
            }
        });

        numericCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                stringCheckBox.setSelected(false);
            }
        });

        // Create submit button
        Button submitButton = new Button("Insert");
        submitButton.setOnAction(event -> {
            String selectedType =
                    stringCheckBox.isSelected() ? "$String" : numericCheckBox.isSelected() ? "#Numeric" : "";

            VariableUserDTO user =
                    new VariableUserDTO(null, nameField.getText().trim(), selectedType, String.valueOf(botJobId));

            if (nameExists(nameField.getText().trim())) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Env Name Already Exists",
                        String.format("This '%s' cannot be inserted with the same name.\n", nameField.getText()));
                return;
            }

            if (nameField.getText().trim().isEmpty() || selectedType.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Name and Type Cannot be Empty", "Name and Type Cannot be Empty");
                return;
            }

            saveUserData(user);
            loadUserData();
        });

        // Create update button
        updateButton = new Button("Update");
        updateButton.setOnAction(event -> {
            String id = idField.getText();
            String selectedType =
                    stringCheckBox.isSelected() ? "$String" : numericCheckBox.isSelected() ? "#Numeric" : "";

            VariableUserDTO user = new VariableUserDTO(id, nameField.getText(), selectedType, String.valueOf(botJobId));
            updateUserData(id, user);
            loadUserData();
        });
        updateButton.setDisable(true);

        // Create delete button
        deleteButton = new Button("Delete");
        deleteButton.setOnAction(event -> {
            String id = idField.getText();
            if (Integer.parseInt(usedVarsField.getText()) > 0) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Action Remove Error",
                        String.format(
                                "This '%s' cannot be deleted.\nIt has %s Jobs",
                                nameField.getText(), usedVarsField.getText()));
                return;
            }

            deleteUserData(id);
            loadUserData();
        });
        deleteButton.setDisable(true);

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

        gridPane.add(typeLabel, 0, 2);
        HBox typeBox = new HBox(10, stringCheckBox, numericCheckBox); // Create an HBox to hold the checkboxes
        gridPane.add(typeBox, 1, 2);

        gridPane.add(jobsLabel, 0, 5);
        gridPane.add(usedVarsField, 1, 5);

        HBox buttonsBox = new HBox(10, submitButton, updateButton, deleteButton);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.setSpacing(10); // Horizontal spacing between buttons

        gridPane.add(buttonsBox, 0, 8, 2, 1);

        HBox hBoxGridPane = new HBox(gridPane);
        hBoxGridPane.setAlignment(Pos.CENTER);
        hBoxGridPane.setSpacing(10); // Horizontal spacing around the gridPane

        // Configure TableView
        tableView = new TableView<>();
        TableColumn<VariableUserDTO, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<VariableUserDTO, String> typeColumn = new TableColumn<>("type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));

        TableColumn<VariableUserDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        tableView.getColumns().addAll(idColumn, typeColumn, nameColumn);
        tableView.setItems(databaseList);

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Get the selected UserDTO object
                VariableUserDTO selectedUser = tableView.getSelectionModel().getSelectedItem();

                // Set the values of the selected row to the text fields
                idField.setText(selectedUser.getId());
                nameField.setText(selectedUser.getName());
                usedVarsField.setText(selectedUser.getUsedVars()); // Update the hidden field

                // Update the checkboxes based on the selected user's type
                if (selectedUser.getType().equals("$String")) {
                    stringCheckBox.setSelected(true);
                    numericCheckBox.setSelected(false);
                } else if (selectedUser.getType().equals("#Numeric")) {
                    stringCheckBox.setSelected(false);
                    numericCheckBox.setSelected(true);
                }
                deleteButton.setDisable(false);
                updateButton.setDisable(false);
            } else {
                // If no row is selected, clear the text fields and checkboxes
                clearData();
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
        mainPane = new AnchorPane(vbox);
        AnchorPane.setTopAnchor(vbox, 0.0);
        AnchorPane.setBottomAnchor(vbox, 0.0);
        AnchorPane.setLeftAnchor(vbox, 0.0);
        AnchorPane.setRightAnchor(vbox, 0.0);
    }

    private void clearData() {
        idField.clear();
        nameField.clear();
        usedVarsField.clear();
        stringCheckBox.setSelected(false);
        numericCheckBox.setSelected(false);
        deleteButton.setDisable(true);
        updateButton.setDisable(true);
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
        for (VariableUserDTO dto : databaseList) {
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
        if (!POSTGRES_DB) {
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
        String selectSQL = " SELECT vars.id, vars.name, vars.type, bot_job_id, COUNT(blk.variable_id) UsedVars "
                + " FROM variable vars "
                + " left join block_loop_instruction blk on blk.variable_id = vars.id "
                + " where bot_job_id = " + botJobId
                + " group by vars.id, vars.Name, vars.type ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String name = rs.getString("name");
                String type = rs.getString("type");
                String botJobId = rs.getString("bot_job_id");
                String usedVars = rs.getString("UsedVars");
                databaseList.add(new VariableUserDTO(id, name, type, botJobId, usedVars));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private Integer loadNexIdData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM variable";
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

    private void saveUserData(VariableUserDTO user) {
        // Generate a Unique-ID
        Integer hashCode = loadNexIdData() + 1;
        //        AlterSeq(hashCode);
        //        Integer hashCode = generateID();

        String insertSQL = "INSERT INTO variable (ID, type, Name, bot_job_id) VALUES ( "
                + hashCode + ","
                + "'" + user.getType() + "', "
                + "'" + user.getName() + "', "
                + "'" + user.getBotJobId() + "')";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            System.out.println("Data saved successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateUserData(String id, VariableUserDTO user) {
        try {
            int userId = Integer.parseInt(id);

            String updateSQL = "UPDATE variable SET Name = '" + user.getName() + "', "
                    + " type = '" + user.getType() + "' "
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
                                user.getName(), e.getMessage()));
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format.");
        }
    }

    private void deleteUserData(String Id) {
        try {
            int variableId = Integer.parseInt(Id);
            String deleteSQL = "DELETE FROM variable WHERE ID = " + variableId;
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
