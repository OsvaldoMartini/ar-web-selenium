package com.allinweb.ch.util;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.vision.RasterImage;
import com.allinweb.ch.vision.RasterImageIO;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Two capture modes:
 * <ul>
 *   <li>{@link #viewportBytes(WebDriver)} / {@link #viewport(WebDriver)} - only what's currently
 *       on screen. Cheap, single Selenium round-trip.</li>
 *   <li>{@link #fullPageBytes(WebDriver)} / {@link #fullPage(WebDriver)} - scroll-and-stitch the
 *       entire document. Lets footer elements (y &gt; viewport_height) reach the OCR pass.
 *       Driven by {@code screenshot.scope=full_page} in OcrConfig.</li>
 * </ul>
 */
@Slf4j
public final class WebScreenshotCapture {

    private static final long SCROLL_SETTLE_MS = 150L;

    private WebScreenshotCapture() {}

    public static byte[] viewportBytes(WebDriver driver) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    public static RasterImage viewport(WebDriver driver) throws IOException {
        return RasterImageIO.readPng(viewportBytes(driver));
    }

    /**
     * Stitch the full document by scrolling viewport-by-viewport and pasting each tile into a
     * single raster image. Restores the original scroll position before returning so the
     * user-visible state is unchanged.
     */
    public static RasterImage fullPage(WebDriver driver) throws IOException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        long initialScrollY =
                ((Number) js.executeScript("return window.pageYOffset || window.scrollY || 0;")).longValue();

        long totalHeight = ((Number) js.executeScript("return Math.max(" + "document.body.scrollHeight,"
                        + "document.documentElement.scrollHeight,"
                        + "document.body.offsetHeight,"
                        + "document.documentElement.offsetHeight"
                        + ");"))
                .longValue();
        long viewportHeight = ((Number) js.executeScript("return window.innerHeight;")).longValue();
        long viewportWidth = ((Number) js.executeScript("return window.innerWidth;")).longValue();
        double dpr = ((Number) js.executeScript("return window.devicePixelRatio || 1;")).doubleValue();

        if (totalHeight <= viewportHeight) {
            RasterImage single = viewport(driver);
            js.executeScript("window.scrollTo(0, arguments[0]);", initialScrollY);
            return single;
        }

        int pixelW = (int) Math.round(viewportWidth * dpr);
        int pixelH = (int) Math.round(totalHeight * dpr);
        RasterImageCanvas stitched = new RasterImageCanvas(pixelW, pixelH);

        try {
            long y = 0;
            while (y < totalHeight) {
                js.executeScript("window.scrollTo(0, arguments[0]);", y);
                try {
                    Thread.sleep(SCROLL_SETTLE_MS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }

                RasterImage tile = viewport(driver);

                long actualY =
                        ((Number) js.executeScript("return window.pageYOffset || window.scrollY || 0;")).longValue();
                int tilePixelY = (int) Math.round(actualY * dpr);
                stitched.copyTile(tile, tilePixelY);

                y += viewportHeight;
            }
        } finally {
            try {
                js.executeScript("window.scrollTo(0, arguments[0]);", initialScrollY);
            } catch (RuntimeException restoreEx) {
                log.debug("Could not restore scroll position: {}", restoreEx.getMessage());
            }
        }

        return stitched.toImage();
    }

    /** PNG bytes of the stitched full page. Convenience for {@link PageOcrDumper}. */
    public static byte[] fullPageBytes(WebDriver driver) throws IOException {
        return RasterImageIO.toPngBytes(fullPage(driver));
    }

    public static byte[] viewportBytes(ARPlaywrightDriver pw) {
        return pw.screenshot(false);
    }

    public static byte[] fullPageBytes(ARPlaywrightDriver pw) {
        return pw.screenshot(true);
    }

    public static RasterImage viewport(ARPlaywrightDriver pw) throws IOException {
        return RasterImageIO.readPng(pw.screenshot(false));
    }

    public static RasterImage fullPage(ARPlaywrightDriver pw) throws IOException {
        return RasterImageIO.readPng(pw.screenshot(true));
    }

    private static final class RasterImageCanvas {
        private final int width;
        private final int height;
        private final int[] rgb;

        private RasterImageCanvas(int width, int height) {
            this.width = width;
            this.height = height;
            this.rgb = new int[width * height];
        }

        private void copyTile(RasterImage tile, int targetY) {
            if (tile == null) return;
            int startY = Math.max(0, targetY);
            int tileStartY = Math.max(0, -targetY);
            int copyHeight = Math.min(tile.height() - tileStartY, height - startY);
            int copyWidth = Math.min(tile.width(), width);
            if (copyWidth <= 0 || copyHeight <= 0) return;

            for (int y = 0; y < copyHeight; y++) {
                int targetOffset = (startY + y) * width;
                for (int x = 0; x < copyWidth; x++) {
                    rgb[targetOffset + x] = tile.pixel(x, tileStartY + y);
                }
            }
        }

        private RasterImage toImage() {
            return new RasterImage(width, height, rgb);
        }
    }
}
