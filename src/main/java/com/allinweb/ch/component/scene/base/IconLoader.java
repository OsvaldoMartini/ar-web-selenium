package com.allinweb.ch.component.scene.base;

import java.util.Objects;
import javafx.scene.image.Image;

public interface IconLoader {

    void setIcon(Image icon);

    default void loadAndSetIcon(String path) {
        try {
            Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(path)));
            setIcon(icon);
        } catch (Exception e) {
            System.err.println("Error loading icon: " + e.getMessage());
        }
    }
}
