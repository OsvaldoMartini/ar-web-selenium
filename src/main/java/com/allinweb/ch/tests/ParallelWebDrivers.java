package com.allinweb.ch.tests;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

public class ParallelWebDrivers {

    private static ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    public static WebDriver getDriver() {
        if (driverThreadLocal.get() == null) {
            String webDriverPath =
                    "D:\\Projects\\AllinWeb\\ar-web-selenium-archive\\ar-web-selenium-files\\ProgramFiles\\edgedriver-versions\\msedgedriver_64-(134).exe"; // Replace with your path
            System.setProperty("webdriver.edge.driver", webDriverPath);
            EdgeOptions options = new EdgeOptions();
            driverThreadLocal.set(new EdgeDriver(options));
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
            ParallelWebDrivers.quitDriver();
        });

        Thread t2 = new Thread(() -> {
            WebDriver driver = ParallelWebDrivers.getDriver();
            driver.get("https://www.example.com/page2");
            ParallelWebDrivers.quitDriver();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}
