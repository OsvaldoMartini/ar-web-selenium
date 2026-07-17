package com.allinweb.ch.vision;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class RasterImageIO {

    private RasterImageIO() {}

    public static RasterImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        return fromBufferedImage(image);
    }

    public static RasterImage readPng(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        return fromBufferedImage(image);
    }

    public static void writePng(RasterImage image, Path path) throws IOException {
        ImageIO.write(toBufferedImage(image), "png", path.toFile());
    }

    public static byte[] toPngBytes(RasterImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(toBufferedImage(image), "png", out);
        return out.toByteArray();
    }

    public static RasterImage fromBufferedImage(BufferedImage image) {
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
        return new RasterImage(width, height, rgb);
    }

    public static BufferedImage toBufferedImage(RasterImage image) {
        if (image == null) return null;
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_RGB);
        buffered.setRGB(0, 0, image.width(), image.height(), image.copyRgb(), 0, image.width());
        return buffered;
    }
}
