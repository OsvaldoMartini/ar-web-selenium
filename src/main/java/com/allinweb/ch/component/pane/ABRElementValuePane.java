package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BankingDTO;
import com.allinweb.ch.component.model.JobDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.model.VariableUserDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.HomeBankingDTO;
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

public class ABRElementValuePane extends ABRPane {

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";

    // Postgres
    private static boolean POSTGRES_DB = false;
    private Connection conn = null;

    private ObservableList<VariableUserDTO> variablesList = FXCollections.observableArrayList();
    private TableView<VariableUserDTO> tableView = new TableView<>();
    private RowMoveDTO rowMoveDTO;
    private int instructionId;
    private String instructionName;
    private String varName;
    private String instructionType;

    private List<BankingDTO> dtoList;
    private int currentIndex = 0;

    private Pane mainPane;

    TextField idField;
    TextField parentField;
    TextField nameField;
    TextField valueField;
    TextField usedVarsField;
    CheckBox stringCheckBox;
    CheckBox numericCheckBox;
    Button updateButton;
    Button deleteButton;

    private static final PerformActions performAction;
    private static final PerformDataBase performDataBase;

    // Static block to initialize
    static {
        performAction = PerformActions.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    public ABRElementValuePane(
            RowMoveDTO rowMoveDTO, int instructionId, String instructionName, String varName, String instructionType) {
        this.rowMoveDTO = rowMoveDTO;
        this.instructionId = instructionId;
        this.instructionName = instructionName;
        this.varName = varName;
        this.instructionType = instructionType;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        String dataBaseType = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.DATABASE_TYPE);

        if (dataBaseType != null && dataBaseType.equalsIgnoreCase("POSTGRES")) {
            POSTGRES_DB = true;
        } else {
            POSTGRES_DB = false;
        }

        // Initialize database IF IS ACCESS TO BE USED
        if (!POSTGRES_DB) {
            initializeDatabase();
        }
        loadUserData();

        // Create labels
        Label idLabel = new Label("ID:");
        Label parentLabel = new Label("Parent:");
        Label nameLabel = new Label("Var Name:");
        Label typeLabel = new Label("Type");
        Label valueLabel = new Label("Value");
        Label jobsLabel = new Label("Used Variables:");

        // Create text fields
        idField = new TextField();
        idField.setEditable(false);
        idField.setText(String.valueOf(instructionId));
        idField.setStyle("-fx-control-inner-background: D3D3D3; -fx-pref-width: 50px;");
        idField.setPrefHeight(30);

        parentField = new TextField();
        parentField.setEditable(false);
        parentField.setText(instructionName);
        parentField.setStyle("-fx-control-inner-background: white; -fx-font-weight: bold;");

        nameField = new TextField();
        nameField.setText(varName);
        nameField.setStyle("-fx-control-inner-background: FFDA33;");
        nameField.requestFocus();

        valueField = new TextField();
        if (instructionType.equals("GET")) {
            valueField.setStyle("-fx-control-inner-background: #c9cbce;");
            valueField.setDisable(true);
        } else {
            valueField.setStyle("-fx-control-inner-background: FFDA33;");
        }

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

            String valueVar = Strings.isNullOrEmpty(valueField.getText()) ? "$EMPTY" : valueField.getText();

            VariableUserDTO user = new VariableUserDTO(
                    -1, selectedType, nameField.getText().trim(), valueVar, rowMoveDTO.getBotJobId(), instructionId);

            if (nameExists(nameField.getText().trim())) {
                performAction.errorMessage(
                        "Variable Name Already Exists",
                        String.format("'%s' cannot be inserted with the existent name!", nameField.getText()),
                        null,
                        null,
                        null);

                return;
            }

            if (nameField.getText().trim().isEmpty() || selectedType.isEmpty()) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Error",
                        "Name and Type Cannot be Empty",
                        "Name and Type Cannot be Empty");
                return;
            }

            saveUserData(user);
            loadUserData();
        });

        // Create update button
        updateButton = new Button("Update");
        updateButton.setOnAction(event -> {
            int id = Strings.isNullOrEmpty(idField.getText()) ? -1 : Integer.parseInt(idField.getText());
            String selectedType =
                    stringCheckBox.isSelected() ? "$String" : numericCheckBox.isSelected() ? "#Numeric" : "";

            String valueVar = Strings.isNullOrEmpty(valueField.getText()) ? "$EMPTY" : valueField.getText();

            VariableUserDTO user = new VariableUserDTO(
                    id, selectedType, nameField.getText(), valueVar, rowMoveDTO.getBotJobId(), instructionId);
            updateUserData(id, user);
            loadUserData();
        });
        updateButton.setDisable(true);

        // Create delete button
        deleteButton = new Button("Delete");
        deleteButton.setOnAction(event -> {
            String id = idField.getText();
            if (Integer.parseInt(usedVarsField.getText()) > 0) {
                performAction.errorMessage(
                        "Action Remove Error",
                        String.format("This '%s' cannot be deleted!", nameField.getText()),
                        String.format("Exist %s Steps attached!", usedVarsField.getText()),
                        null,
                        null);

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

        gridPane.add(parentLabel, 0, 1);
        gridPane.add(parentField, 1, 1);

        gridPane.add(nameLabel, 0, 2);
        gridPane.add(nameField, 1, 2);

        gridPane.add(valueLabel, 0, 3);
        gridPane.add(valueField, 1, 3);

        gridPane.add(typeLabel, 0, 4);
        HBox typeBox = new HBox(10, stringCheckBox, numericCheckBox); // Create an HBox to hold the checkboxes
        gridPane.add(typeBox, 1, 4);

        gridPane.add(jobsLabel, 0, 7);
        gridPane.add(usedVarsField, 1, 7);

        HBox buttonsBox = new HBox(10, submitButton, updateButton, deleteButton);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.setSpacing(10); // Horizontal spacing between buttons

        gridPane.add(buttonsBox, 0, 9, 2, 1);

        HBox hBoxGridPane = new HBox(gridPane);
        hBoxGridPane.setAlignment(Pos.CENTER);
        hBoxGridPane.setSpacing(10); // Horizontal spacing around the gridPane

        // Configure TableView
        tableView = new TableView<>();
        TableColumn<VariableUserDTO, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<VariableUserDTO, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));

        TableColumn<VariableUserDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<VariableUserDTO, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));

        tableView.getColumns().addAll(idColumn, typeColumn, nameColumn, valueColumn);
        tableView.setItems(variablesList);

        // Add listener to TableView selection
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Get the selected UserDTO object
                VariableUserDTO selectedUser = tableView.getSelectionModel().getSelectedItem();

                // Set the values of the selected row to the text fields
                idField.setText(String.valueOf(selectedUser.getId()));
                nameField.setText(selectedUser.getName());
                String valueVar = selectedUser.getValue().equalsIgnoreCase("$EMPTY") ? "" : selectedUser.getValue();
                valueField.setText(valueVar);
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

        // Wrap the TableView in a VBox for more control
        VBox tableViewContainer = new VBox(tableView);
        tableViewContainer.setAlignment(Pos.BOTTOM_CENTER);
        tableViewContainer.setSpacing(10); // Spacing around the TableView
        tableViewContainer.setPadding(new Insets(10)); // Padding inside the TableView container

        // Create the main layout
        VBox mainLayout = new VBox(20, hBoxGridPane, tableViewContainer);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(10)); // Padding around the VBox

        // Adjust VBox properties for better alignment
        VBox.setVgrow(tableViewContainer, Priority.ALWAYS);

        // Use AnchorPane to ensure the VBox resizes with the window
        mainPane = new AnchorPane(mainLayout);
        AnchorPane.setTopAnchor(mainLayout, 0.0);
        AnchorPane.setBottomAnchor(mainLayout, 0.0);
        AnchorPane.setLeftAnchor(mainLayout, 0.0);
        AnchorPane.setRightAnchor(mainLayout, 0.0);
    }

    private void clearData() {
        idField.clear();
        nameField.clear();
        valueField.clear();
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

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private boolean nameExists(String name) {
        for (VariableUserDTO dto : variablesList) {
            if (dto.getName().trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
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

    //    private Connection getConnection() {
    //        if (!POSTGRES_DB) {
    //            if (conn == null) {
    //                String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
    //                String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
    //                try {
    //                    conn = DriverManager.getConnection(dbUrl);
    //                } catch (SQLException e) {
    //                    e.printStackTrace();
    //                }
    //            }
    //            return conn;
    //        } else {
    //
    //            if (conn == null) {
    //                String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    //                try {
    //                    conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
    //                } catch (SQLException e) {
    //                    e.printStackTrace();
    //                }
    //            }
    //            return conn;
    //        }
    //    }

    private void loadUserData() {
        variablesList.clear();
        String selectSQL = " SELECT vars.id, vars.type, vars.name, vars.value, COUNT(blk.variable_id) UsedVars "
                + " FROM variable vars "
                + " left join block_loop_instruction blk on blk.variable_id = vars.id "
                + " where vars.bot_job_id = " + rowMoveDTO.getBotJobId()
                + " and  block_loop_instruction_id = " + instructionId
                + " group by vars.id, vars.type, vars.Name, vars.value ";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                int id = rs.getInt("ID");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String value = rs.getString("value");
                String usedVars = rs.getString("UsedVars");
                variablesList.add(
                        new VariableUserDTO(id, type, name, value, rowMoveDTO.getBotJobId(), instructionId, usedVars));
            }
        } catch (SQLException e) {
            performAction.errorMessage(
                    "Error loading Variables", "Could Not Load the Variables", e.getMessage(), null, null);

            return;
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private Integer loadNextIdData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM variable";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            performAction.errorMessage(
                    "Error loading Next Id Data", "Could Not Load the Next Id Data", null, null, null);
        }
        return null;
    }

    private void saveUserData(VariableUserDTO user) {
        // Generate a Unique-ID
        Integer hashCode = loadNextIdData() + 1;
        //        AlterSeq(hashCode);
        //        Integer hashCode = generateID();

        String insertSQL =
                "INSERT INTO variable (ID, type, Name, Value, bot_job_id, block_loop_instruction_id) VALUES ( "
                        + hashCode + ","
                        + "'" + user.getType() + "', "
                        + "'" + user.getName() + "', "
                        + "'" + user.getValue() + "', "
                        + "'" + user.getBotJobId() + "', "
                        + "'" + user.getInstructionId() + "')";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            System.out.println("Data saved successfully.");
        } catch (SQLException e) {
            performAction.errorMessage(
                    "Error Inserting new Variable",
                    String.format("The '%s' cannot be inserted!", user.getName()),
                    e.getMessage(),
                    null,
                    null);

            return;
        }
    }

    private void updateUserData(Integer userId, VariableUserDTO user) {
        //        try {
        String updateSQL = "UPDATE variable SET Name = '" + user.getName() + "', "
                + " type = '" + user.getType() + "', "
                + " value = '" + user.getValue() + "' "
                + " WHERE ID = " + userId;
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {
                System.out.println("Data updated successfully.");
            } else {
                System.out.println("No matching record found to update.");
            }
        } catch (SQLException e) {
            performAction.errorMessage(
                    "MAX CHARACTERS LIMIT FOR ACCESS",
                    String.format("The '%s' cannot be updated.", user.getName()),
                    e.getMessage(),
                    null,
                    null);

            return;
        }
        //        } catch (NumberFormatException e) {
        //            System.out.println("Invalid ID format.");
        //        }
    }

    private void deleteUserData(String Id) {
        try {
            int variableId = Integer.parseInt(Id);
            String deleteSQL = "DELETE FROM variable WHERE ID = " + variableId;
            try (Statement stmt =
                    ABRSharedResources.getInstance().getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(deleteSQL);
                if (rowsAffected > 0) {
                    System.out.println("Data deleted successfully.");
                } else {
                    System.out.println("No matching record found to delete.");
                }
            } catch (SQLException e) {
                performAction.errorMessage(
                        "Error Deleting", String.format("Cannot be deleted id: '%s'", Id), e.getMessage(), null, null);
            }
        } catch (NumberFormatException e) {
            performAction.errorMessage(
                    "Invalid ID format.", String.format("The id: '%s' is in invalid format!", Id), null, null, null);
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
