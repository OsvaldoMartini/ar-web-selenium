package com.allinweb.ch.vision;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class RasterImageIO {

    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private RasterImageIO() {}

    public static RasterImage read(Path path) throws IOException {
        return readPng(Files.readAllBytes(path));
    }

    public static RasterImage readPng(byte[] png) throws IOException {
        return decodePng(png);
    }

    public static void writePng(RasterImage image, Path path) throws IOException {
        Files.write(path, toPngBytes(image));
    }

    public static byte[] toPngBytes(RasterImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePng(image, out);
        return out.toByteArray();
    }

    private static RasterImage decodePng(byte[] png) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(png))) {
            byte[] signature = input.readNBytes(PNG_SIGNATURE.length);
            if (!Arrays.equals(signature, PNG_SIGNATURE)) {
                throw new IOException("Invalid PNG signature");
            }

            int width = -1;
            int height = -1;
            int bitDepth = -1;
            int colorType = -1;
            ByteArrayOutputStream idat = new ByteArrayOutputStream();

            while (true) {
                int length;
                try {
                    length = input.readInt();
                } catch (EOFException e) {
                    throw new IOException("PNG ended before IEND", e);
                }
                String type = new String(input.readNBytes(4), StandardCharsets.US_ASCII);
                byte[] payload = input.readNBytes(length);
                input.readInt(); // CRC is ignored while decoding.

                if ("IHDR".equals(type)) {
                    try (DataInputStream header = new DataInputStream(new ByteArrayInputStream(payload))) {
                        width = header.readInt();
                        height = header.readInt();
                        bitDepth = header.readUnsignedByte();
                        colorType = header.readUnsignedByte();
                    }
                } else if ("IDAT".equals(type)) {
                    idat.write(payload);
                } else if ("IEND".equals(type)) {
                    break;
                }
            }

            if (width <= 0 || height <= 0 || bitDepth != 8) {
                throw new IOException("Only 8-bit PNG images with positive dimensions are supported");
            }
            int channels = channelsFor(colorType);
            return decodeScanlines(width, height, channels, colorType, inflate(idat.toByteArray()));
        }
    }

    private static byte[] inflate(byte[] compressed) throws IOException {
        try (InputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            return input.readAllBytes();
        }
    }

    private static RasterImage decodeScanlines(int width, int height, int channels, int colorType, byte[] data)
            throws IOException {
        int rowLength = width * channels;
        int[] rgb = new int[width * height];
        byte[] previous = new byte[rowLength];
        int offset = 0;

        for (int y = 0; y < height; y++) {
            if (offset >= data.length) throw new IOException("PNG scanline data ended early");
            int filter = data[offset++] & 0xff;
            if (offset + rowLength > data.length) throw new IOException("PNG scanline data ended early");
            byte[] row = Arrays.copyOfRange(data, offset, offset + rowLength);
            offset += rowLength;
            unfilter(row, previous, channels, filter);
            copyRowPixels(row, rgb, y, width, channels, colorType);
            previous = row;
        }

        return new RasterImage(width, height, rgb);
    }

    private static void unfilter(byte[] row, byte[] previous, int bytesPerPixel, int filter) throws IOException {
        for (int i = 0; i < row.length; i++) {
            int left = i >= bytesPerPixel ? row[i - bytesPerPixel] & 0xff : 0;
            int up = previous[i] & 0xff;
            int upLeft = i >= bytesPerPixel ? previous[i - bytesPerPixel] & 0xff : 0;
            int value = row[i] & 0xff;
            int restored =
                    switch (filter) {
                        case 0 -> value;
                        case 1 -> value + left;
                        case 2 -> value + up;
                        case 3 -> value + ((left + up) / 2);
                        case 4 -> value + paeth(left, up, upLeft);
                        default -> throw new IOException("Unsupported PNG filter: " + filter);
                    };
            row[i] = (byte) (restored & 0xff);
        }
    }

    private static void copyRowPixels(byte[] row, int[] rgb, int y, int width, int channels, int colorType) {
        for (int x = 0; x < width; x++) {
            int i = x * channels;
            int red;
            int green;
            int blue;
            if (colorType == 0 || colorType == 4) {
                red = green = blue = row[i] & 0xff;
            } else {
                red = row[i] & 0xff;
                green = row[i + 1] & 0xff;
                blue = row[i + 2] & 0xff;
            }
            rgb[(y * width) + x] = (red << 16) | (green << 8) | blue;
        }
    }

    private static int channelsFor(int colorType) throws IOException {
        return switch (colorType) {
            case 0 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw new IOException("Unsupported PNG color type: " + colorType);
        };
    }

    private static int paeth(int left, int up, int upLeft) {
        int p = left + up - upLeft;
        int pa = Math.abs(p - left);
        int pb = Math.abs(p - up);
        int pc = Math.abs(p - upLeft);
        if (pa <= pb && pa <= pc) return left;
        return pb <= pc ? up : upLeft;
    }

    private static void writePng(RasterImage image, OutputStream output) throws IOException {
        DataOutputStream data = new DataOutputStream(output);
        data.write(PNG_SIGNATURE);

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(ihdr)) {
            header.writeInt(image.width());
            header.writeInt(image.height());
            header.writeByte(8);
            header.writeByte(2);
            header.writeByte(0);
            header.writeByte(0);
            header.writeByte(0);
        }
        writeChunk(data, "IHDR", ihdr.toByteArray());

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(raw)) {
            for (int y = 0; y < image.height(); y++) {
                compressed.write(0);
                for (int x = 0; x < image.width(); x++) {
                    int pixel = image.pixel(x, y);
                    compressed.write((pixel >> 16) & 0xff);
                    compressed.write((pixel >> 8) & 0xff);
                    compressed.write(pixel & 0xff);
                }
            }
        }
        writeChunk(data, "IDAT", raw.toByteArray());
        writeChunk(data, "IEND", new byte[0]);
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] payload) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(payload.length);
        out.write(typeBytes);
        out.write(payload);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        out.writeInt((int) crc.getValue());
    }
}
