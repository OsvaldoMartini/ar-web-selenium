package com.allinweb.ch.vision;

import java.awt.*;
import java.util.Map;
import lombok.Data;

@Data
public class VisionElement {

    private Integer id;

    /** Raw text detected by OCR (already sanitized). */
    private String text;

    /** Generic type for the UI element (no domain). */
    private UiElementType type;

    /** Bounding box in screenshot coordinates. */
    private Rectangle bounds;

    /** Device coordinates (center point) mapped to driver coordinates. */
    private double deviceX;

    private double deviceY;

    /** Free-form attributes like class, clickable, enabled... */
    private Map<String, String> attributes;
}
