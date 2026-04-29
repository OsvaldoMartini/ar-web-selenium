package com.allinweb.ch.ocr.bridge;

/**
 * Phase 1 smoke test for the native OCR bridge. Loads ar_ocr.dll via JNA and
 * prints the version string. Used to verify the C++ toolchain and the JNA
 * binding before any Tesseract or OpenCV code is wired through the DLL.
 *
 * <p>Run with the JNA library path pointing at the DLL build output:
 * <pre>
 * java -Djna.library.path=D:\Projects_DevOps\multi_ocr\build\Release \
 *      -cp &lt;classpath&gt; com.allinweb.ch.ocr.bridge.OcrBridgeSmokeTest
 * </pre>
 */
public final class OcrBridgeSmokeTest {

    private OcrBridgeSmokeTest() {}

    public static void main(String[] args) {
        try {
            String version = OcrBridge.INSTANCE.aro_version();
            System.out.println("ar_ocr version: " + version);
            System.exit(0);
        } catch (UnsatisfiedLinkError e) {
            System.err.println(
                    "Failed to load ar_ocr.dll. Set -Djna.library.path to the directory containing the DLL.");
            System.err.println("Underlying error: " + e.getMessage());
            System.exit(1);
        } catch (Throwable t) {
            System.err.println("Unexpected failure calling aro_version: " + t);
            t.printStackTrace(System.err);
            System.exit(2);
        }
    }
}
