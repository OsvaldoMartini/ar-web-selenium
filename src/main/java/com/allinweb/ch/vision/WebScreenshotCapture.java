package com.allinweb.ch.vision;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

public final class WebScreenshotCapture {

    private WebScreenshotCapture() {}

    public static byte[] viewportBytes(WebDriver driver) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    public static BufferedImage viewport(WebDriver driver) throws IOException {
        byte[] png = viewportBytes(driver);
        return ImageIO.read(new ByteArrayInputStream(png));
    }
}
