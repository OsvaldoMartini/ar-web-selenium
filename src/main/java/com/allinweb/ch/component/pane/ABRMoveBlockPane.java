package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.MoveBlockListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.util.ABRConstants;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ABRMoveBlockPane extends ABRPane {

    private final ABRComponentBuilder builder = new ABRComponentBuilder();
    private BlockDTO block;

    private Button selectDestinationButton;
    Button closeButton;
    private ListView<BlockDTO> selectBlockListView = new ListView<BlockDTO>();

    private AnchorPane mainPane;

    public ABRMoveBlockPane(BlockDTO block) {
        this.block = block;
    }

    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        Label messageLabel = new Label("0 destination found");

        ObservableList<BlockDTO> blockList = ABRSharedResources.getInstance()
                .getEntityList(
                        BlockDTO.class,
                        Comparator.comparingInt(BlockDTO::getId),
                        (block) -> this.block.getBotJobDTO().getId()
                                        == block.getBotJobDTO().getId()
                                && this.block.getId() != block.getId());

        if (blockList.size() > 0) {
            blockList = FXCollections.observableArrayList(blockList.subList(1, blockList.size()));

        } else {
            blockList = null;
        }

        selectBlockListView = new ListView<>(blockList);
        selectBlockListView.setCellFactory(new ABRCellFactory<>(MoveBlockListCell.class)::call);
        selectBlockListView.setBorder(null);
        selectBlockListView.setBackground(null);
        selectBlockListView.setMaxHeight(Double.MAX_VALUE);
        selectBlockListView.setMaxWidth(Double.MAX_VALUE);

        selectDestinationButton = builder.buildButton(" Select destination ", ABRConstants.SPACE_L);
        closeButton = builder.buildButton(" Close ", ABRConstants.SPACE_L);

        HBox actionPanel = new HBox(selectDestinationButton, closeButton);
        actionPanel.setSpacing(ABRConstants.SPACE_SM);
        actionPanel.setAlignment(Pos.CENTER);

        if (selectBlockListView.getItems().size() != 0) {
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
        } else {
            messageLabel.setVisible(true);
            messageLabel.setManaged(true);
        }

        StackPane stackPane = new StackPane(selectBlockListView, messageLabel);
        StackPane.setAlignment(messageLabel, Pos.CENTER);

        VBox separtorPanel = new VBox(stackPane, actionPanel);
        separtorPanel.setMaxWidth(Double.MAX_VALUE);

        // separtorPanel.setPrefWidth(400);

        AnchorPane.setTopAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(separtorPanel, ABRConstants.SPACE_M);

        mainPane = new AnchorPane(separtorPanel);
    }

    @Override
    public void initUIBehaviour() {
        closeButton.setOnAction(e -> Close());
        selectDestinationButton.setOnAction(e -> {
            BlockDTO selectedBlockDTO = selectBlockListView.getSelectionModel().getSelectedItem();

            if (selectedBlockDTO != null && block != null) {

                int selectBlockOrderNumber = selectedBlockDTO.getBlockOrderNumber();

                ObservableList<BlockDTO> blockObservableList = ABRSharedResources.getInstance()
                        .getEntityList(
                                BlockDTO.class,
                                Comparator.comparingInt(BlockDTO::getBlockOrderNumber),
                                (block) -> block.getBotJobDTO().getId()
                                                == block.getBotJobDTO().getId()
                                        && block.getBlockOrderNumber() >= selectBlockOrderNumber);

                for (BlockDTO blockDTO : blockObservableList) {
                    blockDTO.setBlockOrderNumber(blockDTO.getBlockOrderNumber() + 1);
                }

                Queue<BlockDTO> queue = new LinkedList<>(blockObservableList);

                ABRSharedResources.getInstance().updateAllEntity(queue, BlockDTO.class, () -> {
                    block.setBlockOrderNumber(selectBlockOrderNumber);

                    ABRSharedResources.getInstance().updateEntity(block, BlockDTO.class);
                });

                Close();
            }
        });
    }

    private void Close() {
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
    }

    @Override
    public void start(Stage stage) throws Exception {}
}
