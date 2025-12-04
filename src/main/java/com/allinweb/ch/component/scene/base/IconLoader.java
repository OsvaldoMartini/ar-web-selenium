package com.allinweb.ch.component.scene.base;

import java.awt.Image;
import java.io.InputStream;
import java.util.Objects;
import javax.imageio.ImageIO;

public interface IconLoader {

    void setIcon(Image icon);

    default void loadAndSetIcon(String path) {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            Image icon = ImageIO.read(in);
            setIcon(icon);
        } catch (Exception e) {
            System.err.println("Error loading icon: " + e.getMessage());
        }
    }
}
