package com.allinweb.ch.tests;

import com.allinweb.ch.component.listCell.TableCellWithEditMode;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import java.util.Collections;
import java.util.Comparator;
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
    private ObservableList<BlockLoopInstructionLoadDTO> blockLoopInstructions;

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

        // Add a new column for the buttons (UP, DOWN, Action, Edit, Remove)
        TableColumn<BlockLoopInstructionLoadDTO, Void> actionColumn = new TableColumn<>("Actions");
        actionColumn.setCellFactory(param -> {
            final TableCell<BlockLoopInstructionLoadDTO, Void> cell = new TableCell<>() {

                private final Button upButton = new Button("↑");
                private final Button downButton = new Button("↓");
                private final Button actionButton = new Button("Action");
                private final Button editButton = new Button();
                private final Button removeButton = new Button(); // New remove button

                {
                    // Load the edit image
                    Image editImage = new Image(getClass().getResourceAsStream("/edit.png"));
                    ImageView editImageView = new ImageView(editImage);
                    editImageView.setFitWidth(16);
                    editImageView.setFitHeight(16);
                    editButton.setGraphic(editImageView);

                    // Load the cross image for the remove button
                    Image crossImage = new Image(getClass().getResourceAsStream("/cross.png"));
                    ImageView crossImageView = new ImageView(crossImage);
                    crossImageView.setFitWidth(16);
                    crossImageView.setFitHeight(16);
                    removeButton.setGraphic(crossImageView);

                    // Set up button actions
                    upButton.setOnAction(event -> {
                        TableView<BlockLoopInstructionLoadDTO> table = getTableView();
                        ObservableList<BlockLoopInstructionLoadDTO> currentItems = table.getItems();
                        int currentIndex = getIndex();

                        if (currentIndex > 0) {
                            Collections.swap(currentItems, currentIndex, currentIndex - 1);
                            updateInstructionOrderNumbers(currentItems);
                            syncBlockLoopInstructionsWithBlockDataList();
                            sortTableByOrderNumber(table);
                        }
                    });

                    downButton.setOnAction(event -> {
                        TableView<BlockLoopInstructionLoadDTO> table = getTableView();
                        ObservableList<BlockLoopInstructionLoadDTO> currentItems = table.getItems();
                        int currentIndex = getIndex();

                        if (currentIndex < currentItems.size() - 1) {
                            Collections.swap(currentItems, currentIndex, currentIndex + 1);
                            updateInstructionOrderNumbers(currentItems);
                            syncBlockLoopInstructionsWithBlockDataList();
                            sortTableByOrderNumber(table);
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

                    removeButton.setOnAction(event -> {
                        BlockLoopInstructionLoadDTO data =
                                getTableView().getItems().get(getIndex());
                        System.out.println("Remove button clicked for: " + data.getName());

                        // Remove the item from the TableView's items
                        getTableView().getItems().remove(data);

                        // Remove the item from blockLoopInstructions
                        blockLoopInstructions.remove(data);

                        // Find the corresponding block in blockDataList and remove the item from that block
                        for (List<BlockLoopInstructionLoadDTO> block : blockDataList) {
                            if (block.remove(data)) {
                                // If the item was found and removed, break out of the loop
                                break;
                            }
                        }

                        // Update the instructionOrderNumbers for the remaining items
                        updateInstructionOrderNumbers(getTableView().getItems());

                        // Synchronize blockLoopInstructions with blockDataList
                        syncBlockLoopInstructionsWithBlockDataList();

                        // Sort the table items by instructionOrderNumber to maintain order
                        sortTableByOrderNumber(getTableView());
                    });
                }

                @Override
                public void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        HBox buttonsBox = new HBox(upButton, downButton, actionButton, editButton, removeButton);
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
        blockLoopInstructions = FXCollections.observableArrayList(getBlockLoopInstructions());

        // Group the instructions by blockId
        Map<Integer, List<BlockLoopInstructionLoadDTO>> groupedByBlock =
                blockLoopInstructions.stream().collect(Collectors.groupingBy(BlockLoopInstructionLoadDTO::getBlockId));

        // Initialize the block data list
        blockDataList = FXCollections.observableArrayList(groupedByBlock.values());

        // Create a VBox to display the block names and corresponding instruction tables
        mainVBox = new VBox(0); // No spacing between blocks
        mainVBox.setStyle("-fx-padding: 0;"); // No padding around the VBox

        // Loop through the blocks and re-add them to the VBox
        for (int i = 0; i < blockDataList.size(); i++) {
            // Get the instructions for the current block
            List<BlockLoopInstructionLoadDTO> instructions = blockDataList.get(i);

            // Sort the instructions by instructionOrderNumber in ascending order
            instructions.sort(Comparator.comparingInt(BlockLoopInstructionLoadDTO::getInstructionOrderNumber));

            // Add the block with sorted instructions to the VBox
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

            // Synchronize blockLoopInstructions with blockDataList
            syncBlockLoopInstructionsWithBlockDataList();

            // Clear the mainVBox and re-add all blocks to update their order
            mainVBox.getChildren().clear();

            // Loop through the blocks and re-add them to the VBox
            for (int i = 0; i < blockDataList.size(); i++) {
                // Get the instructions for the current block
                List<BlockLoopInstructionLoadDTO> instructions = blockDataList.get(i);

                // Sort the instructions by instructionOrderNumber in ascending order
                instructions.sort(Comparator.comparingInt(BlockLoopInstructionLoadDTO::getInstructionOrderNumber));

                // Add the block with sorted instructions to the VBox
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

    // Method to sort the table by instructionOrderNumber in ascending order
    private void sortTableByOrderNumber(TableView<BlockLoopInstructionLoadDTO> tableView) {
        ObservableList<BlockLoopInstructionLoadDTO> items = tableView.getItems();
        FXCollections.sort(items, Comparator.comparingInt(BlockLoopInstructionLoadDTO::getInstructionOrderNumber));
        tableView.setItems(items); // Set sorted items back to the table
    }

    // Modify the method to update instructionOrderNumber after any change
    private void updateInstructionOrderNumbers(ObservableList<BlockLoopInstructionLoadDTO> instructions) {
        int orderNumber = 1;
        for (BlockLoopInstructionLoadDTO instruction : instructions) {
            instruction.setInstructionOrderNumber(orderNumber++);
        }
    }

    // Method to update the blockLoopInstructions list in real-time
    private void updateBlockLoopInstructions(
            BlockLoopInstructionLoadDTO movedItem, ObservableList<BlockLoopInstructionLoadDTO> updatedBlock) {
        // Remove all instructions from blockLoopInstructions that belong to the same blockId as movedItem
        blockLoopInstructions.removeIf(instruction -> instruction.getBlockId() == movedItem.getBlockId());

        // Add the updated block instructions back to blockLoopInstructions
        blockLoopInstructions.addAll(updatedBlock);
    }

    // Method to synchronize blockDataList with blockLoopInstructions based on blockId
    private void syncBlockLoopInstructionsWithBlockDataList() {
        // Clear the blockLoopInstructions list
        blockLoopInstructions.clear();

        // Iterate through blockDataList and add the updated instructions back to blockLoopInstructions
        for (List<BlockLoopInstructionLoadDTO> block : blockDataList) {
            blockLoopInstructions.addAll(block); // Add all instructions from the current block to blockLoopInstructions
        }
    }

    // Sample data
    private ObservableList<BlockLoopInstructionLoadDTO> getBlockLoopInstructions() {
        return FXCollections.observableArrayList(
                new BlockLoopInstructionLoadDTO(1, 1, "Instruction 1", "Description 1", 1, "Default Block"),
                new BlockLoopInstructionLoadDTO(2, 4, "Instruction 2", "Description 2", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(3, 3, "Instruction 3", "Description 3", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(4, 2, "Instruction 4", "Description 4", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(5, 1, "Instruction 5", "Description 5", 2, "Block Test 1"),
                new BlockLoopInstructionLoadDTO(6, 2, "Instruction 6", "Description 6", 3, "Block Test 2"),
                new BlockLoopInstructionLoadDTO(7, 1, "Instruction 7", "Description 7", 3, "Block Test 2"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
