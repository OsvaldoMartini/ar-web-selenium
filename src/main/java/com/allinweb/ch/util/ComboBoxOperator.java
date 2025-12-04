package com.allinweb.ch.util;

import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ComboBoxOperator {
    private final String text;
    private final ImageIcon image;
    private final String operator;

    public ComboBoxOperator(String text, ImageIcon image, String operator) {
        this.text = text;
        this.image = image;
        this.operator = operator;
    }

    public String getText() {
        return text;
    }

    public ImageIcon getImage() {
        return image;
    }

    public String getOperator() {
        return operator;
    }
}
