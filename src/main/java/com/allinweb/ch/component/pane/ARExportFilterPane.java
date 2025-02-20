package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BlockLoopInstructionListCell;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.util.ARConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;

public class ARExportFilterPane extends ARPane {

    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
    }

    private ARComponentBuilder builder = new ARComponentBuilder();

    private BotJobLoadDTO botJobLoad;

    // UI COMPONENTS

    private ListView<InstructionLoadDTO> exportedFieldList;
    private ListView<InstructionLoadDTO> filteredFieldList;

    private Button exportFieldButton;
    private Button filterFieldButton;

    private VBox actionPanel;

    private GridPane gridPane;

    private AnchorPane mainPane;

    public ARExportFilterPane(BotJobLoadDTO botJobLoad) {
        this.botJobLoad = botJobLoad;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        //        ObservableList<BlockLoopInstructionDTO> exportedList = ARSharedResources.getInstance()
        //                .getEntityList(
        //                        BlockLoopInstructionDTO.class,
        //                        (exp) -> exp.getExportToAR()
        //                                && exp.getActions().contains(ARConstants.INSERT)
        //                                && exp.getBlock().getBotJobDTO().getId() == this.botJobLoad.getId());
        //
        //
        //        ObservableList<BlockLoopInstructionDTO> filteredList = ARSharedResources.getInstance()
        //                .getEntityList(
        //                        BlockLoopInstructionDTO.class,
        //                        (exp) -> !exp.getExportToAR()
        //                                && exp.getActions().contains(ARConstants.INSERT)
        //                                && exp.getBlock().getBotJobDTO().getId() == this.botJobLoad.getId());

        // Assuming botJobLoad is an instance of BotJobLoadDTO that has blockLoadDTOList
        List<InstructionLoadDTO> allInstructionLoadDTOS = new ArrayList<>();

        // Iterate through each BlockLoadDTO in the blockLoadDTOList
        for (BlockLoadDTO blockLoadDTO : this.botJobLoad.getBlockLoadDTOList()) {
            // For each BlockLoadDTO, extract the list of BlockLoopInstructionLoadDTOs
            List<InstructionLoadDTO> instructionLoadDTOS = blockLoadDTO.getInstructionLoadDTOS();

            // If the list is not null, add all BlockLoopInstructionLoadDTOs to the final list
            if (instructionLoadDTOS != null) {
                allInstructionLoadDTOS.addAll(instructionLoadDTOS);
            }
        }
        // Filtering for instructions where exportToAR is true and actions contain "INSERT"
        ObservableList<InstructionLoadDTO> exportedList = allInstructionLoadDTOS.stream()
                .filter(exp -> exp.getExportToABR() != null
                        && exp.getExportToABR() // Ensure exportToAR is not null
                        && exp.getActions().contains(ARConstants.INSERT) // Check for "INSERT" in actions
                        && exp.getBotJobId().equals(botJobLoad.getId())) // Ensure botJobId matches
                .collect(Collectors.toCollection(FXCollections::observableArrayList)); // Convert to ObservableList

        // Filtering for instructions where exportToAR is false and actions contain "INSERT"
        ObservableList<InstructionLoadDTO> filteredList = allInstructionLoadDTOS.stream()
                .filter(exp -> exp.getExportToABR() == null
                        || !exp.getExportToABR() // Ensure exportToAR is either null or false
                                && exp.getActions().contains(ARConstants.INSERT) // Check for "INSERT" in actions
                                && exp.getBotJobId().equals(botJobLoad.getId())) // Ensure botJobId matches
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        exportedFieldList = new ListView<>(exportedList);
        filteredFieldList = new ListView<>(filteredList);
        exportedFieldList.setCellFactory(new ARCellFactory<>(BlockLoopInstructionListCell.class)::call);
        filteredFieldList.setCellFactory(new ARCellFactory<>(BlockLoopInstructionListCell.class)::call);

        Label exportedFieldLabel = new Label("Exported Fields:");
        Label filteredFieldLabel = new Label("Filtered Fields:");

        exportFieldButton = builder.buildButton(
                "", ARConstants.SPACE_L, "/right.png", ARConstants.SPACE_M, new Insets(ARConstants.SPACE_XS));
        filterFieldButton = builder.buildButton(
                "", ARConstants.SPACE_L, "/left.png", ARConstants.SPACE_M, new Insets(ARConstants.SPACE_XS));
        actionPanel = new VBox(exportFieldButton, filterFieldButton);
        actionPanel.maxWidth(ARConstants.SPACE_L);

        gridPane = new GridPane();
        gridPane.add(new VBox(filteredFieldLabel, filteredFieldList), 0, 0);
        gridPane.add(actionPanel, 1, 0);
        gridPane.add(new VBox(exportedFieldLabel, exportedFieldList), 2, 0);
        AnchorPane.setTopAnchor(gridPane, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(gridPane, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(gridPane, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(gridPane, ARConstants.SPACE_M);

        mainPane = new AnchorPane(gridPane);
    }

    @Override
    public void initUIBehaviour() {
        ColumnConstraints constr = new ColumnConstraints();
        constr.maxWidthProperty()
                .bind(mainPane.widthProperty()
                        .divide(2)
                        .subtract(actionPanel.widthProperty().divide(2))
                        .subtract(ARConstants.SPACE_M));
        constr.prefWidthProperty()
                .bind(mainPane.widthProperty()
                        .divide(2)
                        .subtract(actionPanel.widthProperty().divide(2))
                        .subtract(ARConstants.SPACE_M));
        RowConstraints row = new RowConstraints();
        row.maxHeightProperty().bind(mainPane.heightProperty().subtract(ARConstants.SPACE_M));
        row.prefHeightProperty().bind(mainPane.heightProperty().subtract(ARConstants.SPACE_M));
        gridPane.getColumnConstraints().addAll(constr, new ColumnConstraints(ARConstants.SPACE_L), constr);
        gridPane.getRowConstraints().add(row);

        exportFieldButton.setOnMouseClicked(e -> {
            filteredFieldList.getSelectionModel().getSelectedItems().forEach(instruction -> {
                instruction.setExportToABR(true);
                performDataBase.updateExportAR(instruction);
                //                ARSharedResources.getInstance().updateEntity(instruction,
                // BlockLoopInstructionDTO.class);
            });
        });

        filterFieldButton.setOnMouseClicked(e -> {
            exportedFieldList.getSelectionModel().getSelectedItems().forEach(instruction -> {
                instruction.setExportToABR(false);
                performDataBase.updateExportAR(instruction);
                //                ARSharedResources.getInstance().updateEntity(instruction,
                // BlockLoopInstructionDTO.class);
            });
        });
    }
}
