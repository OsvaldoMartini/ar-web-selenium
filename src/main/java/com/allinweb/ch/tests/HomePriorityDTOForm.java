package com.allinweb.ch.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class HomePriorityDTOForm extends Application {

    private List<HomePriorityDTO> dtoList;
    private int currentIndex = 0;

    private TextField nameField;
    private TextArea priorityField;

    private Button prevButton;
    private Button nextButton;
    private Button updateButton;
    private Button insertButton;
    private Button deleteButton;

    private boolean isNewState = false;

    @Override
    public void start(Stage primaryStage) {
        // Create sample data with 10 rows
        dtoList = createSampleData();

        // Create text fields for each field in the selected DTO
        nameField = new TextField();
        priorityField = new TextArea();

        // Create labels for the text fields
        Label nameLabel = new Label("Name:");
        Label priorityLabel = new Label("Priority:");

        // Set initial data
        updateFields();

        // Create navigation buttons
        prevButton = new Button("Previous");
        prevButton.setOnAction(e -> {
            currentIndex = Math.max(0, currentIndex - 1);
            updateFields();
        });

        nextButton = new Button("Next");
        nextButton.setOnAction(e -> {
            currentIndex = Math.min(dtoList.size() - 1, currentIndex + 1);
            updateFields();
        });

        // Create delete button
        deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> {
            if (!dtoList.isEmpty() && !isNewState) {
                boolean confirmDelete = showConfirmationDialog(nameField.getText());
                if (confirmDelete) {
                    dtoList.remove(currentIndex);
                    if (currentIndex >= dtoList.size()) {
                        currentIndex = Math.max(0, dtoList.size() - 1);
                    }
                    updateFields();
                }
            }
        });

        // Create update button
        updateButton = new Button("Update");
        updateButton.setOnAction(e -> {
            if (!dtoList.isEmpty()) {
                if (isNewState) {
                    if (nameField.getText().isEmpty() || priorityField.getText().isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Validation Error", "Name and Priority cannot be empty.");
                        return;
                    }

                    // Check if the name already exists
                    if (nameExists(nameField.getText())) {
                        showAlert(
                                Alert.AlertType.ERROR,
                                "Validation Error",
                                "Name '" + nameField.getText() + "' already exists.");
                        return;
                    }
                }

                HomePriorityDTO selectedDTO = dtoList.get(currentIndex);
                selectedDTO.setPriority(priorityField.getText());
                selectedDTO.setName(nameField.getText());
                showAlert(
                        Alert.AlertType.INFORMATION,
                        "Update",
                        isNewState
                                ? "The new record with the name '" + nameField.getText() + "' has been created."
                                : "Record updated successfully.");
                enableNavigationButtons();
                updateButton.setText("Update");

                if (isNewState) {
                    insertButton.setText("New");
                    isNewState = false;
                }
            }
        });

        // Create insert button
        insertButton = new Button("New");
        insertButton.setOnAction(e -> {
            if (!isNewState) {
                prevButton.setDisable(true);
                nextButton.setDisable(true);
                deleteButton.setDisable(true);
                updateButton.setText("Save");
                clearFields();
                insertButton.setText("Cancel");
                isNewState = true;
            } else {
                isNewState = false;
                insertButton.setText("New");
                prevButton.setDisable(false);
                nextButton.setDisable(false);
                deleteButton.setDisable(false);
                updateButton.setText("Update");
                updateFields(); // Update fields when canceling insert
            }
        });

        // Create a HBox to hold the navigation buttons
        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(prevButton, nextButton, deleteButton, updateButton, insertButton);

        // Create a VBox to hold the labels, text fields, and buttons
        VBox vbox = new VBox(10); // spacing between elements
        vbox.setPadding(new Insets(10)); // padding around the VBox
        vbox.getChildren().addAll(nameLabel, nameField, priorityLabel, priorityField, buttonBox);

        // Create the scene and set it on the stage
        Scene scene = new Scene(vbox, 350, 350);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HomePriorityDTOForm");
        primaryStage.show();
    }

    private List<HomePriorityDTO> createSampleData() {
        List<HomePriorityDTO> dataList = new ArrayList<>();
        // Assuming you have a constructor in HomePriorityDTO
        for (int i = 0; i < 10; i++) {
            dataList.add(new HomePriorityDTO("Name " + i, "Priority " + i));
        }
        return dataList;
    }

    private void updateFields() {
        if (!dtoList.isEmpty()) {
            HomePriorityDTO selectedDTO = dtoList.get(currentIndex);
            nameField.setText(selectedDTO.getName());
            priorityField.setText(selectedDTO.getPriority());
        } else {
            // If the list is empty, clear the text fields
            clearFields();
        }
    }

    private void clearFields() {
        nameField.clear();
        priorityField.clear();
    }

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

    private void enableNavigationButtons() {
        prevButton.setDisable(false);
        nextButton.setDisable(false);
        deleteButton.setDisable(false);
    }

    private boolean nameExists(String name) {
        for (HomePriorityDTO dto : dtoList) {
            if (dto.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        launch(args);
    }

    // HomePriorityDTO class definition
    static class HomePriorityDTO {
        private String name;
        private String priority;

        public HomePriorityDTO(String name, String priority) {
            this.name = name;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }
    }
}
