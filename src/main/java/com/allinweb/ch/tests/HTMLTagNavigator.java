package com.allinweb.ch.tests;

import com.allinweb.ch.util.ARConstants;
import java.util.Collections;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class HTMLTagNavigator {

    public static void main(String[] args) {
        // Set up WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");
        //        WebDriver driver = new ChromeDriver(options);
        //        driver.get("https://www.example.com");
        ChromeOptions options = new ChromeOptions();
        //        options.addArguments("--headless"); // Run in headless mode
        options.setBinary(ARConstants.CURRENT_PATH + "\\chrome\\chrome.exe");
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        //        options.setBinary("C:/Program Files (x86)/Google/Chrome/Application/chrome.exe");

        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        // options.addArguments("--headless"); // Optional: run Chrome in headless mode
        options.addArguments("start-maximized");
        WebDriver driver = new ChromeDriver(options);

        try {
            // Open the desired webpage
            driver.get("https://www.ca-nextbank.ch/en/contact"); // Replace with the desired URL
            //            driver.get("https://www.fnz.com/contact"); // Replace with the desired URL

            // Get all elements on the page
            List<WebElement> elements = driver.findElements(By.xpath("//*"));

            // Loop through each element and send focus using Tab key
            for (WebElement element : elements) {
                try {
                    // Scroll to the element and bring it into view
                    ((ChromeDriver) driver).executeScript("arguments[0].scrollIntoView(true);", element);

                    // Click on the element to bring it into focus
                    element.click();

                    // Print the tag name of the element
                    System.out.println("Focused on: " + element.getTagName());

                    // Send Tab key to move to the next element
                    element.sendKeys(Keys.TAB);
                } catch (Exception e) {
                    // Handle the exception if the element is not interactable
                    System.out.println("Cannot focus on element: " + element.getTagName());
                }
            }

        } finally {
            // Quit the driver
            driver.quit();
        }
    }
}
