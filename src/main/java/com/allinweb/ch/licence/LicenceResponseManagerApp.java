package com.allinweb.ch.licence;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

public class LicenceResponseManagerApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Generate AR Web Licence File App");

        VBox root = new VBox(10);
        root.setPadding(new Insets(20, 10, 10, 10));

        // Header label for the application
        Label headerLabel = new Label("AR Web Licence response file generator");
        headerLabel.setStyle(
                "-fx-background-color: #0078d7; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10;");
        headerLabel.setMinWidth(500);
        headerLabel.setMaxHeight(Double.MAX_VALUE); // Ensure the label stretches across the top
        root.getChildren().add(headerLabel);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 0, 20, 0));

        Label filePathLabel = new Label("Upload AR request file:");
        grid.add(filePathLabel, 0, 0);

        TextField filePathField = new TextField();
        filePathField.setPromptText("Upload AR request file");
        filePathField.setEditable(false);
        grid.add(filePathField, 1, 0);

        Button uploadButton = new Button("Upload");
        uploadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Request AR Web File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
            var file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                filePathField.setText(file.getAbsolutePath());
            }
        });
        grid.add(uploadButton, 2, 0);

        Label daysLabel = new Label("Numero di giorni concessi:");
        grid.add(daysLabel, 0, 1);

        TextField daysField = new TextField();
        daysField.setPromptText("Enter number of days");
        daysField.setTextFormatter(new TextFormatter<>(new IntegerStringConverter(), null, change -> {
            String newText = change.getControlNewText();
            return newText.matches("^[0-9,-]*$") ? change : null;
        }));
        grid.add(daysField, 1, 1);

        Button generateButton = new Button("Generate");
        generateButton.setOnAction(e -> {
            try {
                String decryptedContent = LicenseManager.getDecryptedResponseFile(filePathField.getText());
                new LicenseManager().genereteResponseFile(decryptedContent, Integer.parseInt(daysField.getText()));
                new Alert(
                                Alert.AlertType.INFORMATION,
                                "File ARWeb 1.1.0.response generato con successo.",
                                ButtonType.OK)
                        .showAndWait();
            } catch (Exception ex) {
                new Alert(
                                Alert.AlertType.ERROR,
                                "Errore durante la generazione del file: " + ex.getMessage(),
                                ButtonType.OK)
                        .showAndWait();
            }
        });
        grid.add(generateButton, 1, 2);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> primaryStage.close());
        grid.add(closeButton, 1, 3);

        root.getChildren().add(grid);

        Scene scene = new Scene(root, 600, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
