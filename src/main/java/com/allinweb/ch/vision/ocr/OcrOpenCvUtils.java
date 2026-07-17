package com.allinweb.ch.vision.ocr;

import com.allinweb.ch.vision.RasterImage;
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
        RasterImage raster = matToRasterImage(matrix);
        BufferedImage img = new BufferedImage(raster.width(), raster.height(), BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, raster.width(), raster.height(), raster.copyRgb(), 0, raster.width());
        return img;
    }

    public static RasterImage matToRasterImage(Mat matrix) {
        int channels = matrix.channels();
        int width = matrix.width();
        int height = matrix.height();
        byte[] data = new byte[width * height * channels];
        matrix.get(0, 0, data);

        int[] pixels = new int[width * height];
        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel;
                if (channels > 1) {
                    int blue = data[offset++] & 0xff;
                    int green = data[offset++] & 0xff;
                    int red = data[offset++] & 0xff;
                    pixel = (red << 16) | (green << 8) | blue;
                } else {
                    int gray = data[offset++] & 0xff;
                    pixel = (gray << 16) | (gray << 8) | gray;
                }
                pixels[(y * width) + x] = pixel;
            }
        }
        return new RasterImage(width, height, pixels);
    }
}
