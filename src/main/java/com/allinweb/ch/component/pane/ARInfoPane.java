package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARLicenseScene;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class ARInfoPane extends ARPane {

    private Label applicationNameLabel;
    private Label compileDateLabel;
    private Label expirationDateLabel;
    private Label copyrightLabel;
    private Label rightsReservedLabel;

    private Button btnLicense; // Declare the License button

    private Pane mainPane;

    private static final ARPropertyManager arPropertyManager;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Initialize labels with their corresponding text
        applicationNameLabel = new Label(arPropertyManager.getProperty(ARPropertyEnum.VERSION));
        compileDateLabel = new Label(arPropertyManager.getProperty(ARPropertyEnum.BUILD));
        expirationDateLabel = new Label("Expiration :" + arPropertyManager.getProperty(ARPropertyEnum.EXPIRATION));
        copyrightLabel = new Label("Copyright Allinweb AG");
        rightsReservedLabel = new Label("All rights reserved");

        // Initialize the License button
        btnLicense = new Button("License");
        btnLicense.setId("btnLicense"); // Set an ID for styling purposes
        btnLicense.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 14px;"); // Blue color

        // Optional: Add a hover effect to change button color when hovered
        btnLicense.setOnMouseEntered(event ->
                btnLicense.setStyle("-fx-background-color: #0056b3; -fx-text-fill: white; -fx-font-size: 14px;"));
        btnLicense.setOnMouseExited(event ->
                btnLicense.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 14px;"));

        // Arrange labels and button in a VBox layout for a clean vertical arrangement
        VBox infoGroup = new VBox(
                10,
                applicationNameLabel,
                compileDateLabel,
                expirationDateLabel,
                copyrightLabel,
                rightsReservedLabel,
                btnLicense);
        infoGroup.setStyle("-fx-padding: " + ARConstants.SPACE_M + ";");

        // Set layout constraints for the VBox within the AnchorPane
        AnchorPane.setTopAnchor(infoGroup, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(infoGroup, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(infoGroup, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(infoGroup, ARConstants.SPACE_M);

        // Create the main pane (AnchorPane) and add the VBox layout to it
        mainPane = new AnchorPane(infoGroup);
    }

    @Override
    public void initUIBehaviour() {
        // Additional behavior for the button can be added here if needed
        btnLicense.setOnMouseClicked(e -> new ARLicenseScene().showModal());
    }
}
