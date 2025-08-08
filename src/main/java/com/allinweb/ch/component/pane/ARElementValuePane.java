package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.FormatOption;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.model.VariableUserDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.google.common.base.Strings;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Callback;

public class ARElementValuePane extends ARPane {

    protected static volatile ARElementValuePane instance;

    // Private constructor to prevent instantiation
    private ARElementValuePane() {
        // Initialize if necessary
    }

    public static ARElementValuePane getInstance() {
        if (instance == null) {
            synchronized (ARElementValuePane.class) {
                if (instance == null) {
                    instance = new ARElementValuePane();
                }
            }
        }
        return instance;
    }

    // Postgres
    private Connection conn = null;

    private ObservableList<VariableUserDTO> variablesList = FXCollections.observableArrayList();
    private TableView<VariableUserDTO> tableView = new TableView<>();
    private RowMoveDTO rowMoveDTO;
    private int varId;
    private String varValue;
    private int instructionId;

    private String instructionName;
    private String varName;
    private String instructionType;

    private Pane mainPane;

    TextField idField;
    TextField parentField;
    TextField nameField;
    TextField valueField;
    TextField usedVarsField;
    CheckBox stringCheckBox;
    CheckBox numericCheckBox;
    Label numberFormatLabel;
    Label delimeterCSVLabel;
    ComboBox<FormatOption> comboBoxLocalFormat;
    ComboBox<FormatOption> comboBoxCSVColumns;
    Button updateButton;
    Button deleteButton;

    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final ARNewCommandPane arNewCommandPane;

    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        arNewCommandPane = ARNewCommandPane.getInstance();
    }

    public void initialize(
            RowMoveDTO rowMoveDTO,
            int varId,
            String varValue,
            int instructionId,
            String instructionName,
            String varName,
            String instructionType) {
        this.rowMoveDTO = rowMoveDTO;
        this.varId = varId;
        this.varValue = varValue;
        this.instructionId = instructionId;
        this.instructionName = instructionName;
        this.varName = varName;
        this.instructionType = instructionType;

        this.variablesList = performDataBase.loadAllVariablesByCriteria(rowMoveDTO.getBotJobId(), instructionId);

        if (this.varId > -1) {
            selectRowById(varId);
        }

        if (idField != null && this.varId == -1) {
            idField.clear();
            idField.setText(String.valueOf(varId));
            parentField.setText(instructionName);
            nameField.setText(varName);
        }

        if (valueField != null) {
            if (instructionType.equals("GET")) {
                valueField.setStyle("-fx-control-inner-background: #c9cbce;");
                // valueField.setDisable(true);
            } else {
                valueField.setStyle("-fx-control-inner-background: FFDA33;");
                valueField.setText(varValue);
            }
        }
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {

        // Create labels
        Label idLabel = new Label("ID:");
        Label parentLabel = new Label("Parent:");
        Label nameLabel = new Label("Var Name:");
        Label typeLabel = new Label("Type");
        Label valueLabel = new Label("Value");
        Label jobsLabel = new Label("Used Variables:");
        numberFormatLabel = new Label("Currency Format:");
        delimeterCSVLabel = new Label("CSV Delimiter:");

        // Create text fields
        idField = new TextField();
        idField.setEditable(false);
        idField.setText(String.valueOf(varId));
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
            // valueField.setDisable(true);
        } else {
            valueField.setStyle("-fx-control-inner-background: FFDA33;");
            valueField.setText(varValue);
        }

        usedVarsField = new TextField();
        usedVarsField.setEditable(false);
        usedVarsField.setStyle("-fx-control-inner-background: D3D3D3;");
        usedVarsField.setPrefWidth(50);
        usedVarsField.setPrefHeight(30);

        // Create checkboxes for type selection
        stringCheckBox = new CheckBox("$String");
        numericCheckBox = new CheckBox("#Numeric");

        // Create ComboBox for number format
        comboBoxLocalFormat = new ComboBox<>();
        comboBoxLocalFormat
                .getItems()
                .addAll(new FormatOption("American (9,999.99)", "US"), new FormatOption("European (9.999,99)", "EU"));

        comboBoxLocalFormat.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK);
                }

                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });

        comboBoxLocalFormat.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK);
                }
            }
        });
        comboBoxLocalFormat.setDisable(true);
        comboBoxLocalFormat.setPrefWidth(200);
        comboBoxLocalFormat.getSelectionModel().selectFirst();

        // Create ComboBox for number format
        comboBoxCSVColumns = new ComboBox<>();
        comboBoxCSVColumns
                .getItems()
                .addAll(new FormatOption("Comma: \",\"", ","), new FormatOption("Pipe \"|\"", "|"));
        comboBoxCSVColumns.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });

        comboBoxCSVColumns.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FormatOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxCSVColumns.getSelectionModel().selectFirst();

        // Ensure only one checkbox can be selected at a time
        stringCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                numericCheckBox.setSelected(false);
                numberFormatLabel.setDisable(true);
                comboBoxLocalFormat.setDisable(true);
            }
        });

        numericCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                stringCheckBox.setSelected(false);
                numberFormatLabel.setDisable(false);
                comboBoxLocalFormat.setDisable(false);
            } else {
                numberFormatLabel.setDisable(true);
                comboBoxLocalFormat.setDisable(true);
            }
        });

        // Create submit button
        Button submitButton = new Button("Insert");
        submitButton.setOnAction(event -> {
            String selectedType =
                    stringCheckBox.isSelected() ? "$String" : numericCheckBox.isSelected() ? "#Numeric" : "";

            String valueVar = Strings.isNullOrEmpty(valueField.getText()) ? "$EMPTY" : valueField.getText();

            String localFormat = "";
            if (numericCheckBox.isSelected()) {
                FormatOption selected = comboBoxLocalFormat.getValue();
                if (selected != null) {
                    localFormat = selected.getValue(); // "US" or "EU"
                }
            }

            String delimiter = "";
            FormatOption selected = comboBoxCSVColumns.getValue();
            if (selected != null) {
                delimiter = selected.getValue(); // "US" or "EU"
            }

            VariableUserDTO user = new VariableUserDTO(
                    -1,
                    selectedType,
                    nameField.getText().trim(),
                    valueVar,
                    rowMoveDTO.getBotJobId(),
                    instructionId,
                    localFormat,
                    delimiter,
                    "");

            if (nameExists(nameField.getText().trim())) {
                performMessage.errorMessage(
                        "Variable Name Already Exists",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                        String.format(
                                "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> Duplicate Variable Name - '%s'",
                                nameField.getText()),
                        "<span style='font-style: italic;'>Detail:</span> A variable with this name already exists. Please choose a unique name.",
                        null,
                        0);

                return;
            }

            if (nameField.getText().trim().isEmpty()) {
                performMessage.errorMessage(
                        "Name Cannot be Empty",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                        "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> Name Cannot be Empty",
                        "<span style='font-style: italic;'>Detail:</span> Please enter a valid name before continuing. This field is required.",
                        null,
                        0);
                return;
            }

            if (selectedType.isEmpty()) {
                performMessage.errorMessage(
                        "Type of Variable Cannot be Empty",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                        "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> Type of Variable Cannot be Empty",
                        "<span style='font-style: italic;'>Detail:</span> You must select or provide a valid variable type before proceeding.",
                        null,
                        0);
                return;
            }

            performDataBase.saveUserData(user);
            this.variablesList.clear();
            this.variablesList = performDataBase.loadAllVariablesByCriteria(rowMoveDTO.getBotJobId(), instructionId);
            arNewCommandPane.reloadComboVars(instructionId, true, -1);
        });

        // Create update button
        updateButton = new Button("Update");
        updateButton.setOnAction(event -> {
            VariableUserDTO selectedUser = tableView.getSelectionModel().getSelectedItem();

            String selectedType =
                    stringCheckBox.isSelected() ? "$String" : numericCheckBox.isSelected() ? "#Numeric" : "";

            String valueVar = Strings.isNullOrEmpty(valueField.getText()) ? "$EMPTY" : valueField.getText();

            selectedUser.setType(selectedType);
            selectedUser.setName(nameField.getText().trim());
            selectedUser.setValue(valueVar.trim());

            String localFormat = "";
            if (numericCheckBox.isSelected()) {
                FormatOption selected = comboBoxLocalFormat.getValue();
                if (selected != null) {
                    localFormat = selected.getValue(); // "US" or "EU"
                }
            }
            selectedUser.setLocalFormat(localFormat);

            String delimiter = "";
            FormatOption selected = comboBoxCSVColumns.getValue();
            if (selected != null) {
                delimiter = selected.getValue(); // "US" or "EU"
            }
            selectedUser.setDelimiter(delimiter);

            performDataBase.updateUserData(selectedUser.getId(), selectedUser);

            this.variablesList = performDataBase.loadAllVariablesByCriteria(rowMoveDTO.getBotJobId(), instructionId);
            arNewCommandPane.reloadComboVars(instructionId, true, varId);
        });
        updateButton.setDisable(true);

        // Create delete button
        deleteButton = new Button("Delete");
        deleteButton.setOnAction(event -> {
            String id = idField.getText();
            if (Integer.parseInt(usedVarsField.getText()) > 0) {
                performMessage.errorMessage(
                        "Action Remove Error",
                        String.format("This '%s' cannot be deleted!", nameField.getText()),
                        String.format("Exist %s Steps attached!", usedVarsField.getText()),
                        null,
                        null,
                        0);

                return;
            }

            performDataBase.deleteUserData(id);
            this.variablesList = performDataBase.loadAllVariablesByCriteria(rowMoveDTO.getBotJobId(), instructionId);
            arNewCommandPane.reloadComboVars(instructionId, true, -1);
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

        gridPane.add(numberFormatLabel, 0, 5);
        gridPane.add(comboBoxLocalFormat, 1, 5);

        gridPane.add(delimeterCSVLabel, 0, 6);
        gridPane.add(comboBoxCSVColumns, 1, 6);

        gridPane.add(jobsLabel, 0, 8);
        gridPane.add(usedVarsField, 1, 8);

        HBox buttonsBox = new HBox(11, submitButton, updateButton, deleteButton);
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
        applyBoldColumnStyle(idColumn);

        TableColumn<VariableUserDTO, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        applyBoldColumnStyle(typeColumn);

        TableColumn<VariableUserDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        applyBoldColumnStyle(nameColumn);

        TableColumn<VariableUserDTO, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        applyBoldColumnStyle(valueColumn);

        TableColumn<VariableUserDTO, String> localFormatColumn = new TableColumn<>("Local Format");
        localFormatColumn.setCellValueFactory(new PropertyValueFactory<>("localFormat"));
        applyBoldColumnStyle(localFormatColumn);

        TableColumn<VariableUserDTO, String> delimiterColumn = new TableColumn<>("CSV Delimiter");
        delimiterColumn.setCellValueFactory(new PropertyValueFactory<>("delimiter"));
        applyStyledColumn(delimiterColumn, item -> {
            if (",".equals(item)) {
                return "Comma \",\"";
            } else if ("|".equals(item)) {
                return "Pipe \"|\"";
            } else {
                return String.valueOf(item);
            }
        });

        List<TableColumn<VariableUserDTO, String>> columns =
                List.of(idColumn, typeColumn, nameColumn, valueColumn, localFormatColumn, delimiterColumn);
        tableView.getColumns().addAll(columns);
        tableView.setItems(variablesList);

        // Add listener to TableView selection
        //        tableView
        //                .getColumns()
        //                .addAll(idColumn, typeColumn, nameColumn, valueColumn, localFormatColumn, delimiterColumn);
        //        tableView.setItems(variablesList);

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Get the selected UserDTO object
                VariableUserDTO selectedUser = tableView.getSelectionModel().getSelectedItem();

                fillFields(selectedUser);

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
        numberFormatLabel.setDisable(true);
        comboBoxLocalFormat.setDisable(true);
        deleteButton.setDisable(true);
        updateButton.setDisable(true);
    }

    //    private List<BankingDTO> loadFromDB() {
    //
    //        PerformDataBase..refreshEntity(null, HomeBankingDTO.class);
    //
    //        List<BankingDTO> dtoList = new ArrayList<>();
    //
    //        List<HomeBankingDTO> listHomeBankingDTO =
    //                PerformDataBase..getEntityList(HomeBankingDTO.class);
    //
    //        // Iterate through the result set and populate the DTO list
    //        for (HomeBankingDTO homeBankingDTO : listHomeBankingDTO) {
    //            List<JobDTO> listJobsDto = new ArrayList<>();
    //            for (BotJobDTO botJobDTO : homeBankingDTO.getBotJobs()) {
    //                JobDTO jobsDto = new JobDTO(botJobDTO.getName(), botJobDTO.getDescription(), new ArrayList<>());
    //                listJobsDto.add(jobsDto);
    //            }
    //
    //            dtoList.add(new BankingDTO(
    //                    homeBankingDTO.getId(),
    //                    homeBankingDTO.getName(),
    //                    homeBankingDTO.getUrl(),
    //                    homeBankingDTO.getPriority(),
    //                    listJobsDto.size(),
    //                    listJobsDto));
    //        }
    //        return dtoList;
    //    }

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

    public void selectRowById(int idToFind) {
        if (rowMoveDTO != null && rowMoveDTO.getBotJobId() != null) {

            if (this.variablesList.isEmpty()) {
                this.variablesList =
                        performDataBase.loadAllVariablesByCriteria(rowMoveDTO.getBotJobId(), instructionId);
            }

            if (tableView == null) {
                System.err.println("TableView not initialized.");
                return;
            }

            ObservableList<VariableUserDTO> items = tableView.getItems();
            if (items == null || items.isEmpty()) {
                System.out.println("TableView is empty.");
                return;
            }

            // Iterate through the items in the TableView's list
            for (int i = 0; i < items.size(); i++) {
                VariableUserDTO userDTO = items.get(i);
                if (userDTO.getId() == idToFind) {
                    // If the ID matches, select the row at the current index
                    tableView.getSelectionModel().clearAndSelect(i);

                    // Optionally, scroll the selected row into view
                    tableView.scrollTo(i);

                    fillFields(userDTO);

                    return; // Exit the method once the row is found and selected
                }
            }
        }

        // If the loop completes without finding the ID
        System.out.println("Variable with ID " + idToFind + " not found in the TableView.");
    }

    private void fillFields(VariableUserDTO userDTO) {
        // Set the values of the selected row to the text fields
        idField.setText(String.valueOf(userDTO.getId()));
        parentField.setText("(" + userDTO.getParentId() + ")" + userDTO.getName());
        nameField.setText(userDTO.getName());
        String valueVar = userDTO.getValue().equalsIgnoreCase("$EMPTY") ? "" : userDTO.getValue();
        valueField.setText(valueVar);

        // Update the checkboxes based on the selected user's type
        if (!Strings.isNullOrEmpty(userDTO.getLocalFormat())
                && userDTO.getLocalFormat().equals("EU")) {
            comboBoxLocalFormat.getSelectionModel().selectLast();
        } else {
            comboBoxLocalFormat.getSelectionModel().selectFirst();
        }

        // Update the checkboxes based on the selected user's type
        if (!Strings.isNullOrEmpty(userDTO.getDelimiter())
                && userDTO.getDelimiter().equals("|")) {
            comboBoxCSVColumns.getSelectionModel().selectLast();
        } else {
            comboBoxCSVColumns.getSelectionModel().selectFirst();
        }

        usedVarsField.setText(userDTO.getUsedVars()); // Update the hidden field

        // Update the checkboxes based on the selected user's type
        if (userDTO.getType().equals("$String")) {
            stringCheckBox.setSelected(true);
            numericCheckBox.setSelected(false);
            comboBoxLocalFormat.setDisable(true);
        } else if (userDTO.getType().equals("#Numeric")) {
            stringCheckBox.setSelected(false);
            numericCheckBox.setSelected(true);
            comboBoxLocalFormat.setDisable(false);
        }

        this.varId = userDTO.getId();
        this.varValue = userDTO.getValue();
        this.instructionId = userDTO.getParentId();
        this.instructionName = "(" + userDTO.getParentId() + ")" + userDTO.getName();
    }

    private <S, T> void applyBoldColumnStyle(TableColumn<S, T> column) {
        column.setCellFactory(tc -> new TableCell<S, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    if (item.equals(",")) {
                        setText("Comma \",\"");
                    } else if (item.equals("|")) {
                        setText("Pipe \"|\"");
                    } else {
                        setText(String.valueOf(item)); // Convert any type to String for display
                    }
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
    }

    private <S, T> void applyStyledColumn(TableColumn<S, T> column, Callback<T, String> renderFunction) {
        column.setCellFactory(tc -> new TableCell<S, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(renderFunction.call(item));
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
    }
}
