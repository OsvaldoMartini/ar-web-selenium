package com.allinweb.ch.component.pane;

import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.util.ARConstants;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

final class ScannerPluginUpdateButtonAdapter {

    Button build(ARComponentBuilder builder, Runnable onUpdate) {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_ZERO, ARConstants.ICON_DOWNLOAD, ARConstants.SPACE_M, new Insets(5.0D));
        button.setTooltip(new Tooltip("Download latest plugins from configured URL"));
        button.setOnAction(e -> onUpdate.run());
        return button;
    }
}
