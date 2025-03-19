package com.allinweb.ch.tests;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

public class ParallelWebDrivers {

    private static ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    public static WebDriver getDriver() {
        if (driverThreadLocal.get() == null) {
            String webDriverPath =
                    "D:/Projects/AllinWeb/ar-web-selenium-archive/ar-web-selenium-files/ProgramFiles/edgedriver-versions/msedgedriver_64-(134.0.3124.77).exe";
            System.setProperty("webdriver.edge.driver", webDriverPath);

            EdgeOptions options = new EdgeOptions();
            options.addArguments("--remote-allow-origins=*"); // Required for some Edge versions
            options.addArguments("--start-maximized"); // Opens browser in full-screen
            options.addArguments("--disable-gpu"); // Fixes potential rendering issues
            options.addArguments("--no-sandbox"); // Bypass OS security model
            options.addArguments("--disable-dev-shm-usage"); // Prevents resource exhaustion

            WebDriver driver = new EdgeDriver(options);
            driverThreadLocal.set(driver);
        }
        return driverThreadLocal.get();
    }

    public static void quitDriver() {
        WebDriver driver = driverThreadLocal.get();
        if (driver != null) {
            driver.quit();
            driverThreadLocal.remove();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            WebDriver driver = ParallelWebDrivers.getDriver();
            driver.get("https://www.example.com/page1");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } // Wait for page load
            ParallelWebDrivers.quitDriver();
        });

        Thread t2 = new Thread(() -> {
            WebDriver driver = ParallelWebDrivers.getDriver();
            driver.get("https://www.example.com/page2");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } // Wait for page load
            ParallelWebDrivers.quitDriver();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}
