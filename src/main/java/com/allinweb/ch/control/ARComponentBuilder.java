package com.allinweb.ch.control;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

public class ARComponentBuilder {

    // Static final variable to hold the singleton instance
    protected static volatile ARComponentBuilder instance;

    // Private constructor to prevent instantiation
    private ARComponentBuilder() {
        // Initialize if necessary
    }

    // Public method to access the singleton instance
    public static ARComponentBuilder getInstance() {
        if (instance == null) {
            synchronized (ARComponentBuilder.class) {
                if (instance == null) {
                    instance = new ARComponentBuilder();
                }
            }
        }
        return instance;
    }

    public HBox createTopPanel(Double topPanelHeight, Double edgeSpace) {
        HBox topPane = new HBox();
        topPane.setMaxHeight(topPanelHeight);
        AnchorPane.setTopAnchor(topPane, edgeSpace);
        AnchorPane.setLeftAnchor(topPane, edgeSpace);
        AnchorPane.setRightAnchor(topPane, edgeSpace);
        return topPane;
    }

    public HBox createBottomPanel(Double bottomPanelHeight, Double edgeSpace) {
        HBox bottomPane = new HBox();
        bottomPane.setMaxHeight(bottomPanelHeight);
        AnchorPane.setBottomAnchor(bottomPane, edgeSpace);
        AnchorPane.setLeftAnchor(bottomPane, edgeSpace);
        AnchorPane.setRightAnchor(bottomPane, edgeSpace);
        return bottomPane;
    }

    public AnchorPane createContentPanel(Double topPanelHeight, Double bottomPanelHeight, Double edgeSpace) {
        AnchorPane contentPane = new AnchorPane();
        AnchorPane.setTopAnchor(contentPane, edgeSpace + topPanelHeight);
        AnchorPane.setBottomAnchor(contentPane, edgeSpace + bottomPanelHeight);
        AnchorPane.setLeftAnchor(contentPane, edgeSpace);
        AnchorPane.setRightAnchor(contentPane, edgeSpace);
        return contentPane;
    }

    public <T extends Node> T setAnchorPaneAnchors(T node, Double edge) {
        return setAnchorPaneAnchors(node, edge, edge);
    }

    public <T extends Node> T setAnchorPaneAnchors(T node, Double vertical, Double horizontal) {
        return setAnchorPaneAnchors(node, vertical, vertical, horizontal, horizontal);
    }

    public <T extends Node> T setAnchorPaneAnchors(T node, Double top, Double bottom, Double left, Double right) {
        AnchorPane.setTopAnchor(node, top);
        AnchorPane.setBottomAnchor(node, bottom);
        AnchorPane.setLeftAnchor(node, left);
        AnchorPane.setRightAnchor(node, right);
        return node;
    }

    public ImageView buildImageView(String source, Double size) {
        try {
            ImageView image =
                    new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream(source))));
            image.setFitHeight(size);
            image.setFitWidth(size);
            image.setPreserveRatio(true);
            return image;
        } catch (Exception e) {
            ARLogger.getInstance(ARWebDriver.class)
                    .severe(String.format("BuildImageView source: %s size %d \n%s", source, size, e.getMessage()));
        }
        return null;
    }

    public Button buildButton(String text) {
        return buildButton(text, ARConstants.SPACE_L);
    }

    public Button buildButton(String text, Double height) {
        return buildButton(text, height, new Insets(ARConstants.SPACE_XS));
    }

    public Button buildButton(String text, Double height, Insets padding) {
        Button button = new Button(text);
        button.setMaxHeight(height);
        button.setPadding(padding);
        return button;
    }

    public Button buildButton(String text, Double height, String iconSource, Double iconSize, Insets padding) {
        return buildButton(text, height, iconSource, iconSize, padding, null);
    }

    public Button buildButton(
            String text, Double height, String iconSource, Double iconSize, Insets padding, double maxTextWidth) {

        return buildButton(text, height, iconSource, iconSize, padding, null, maxTextWidth);
    }

    public Button buildButton(
            String text, Double height, String iconSource, Double iconSize, Insets padding, Background fill) {
        ImageView image = buildImageView(iconSource, iconSize);
        Button button = new Button(text);
        button.setGraphic(image);
        button.setMaxHeight(height);
        button.setPadding(padding);
        if (fill != null) {
            button.setBackground(fill);
        }
        return button;
    }

    public Button buildButton(
            String text,
            Double height,
            String iconSource,
            Double iconSize,
            Insets padding,
            Background fill,
            double maxTextWidth) {

        ImageView image = buildImageView(iconSource, iconSize);
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(maxTextWidth);

        VBox vbox = new VBox(image, label);
        vbox.setAlignment(Pos.CENTER);
        vbox.setSpacing(2);

        Button button = new Button();
        button.setGraphic(vbox);
        button.setMaxHeight(height);
        button.setPadding(padding);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY); // Important to avoid text outside graphic
        if (fill != null) {
            button.setBackground(fill);
        }
        return button;
    }
}
