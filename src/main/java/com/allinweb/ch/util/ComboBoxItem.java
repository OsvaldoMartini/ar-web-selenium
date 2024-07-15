package com.allinweb.ch.util;

import javafx.scene.image.Image;

// Helper class to hold text and image
public class ComboBoxItem {
    private final String text;
    private final Image image;

    public ComboBoxItem(String text, Image image) {
        this.text = text;
        this.image = image;
    }

    public String getText() {
        return text;
    }

    public Image getImage() {
        return image;
    }
}
