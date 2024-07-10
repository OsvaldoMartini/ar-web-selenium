package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class NewConfig extends Application {

    // Declare UI components
    private TextField idField, nameField, urlField, jobsField, searchConfigField, optionsConfigField;
    private TextArea priorityField;
    private TableView<DatabaseUserDTO> tableView;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JavaFX Application");

        // Initialize UI components
        Label idLabel = new Label("ID:");
        idField = new TextField();

        Label nameLabel = new Label("Name:");
        nameField = new TextField();

        Label urlLabel = new Label("Url:");
        urlField = new TextField();

        Label priorityLabel = new Label("Priority Identifier:");
        priorityField = new TextArea();
        priorityField.setPrefRowCount(2); // Span the TextArea over 2 rows

        Label jobsLabel = new Label("Jobs:");
        jobsField = new TextField();

        Label searchConfigLabel = new Label("Search Config:");
        searchConfigField = new TextField();

        Label optionsConfigLabel = new Label("WebDriver Options:");
        optionsConfigField = new TextField();

        Button submitButton = new Button("Insert");
        Button updateButton = new Button("Update");
        Button deleteButton = new Button("Delete");
        Button templateButton = new Button("Template");

        // Dummy data for testing
        ObservableList<DatabaseUserDTO> databaseList = FXCollections.observableArrayList(
                new DatabaseUserDTO(
                        "1", "Job1", "Name1", "http://example1.com", "Priority1", "SearchConfig1", "OptionsConfig1"),
                new DatabaseUserDTO(
                        "2", "Job2", "Name2", "http://example2.com", "Priority2", "SearchConfig2", "OptionsConfig2"));

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
                jobsField.setText(selectedUser.getJobs());
                searchConfigField.setText(selectedUser.getSearchConfig());
                optionsConfigField.setText(selectedUser.getOptionsConfig());
            } else {
                // If no row is selected, clear the text fields
                idField.clear();
                nameField.clear();
                urlField.clear();
                priorityField.clear();
                jobsField.clear();
                searchConfigField.clear();
                optionsConfigField.clear();
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
        tableViewContainer.setPrefWidth(950); // Adjust the preferred height as needed

        Scene scene = new Scene(new AnchorPane(vbox), 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// Dummy DatabaseUserDTO class for demonstration
class DatabaseUserDTO {
    private final SimpleStringProperty id, jobs, name, url, priority, searchConfig, optionsConfig;

    public DatabaseUserDTO(
            String id,
            String jobs,
            String name,
            String url,
            String priority,
            String searchConfig,
            String optionsConfig) {
        this.id = new SimpleStringProperty(id);
        this.jobs = new SimpleStringProperty(jobs);
        this.name = new SimpleStringProperty(name);
        this.url = new SimpleStringProperty(url);
        this.priority = new SimpleStringProperty(priority);
        this.searchConfig = new SimpleStringProperty(searchConfig);
        this.optionsConfig = new SimpleStringProperty(optionsConfig);
    }

    public String getId() {
        return id.get();
    }

    public String getJobs() {
        return jobs.get();
    }

    public String getName() {
        return name.get();
    }

    public String getUrl() {
        return url.get();
    }

    public String getPriority() {
        return priority.get();
    }

    public String getSearchConfig() {
        return searchConfig.get();
    }

    public String getOptionsConfig() {
        return optionsConfig.get();
    }
}
