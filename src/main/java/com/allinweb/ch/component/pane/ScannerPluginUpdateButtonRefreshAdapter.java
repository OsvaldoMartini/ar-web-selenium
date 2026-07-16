package com.allinweb.ch.component.pane;

import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginUpdateButtonRefreshAdapter {

    Button refresh(Button currentButton, Supplier<Button> replacementFactory) {
        if (!(currentButton.getParent() instanceof GridPane grid)) {
            return currentButton;
        }
        int index = grid.getChildren().indexOf(currentButton);
        if (index < 0) {
            return currentButton;
        }
        try {
            grid.getChildren().remove(currentButton);
            Button replacement = replacementFactory.get();
            grid.add(replacement, 1, 0);
            return replacement;
        } catch (Exception ex) {
            log.warn("UpdatePlugins - could not refresh pluginUpdateButton", ex);
            return currentButton;
        }
    }
}
