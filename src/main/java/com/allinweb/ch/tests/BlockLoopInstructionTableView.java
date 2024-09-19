package com.allinweb.ch.tests;

import com.allinweb.ch.component.listCell.TableCellWithEditMode;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import java.util.Collections;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class BlockLoopInstructionTableView extends Application {

    private VBox mainVBox; // Main VBox layout to hold the blocks
    private ObservableList<List<BlockLoopInstructionLoadDTO>> blockDataList; // List of block data

    private TableView<BlockLoopInstructionLoadDTO> createInstructionTable() {
        TableView<BlockLoopInstructionLoadDTO> tableView = new TableView<>();

        // Create columns
        TableColumn<BlockLoopInstructionLoadDTO, Integer> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<BlockLoopInstructionLoadDTO, Integer> instructionOrderColumn =
                new TableColumn<>("Instruction Order");
        instructionOrderColumn.setCellValueFactory(new PropertyValueFactory<>("instructionOrderNumber"));

        // Add a new column for the Name with TextField and Save button
        TableColumn<BlockLoopInstructionLoadDTO, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Use custom TableCellWithEditMode for Name column
        nameColumn.setCellFactory(param -> new TableCellWithEditMode());

        TableColumn<BlockLoopInstructionLoadDTO, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Add a new column for the buttons (UP, DOWN, Action, Edit)
        TableColumn<BlockLoopInstructionLoadDTO, Void> actionColumn = new TableColumn<>("Actions");
        actionColumn.setCellFactory(param -> {
            final TableCell<BlockLoopInstructionLoadDTO, Void> cell = new TableCell<>() {

                private final Button upButton = new Button("↑");
                private final Button downButton = new Button("↓");
                private final Button actionButton = new Button("Action");
                private final Button editButton = new Button();

                {
                    // Load the edit image
                    Image editImage = new Image(getClass().getResourceAsStream("/edit.png"));
                    ImageView editImageView = new ImageView(editImage);
                    editImageView.setFitWidth(16);
                    editImageView.setFitHeight(16);
                    editButton.setGraphic(editImageView);

                    // Define actions for each button
                    upButton.setOnAction(event -> {
                        TableView<BlockLoopInstructionLoadDTO> table = getTableView();
                        ObservableList<BlockLoopInstructionLoadDTO> currentItems = table.getItems();
                        int currentIndex = getIndex();

                        if (currentIndex > 0) {
                            // Swap the current item with the one above
                            Collections.swap(currentItems, currentIndex, currentIndex - 1);

                            // Update instructionOrderNumbers
                            updateInstructionOrderNumbers(currentItems);

                            // Refresh the table view to show the updated order
                            table.refresh();
                        }
                    });

                    downButton.setOnAction(event -> {
                        TableView<BlockLoopInstructionLoadDTO> table = getTableView();
                        ObservableList<BlockLoopInstructionLoadDTO> currentItems = table.getItems();
                        int currentIndex = getIndex();

                        if (currentIndex < currentItems.size() - 1) {
                            // Swap the current item with the one below
                            Collections.swap(currentItems, currentIndex, currentIndex + 1);

                            // Update instructionOrderNumbers
                            updateInstructionOrderNumbers(currentItems);

                            // Refresh the table view to show the updated order
                            table.refresh();
                        }
                    });

                    actionButton.setOnAction(event -> {
                        BlockLoopInstructionLoadDTO data =
                                getTableView().getItems().get(getIndex());
                        System.out.println("Action button clicked for: " + data.getName());
                    });

                    editButton.setOnAction(event -> {
                        BlockLoopInstructionLoadDTO data =
                                getTableView().getItems().get(getIndex());
                        System.out.println("Edit button clicked for: " + data.getName());

                        // Trigger the edit mode in the name column's cell
                        TableRow<BlockLoopInstructionLoadDTO> row = getTableRow();
                        if (row != null) {
                            TableCell<BlockLoopInstructionLoadDTO, String> nameCell =
                                    (TableCell<BlockLoopInstructionLoadDTO, String>)
                                            row.getChildrenUnmodifiable().get(2);
                            if (nameCell instanceof TableCellWithEditMode) {
                                ((TableCellWithEditMode) nameCell).showEditMode();
                            }
                        }
                    });
                }

                @Override
                public void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        HBox buttonsBox = new HBox(upButton, downButton, actionButton, editButton);
                        buttonsBox.setSpacing(5);
                        buttonsBox.setAlignment(Pos.CENTER);
                        setGraphic(buttonsBox);
                    }
                }
            };
            return cell;
        });

        // Add all columns to TableView
        tableView.getColumns().addAll(idColumn, instructionOrderColumn, nameColumn, descriptionColumn, actionColumn);

        return tableView;
    }

    @Override
    public void start(Stage primaryStage) {
        // Sample data
        ObservableList<BlockLoopInstructionLoadDTO> blockLoopInstructions = getBlockLoopInstructions();

        // Group the instructions by blockId
        Map<Integer, List<BlockLoopInstructionLoadDTO>> groupedByBlock =
                blockLoopInstructions.stream().collect(Collectors.groupingBy(BlockLoopInstructionLoadDTO::getBlockId));

        // Initialize the block data list
        blockDataList = FXCollections.observableArrayList(groupedByBlock.values());

        // Create a VBox to display the block names and corresponding instruction tables
        mainVBox = new VBox(0); // No spacing between blocks
        mainVBox.setStyle("-fx-padding: 0;"); // No padding around the VBox

        // Add each block to the VBox
        for (int i = 0; i < blockDataList.size(); i++) {
            addBlockToVBox(i);
        }

        // Set the VBox as the root layout
        Scene scene = new Scene(mainVBox, 600, 400);

        // Set up the Stage
        primaryStage.setScene(scene);
        primaryStage.setTitle("BlockLoopInstructionLoadDTO TableView with Block Separation and Buttons");
        primaryStage.show();
    }

    // Method to add blocks to VBox by index
    private void addBlockToVBox(int index) {
        List<BlockLoopInstructionLoadDTO> instructions = blockDataList.get(index);

        // Create a Label for the block name
        String blockName = instructions.get(0).getBlockName();
        Label blockLabel = new Label("Block: " + blockName);
        blockLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Create UP and DOWN buttons for the block header
        Button upButton = new Button("↑");
        Button downButton = new Button("↓");

        // Create an HBox to place label and buttons
        HBox blockHeader = new HBox(10);
        blockHeader.setAlignment(Pos.CENTER_LEFT);
        blockHeader.setStyle("-fx-padding: 0px;");

        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(upButton, downButton);

        blockHeader.getChildren().addAll(blockLabel, buttonBox);
        blockHeader.setSpacing(20);
        HBox.setHgrow(buttonBox, javafx.scene.layout.Priority.ALWAYS);

        // Create a TableView for the instructions under this block
        TableView<BlockLoopInstructionLoadDTO> tableView = createInstructionTable();
        tableView.setItems(FXCollections.observableArrayList(instructions));

        // Add the block header (with label and buttons) and table to the main VBox
        mainVBox.getChildren().addAll(blockHeader, tableView);

        // UP button action to move the block up
        upButton.setOnAction(event -> {
            if (index > 0) {
                moveBlock(index, index - 1);
            }
        });

        // DOWN button action to move the block down
        downButton.setOnAction(event -> {
            if (index < blockDataList.size() - 1) {
                moveBlock(index, index + 1);
            }
        });
    }

    // Method to move blocks up and down in the VBox and update data list
    private void moveBlock(int currentIndex, int newIndex) {
        if (newIndex >= 0 && newIndex < blockDataList.size()) {
            // Swap the data in the blockDataList
            Collections.swap(blockDataList, currentIndex, newIndex);

            // After swapping blocks, update the instructionOrderNumber sequentially for all blocks
            updateInstructionOrderNumbersForAllBlocks();

            // Clear the mainVBox and re-add all blocks to update their order
            mainVBox.getChildren().clear();
            for (int i = 0; i < blockDataList.size(); i++) {
                addBlockToVBox(i);
            }
        }
    }

    // Method to update instructionOrderNumber for all blocks after block movement
    private void updateInstructionOrderNumbersForAllBlocks() {
        for (List<BlockLoopInstructionLoadDTO> block : blockDataList) {
            int orderNumber = 1;
            for (BlockLoopInstructionLoadDTO instruction : block) {
                instruction.setInstructionOrderNumber(orderNumber++);
            }
        }
    }

    // Method to update instructionOrderNumber for a specific block
    private void updateInstructionOrderNumbers(ObservableList<BlockLoopInstructionLoadDTO> instructions) {
        int orderNumber = 1;
        for (BlockLoopInstructionLoadDTO instruction : instructions) {
            instruction.setInstructionOrderNumber(orderNumber++);
        }
    }

    // Sample data
    private ObservableList<BlockLoopInstructionLoadDTO> getBlockLoopInstructions() {
        return FXCollections.observableArrayList(
                new BlockLoopInstructionLoadDTO(1, 1, "Instruction 1", "Description 1", 1, "Default Block"),
                new BlockLoopInstructionLoadDTO(2, 1, "Instruction 2", "Description 2", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(3, 2, "Instruction 3", "Description 3", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(3, 3, "Instruction 4", "Description 4", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(3, 4, "Instruction 5", "Description 5", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(4, 1, "Instruction 6", "Description 6", 3, "Block Test 2"),
                new BlockLoopInstructionLoadDTO(5, 2, "Instruction 7", "Description 7", 3, "Block Test 2"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
