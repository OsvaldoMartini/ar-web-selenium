// package com.allinweb.ch.component.pane;
//
// import com.allinweb.ch.component.pane.base.ARPane;
// import com.allinweb.ch.control.ARComponentBuilder;
// import com.allinweb.ch.util.ARConstants;
// import javafx.geometry.Pos;
// import javafx.scene.control.Label;
// import javafx.scene.layout.AnchorPane;
// import javafx.scene.layout.Pane;
//
// public class ARAlertPane extends ARPane {
//
//    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
//
//    private final String message;
//
//    // UI components
//
//    AnchorPane mainPane;
//
//    public ARAlertPane(String message) {
//        this.message = message;
//    }
//
//    @Override
//    public Pane getPaneReference() {
//        return mainPane;
//    }
//
//    @Override
//    public void initUIComponents() {
//        Label errorLabel = new Label(message);
//        errorLabel.setAlignment(Pos.CENTER);
//        errorLabel.setWrapText(true);
//        builder.setAnchorPaneAnchors(errorLabel, ARConstants.SPACE_XL);
//        mainPane = new AnchorPane(errorLabel);
//    }
//
//    @Override
//    public void initUIBehaviour() {}
// }
