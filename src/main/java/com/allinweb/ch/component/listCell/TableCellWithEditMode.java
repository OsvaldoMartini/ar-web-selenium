package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class TableCellWithEditMode extends TableCell<BlockLoopInstructionLoadDTO, String> {

    private final TextField textField = new TextField();
    private final Button saveButton = new Button();
    private final Label nameLabel = new Label();
    private final HBox hbox = new HBox(textField, saveButton);

    {
        // Set up the save button and behavior here
        Image saveImage = new Image(getClass().getResourceAsStream("/save.png"));
        ImageView saveImageView = new ImageView(saveImage);
        saveImageView.setFitWidth(16);
        saveImageView.setFitHeight(16);
        saveButton.setGraphic(saveImageView);

        hbox.setSpacing(5);

        // Save button action
        saveButton.setOnAction(event -> {
            BlockLoopInstructionLoadDTO data = getTableView().getItems().get(getIndex());
            data.setName(textField.getText()); // Update the name in the data object
            nameLabel.setText(textField.getText()); // Update label to show the new name

            // Hide TextField and Save button, show the label
            textField.setVisible(false);
            saveButton.setVisible(false);
            nameLabel.setVisible(true);

            setGraphic(nameLabel); // Set the graphic back to the label
            System.out.println("Save button clicked: " + textField.getText());
        });
    }

    @Override
    public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setGraphic(null);
        } else {
            nameLabel.setText(item);
            textField.setText(item);
            setGraphic(nameLabel); // Display the label when TextField and Save button are hidden
        }
    }

    // Custom method to show the TextField and Save button
    public void showEditMode() {
        nameLabel.setVisible(false); // Hide the label
        textField.setVisible(true); // Show the TextField
        saveButton.setVisible(true); // Show the Save button
        setGraphic(hbox); // Set the graphic to the HBox with TextField and Save button
    }
}
