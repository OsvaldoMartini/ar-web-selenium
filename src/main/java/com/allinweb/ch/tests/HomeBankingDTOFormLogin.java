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

public class HomeBankingDTOFormLogin extends Application {

    private List<HomeBankingDTO> dtoList;
    private int currentIndex = 0;

    private TextField urlField;
    private TextField nameField;
    private TextField usernameField;
    private TextField passwordField;
    private TextField loginUsernameField;
    private PasswordField loginPasswordField;

    private Button prevButton;
    private Button nextButton;
    private Button updateButton;
    private Button insertButton;
    private Button deleteButton;
    private Button loginButton;

    private boolean isNewState = false;

    @Override
    public void start(Stage primaryStage) {
        // Create sample data with 10 rows
        dtoList = createSampleData();

        // Create text fields for each field in the selected DTO
        urlField = new TextField();
        nameField = new TextField();
        usernameField = new TextField();
        passwordField = new TextField();
        loginUsernameField = new TextField();
        loginPasswordField = new PasswordField();

        // Create labels for the text fields
        Label urlLabel = new Label("URL:");
        Label nameLabel = new Label("Name:");
        Label usernameLabel = new Label("Username:");
        Label passwordLabel = new Label("Password:");
        Label loginUsernameLabel = new Label("Login Username:");
        Label loginPasswordLabel = new Label("Login Password:");

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
                    if (nameField.getText().isEmpty() || urlField.getText().isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Validation Error", "Name and URL cannot be empty.");
                        return;
                    }

                    // Check if the name already exists
                    if (nameExists(nameField.getText())) {
                        showAlert(Alert.AlertType.ERROR, "Validation Error", "Name already exists.");
                        return;
                    }
                }

                HomeBankingDTO selectedDTO = dtoList.get(currentIndex);
                selectedDTO.setUrl(urlField.getText());
                selectedDTO.setName(nameField.getText());
                selectedDTO.setUsername(usernameField.getText());
                selectedDTO.setPassword(passwordField.getText());
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

        // Create login button
        loginButton = new Button("Login");
        loginButton.setOnAction(e -> {
            String enteredUsername = loginUsernameField.getText();
            String enteredPassword = loginPasswordField.getText();
            if (validateLogin(enteredUsername, enteredPassword)) {
                showAlert(Alert.AlertType.INFORMATION, "Login Successful", "You are now logged in.");
                loginUsernameField.clear();
                loginPasswordField.clear();
            } else {
                showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid username or password.");
            }
        });

        // Create a HBox to hold the navigation buttons
        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(prevButton, nextButton, deleteButton, updateButton, insertButton);

        // Create a VBox to hold the labels, text fields, and buttons
        VBox vbox = new VBox(10); // spacing between elements
        vbox.setPadding(new Insets(10)); // padding around the VBox
        vbox.getChildren()
                .addAll(
                        urlLabel,
                        urlField,
                        nameLabel,
                        nameField,
                        usernameLabel,
                        usernameField,
                        passwordLabel,
                        passwordField,
                        loginUsernameLabel,
                        loginUsernameField,
                        loginPasswordLabel,
                        loginPasswordField,
                        loginButton,
                        buttonBox);

        // Create the scene and set it on the stage
        Scene scene = new Scene(vbox, 350, 350);
        primaryStage.setScene(scene);
        primaryStage.setTitle("HomeBankingDTO Form");
        primaryStage.show();
    }

    private List<HomeBankingDTO> createSampleData() {
        List<HomeBankingDTO> dataList = new ArrayList<>();
        // Assuming you have a constructor in HomeBankingDTO
        for (int i = 0; i < 10; i++) {
            dataList.add(new HomeBankingDTO("URL " + i, "Name " + i, "Username " + i, "Password " + i));
        }
        return dataList;
    }

    private void updateFields() {
        if (!dtoList.isEmpty()) {
            HomeBankingDTO selectedDTO = dtoList.get(currentIndex);
            urlField.setText(selectedDTO.getUrl());
            nameField.setText(selectedDTO.getName());
            usernameField.setText(selectedDTO.getUsername());
            passwordField.setText(selectedDTO.getPassword());
        } else {
            // If the list is empty, clear the text fields
            clearFields();
        }
    }

    private void clearFields() {
        urlField.clear();
        nameField.clear();
        usernameField.clear();
        passwordField.clear();
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

    private boolean validateLogin(String username, String password) {
        // Simple validation, you can replace it with your actual login logic
        return username.equals("admin") && password.equals("admin123");
    }

    private boolean nameExists(String name) {
        for (HomeBankingDTO dto : dtoList) {
            if (dto.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        launch(args);
    }

    // HomeBankingDTO class definition
    static class HomeBankingDTO {
        private String url;
        private String name;
        private String username;
        private String password;

        public HomeBankingDTO(String url, String name, String username, String password) {
            this.url = url;
            this.name = name;
            this.username = username;
            this.password = password;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
