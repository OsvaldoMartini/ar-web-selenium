package com.allinweb.ch.component.pane;

import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.util.ARConstants;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

final class ScannerSupportButtonAdapter {

    Button buildSendDomReview(ARComponentBuilder builder) {
        Button button = builder.buildButton(
                "Send Pure HTML Review",
                ARConstants.SPACE_ZERO,
                "/warning_red.png",
                ARConstants.SPACE_M,
                new Insets(5.0D));
        button.setTooltip(new Tooltip(
                "Send sanitized HTML for review - personal data is replaced with synthetic test data."));
        hide(button);
        return button;
    }

    Button buildRequestSupport(ARComponentBuilder builder) {
        Button button =
                builder.buildButton("", ARConstants.SPACE_ZERO, "/info.png", ARConstants.SPACE_M, new Insets(5.0D));
        button.setTooltip(new Tooltip("Request Support - send a text message to the MultiPlugins support team."));
        hide(button);
        return button;
    }

    private static void hide(Button button) {
        button.setVisible(false);
        button.setManaged(false);
    }
}
