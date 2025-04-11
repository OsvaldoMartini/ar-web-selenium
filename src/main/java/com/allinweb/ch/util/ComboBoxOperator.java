package com.allinweb.ch.util;

import javafx.scene.image.Image;

public class ComboBoxOperator {
    private final String text;
    private final Image image;
    private final String operator;

    public ComboBoxOperator(String text, Image image, String operator) {
        this.text = text;
        this.image = image;
        this.operator = operator;
    }

    public String getText() {
        return text;
    }

    public Image getImage() {
        return image;
    }

    public String getOperator() {
        return operator;
    }
}
