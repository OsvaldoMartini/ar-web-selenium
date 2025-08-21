package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARLicenseScene;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class ARInfoPane extends ARPane {

    protected static volatile ARInfoPane instance;

    // Private constructor to prevent instantiation
    private ARInfoPane() {

        super();
    }

    public static ARInfoPane getInstance() {
        if (instance == null) {
            synchronized (ARInfoPane.class) {
                if (instance == null) {
                    instance = new ARInfoPane();
                }
            }
        }
        return instance;
    }

    private boolean isEnabledLicence;

    public void initialize(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }

    private Label applicationNameLabel;
    private Label compileDateLabel;
    private Label expirationDateLabel;
    private Label copyrightLabel;
    private Label rightsReservedLabel;

    private Button btnLicense; // Declare the License button

    private Pane mainPane;

    private static final ARPropertyManager arPropertyManager;
    private static final ARLicenseScene arLicenseScene;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        arLicenseScene = ARLicenseScene.getInstance();
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        Label applicationNameLabel = new Label(arPropertyManager.getProperty(ARPropertyEnum.VERSION));
        Label compileDateLabel = new Label("Build: " + arPropertyManager.getProperty(ARPropertyEnum.BUILD));

        String expirationStr = arPropertyManager.getProperty(ARPropertyEnum.EXPIRATION);
        Label expirationDateLabel = new Label("Expiration: " + expirationStr);

        Label copyrightLabel = new Label("© Allinweb AG");
        Label rightsReservedLabel = new Label("All rights reserved");

        // Styles
        String baseLabelStyle = "-fx-font-size: 13px; -fx-text-fill: #2d3436;";
        String versionStyle = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0984e3;";
        String footerStyle = "-fx-text-fill: #636e72;";

        // Apply version and other static styles
        applicationNameLabel.setStyle(versionStyle);
        compileDateLabel.setStyle(baseLabelStyle);
        copyrightLabel.setStyle(baseLabelStyle + footerStyle);
        rightsReservedLabel.setStyle(baseLabelStyle + footerStyle);

        // Handle expiration color dynamically
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate expirationDate = LocalDate.parse(expirationStr, formatter);
            LocalDate today = LocalDate.now();
            long daysLeft = ChronoUnit.DAYS.between(today, expirationDate);

            String expirationColor = daysLeft > 30 ? "#218c52" : "#c0392b"; // Dark green or dark red
            expirationDateLabel.setStyle(
                    baseLabelStyle + "-fx-font-weight: bold; -fx-text-fill: " + expirationColor + ";");

        } catch (Exception e) {
            // Fallback in case of invalid format
            if (isEnabledLicence) {
                expirationDateLabel.setStyle(baseLabelStyle + "-fx-text-fill: #d63031; -fx-font-weight: bold;");
                expirationDateLabel.setText("⚠ Unlicensed Version – Features May Be Limited");
            } else {
                expirationDateLabel.setStyle(
                        baseLabelStyle + "-fx-text-fill: #3498db; -fx-font-weight: bold;"); // Blue tone
                expirationDateLabel.setText("⚠ Unlicensed Version – Demo Version");
            }
        }

        // VBox container
        VBox versionInfoBox = new VBox(
                5, applicationNameLabel, compileDateLabel, expirationDateLabel, copyrightLabel, rightsReservedLabel);
        versionInfoBox.setPadding(new Insets(12));
        versionInfoBox.setAlignment(Pos.CENTER_LEFT);
        versionInfoBox.setStyle("-fx-background-color: #f1f2f6; " + "-fx-border-color: #dcdde1; "
                + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; "
                + "-fx-background-radius: 6;");

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
        btnLicense.setOnMouseClicked(e -> arLicenseScene.showModal());
    }
}
