package com.allinweb.ch.tests;

import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class BlockLoopInstructionTableView extends Application {

    private TableView<BlockLoopInstructionLoadDTO> createInstructionTable() {
        TableView<BlockLoopInstructionLoadDTO> tableView = new TableView<>();

        // Create columns
        TableColumn<BlockLoopInstructionLoadDTO, Integer> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<BlockLoopInstructionLoadDTO, Integer> instructionOrderColumn =
                new TableColumn<>("Instruction Order");
        instructionOrderColumn.setCellValueFactory(new PropertyValueFactory<>("instructionOrderNumber"));

        TableColumn<BlockLoopInstructionLoadDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<BlockLoopInstructionLoadDTO, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Add columns to TableView
        tableView.getColumns().addAll(idColumn, instructionOrderColumn, nameColumn, descriptionColumn);

        return tableView;
    }

    @Override
    public void start(Stage primaryStage) {
        // Sample data
        ObservableList<BlockLoopInstructionLoadDTO> blockLoopInstructions = getBlockLoopInstructions();

        // Group the instructions by blockId
        Map<Integer, List<BlockLoopInstructionLoadDTO>> groupedByBlock =
                blockLoopInstructions.stream().collect(Collectors.groupingBy(BlockLoopInstructionLoadDTO::getBlockId));

        // Create a VBox to display the block names and corresponding instruction tables
        VBox mainVBox = new VBox(10);

        // Loop through each block and its instructions
        for (Map.Entry<Integer, List<BlockLoopInstructionLoadDTO>> entry : groupedByBlock.entrySet()) {
            // Create a Label for the block name
            String blockName = entry.getValue().get(0).getBlockName();
            Label blockLabel = new Label("Block: " + blockName);
            blockLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            // Create UP and DOWN buttons
            Button upButton = new Button("↑"); // Arrow Up symbol
            Button downButton = new Button("↓"); // Arrow Down symbol

            // Create an HBox to place label and buttons
            HBox blockHeader = new HBox(10);
            blockHeader.setAlignment(Pos.CENTER_LEFT);
            blockHeader.setStyle("-fx-padding: 10px;");

            // Create an HBox for buttons, aligning them to the right
            HBox buttonBox = new HBox(5);
            buttonBox.setAlignment(Pos.CENTER_RIGHT);
            buttonBox.getChildren().addAll(upButton, downButton);

            // Add the label and buttons to the header
            blockHeader.getChildren().addAll(blockLabel, buttonBox);
            blockHeader.setSpacing(20); // Add spacing between the label and button box
            HBox.setHgrow(buttonBox, javafx.scene.layout.Priority.ALWAYS); // Make the buttons stay on the right side

            // Create a TableView for the instructions under this block
            TableView<BlockLoopInstructionLoadDTO> tableView = createInstructionTable();

            // Set the items for this block's TableView
            tableView.setItems(FXCollections.observableArrayList(entry.getValue()));

            // Add the block header (with label and buttons) and table to the main VBox
            mainVBox.getChildren().addAll(blockHeader, tableView);
        }

        // Set the VBox as the root layout
        Scene scene = new Scene(mainVBox, 600, 400);

        // Set up the Stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("BlockLoopInstructionLoadDTO TableView with Block Separation and Buttons");
        primaryStage.show();
    }

    // Sample data
    private ObservableList<BlockLoopInstructionLoadDTO> getBlockLoopInstructions() {
        return FXCollections.observableArrayList(
                new BlockLoopInstructionLoadDTO(1, 101, "Instruction 1", "Description 1", 1, "Default Block"),
                new BlockLoopInstructionLoadDTO(2, 102, "Instruction 2", "Description 2", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(3, 103, "Instruction 3", "Description 3", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(4, 104, "Instruction 4", "Description 4", 3, "Block Test 2"),
                new BlockLoopInstructionLoadDTO(5, 105, "Instruction 5", "Description 5", 3, "Block Test 2"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
