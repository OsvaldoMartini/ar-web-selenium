package com.allinweb.ch.vision.ocr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class OpenCvNativeLoader {

    static {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();

            String libName;
            String resourcePath;

            if (os.contains("win")) {
                libName = "opencv_java4100.dll";
                resourcePath = "/opencv/windows/" + libName;
            } else if (os.contains("mac") || os.contains("darwin")) {
                libName = "libopencv_java4100.dylib";
                resourcePath = "/opencv/macos/" + libName;
            } else if (os.contains("nux") || os.contains("linux")) {
                libName = "libopencv_java4100.so";
                resourcePath = "/opencv/linux/" + libName;
            } else {
                throw new RuntimeException("Unsupported OS: " + os);
            }

            System.out.println("Detected OS: " + os + ", arch: " + arch);
            System.out.println("Loading OpenCV native library from: " + resourcePath);

            InputStream in = OpenCvNativeLoader.class.getResourceAsStream(resourcePath);
            if (in == null) {
                throw new RuntimeException("Native OpenCV library not found in resources: " + resourcePath);
            }

            File temp = File.createTempFile("opencv", libName);
            temp.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(temp)) {
                in.transferTo(out);
            }

            System.load(temp.getAbsolutePath());
            System.out.println("OpenCV successfully loaded from: " + temp.getAbsolutePath());

        } catch (Exception e) {
            throw new RuntimeException("Failed to load OpenCV native library", e);
        }
    }
}
