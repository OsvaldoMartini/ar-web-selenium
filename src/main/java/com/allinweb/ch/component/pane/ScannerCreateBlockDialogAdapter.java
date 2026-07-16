package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerCreateBlockModalPresentationService;
import com.allinweb.ch.facade.ScannerCreateBlockPlanner;
import com.allinweb.ch.model.BlockLoadDTO;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

final class ScannerCreateBlockDialogAdapter {

    private final ScannerCreateBlockPlanner planner;

    ScannerCreateBlockDialogAdapter(ScannerCreateBlockPlanner planner) {
        this.planner = planner;
    }

    Optional<Result> show(
            ScannerCreateBlockModalPresentationService.Presentation presentation,
            boolean reactive,
            List<BlockLoadDTO> existingSorted) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(presentation.title());
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(presentation.verticalSpacing());
        root.setPadding(new Insets(15, 15, 35, 15));
        root.setMinWidth(presentation.minWidth());

        if (reactive) {
            Label banner = new Label(presentation.banner());
            banner.setWrapText(true);
            banner.setMaxWidth(Double.MAX_VALUE);
            banner.setStyle(presentation.bannerStyle());
            root.getChildren().add(banner);
        }

        Label nameLabel = new Label(presentation.nameLabel());
        TextField nameField = new TextField();
        nameField.setPromptText(presentation.namePrompt());

        Label posLabel = new Label(presentation.positionLabel());
        ComboBox<String> posCombo = new ComboBox<>();
        posCombo.setMaxWidth(Double.MAX_VALUE);
        posCombo.setItems(FXCollections.observableArrayList(planner.positionOptions(existingSorted)));
        posCombo.getSelectionModel().selectFirst();

        Label previewLabel = new Label();
        previewLabel.setWrapText(true);
        previewLabel.setStyle(presentation.previewStyle());

        Runnable updatePreview = () -> {
            int targetOrder = planner.computeInsertOrderNumber(posCombo.getValue(), existingSorted);
            previewLabel.setText(planner.buildCreateBlockPreview(targetOrder, existingSorted));
        };
        posCombo.valueProperty().addListener((o, ov, nv) -> updatePreview.run());
        updatePreview.run();

        root.getChildren()
                .addAll(
                        nameLabel,
                        nameField,
                        posLabel,
                        posCombo,
                        new Separator(),
                        new Label(presentation.previewLabel()),
                        previewLabel);

        dialog.getDialogPane().setContent(root);

        ButtonType createBtn = new ButtonType(presentation.createButton(), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        javafx.scene.Node createNode = dialog.getDialogPane().lookupButton(createBtn);
        createNode.setDisable(true);
        nameField
                .textProperty()
                .addListener((o, ov, nv) ->
                        createNode.setDisable(nv == null || nv.trim().isEmpty()));

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != createBtn) {
            return Optional.empty();
        }

        String name = nameField.getText().trim();
        int orderNumber = planner.computeInsertOrderNumber(posCombo.getValue(), existingSorted);
        return Optional.of(new Result(name, orderNumber));
    }

    record Result(String name, int orderNumber) {}
}
