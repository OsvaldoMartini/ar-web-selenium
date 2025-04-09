package com.allinweb.ch.util;

import javafx.scene.image.Image;
import lombok.Getter;

// Helper class to hold text and image
@Getter
public class ComboBoxImage {
    private final String text;
    private final Image image;
    private final String value;
    private final Integer blockId;
    private final Integer instructionId;
    private final Integer orderNumber;

    public ComboBoxImage(
            String text, Image image, String value, Integer blockId, Integer instructionId, Integer orderNumber) {
        this.text = text;
        this.image = image;
        this.value = value;
        this.blockId = blockId;
        this.instructionId = instructionId;
        this.orderNumber = orderNumber;
    }
}
