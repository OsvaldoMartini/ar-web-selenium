package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.util.ARConstants;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.TilePane;

public class HomeBankingListCell extends ListCell<HomeBankingLoadDTO> {
  @Override
  protected void updateItem(HomeBankingLoadDTO item, boolean empty) {
    super.updateItem(item, empty);
    Node graphic = null;
    if (!empty && item != null && item.getUrl() != null) {
      Label name = new Label(item.getName());
      Label url = new Label(item.getUrl());
      TilePane pane = new TilePane(name, url);
      pane.setPrefColumns(2);
      pane.setPrefRows(1);
      pane.setVgap(10); // Add vertical spacing between rows (not used much with one row)
      pane.setHgap(10); // Add horizontal spacing between columns (adjust as needed)
      pane.prefTileWidthProperty()
          .bind(pane.widthProperty().divide(2).subtract(ARConstants.SPACE_SM)); // Adjust width

      graphic = pane;
    }
    Node finalGraphic = graphic;
    Platform.runLater(() -> setGraphic(finalGraphic));
  }
}
