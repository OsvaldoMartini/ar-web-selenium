package com.allinweb.ch.licence;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LicenseActivationApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        LicenseManager.showAlert(
                Alert.AlertType.INFORMATION, LicenseManager.checkLicenseFile().getStaus() + "\n\nPress OK to proceed.");

        // Header label for the application
        Label headerLabel = new Label("AR Web Activation software required");
        headerLabel.setStyle(
                "-fx-background-color: #0078d7; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10;");
        headerLabel.setMinWidth(500);
        headerLabel.setMaxHeight(Double.MAX_VALUE); // Ensure the label stretches across the top

        // ToggleGroup for exclusive RadioButton selection
        ToggleGroup toggleGroup = new ToggleGroup();

        RadioButton rbRequestLicense = new RadioButton("Request License");
        rbRequestLicense.setToggleGroup(toggleGroup);
        rbRequestLicense.setSelected(true);

        RadioButton rbActivateLicense = new RadioButton("Activate with License");
        rbActivateLicense.setToggleGroup(toggleGroup);

        HBox radioButtonsBox = new HBox(10, rbRequestLicense, rbActivateLicense);
        radioButtonsBox.setPadding(new Insets(10));

        // TextArea for the License Agreement
        TextArea taLicenseAgreement = new TextArea(
                "SOFTWARE LICENSE AGREEMENT\n\n"
                        + "Important - Read Carefully: This License Agreement (\"Agreement\") is a legal contract between you (an individual or a legal entity) and [Your Company Name] (\"Licensor\") for the software that accompanies this agreement, which includes associated software and media material, whether printed, electronic, or online (\"Software\").\n\n"
                        + "1. License Grant: Subject to the terms of this Agreement, the Licensor grants you a non-exclusive, non-transferable license to use the Software for internal purposes according to the following limitations and in compliance with the provided documentation.\n\n"
                        + "2. Restrictions: You are not authorized to:\n"
                        + "   - Modify, translate, adapt, or create derivative works from the Software.\n"
                        + "   - Reverse engineer, decompile, disassemble, or otherwise attempt to discover the Software’s source code.\n"
                        + "   - Resell, rent, sublicense, distribute, or otherwise transfer the Software without prior written consent from the Licensor.\n"
                        + "   - Remove any copyright notices, trademarks, or other proprietary notices included in the Software.\n\n"
                        + "3. Ownership of the Software: The Software is protected by copyright laws and international treaties, as well as other intellectual property laws and treaties. The Software is licensed, not sold.\n\n"
                        + "4. Limited Warranty: The Licensor warrants that the Software will operate substantially in accordance with the documentation for a period of ninety (90) days from the date of your purchase. Any replacement Software will be warranted for the remainder of the original warranty period or for thirty (30) days, whichever is longer.\n\n"
                        + "5. Limitation of Liability: In no event shall the Licensor be liable for special, incidental, indirect, or consequential damages resulting from the use or inability to use the Software, even if the Licensor has been advised of the possibility of such damages. In no event shall the Licensor’s liability for damages exceed the amount paid to purchase the Software.\n\n"
                        + "6. Termination: This Agreement remains in effect until terminated. This Agreement will automatically terminate without notice from the Licensor if you fail to comply with any term or condition of this Agreement.\n\n"
                        + "7. Miscellaneous: This Agreement constitutes the entire agreement between you and the Licensor and supersedes all prior communications, proposals, or agreements, whether verbal or written, regarding the Software.\n");

        taLicenseAgreement.setWrapText(true);
        taLicenseAgreement.setEditable(false);
        ScrollPane scrollPane = new ScrollPane(taLicenseAgreement);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        // TextField for entering the license owner's name
        TextField tfLicenseOwner = new TextField();
        tfLicenseOwner.setPromptText("Licensed to (Owner of the license, min 6 chars)");

        // Checkbox to agree
        CheckBox cbAgree = new CheckBox("Agree");
        cbAgree.setPadding(new Insets(10));

        // Button to proceed
        Button btnProceed = new Button("Proceed");
        btnProceed.setDisable(true); // Initially disabled

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        // Actions for Proceed button
        btnProceed.setOnAction(event -> {
            if (!cbAgree.isSelected()) {
                LicenseManager.showAlert(Alert.AlertType.ERROR, "Please agree to the terms to proceed.");
            } else
                try {
                    if (tfLicenseOwner.getText().isEmpty() && !LicenseManager.importResponseFile()) {
                        LicenseManager.showAlert(Alert.AlertType.ERROR, "The 'Licensed to' field is required.");
                    } else {
                        try {
                            if (rbRequestLicense.isSelected()) {
                                LicenseManager.generateRequestFile(tfLicenseOwner.getText());
                                LicenseManager.showAlert(
                                        Alert.AlertType.INFORMATION, "Request file generated successfully.");
                            } else if (rbActivateLicense.isSelected() && LicenseManager.importResponseFile()) {
                                LicenseManager.showAlert(
                                        Alert.AlertType.INFORMATION, "Licence activated! You can close this Message!");
                            } else {
                                LicenseManager.showAlert(
                                        Alert.AlertType.ERROR, "Response file not found or could not be processed.");
                            }
                        } catch (Exception erro) {
                            LicenseManager.showAlert(Alert.AlertType.ERROR, "An error occurred: " + erro.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        });

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        // Button to close the application
        Button btnClose = new Button("Close");
        btnClose.setOnAction(event -> primaryStage.close());

        HBox actionButtonsBox = new HBox(10, btnProceed, btnClose);
        actionButtonsBox.setPadding(new Insets(10));

        // Main layout
        VBox mainLayout =
                new VBox(10, headerLabel, radioButtonsBox, scrollPane, tfLicenseOwner, cbAgree, actionButtonsBox);
        mainLayout.setPadding(new Insets(10));

        // Set up the scene
        Scene scene = new Scene(mainLayout, 600, 450); // Adjusted window size for better layout
        primaryStage.setTitle("Activation Software Required");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) throws Exception {
        if (!LicenseManager.checkLicenseFile().isActive()) {
            launch(args);
        } else {
            System.out.println("AR Web agree licence terms are activate.\n\nPress OK to proceed.");
            //        Application.launch(LicenceResponseManagerApp.class, args); // Lancia questa
            // applicazione se la
            // condizione  falsa
        }
    }
}
