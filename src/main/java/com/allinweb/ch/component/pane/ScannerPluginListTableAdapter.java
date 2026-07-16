package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import com.allinweb.ch.model.PluginManifestDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

final class ScannerPluginListTableAdapter {

    Result build(PluginManifestDTO manifest) {
        TableView<PluginDTO> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPrefHeight(220);

        TableColumn<PluginDTO, String> icon = column("Icon", PluginDTO::getIcon);
        icon.setPrefWidth(40);
        icon.setMinWidth(40);
        icon.setMaxWidth(40);

        TableColumn<PluginDTO, String> name = column("Name", PluginDTO::getName);
        name.setPrefWidth(130);

        TableColumn<PluginDTO, String> version = column("Version", PluginDTO::getVersion);
        version.setPrefWidth(65);

        TableColumn<PluginDTO, String> size = column("Size", PluginDTO::getSize);
        size.setPrefWidth(55);

        TableColumn<PluginDTO, String> description = column("Description", PluginDTO::getDescription);

        table.getColumns().addAll(icon, name, version, size, description);
        table.setItems(FXCollections.observableArrayList(manifest.getPlugins()));
        return new Result(table, () -> new ArrayList<>(table.getSelectionModel().getSelectedItems()));
    }

    private static TableColumn<PluginDTO, String> column(
            String title, java.util.function.Function<PluginDTO, String> value) {
        TableColumn<PluginDTO, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        return column;
    }

    record Result(Node table, Supplier<List<PluginDTO>> selectedPlugins) {}
}
