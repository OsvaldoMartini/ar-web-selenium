package com.allinweb.ch.util;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.vision.RasterImage;
import com.allinweb.ch.vision.RasterImageIO;
import java.io.IOException;

/** Captures viewport or full-page PNG images from the active Playwright page. */
public final class WebScreenshotCapture {

    private WebScreenshotCapture() {}

    public static byte[] viewportBytes(ARPlaywrightDriver browser) {
        return browser.screenshot(false);
    }

    public static byte[] fullPageBytes(ARPlaywrightDriver browser) {
        return browser.screenshot(true);
    }

    public static RasterImage viewport(ARPlaywrightDriver browser) throws IOException {
        return RasterImageIO.readPng(viewportBytes(browser));
    }

    public static RasterImage fullPage(ARPlaywrightDriver browser) throws IOException {
        return RasterImageIO.readPng(fullPageBytes(browser));
    }
}
