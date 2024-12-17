package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ABRNewCommandPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.util.ComboBoxVars;
import java.util.Arrays;
import java.util.List;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ABRNewCommandScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 300D;
    private static final Double SCENE_WIDTH = 700D;
    private static final String TITLE = "Add Command";
    private RowMoveDTO rowMoveDTO;
    private List<BotJobLoadDTO> blockLoadDTOList;
    private ObservableList<ComboBoxVars> webPageItems;

    public ABRNewCommandScene(
            RowMoveDTO rowMoveDTO, List<BotJobLoadDTO> blockLoadDTOList, ObservableList<ComboBoxVars> webPageItems) {
        super();
        this.rowMoveDTO = rowMoveDTO;
        this.blockLoadDTOList = blockLoadDTOList;
        this.webPageItems = webPageItems;
    }

    @Override
    public IABRPane buildPane() {
        return new ABRNewCommandPane(rowMoveDTO, blockLoadDTOList, webPageItems);
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        String titleMsg = createDescriptionString(rowMoveDTO, webPageItems);
        if (titleMsg != null) {
            return titleMsg;
        } else {
            return TITLE;
        }
    }

    public void showModal() {
        Stage modalStage = new Stage();
        IABRPane pane = buildPane();
        if (pane != null) {
            Scene scene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(scene);
            modalStage.setTitle(getTitle());
            modalStage.initModality(Modality.APPLICATION_MODAL); // Make it modal
            modalStage.showAndWait(); // Block until this window is closed
        }
    }

    public String createDescriptionString(RowMoveDTO rowMoveDTO, ObservableList<ComboBoxVars> webPageItems) {
        // Ensure there are updatedRows to work with
        if (rowMoveDTO.getUpdatedRows() == null || rowMoveDTO.getUpdatedRows().isEmpty()) {
            return "No updated rows available";
        }

        // Get the first updated row from RowMoveDTO
        InstructionDTO firstInstruction = rowMoveDTO.getUpdatedRows().get(0);

        String operation = renderInstructionActions(firstInstruction);

        //        // Find the ComboBoxVars entry where instructionId matches updatedRows.get(0).instructionId
        //        ComboBoxVars matchingItem = webPageItems.stream()
        //                .filter(item -> item.getVarId() == firstInstruction.getInstructionId())
        //                .findFirst()
        //                .orElse(null); // Default to null if not found
        //
        //        // Extract the text field from matchingItem (if found), otherwise use a default message
        //        String parentText = (matchingItem != null) ? " Parent " + matchingItem.getText() : "Parent not found";

        // Construct the final string
        String result = " " + rowMoveDTO.getType().replace("_", " ") + " " + firstInstruction.getInstructionName() + " "
                + operation + " on " + rowMoveDTO.getBlockName();

        return result;
    }

    public String renderInstructionActions(InstructionDTO instruction) {
        // List of valid actions
        List<String> validActions = Arrays.asList("SET", "GET", "CK", "E");

        // Handle the "CK" action with special formatting for operation
        if ("CK".equals(instruction.getActions()) && instruction.getOperation() != null) {
            String[] parts = instruction.getOperation().split(":");
            if (parts.length == 3) {
                String left = parts[0].trim();
                String middle = parts[1].trim();
                String right = parts[2].trim();

                // Handle special case where middle is "="
                if ("=".equals(middle)) {
                    return String.format("(%d)%s %s %s", instruction.getParentId(), left, middle, right);
                }
            }
        }

        // Handle operations for other actions (SET, GET)
        if (instruction.getOperation() != null && validActions.contains(instruction.getActions())) {
            String[] parts = instruction.getOperation().split(":");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim();
                return String.format("(%d)%s: %s", instruction.getParentId(), left, right);
            }
        }

        // Handle if the action is valid but has no operation
        if (validActions.contains(instruction.getActions())) {
            return instruction.getActions();
        }

        // Return empty string for no actions
        return "";
    }
}
