package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.BlockLoopInstructionListCell;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.util.ABRConstants;
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

public class ABRExportFilterPane extends ABRPane {

    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
    }

    private ABRComponentBuilder builder = new ABRComponentBuilder();

    private BotJobLoadDTO botJobLoad;

    // UI COMPONENTS

    private ListView<BlockLoopInstructionLoadDTO> exportedFieldList;
    private ListView<BlockLoopInstructionLoadDTO> filteredFieldList;

    private Button exportFieldButton;
    private Button filterFieldButton;

    private VBox actionPanel;

    private GridPane gridPane;

    private AnchorPane mainPane;

    public ABRExportFilterPane(BotJobLoadDTO botJobLoad) {
        this.botJobLoad = botJobLoad;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        //        ObservableList<BlockLoopInstructionDTO> exportedList = ABRSharedResources.getInstance()
        //                .getEntityList(
        //                        BlockLoopInstructionDTO.class,
        //                        (exp) -> exp.getExportToABR()
        //                                && exp.getActions().contains(ABRConstants.INSERT)
        //                                && exp.getBlock().getBotJobDTO().getId() == this.botJobLoad.getId());
        //
        //
        //        ObservableList<BlockLoopInstructionDTO> filteredList = ABRSharedResources.getInstance()
        //                .getEntityList(
        //                        BlockLoopInstructionDTO.class,
        //                        (exp) -> !exp.getExportToABR()
        //                                && exp.getActions().contains(ABRConstants.INSERT)
        //                                && exp.getBlock().getBotJobDTO().getId() == this.botJobLoad.getId());

        // Assuming botJobLoad is an instance of BotJobLoadDTO that has blockLoadDTOList
        List<BlockLoopInstructionLoadDTO> allBlockLoopInstructionLoadDTOS = new ArrayList<>();

        // Iterate through each BlockLoadDTO in the blockLoadDTOList
        for (BlockLoadDTO blockLoadDTO : this.botJobLoad.getBlockLoadDTOList()) {
            // For each BlockLoadDTO, extract the list of BlockLoopInstructionLoadDTOs
            List<BlockLoopInstructionLoadDTO> blockLoopInstructionLoadDTOS =
                    blockLoadDTO.getBlockLoopInstructionLoadDTOS();

            // If the list is not null, add all BlockLoopInstructionLoadDTOs to the final list
            if (blockLoopInstructionLoadDTOS != null) {
                allBlockLoopInstructionLoadDTOS.addAll(blockLoopInstructionLoadDTOS);
            }
        }
        // Filtering for instructions where exportToABR is true and actions contain "INSERT"
        ObservableList<BlockLoopInstructionLoadDTO> exportedList = allBlockLoopInstructionLoadDTOS.stream()
                .filter(exp -> exp.getExportToABR() != null
                        && exp.getExportToABR() // Ensure exportToABR is not null
                        && exp.getActions().contains(ABRConstants.INSERT) // Check for "INSERT" in actions
                        && exp.getBotJobId().equals(botJobLoad.getId())) // Ensure botJobId matches
                .collect(Collectors.toCollection(FXCollections::observableArrayList)); // Convert to ObservableList

        // Filtering for instructions where exportToABR is false and actions contain "INSERT"
        ObservableList<BlockLoopInstructionLoadDTO> filteredList = allBlockLoopInstructionLoadDTOS.stream()
                .filter(exp -> exp.getExportToABR() == null
                        || !exp.getExportToABR() // Ensure exportToABR is either null or false
                                && exp.getActions().contains(ABRConstants.INSERT) // Check for "INSERT" in actions
                                && exp.getBotJobId().equals(botJobLoad.getId())) // Ensure botJobId matches
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        exportedFieldList = new ListView<>(exportedList);
        filteredFieldList = new ListView<>(filteredList);
        exportedFieldList.setCellFactory(new ABRCellFactory<>(BlockLoopInstructionListCell.class)::call);
        filteredFieldList.setCellFactory(new ABRCellFactory<>(BlockLoopInstructionListCell.class)::call);

        Label exportedFieldLabel = new Label("Exported Fields:");
        Label filteredFieldLabel = new Label("Filtered Fields:");

        exportFieldButton = builder.buildButton(
                "", ABRConstants.SPACE_L, "/right.png", ABRConstants.SPACE_M, new Insets(ABRConstants.SPACE_XS));
        filterFieldButton = builder.buildButton(
                "", ABRConstants.SPACE_L, "/left.png", ABRConstants.SPACE_M, new Insets(ABRConstants.SPACE_XS));
        actionPanel = new VBox(exportFieldButton, filterFieldButton);
        actionPanel.maxWidth(ABRConstants.SPACE_L);

        gridPane = new GridPane();
        gridPane.add(new VBox(filteredFieldLabel, filteredFieldList), 0, 0);
        gridPane.add(actionPanel, 1, 0);
        gridPane.add(new VBox(exportedFieldLabel, exportedFieldList), 2, 0);
        AnchorPane.setTopAnchor(gridPane, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(gridPane, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(gridPane, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(gridPane, ABRConstants.SPACE_M);

        mainPane = new AnchorPane(gridPane);
    }

    @Override
    public void initUIBehaviour() {
        ColumnConstraints constr = new ColumnConstraints();
        constr.maxWidthProperty()
                .bind(mainPane.widthProperty()
                        .divide(2)
                        .subtract(actionPanel.widthProperty().divide(2))
                        .subtract(ABRConstants.SPACE_M));
        constr.prefWidthProperty()
                .bind(mainPane.widthProperty()
                        .divide(2)
                        .subtract(actionPanel.widthProperty().divide(2))
                        .subtract(ABRConstants.SPACE_M));
        RowConstraints row = new RowConstraints();
        row.maxHeightProperty().bind(mainPane.heightProperty().subtract(ABRConstants.SPACE_M));
        row.prefHeightProperty().bind(mainPane.heightProperty().subtract(ABRConstants.SPACE_M));
        gridPane.getColumnConstraints().addAll(constr, new ColumnConstraints(ABRConstants.SPACE_L), constr);
        gridPane.getRowConstraints().add(row);

        exportFieldButton.setOnMouseClicked(e -> {
            filteredFieldList.getSelectionModel().getSelectedItems().forEach(instruction -> {
                instruction.setExportToABR(true);
                performDataBase.updateExportABR(instruction);
                //                ABRSharedResources.getInstance().updateEntity(instruction,
                // BlockLoopInstructionDTO.class);
            });
        });

        filterFieldButton.setOnMouseClicked(e -> {
            exportedFieldList.getSelectionModel().getSelectedItems().forEach(instruction -> {
                instruction.setExportToABR(false);
                performDataBase.updateExportABR(instruction);
                //                ABRSharedResources.getInstance().updateEntity(instruction,
                // BlockLoopInstructionDTO.class);
            });
        });
    }
}
