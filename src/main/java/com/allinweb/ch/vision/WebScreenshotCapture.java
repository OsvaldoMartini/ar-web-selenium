package com.allinweb.ch.vision;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Two capture modes:
 * <ul>
 *   <li>{@link #viewportBytes(WebDriver)} / {@link #viewport(WebDriver)} — only what's currently
 *       on screen. Cheap, single Selenium round-trip.</li>
 *   <li>{@link #fullPageBytes(WebDriver)} / {@link #fullPage(WebDriver)} — scroll-and-stitch the
 *       entire document. Roadmap 2 follow-on so footer elements (y &gt; viewport_height) are
 *       reachable by the OCR pass; without this, {@code OcrDomCorrelator} returns {@code NONE}
 *       for everything below the fold. Driven by {@code screenshot.scope=full_page} in OcrConfig.</li>
 * </ul>
 */
@Slf4j
public final class WebScreenshotCapture {

    /** Time to wait between scroll and capture so layouts that animate-on-scroll settle. */
    private static final long SCROLL_SETTLE_MS = 150L;

    private WebScreenshotCapture() {}

    public static byte[] viewportBytes(WebDriver driver) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    public static BufferedImage viewport(WebDriver driver) throws IOException {
        byte[] png = viewportBytes(driver);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /**
     * Stitch the full document by scrolling viewport-by-viewport and pasting each tile into a
     * single {@link BufferedImage}. Restores the original scroll position before returning so
     * the user-visible state is unchanged.
     *
     * <p>Works across drivers that don't expose {@code Page.captureScreenshot}'s
     * {@code captureBeyondViewport} flag — older Selenium 4 builds and some embedded browsers.
     */
    public static BufferedImage fullPage(WebDriver driver) throws IOException {
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
            // Page fits in the viewport — no need to stitch.
            BufferedImage single = viewport(driver);
            // Restore in case the user had non-zero scroll.
            js.executeScript("window.scrollTo(0, arguments[0]);", initialScrollY);
            return single;
        }

        int pixelW = (int) Math.round(viewportWidth * dpr);
        int pixelH = (int) Math.round(totalHeight * dpr);
        BufferedImage stitched = new BufferedImage(pixelW, pixelH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = stitched.createGraphics();

        try {
            long y = 0;
            while (y < totalHeight) {
                js.executeScript("window.scrollTo(0, arguments[0]);", y);
                try {
                    Thread.sleep(SCROLL_SETTLE_MS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }

                BufferedImage tile = viewport(driver);

                // Use the *actual* post-scroll y in case the browser clamped at the bottom.
                long actualY =
                        ((Number) js.executeScript("return window.pageYOffset || window.scrollY || 0;")).longValue();
                int tilePixelY = (int) Math.round(actualY * dpr);
                g.drawImage(tile, 0, tilePixelY, null);

                y += viewportHeight;
            }
        } finally {
            g.dispose();
            // Restore so the user's view doesn't visibly jump after the OCR pass.
            try {
                js.executeScript("window.scrollTo(0, arguments[0]);", initialScrollY);
            } catch (RuntimeException restoreEx) {
                log.debug("Could not restore scroll position: {}", restoreEx.getMessage());
            }
        }

        return stitched;
    }

    /** PNG bytes of the stitched full page. Convenience for {@link PageOcrDumper}. */
    public static byte[] fullPageBytes(WebDriver driver) throws IOException {
        BufferedImage img = fullPage(driver);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
