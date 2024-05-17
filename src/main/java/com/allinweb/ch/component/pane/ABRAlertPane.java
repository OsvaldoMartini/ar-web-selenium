package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.util.ABRConstants;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

public class ABRAlertPane extends ABRPane {

    private static final ABRComponentBuilder builder = new ABRComponentBuilder();

    private final String message;

    // UI components

    AnchorPane mainPane;

    public ABRAlertPane(String message) {
        this.message = message;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        Label errorLabel = new Label(message);
        errorLabel.setAlignment(Pos.CENTER);
        errorLabel.setWrapText(true);
        builder.setAnchorPaneAnchors(errorLabel, ABRConstants.SPACE_XL);
        mainPane = new AnchorPane(errorLabel);
    }

    @Override
    public void initUIBehaviour() {}
}
