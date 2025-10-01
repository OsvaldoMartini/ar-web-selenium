package com.allinweb.ch.license;

import com.allinweb.ch.facade.PerformMessage;
import com.google.common.base.Strings;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.ptr.PointerByReference;
import java.io.File;
import java.io.IOException;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LicenceResponseManagerApp extends Application {

    private static final PerformMessage performMessage;

    static {
        performMessage = PerformMessage.getInstance();
    }

    public static void main(String[] args) {
        launch(args);
    }

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

            // Set initial directory to Desktop
            try {
                // Use SHGetKnownFolderPath to get Desktop path
                PointerByReference ppszPath = new PointerByReference();
                if (Shell32.INSTANCE
                                .SHGetKnownFolderPath(KnownFolders.FOLDERID_Desktop, 0, null, ppszPath)
                                .intValue()
                        != 0) {
                    throw new IOException("Failed to get desktop directory.");
                }

                // Convert pointer to string
                String desktopPath = ppszPath.getValue().getWideString(0);
                Native.free(Pointer.nativeValue(ppszPath.getValue()));

                File desktopDir = new File(desktopPath);
                if (desktopDir.exists() && desktopDir.isDirectory()) {
                    fileChooser.setInitialDirectory(desktopDir);
                }

            } catch (Exception ex) {
                ex.printStackTrace(); // fallback to default if desktop not available
            }

            var file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                filePathField.setText(file.getAbsolutePath());
            }

            if (file != null) {
                if (file.getName().endsWith(".request")) {
                    filePathField.setText(file.getAbsolutePath());
                } else {
                    performMessage.errorMessage(
                            "Invalid file selected!",
                            "Must have a '.request' extension.",
                            "File selected:",
                            file.getName(),
                            null,
                            0);
                }
            }
        });

        grid.add(uploadButton, 2, 0);

        Label daysLabel = new Label("Number of days granted:");
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

                if (Strings.isNullOrEmpty(filePathField.getText().trim())) {
                    performMessage.errorMessage(
                            "Error reading the file!", "You have not selected any file!", null, null, null, 0);
                    return;
                }

                if (Strings.isNullOrEmpty(daysField.getText().trim())) {
                    performMessage.errorMessage(
                            "Field empty!", "You must provide the quantity of the Days!!", null, null, null, 0);
                    return;
                }

                String decryptedContent = LicenseManager.getDecryptedResponseFile(filePathField.getText());
                if (decryptedContent.equals("Invalid file selected")) {
                    return;
                }
                String response = new LicenseManager()
                        .genereteResponseFile(decryptedContent, Integer.parseInt(daysField.getText()));
                if (response.equals("File creation success")) {

                    performMessage.errorMessage(
                            "File was Generated successfully!",
                            "File Name:",
                            "ARWeb 1.1.0.response",
                            "Generated successfully.",
                            null,
                            0);
                }
            } catch (Exception ex) {
                performMessage.errorMessage(
                        "Error writing to the file!",
                        "File Name:",
                        "ARWeb 1.1.0.response",
                        "Please verify that you have permission to read/write to the Desktop.",
                        null,
                        0);
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
}
