package com.allinweb.ch.vision.ocr;

import java.awt.image.BufferedImage;
import org.opencv.core.*;

public class OcrOpenCvUtils {

    public static Mat bufferedImageToMat(BufferedImage bi) {
        int width = bi.getWidth();
        int height = bi.getHeight();
        byte[] data = new byte[width * height * 3];
        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bi.getRGB(x, y);
                data[offset++] = (byte) (rgb & 0xff);
                data[offset++] = (byte) ((rgb >> 8) & 0xff);
                data[offset++] = (byte) ((rgb >> 16) & 0xff);
            }
        }

        Mat mat = new Mat(height, width, CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    public static BufferedImage matToBufferedImage(Mat matrix) {
        int channels = matrix.channels();
        int width = matrix.width();
        int height = matrix.height();
        byte[] data = new byte[width * height * channels];
        matrix.get(0, 0, data);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb;
                if (channels > 1) {
                    int blue = data[offset++] & 0xff;
                    int green = data[offset++] & 0xff;
                    int red = data[offset++] & 0xff;
                    rgb = (red << 16) | (green << 8) | blue;
                } else {
                    int gray = data[offset++] & 0xff;
                    rgb = (gray << 16) | (gray << 8) | gray;
                }
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }
}
