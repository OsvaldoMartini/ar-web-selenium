package com.allinweb.ch.util;

import javax.swing.Icon;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class ComboBoxImage {

    private final String text;
    private final Icon icon; // Swing replacement for JavaFX Image
    private final String value;
    private final Integer blockId;
    private final Integer instructionId;
    private final Integer orderNumber;

    public ComboBoxImage(
            String text, Icon icon, String value, Integer blockId, Integer instructionId, Integer orderNumber) {
        this.text = text;
        this.icon = icon;
        this.value = value;
        this.blockId = blockId;
        this.instructionId = instructionId;
        this.orderNumber = orderNumber;
    }
}
