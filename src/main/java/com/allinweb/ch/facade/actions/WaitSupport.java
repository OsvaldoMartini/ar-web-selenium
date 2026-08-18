package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.PerformMessage;
import java.util.concurrent.TimeUnit;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wait/timing helpers (cluster I). The HOLD sleeps themselves stay on the PerformActions
 * facade because they are {@code synchronized} + {@code Object.wait()} on the singleton
 * monitor — moving them would change which lock is used. Bodies moved verbatim.
 */
public final class WaitSupport {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private WaitSupport() {}

    public static long fromSecondsToMilliseconds(TimeUnit timeUnit, int units) throws Exception {
        long milliseconds;

        switch (timeUnit) {
            case SECONDS:
                milliseconds = units * 1000L;
                break;

            case MINUTES:
                milliseconds = units * 1000L * 60L;
                break;

            default:
                throw new Exception("time unit: " + timeUnit.name() + " is not available for this operation");
        }
        return milliseconds;
    }

    public static void waitPage(Wait<WebDriver> waitForPage, WebDriver driver) {
        if (driver != null) {
            try {

                waitForPage.until(d -> ((JavascriptExecutor) driver)
                        .executeScript("return document.readyState")
                        .equals("complete"));
            } catch (Exception ex) {

                logOperations.warn(String.format(
                        "WaitForPage.until(d -> ((JavascriptExecutor) driver) error: %s", ex.getMessage()));

                PerformMessage.getInstance().couldNotFindElement("WaitForPage.until");
            }
        } else {
            // Handle the case when driver is null (e.g., throw an exception or initialize the driver)

            logOperations.warn("WaitForPage.until(d -> ((JavascriptExecutor) driver) is returning nulls");
        }
    }

    public static long duration(long startTime) {
        long currentInstructionEndTime = System.nanoTime();
        return currentInstructionEndTime - startTime;
    }
}
