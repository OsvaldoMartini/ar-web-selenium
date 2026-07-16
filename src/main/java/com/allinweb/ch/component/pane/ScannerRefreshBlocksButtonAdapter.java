package com.allinweb.ch.component.pane;

import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.util.ARConstants;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;

final class ScannerRefreshBlocksButtonAdapter {

    Button build(ARComponentBuilder builder) {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }
}
