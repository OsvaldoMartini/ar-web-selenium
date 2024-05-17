package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class ListViewWithBoundInParent extends Application {

    private ListView<String> listView1;
    private ListView<String> listView2;
    private Button previousButton;

    @Override
    public void start(Stage primaryStage) {
        // Create observable array lists with sample data
        ObservableList<String> list1 = FXCollections.observableArrayList("Item 1", "Item 2", "Item 3");
        ObservableList<String> list2 = FXCollections.observableArrayList("Apple", "Banana", "Orange");
        ObservableList<String> list3 = FXCollections.observableArrayList("Dog", "Cat", "Bird");

        // Create list views to display the observable lists
        ListView<String> listView1 = new ListView<>(list1);
        ListView<String> listView2 = new ListView<>(list2);
        ListView<String> listView3 = new ListView<>(list3);

        // Create Buttons
        Button moveRightButton = new Button("Move List2 Right");
        Button moveLeftButton = new Button("Reset List2 Position");

        // Create AnchorPane for the buttons
        AnchorPane buttonAnchorPane = new AnchorPane();
        buttonAnchorPane.getChildren().addAll(moveRightButton, moveLeftButton);

        // Set the layout constraints for the buttons
        AnchorPane.setTopAnchor(moveRightButton, 0.0);
        AnchorPane.setLeftAnchor(moveRightButton, 10.0);
        AnchorPane.setTopAnchor(moveLeftButton, 0.0);
        AnchorPane.setLeftAnchor(moveLeftButton, 100.0);

        // Create AnchorPane for the main content
        AnchorPane mainAnchorPane = new AnchorPane();
        mainAnchorPane.getChildren().addAll(listView1, listView2, listView3, buttonAnchorPane);

        // Set the layout constraints for the Button HBox
        AnchorPane.setTopAnchor(buttonAnchorPane, 0.0);
        AnchorPane.setLeftAnchor(buttonAnchorPane, 10.0);

        // Set the layout constraints for the ListViews
        AnchorPane.setLeftAnchor(listView1, 10.0);
        //        AnchorPane.setTopAnchor(listView1, 30.0);
        //        AnchorPane.setTopAnchor(listView2, 30.0);
        //        AnchorPane.setLeftAnchor(listView2, 10.0 + listView1.getBoundsInLocal().getWidth() + 5.0);
        //        AnchorPane.setLeftAnchor(listView2, 10.0 + listView1.getBoundsInLocal().getWidth() + 5.0);

        //        // Set up event handler for the "Move List2 Right" button
        //        moveRightButton.setOnAction(event -> moveListView2Right(moveRightButton));

        // Add event handler for the "Move List2 Right" button
        moveRightButton.setOnAction(event -> {
            // Move listView2 right by changing its left anchor
            double newLeftAnchor = AnchorPane.getLeftAnchor(listView2) + 10; // Move listView2 right by 10 pixels
            AnchorPane.setLeftAnchor(listView2, newLeftAnchor);
            AnchorPane.setLeftAnchor(listView3, AnchorPane.getLeftAnchor(listView3) - 10.0);
        });

        // Add event handler for the "Reset List2 Position" button
        moveLeftButton.setOnAction(event -> {
            // Reset listView2 to its initial position
            AnchorPane.setLeftAnchor(listView2, AnchorPane.getLeftAnchor(listView2) - 10.0);
            AnchorPane.setLeftAnchor(listView3, AnchorPane.getLeftAnchor(listView3) + 10.0);
        });

        // Set up the scene
        Scene scene = new Scene(mainAnchorPane, 770, 300);

        // Set the scene to the stage and show the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("ListView Side By Side");
        primaryStage.show();

        // After Call the Render Reposition the Objects
        AnchorPane.setLeftAnchor(
                moveLeftButton, moveRightButton.getBoundsInParent().getMaxX() + 5.0);
        AnchorPane.setTopAnchor(listView1, buttonAnchorPane.getBoundsInLocal().getHeight());

        AnchorPane.setTopAnchor(listView2, buttonAnchorPane.getBoundsInLocal().getHeight());
        AnchorPane.setLeftAnchor(listView2, 10.0 + listView1.getBoundsInLocal().getWidth());

        AnchorPane.setTopAnchor(listView3, buttonAnchorPane.getBoundsInLocal().getHeight());
        AnchorPane.setLeftAnchor(listView3, 10.0 + (listView1.getBoundsInLocal().getWidth() * 2));
    }

    private void moveListView2Right(Button currentButton) {
        double newLeftAnchor;
        if (previousButton != null) {
            double previousButtonRight = previousButton.getBoundsInParent().getMaxX();
            newLeftAnchor = previousButtonRight
                    + 10; // Move listView2 right by 10 pixels from the right edge of the previous button
        } else {
            newLeftAnchor = 10.0 + listView1.getBoundsInLocal().getWidth() + 5.0; // Initial position
        }
        AnchorPane.setLeftAnchor(listView2, newLeftAnchor);
        previousButton = currentButton;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
