package com.allinweb.ch.tests;

import com.allinweb.ch.util.ABRConstants;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ComboBoxImageExample extends Application {

    private ComboBox<ComboBoxItem> comboBox;
    private ObservableList<ComboBoxItem> items;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Initialize items with images and text
        items = FXCollections.observableArrayList(
                new ComboBoxItem("nothing", new Image(ABRConstants.ICON_BLANK)),
                new ComboBoxItem("setValue", new Image(ABRConstants.ICON_SET_VALUE_BTN)),
                new ComboBoxItem("getValue", new Image(ABRConstants.ICON_GET_VALUE_BTN)));

        // Create ComboBox
        comboBox = new ComboBox<>(items);
        comboBox.setPrefWidth(120); // Set preferred width of ComboBox

        // Set cell factory to display images and text
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(20); // Set the width for icon size
                    imageView.setFitHeight(20); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    imageView.setStyle("-fx-font-size: 18px; -fx-text-fill: blue;");
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                    //                    setStyle("-fx-background-color: none;"); // Ensure no background
                }
            }
        });

        comboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    ImageView imageView = new ImageView(item.getImage());
                    imageView.setFitWidth(20); // Set the width for icon size
                    imageView.setFitHeight(20); // Set the height for icon size
                    imageView.setPreserveRatio(true);
                    imageView.setStyle("-fx-font-size: 18px; -fx-text-fill: blue;");
                    setGraphic(imageView);
                    setTextFill(Color.BLACK); // Ensure text is black
                    //                    setStyle("-fx-background-color: none;"); // Ensure no background
                }
                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });

        // Button to reload ComboBox items
        javafx.scene.control.Button reloadButton = new javafx.scene.control.Button("Reload Items");
        reloadButton.setOnAction(e -> reloadComboBoxItems());

        // Layout
        VBox layout = new VBox(10, comboBox, reloadButton);
        layout.setPadding(new javafx.geometry.Insets(20));

        // Create the scene
        Scene scene = new Scene(layout, 400, 300);

        // Set the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("ComboBox Image Example");
        primaryStage.show();
    }

    private void reloadComboBoxItems() {
        // Clear existing items
        items.clear();

        // Add new items with images and text
        items.addAll(
                new ComboBoxItem("setValue", createImage(ABRConstants.ICON_SET_VALUE_BTN)),
                new ComboBoxItem("getValue", createImage(ABRConstants.ICON_GET_VALUE_BTN)),
                new ComboBoxItem("NDA", createImage(ABRConstants.ICON_BLANK)));

        // Optionally set a default value after reload
        if (!items.isEmpty()) {
            comboBox.setValue(items.get(0));
        }
    }

    // Helper class to hold text and image
    private static class ComboBoxItem {
        private final String text;
        private final Image image;

        public ComboBoxItem(String text, Image image) {
            this.text = text;
            this.image = image;
        }

        public String getText() {
            return text;
        }

        public Image getImage() {
            return image;
        }
    }

    private Image createImage(String iconPath) {
        Image imageView = new Image(iconPath);
        return imageView;
    }
}
