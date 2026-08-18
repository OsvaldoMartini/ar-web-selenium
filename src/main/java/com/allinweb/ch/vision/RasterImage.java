package com.allinweb.ch.vision;

import java.util.Arrays;

public final class RasterImage {
    private final int width;
    private final int height;
    private final int[] rgb;

    public RasterImage(int width, int height, int[] rgb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        if (rgb == null || rgb.length != width * height) {
            throw new IllegalArgumentException("RGB buffer size does not match image dimensions");
        }
        this.width = width;
        this.height = height;
        this.rgb = Arrays.copyOf(rgb, rgb.length);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixel(int x, int y) {
        return rgb[(y * width) + x];
    }

    public RasterImage withPixel(int x, int y, int color) {
        int[] copy = Arrays.copyOf(rgb, rgb.length);
        copy[(y * width) + x] = color;
        return new RasterImage(width, height, copy);
    }

    public int[] copyRgb() {
        return Arrays.copyOf(rgb, rgb.length);
    }
}
