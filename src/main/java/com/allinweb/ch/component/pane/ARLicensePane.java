package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.licence.LicenseManager;
import com.allinweb.ch.util.ARConstants;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ARLicensePane extends ARPane {

    private static final ARComponentBuilder builder = new ARComponentBuilder();

    private Button btnLicense; // Declare the License button

    private Pane mainPane;
    private CheckBox cbAgree;
    private Button btnProceed;
    private Button btnClose;

    private RadioButton rbRequestLicense;
    private RadioButton rbActivateLicense;
    private TextField tfLicenseOwner;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Initialize the License button
        //        LicenseManager.showAlert(
        //                Alert.AlertType.INFORMATION, LicenseManager.checkLicenseFile().getStaus() + "\n\nPress OK to
        // proceed.");

        // Header label for the application
        // TextArea for the License Agreement
        TextArea taLicenseAgreement = new TextArea(
                """
                SOFTWARE LICENSE AGREEMENT

                Important - Read Carefully: This License Agreement ("Agreement") is a legal contract between you
                (an individual or a legal entity) and [Your Company Name] ("Licensor") for the software that accompanies
                this agreement, which includes associated software and media material, whether printed, electronic, or online ("Software").

                1. License Grant:
                   Subject to the terms of this Agreement, the Licensor grants you a non-exclusive,
                   non-transferable license to use the Software for internal purposes according to the following limitations
                   and in compliance with the provided documentation.

                2. Restrictions:
                   You are not authorized to:
                   - Modify, translate, adapt, or create derivative works from the Software.
                   - Reverse engineer, decompile, disassemble, or otherwise attempt to discover the Software's source code.
                   - Resell, rent, sublicense, distribute, or otherwise transfer the Software without prior written consent from the Licensor.
                   - Remove any copyright notices, trademarks, or other proprietary notices included in the Software.

                3. Ownership of the Software:
                   The Software is protected by copyright laws and international treaties, as well as other intellectual
                   property laws and treaties. The Software is licensed, not sold.

                4. Limited Warranty:
                   The Licensor warrants that the Software will operate substantially in accordance with the documentation
                   for a period of ninety (90) days from the date of your purchase. Any replacement Software will be
                   warranted for the remainder of the original warranty period or for thirty (30) days, whichever is longer.

                5. Limitation of Liability:
                   In no event shall the Licensor be liable for special, incidental, indirect, or consequential damages
                   resulting from the use or inability to use the Software, even if the Licensor has been advised of
                   the possibility of such damages. In no event shall the Licensor's liability for damages exceed
                   the amount paid to purchase the Software.

                6. Termination:
                   This Agreement remains in effect until terminated. This Agreement will automatically terminate without notice
                   from the Licensor if you fail to comply with any term or condition of this Agreement.

                7. Miscellaneous:
                   This Agreement constitutes the entire agreement between you and the Licensor and supersedes all prior
                   communications, proposals, or agreements, whether verbal or written, regarding the Software.
                """);

        Label headerLabel = new Label("AR Web Activation software required");
        headerLabel.setStyle("-fx-text-fill: white; " + // Keep text color white
                "-fx-font-size: 14px; "
                + "-fx-padding: 10;");

        HBox headerContainer = new HBox(headerLabel);
        headerContainer.setStyle("-fx-background-color: #0078d7;"); // Blue background
        headerContainer.setPadding(new Insets(10));
        headerContainer.setAlignment(Pos.CENTER_LEFT); // Align text to the left

        HBox.setHgrow(headerLabel, Priority.ALWAYS);
        HBox.setHgrow(headerContainer, Priority.ALWAYS);

        // ToggleGroup for exclusive RadioButton selection
        ToggleGroup toggleGroup = new ToggleGroup();

        rbRequestLicense = new RadioButton("Request License");
        rbRequestLicense.setToggleGroup(toggleGroup);
        rbRequestLicense.setSelected(true);

        rbActivateLicense = new RadioButton("Activate with License");
        rbActivateLicense.setToggleGroup(toggleGroup);

        HBox radioButtonsBox = new HBox(10, rbRequestLicense, rbActivateLicense);
        radioButtonsBox.setPadding(new Insets(10));

        taLicenseAgreement.setWrapText(true);
        taLicenseAgreement.setEditable(false);

        tfLicenseOwner = new TextField();
        tfLicenseOwner.setPromptText("Licensed to (Owner of the license, min 6 chars)");

        // Checkbox to agree
        cbAgree = new CheckBox("Agree");
        cbAgree.setPadding(new Insets(10));

        // Button to proceed
        btnProceed = builder.buildButton("Procedere");
        btnProceed.setDisable(true);

        btnClose = builder.buildButton("Close");

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        HBox actionButtonsBox = new HBox(10, btnProceed, btnClose);
        actionButtonsBox.setPadding(new Insets(10));

        VBox mainLayout = new VBox(
                10, headerContainer, radioButtonsBox, taLicenseAgreement, tfLicenseOwner, cbAgree, actionButtonsBox);
        mainLayout.setPadding(new Insets(10));
        mainLayout.setFillWidth(true); // Ensure components stretch horizontally

        VBox.setVgrow(taLicenseAgreement, Priority.ALWAYS);

        AnchorPane.setTopAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(mainLayout, ARConstants.SPACE_M);

        mainPane = new AnchorPane(mainLayout);
    }

    @Override
    public void initUIBehaviour() {
        // Additional behavior for the button can be added here if needed
        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        btnClose.setOnAction(event -> {
            Stage stage = (Stage) btnClose.getScene().getWindow();
            stage.close();
        });

        // Actions for Proceed button
        btnProceed.setOnAction(event -> {
            if (!cbAgree.isSelected()) {
                LicenseManager.showAlert(Alert.AlertType.ERROR, "Please agree to the terms to proceed.");
            } else
                try {
                    if (tfLicenseOwner.getText().isEmpty()) {
                        LicenseManager.showAlert(Alert.AlertType.ERROR, "The 'Licensed to' field is required.");
                    } else {
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
                    }
                } catch (Exception error) {
                    // TODO Auto-generated catch block
                    LicenseManager.showAlert(Alert.AlertType.ERROR, "An error occurred: " + error.getMessage());
                }
        });
    }
}
