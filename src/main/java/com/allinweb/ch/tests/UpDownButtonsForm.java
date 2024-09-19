package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class UpDownButtonsForm extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create buttons
        Button upButton = new Button();
        Button downButton = new Button();

        // Set preferred button size (fixed size to avoid infinite growth)
        upButton.setPrefSize(16, 16); // Adjust size to your needs
        downButton.setPrefSize(16, 16); // Adjust size to your needs

        // Load images (Ensure that the images are in the resource folder)
        Image upImage = new Image(getClass().getResourceAsStream("/up.png"));
        Image downImage = new Image(getClass().getResourceAsStream("/down.png"));

        // Create ImageViews for buttons
        ImageView upImageView = new ImageView(upImage);
        ImageView downImageView = new ImageView(downImage);

        // Set image size explicitly to fill the button
        upImageView.setFitWidth(16); // Slightly smaller than button to allow padding
        upImageView.setFitHeight(16); // Slightly smaller than button to allow padding
        downImageView.setFitWidth(16); // Slightly smaller than button to allow padding
        downImageView.setFitHeight(16); // Slightly smaller than button to allow padding

        // Set images to buttons
        upButton.setGraphic(upImageView);
        downButton.setGraphic(downImageView);

        // Optional: Set button styles (transparent background)
        // Remove any padding inside the buttons
        upButton.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        downButton.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        // Create an HBox to hold the buttons with minimal spacing between them
        HBox buttonBox = new HBox(1); // Set minimal spacing between buttons (1 pixel)
        buttonBox.getChildren().addAll(upButton, downButton);

        // Create a VBox to position the buttons in the center vertically
        VBox root = new VBox(20); // Set spacing between elements in VBox
        root.getChildren().add(buttonBox);

        // Create the scene
        Scene scene = new Scene(root, 300, 200);

        // Set up the Stage
        primaryStage.setTitle("Up and Down Buttons Form");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
