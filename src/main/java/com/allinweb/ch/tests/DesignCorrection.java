package com.allinweb.ch.tests;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DesignCorrection extends Application {

    // Define your UI elements
    private ExecutorService executorService;
    ProgressBar progressBar;
    Button scanButton = new Button("Scan");
    Button addWaitButton = new Button("Add Wait");
    Button addCloseActionButton = new Button("Add Close Action");
    Button addScreenButton = new Button("Add Screen");
    Button configureButton = new Button("Configure");
    Button launchBotJobButton = new Button("Launch Bot Job");
    CheckBox checkActiveHover = new CheckBox("Active Hover");
    TextField currentXPathTextField = new TextField();
    Button addElement = new Button("Add Element");
    CheckBox checkClickElement = new CheckBox("Click Element");
    CheckBox checkInputText = new CheckBox("Input Text");
    Button refreshInputFieldsButton = new Button("Refresh Input Fields");
    Button searchWithIdsButton = new Button("Search with IDs");
    Button searchWithNamesButton = new Button("Search with Names");
    Button searchWithoutIdsAndNamesBtn = new Button("Search without IDs and Names");
    Button refreshOutputFieldsButton = new Button("Refresh Output Fields");
    Button refreshOtherFieldsButton = new Button("Refresh Other Fields");
    CheckBox checkBoxAction = new CheckBox("Action");
    ListView<String> scannedElements1 = new ListView<>();
    ListView<String> scannedElements2 = new ListView<>();
    ListView<String> scannedElements3 = new ListView<>();
    Pane contentPane = new Pane(); // Assuming you have a contentPane
    Pane topPane = new Pane(); // Assuming you have a topPane
    HBox bottomPane = new HBox(); // Assuming you have a bottomPane
    final double LIST_VIEW_WIDTH = 200.0; // Example width for ListViews

    @Override
    public void start(Stage primaryStage) {

        // Create a GridPane for the top section
        GridPane gridPaneTop = new GridPane();
        gridPaneTop.setPadding(new Insets(10));
        gridPaneTop.setHgap(10); // Set horizontal gap between columns

        // Add buttons and checkbox to the GridPane
        gridPaneTop.add(scanButton, 0, 0);
        gridPaneTop.add(addWaitButton, 1, 0);
        gridPaneTop.add(addCloseActionButton, 2, 0);
        gridPaneTop.add(addScreenButton, 3, 0);
        gridPaneTop.add(configureButton, 4, 0);
        gridPaneTop.add(launchBotJobButton, 5, 0);
        gridPaneTop.add(checkActiveHover, 6, 0);
        gridPaneTop.add(currentXPathTextField, 7, 0);
        gridPaneTop.add(addElement, 8, 0);

        VBox vBox = new VBox();
        vBox.getChildren().addAll(checkClickElement, checkInputText);
        vBox.setSpacing(6); // Adjust spacing between CheckBoxes
        gridPaneTop.add(vBox, 9, 0);

        topPane.getChildren().add(gridPaneTop); // Add gridPaneTop to topPane

        VBox verticalBox = new VBox();
        verticalBox.setSpacing(10);
        verticalBox.setPadding(new Insets(10));
        VBox.setVgrow(verticalBox, Priority.ALWAYS);

        // Create a GridPane for the middle section
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setHgap(10); // Set horizontal gap between columns

        // Add buttons and checkbox to the GridPane
        gridPane.add(refreshInputFieldsButton, 0, 0);
        gridPane.add(searchWithIdsButton, 1, 0);
        gridPane.add(searchWithNamesButton, 2, 0);
        gridPane.add(searchWithoutIdsAndNamesBtn, 3, 0);
        gridPane.add(refreshOutputFieldsButton, 4, 0);
        gridPane.add(refreshOtherFieldsButton, 5, 0);
        gridPane.add(checkBoxAction, 6, 0);

        HBox boxListViews = new HBox();

        // Bind the height of ListViews to the height of the HBox
        scannedElements1.prefHeightProperty().bind(boxListViews.heightProperty());
        scannedElements2.prefHeightProperty().bind(boxListViews.heightProperty());
        scannedElements3.prefHeightProperty().bind(boxListViews.heightProperty());

        boxListViews.setSpacing(5);

        // Set Hgrow for each ListView to make them equally distributed
        HBox.setHgrow(scannedElements1, Priority.ALWAYS);
        HBox.setHgrow(scannedElements2, Priority.ALWAYS);
        HBox.setHgrow(scannedElements3, Priority.ALWAYS);

        boxListViews.getChildren().addAll(scannedElements1, scannedElements2, scannedElements3);

        VBox.setVgrow(boxListViews, Priority.ALWAYS);

        verticalBox.getChildren().addAll(gridPane, boxListViews, bottomPane);
        VBox.setVgrow(verticalBox, Priority.ALWAYS);

        // Create and add ProgressBar to the bottomPane
        progressBar = new ProgressBar();
        addNodesToPane(bottomPane, progressBar);
        progressBar = new ProgressBar();
        addNodesToPane(bottomPane, progressBar);
        progressBar = new ProgressBar();
        addNodesToPane(bottomPane, progressBar);
        progressBar = new ProgressBar();
        addNodesToPane(bottomPane, progressBar);
        progressBar = new ProgressBar();
        VBox.setVgrow(bottomPane, Priority.NEVER);

        // Instantiate
        executorService = Executors.newSingleThreadExecutor();

        // Execute anywhere
        executorService.execute(() -> {
            try {
                // Wait for 5 seconds

                // Remove nodes from the pane on the JavaFX Application Thread
                while (bottomPane.getChildren().size() > 0) {
                    Platform.runLater(() -> {
                        removeNodesFromPane(bottomPane);
                    });

                    TimeUnit.SECONDS.sleep(2);
                }
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
        });

        // Shutdown the executor service
        executorService.shutdown();

        // Use AnchorPane to anchor components
        AnchorPane.setTopAnchor(topPane, 0.0);
        AnchorPane.setLeftAnchor(topPane, 0.0);
        AnchorPane.setRightAnchor(topPane, 0.0);

        AnchorPane.setTopAnchor(verticalBox, 0.0);
        AnchorPane.setBottomAnchor(verticalBox, 0.0);
        AnchorPane.setLeftAnchor(verticalBox, 0.0);
        AnchorPane.setRightAnchor(verticalBox, 0.0);

        contentPane.getChildren().addAll(topPane, verticalBox);

        // Set up the scene and stage
        Scene scene = new Scene(contentPane, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("JavaFX App");
        primaryStage.show();
    }

    private void removeNodesFromPane(Pane bottomPane) {
        if (bottomPane.getChildren().size() > 0) {
            bottomPane
                    .getChildren()
                    .remove(bottomPane
                            .getChildren()
                            .get(bottomPane.getChildren().size() - 1));
        }
    }

    // Method to add nodes to a Pane
    private void addNodesToPane(Pane pane, javafx.scene.Node... nodes) {
        pane.getChildren().addAll(nodes);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
