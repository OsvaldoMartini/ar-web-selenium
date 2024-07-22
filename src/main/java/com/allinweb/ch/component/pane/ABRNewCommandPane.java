package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRElementValueScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.driver.ABRWebElement;
import com.allinweb.ch.persistence.VariableUserDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.allinweb.ch.util.ComboBoxItem;
import com.allinweb.ch.util.ComboBoxVars;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class ABRNewCommandPane extends ABRPane {

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

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

    private ObservableList<VariableUserDTO> variablesList = FXCollections.observableArrayList();

    private int botJobId;
    private int instructionId;
    private String instructionName;

    private Pane mainPane;

    TextField nameField;
    TextField valueField;

    private Button variableButton;

    Button submitButton;
    Button cancelButton;

    private ComboBox<ComboBoxItem> comboBoxInstruc;
    private ObservableList<ComboBoxItem> itemsInstructions;

    private ComboBox<ComboBoxVars> comboBoxVars;
    private ObservableList<ComboBoxVars> variablesItems = FXCollections.observableArrayList();

    private ComboBox<ComboBoxVars> comboBoxWebPage;
    private ObservableList<ComboBoxVars> webPageItems;

    public ABRNewCommandPane(int botJobId, ObservableList<ComboBoxVars> webPageItems) {
        this.botJobId = botJobId;
        this.webPageItems = webPageItems;

        // Initialize database IF IS ACCESS TO BE USED
        if (!POSTGRES_DB) {
            initializeDatabase();
        }

        itemsInstructions = FXCollections.observableArrayList(
                new ComboBoxItem("setValue", new Image(ABRConstants.ICON_SET_VALUE_BTN)),
                new ComboBoxItem("getValue", new Image(ABRConstants.ICON_GET_VALUE_BTN)),
                new ComboBoxItem("Check", new Image(ABRConstants.ICON_CHECK)));

        if (webPageItems !=null && webPageItems.size() > 0) {
            loadJobVariables(webPageItems.get(0).getVarId());
        }

    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {

        // Create labels
        Label commandLabel = new Label("Command:");
        Label botJobVarsLabel = new Label("Bot-Job Variable");
        Label webPageLabel = new Label("WebPage Field");

        comboBoxInstruc = new ComboBox<>(itemsInstructions);
        comboBoxInstruc.setPrefWidth(120); // Set preferred width of ComboBox

        // Set cell factory to display images and text
        comboBoxInstruc.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(20); // Set the width for icon size
                    imageView.setFitHeight(20); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });

        comboBoxInstruc.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(20); // Set the width for icon size
                    imageView.setFitHeight(20); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxInstruc.getSelectionModel().selectFirst();

        reloadComboVars();
//        if (variablesList != null && variablesList.size() > 0) {
//            List<ComboBoxVars> variablesNames = variablesList.stream()
//                    .map(variable -> new ComboBoxVars(
//                            variable.getType().substring(0, 1) + variable.getName(),
//                            variable.getInstructionId(),
//                            variable.getValue()))
//                    .collect(Collectors.toList());
//            variablesItems.addAll(variablesNames);
//        } else {
//            variablesItems.add(new ComboBoxVars("no variables added", -1, ""));
//        }
        comboBoxVars = new ComboBox<>(variablesItems);
        comboBoxVars.getSelectionModel().selectFirst();

        // Set cell factory to display images and text
        comboBoxVars.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });

        comboBoxVars.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxVars.getSelectionModel().selectFirst();

        comboBoxWebPage = new ComboBox<>(webPageItems);
        comboBoxWebPage.setPrefWidth(50);

        // Set cell factory to display images and text
        comboBoxWebPage.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });

        comboBoxWebPage.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxWebPage.getSelectionModel().selectFirst();

        String css = getClass().getResource("/button.css").toExternalForm();

        submitButton = componentBuilder.buildButton("OK", ABRConstants.SPACE_L, Insets.EMPTY);
        submitButton.getStyleClass().add("ok-button");

        cancelButton = componentBuilder.buildButton("Cancel", ABRConstants.SPACE_L, Insets.EMPTY);
        cancelButton.getStyleClass().add("cancel-button");

        variableButton = componentBuilder.buildButton(
                "Variables", ABRConstants.SPACE_L, ABRConstants.ICON_VARIABLES, ABRConstants.SPACE_M, Insets.EMPTY);

        // Define a uniform width for the buttons
        double buttonWidth = 200;

        // Create layout and add components
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        //  gridPane.setPadding(new Insets(10)); // Padding around the gridPane

        // Add components to the grid
        gridPane.add(commandLabel, 0, 0);
        gridPane.add(botJobVarsLabel, 1, 0);
        gridPane.add(webPageLabel, 2, 0);

        comboBoxInstruc.setPrefWidth(buttonWidth);
        gridPane.add(comboBoxInstruc, 0, 1);

        comboBoxVars.setPrefWidth(buttonWidth);
        gridPane.add(comboBoxVars, 1, 1);

        comboBoxWebPage.setPrefWidth(buttonWidth);
        gridPane.add(comboBoxWebPage, 2, 1);

        variableButton.setPrefWidth(buttonWidth);
        gridPane.add(variableButton, 0, 2);

        submitButton.setPrefWidth(buttonWidth);
        gridPane.add(submitButton, 2, 2);

        cancelButton.setPrefWidth(buttonWidth);
        gridPane.add(cancelButton, 2, 3);

        ////        gridPane.add(buttonsBox, 2, 1);
        ////
        //        VBox buttonsBox = new VBox(10, submitButton, cancelButton);
        //        buttonsBox.setAlignment(Pos.CENTER);
        //        buttonsBox.setSpacing(10);
        //
        //        gridPane.add(buttonsBox, 3, 1);

        HBox hBoxGridPane = new HBox(gridPane);
        hBoxGridPane.setAlignment(Pos.CENTER);
        hBoxGridPane.setSpacing(10); // Horizontal spacing around the gridPane

        // Create main layout
        VBox vbox = new VBox(20, hBoxGridPane);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(10)); // Padding around the VBox

        // Adjust VBox properties for better alignment
        VBox.setVgrow(hBoxGridPane, Priority.ALWAYS);

        // Use AnchorPane to ensure the VBox resizes with the window
        mainPane = new AnchorPane(vbox);
        mainPane.getStylesheets().add(css);

        AnchorPane.setTopAnchor(vbox, 0.0);
        AnchorPane.setBottomAnchor(vbox, 0.0);
        AnchorPane.setLeftAnchor(vbox, 0.0);
        AnchorPane.setRightAnchor(vbox, 0.0);
    }

    private void clearData() {
        nameField.clear();
        valueField.clear();
    }

    @Override
    public void initUIBehaviour() {

        // Add a listener to print the ID when the selection changes
        comboBoxWebPage.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<ComboBoxVars>() {
                    @Override
                    public void changed(ObservableValue<? extends ComboBoxVars> observable, ComboBoxVars oldValue, ComboBoxVars newValue) {
                        if (newValue != null) {
                            loadJobVariables(newValue.getVarId());
                            reloadComboVars();
                            comboBoxVars.getSelectionModel().selectFirst();
                        }
                    }
                }
        );

        submitButton.setOnAction(event -> {
            /*String selectedType =
                    stringCheckBox.isSelected() ? "$String" : numericCheckBox.isSelected() ? "#Numeric" : "";

            VariableUserDTO user = new VariableUserDTO(
                    null, selectedType, nameField.getText().trim(), valueField.getText(), botJobId, instructionId);

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
            loadVariablesJob();*/
        });

        // Create delete button
        cancelButton.setOnAction(event -> {
            return;
        });

        variableButton.setOnAction(e -> {
            ABRLogger.getInstance(ABRWebElement.class)
                    .info("creating variable for instruction Name " + instructionName);
            ABRElementValueScene elementValueScene = new ABRElementValueScene(
                    botJobId, comboBoxWebPage.getValue().getVarId(), comboBoxWebPage.getValue().getText());
            elementValueScene.showModal();
            loadJobVariables(comboBoxWebPage.getValue().getVarId());
            reloadComboVars();
            // Set ComboBox to first item
            comboBoxVars.getSelectionModel().selectFirst();
        });
    }

    private void reloadComboVars() {
        if (variablesList != null && variablesList.size() > 0) {
            List<ComboBoxVars> variablesNames = variablesList.stream()
                    .map(variable -> new ComboBoxVars(
                            variable.getType().substring(0, 1) + variable.getName(),
                            variable.getInstructionId(),
                            variable.getValue()))
                    .collect(Collectors.toList());
            variablesItems.addAll(variablesNames);
        } else {
            variablesItems.add(new ComboBoxVars("no variables added", -1, ""));
        }
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
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

    private void loadJobVariables(int instructionId) {
        variablesItems.clear();
        variablesList.clear();
        String selectSQL = " SELECT vars.id, vars.type, vars.name, vars.value, COUNT(blk.variable_id) UsedVars "
                + " FROM variable vars "
                + " left join block_loop_instruction blk on blk.variable_id = vars.id "
                + " where bot_job_id = " + botJobId
                + " and  block_loop_instruction_id = " + instructionId
                + " group by vars.id, vars.type, vars.Name, vars.value ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String value = rs.getString("value");
                String usedVars = rs.getString("UsedVars");
                variablesList.add(new VariableUserDTO(id, type, name, value, botJobId, instructionId, usedVars));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private void saveUserData(VariableUserDTO user) {
        // Generate a Unique-ID
        Integer hashCode = loadNexIdData() + 1;
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
                    + " type = '" + user.getType() + "', "
                    + " value = '" + user.getValue() + "' "
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
                        "MAX CHARACTERS LIMIT FOR ACCESS",
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
