package com.allinweb.ch.util;

import javafx.scene.image.Image;

// Helper class to hold text and image
public class ComboBoxItem {
    private final String text;
    private final Image image;
    private final String value;

    public ComboBoxItem(String text, Image image, String value) {
        this.text = text;
        this.image = image;
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public Image getImage() {
        return image;
    }

    public String getValue() {
        return value;
    }
}
