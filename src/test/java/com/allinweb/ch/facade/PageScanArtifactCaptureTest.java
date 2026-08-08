package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.vision.RasterImage;
import com.allinweb.ch.vision.RasterImageIO;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class PageScanArtifactCaptureTest {

    private static final String PAGE_URL = "https://bank.example/accounts";

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCurrentScreenshotAndValidRectangleJsonWithDprMetadata() throws Exception {
        ARPlaywrightDriver browser = mock(ARPlaywrightDriver.class);
        when(browser.currentUrl()).thenReturn(PAGE_URL);
        when(browser.evaluate(anyString(), any())).thenReturn(Map.of(
                "meta", Map.of(
                        "devicePixelRatio", 2.0d,
                        "viewportWidth", 100.0d,
                        "viewportHeight", 50.0d,
                        "documentWidth", 400.0d,
                        "documentHeight", 300.0d,
                        "scrollX", 4.0d,
                        "scrollY", 8.0d),
                "rects", List.of(Map.of(
                        "elementIndex", 0,
                        "xPath", "/html/body/input[1]",
                        "iframeXPath", "",
                        "found", true,
                        "bounds", Map.of(
                                "x", 10.0d,
                                "y", 12.0d,
                                "width", 80.0d,
                                "height", 20.0d,
                                "pageX", 14.0d,
                                "pageY", 20.0d)))));
        when(browser.screenshot(false)).thenReturn(png(200, 100));
        ElementDTO element = new ElementDTO();
        element.setXPath("/html/body/input[1]");
        ElementDTO missingXPath = new ElementDTO();

        PageScanSnapshotStore.CaptureMetadata metadata = PageScanArtifactCapture.capture(
                browser,
                ScannedPageIdentity.fromLiveUrl(PAGE_URL),
                List.of(element, missingXPath),
                temporaryDirectory,
                "viewport");

        assertEquals("viewport", metadata.screenshotScope());
        assertEquals(2.0d, metadata.devicePixelRatio());
        assertEquals(100.0d, metadata.cssWidth());
        assertEquals(50.0d, metadata.cssHeight());
        assertEquals(200, metadata.pixelWidth());
        assertEquals(100, metadata.pixelHeight());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("screenshot.png")));
        var rectangles = JsonParser.parseString(Files.readString(temporaryDirectory.resolve("rects.json")))
                .getAsJsonArray();
        assertEquals(1, rectangles.size());
        assertEquals(0, rectangles.get(0).getAsJsonObject().get("elementIndex").getAsInt());
        assertEquals(80.0d, rectangles.get(0).getAsJsonObject()
                .getAsJsonObject("bounds").get("width").getAsDouble());
        ArgumentCaptor<Object> targets = ArgumentCaptor.forClass(Object.class);
        verify(browser).evaluate(anyString(), targets.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> submitted = (List<Map<String, Object>>) targets.getValue();
        assertEquals(2, submitted.size());
        assertEquals(0, submitted.get(0).get("elementIndex"));
        assertEquals(1, submitted.get(1).get("elementIndex"));
        assertEquals("", submitted.get(1).get("xPath"));
        verify(browser).screenshot(false);
    }

    @Test
    void capturesEmptyFullPageUsingDocumentDimensions() throws Exception {
        ARPlaywrightDriver browser = mock(ARPlaywrightDriver.class);
        when(browser.currentUrl()).thenReturn(PAGE_URL);
        when(browser.evaluate(anyString(), any())).thenReturn(Map.of(
                "meta", Map.of(
                        "devicePixelRatio", 1.5d,
                        "viewportWidth", 100.0d,
                        "viewportHeight", 50.0d,
                        "documentWidth", 300.0d,
                        "documentHeight", 200.0d,
                        "scrollX", 0.0d,
                        "scrollY", 0.0d),
                "rects", List.of()));
        when(browser.screenshot(true)).thenReturn(png(450, 300));

        PageScanSnapshotStore.CaptureMetadata metadata = PageScanArtifactCapture.capture(
                browser,
                ScannedPageIdentity.fromLiveUrl(PAGE_URL),
                List.of(),
                temporaryDirectory,
                "full_page");

        assertEquals("full_page", metadata.screenshotScope());
        assertEquals(300.0d, metadata.cssWidth());
        assertEquals(200.0d, metadata.cssHeight());
        assertEquals(0, JsonParser.parseString(Files.readString(temporaryDirectory.resolve("rects.json")))
                .getAsJsonArray().size());
        verify(browser).screenshot(true);
    }

    @Test
    void refusesAChangedPageBeforeCapturingAnyArtifact() {
        ARPlaywrightDriver browser = mock(ARPlaywrightDriver.class);
        when(browser.currentUrl()).thenReturn("https://bank.example/payments");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> PageScanArtifactCapture.capture(
                        browser,
                        ScannedPageIdentity.fromLiveUrl(PAGE_URL),
                        List.of(),
                        temporaryDirectory,
                        "viewport"));

        assertTrue(failure.getMessage().contains("browser page changed"));
        verify(browser, never()).evaluate(anyString(), any());
        verify(browser, never()).screenshot(false);
        assertFalse(Files.exists(temporaryDirectory.resolve("screenshot.png")));
    }

    @Test
    void refusesAnOversizedScreenshotBeforeWritingIt() throws Exception {
        ARPlaywrightDriver browser = mock(ARPlaywrightDriver.class);
        when(browser.currentUrl()).thenReturn(PAGE_URL);
        when(browser.evaluate(anyString(), any())).thenReturn(Map.of(
                "meta", Map.of(
                        "devicePixelRatio", 1.0d,
                        "viewportWidth", 100.0d,
                        "viewportHeight", 50.0d,
                        "documentWidth", 100.0d,
                        "documentHeight", 50.0d,
                        "scrollX", 0.0d,
                        "scrollY", 0.0d),
                "rects", List.of()));
        when(browser.screenshot(false)).thenReturn(
                new byte[(int) PageScanArtifactPolicy.MAX_SCREENSHOT_BYTES + 1]);

        Exception failure = assertThrows(
                Exception.class,
                () -> PageScanArtifactCapture.capture(
                        browser,
                        ScannedPageIdentity.fromLiveUrl(PAGE_URL),
                        List.of(),
                        temporaryDirectory,
                        "viewport"));

        assertTrue(failure.getMessage().contains("exceeds its safe size"));
        assertFalse(Files.exists(temporaryDirectory.resolve("screenshot.png")));
    }

    private byte[] png(int width, int height) throws Exception {
        Path source = temporaryDirectory.resolve("source-" + width + "x" + height + ".png");
        RasterImageIO.writePng(new RasterImage(width, height, new int[width * height]), source);
        return Files.readAllBytes(source);
    }
}
