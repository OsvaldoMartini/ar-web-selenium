package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.util.ABRConstants;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class ABRExcelFilePane extends ABRPane {

    private Button btnLicense; // Declare the License button

    private Pane mainPane;
    private BlockMoveDTO blockExcelDTO;

    public ABRExcelFilePane(BlockMoveDTO blockExcelDTO) {
        this.blockExcelDTO = blockExcelDTO;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Initialize the License button
        btnLicense = new Button("License");
        btnLicense.setId("btnLicense"); // Set an ID for styling purposes
        btnLicense.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 14px;"); // Blue color

        // Optional: Add a hover effect to change button color when hovered
        btnLicense.setOnMouseEntered(event ->
                btnLicense.setStyle("-fx-background-color: #0056b3; -fx-text-fill: white; -fx-font-size: 14px;"));
        btnLicense.setOnMouseExited(event ->
                btnLicense.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 14px;"));

        // Arrange the button in a VBox layout
        VBox infoGroup = new VBox(10, btnLicense);
        infoGroup.setStyle("-fx-padding: " + ABRConstants.SPACE_M + ";");

        // Set layout constraints for the VBox within the AnchorPane
        AnchorPane.setTopAnchor(infoGroup, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(infoGroup, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(infoGroup, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(infoGroup, ABRConstants.SPACE_M);

        // Create the main pane (AnchorPane) and add the VBox layout to it
        mainPane = new AnchorPane(infoGroup);
    }

    @Override
    public void initUIBehaviour() {
        // Additional behavior for the button can be added here if needed
    }
}
