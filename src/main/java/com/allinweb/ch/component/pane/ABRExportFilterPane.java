package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.BlockLoopInstructionListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.util.ABRConstants;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;

public class ABRExportFilterPane extends ABRPane {

    private ABRComponentBuilder builder = new ABRComponentBuilder();

    private BotJobDTO botJob;

    // UI COMPONENTS

    private ListView<BlockLoopInstructionDTO> exportedFieldList;
    private ListView<BlockLoopInstructionDTO> filteredFieldList;

    private Button exportFieldButton;
    private Button filterFieldButton;

    private VBox actionPanel;

    private GridPane gridPane;

    private AnchorPane mainPane;

    public ABRExportFilterPane(BotJobDTO botJob) {
        this.botJob = botJob;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        ObservableList<BlockLoopInstructionDTO> exportedList = ABRSharedResources.getInstance()
                .getEntityList(
                        BlockLoopInstructionDTO.class,
                        (exp) -> exp.getExportToABR()
                                && exp.getActions().contains(ABRConstants.INSERT)
                                && exp.getBlock().getBotJobDTO().getId() == botJob.getId());
        ObservableList<BlockLoopInstructionDTO> filteredList = ABRSharedResources.getInstance()
                .getEntityList(
                        BlockLoopInstructionDTO.class,
                        (exp) -> !exp.getExportToABR()
                                && exp.getActions().contains(ABRConstants.INSERT)
                                && exp.getBlock().getBotJobDTO().getId() == botJob.getId());
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
                ABRSharedResources.getInstance().updateEntity(instruction, BlockLoopInstructionDTO.class);
            });
        });

        filterFieldButton.setOnMouseClicked(e -> {
            exportedFieldList.getSelectionModel().getSelectedItems().forEach(instruction -> {
                instruction.setExportToABR(false);
                ABRSharedResources.getInstance().updateEntity(instruction, BlockLoopInstructionDTO.class);
            });
        });
    }
}
