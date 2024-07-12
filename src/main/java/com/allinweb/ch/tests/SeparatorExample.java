package com.allinweb.ch.tests;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SeparatorExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create UI elements
        CheckBox checkActiveHover = new CheckBox("Active Hover");

        Label currentXPathLabel = new Label("XPath:");
        TextField currentXPathTextField = new TextField();

        Label tagNameTextFieldLabel = new Label("Tag Name:");
        TextField tagNameTextField = new TextField();

        Label coordsTextFieldLabel = new Label("Coordinates:");
        TextField coordsTextField = new TextField();

        CheckBox checkBoxAction = new CheckBox("Action");

        Button addNewElement = new Button("Add New Element");
        Button launchBotJobButton = new Button("Launch Bot Job");
        Button configureButton = new Button("Configure");

        // Create separator line
        Separator separatorLine = new Separator();
        separatorLine.setOrientation(Orientation.HORIZONTAL);
        separatorLine.setValignment(VPos.CENTER); // Extend the line horizontally
        separatorLine.setPrefHeight(80);
        separatorLine.setStyle("-fx-stroke: black;"); // Set line color

        // Create VBox for text fields and buttons
        VBox textFieldVBox = new VBox();
        textFieldVBox.setSpacing(6); // Adjust spacing between elements
        textFieldVBox.setPadding(new Insets(10)); // Optional padding

        // Add elements to VBox
        textFieldVBox
                .getChildren()
                .addAll(
                        checkActiveHover,
                        currentXPathLabel,
                        currentXPathTextField,
                        tagNameTextFieldLabel,
                        tagNameTextField,
                        coordsTextFieldLabel,
                        coordsTextField,
                        separatorLine, // Add separator line
                        checkBoxAction,
                        addNewElement,
                        launchBotJobButton,
                        configureButton);

        // Bind button widths to VBox width
        addNewElement.maxWidthProperty().bind(textFieldVBox.widthProperty());
        launchBotJobButton.maxWidthProperty().bind(textFieldVBox.widthProperty());
        configureButton.maxWidthProperty().bind(textFieldVBox.widthProperty());

        // Set up the scene
        Scene scene = new Scene(textFieldVBox, 400, 300);

        // Set the scene and show the stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("Separator Example");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
