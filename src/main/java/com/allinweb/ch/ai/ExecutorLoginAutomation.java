package com.allinweb.ch.ai;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class ExecutorLoginAutomation {

    public static void main(String[] args) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("output.json")));
        JSONArray elements = new JSONArray(json);

        Scanner scanner = new Scanner(System.in);
        System.out.print("Inserisci il tuo ID utente: ");
        String username = scanner.nextLine();
        System.out.print("Inserisci la tua password: ");
        String password = scanner.nextLine();

        System.setProperty("webdriver.chrome.driver", "chromedriver");
        WebDriver driver = new ChromeDriver();

        driver.get("https://www.inlinea.ch");
        Thread.sleep(2000);

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            String type = element.optString("type");
            String label = element.optString("label", "");
            String placeholder = element.optString("placeholder", "");

            if ("input".equals(type)) {
                WebElement input = findInputByPlaceholder(driver, placeholder);
                if (placeholder.toLowerCase().contains("id")) {
                    input.sendKeys(username);
                } else if (placeholder.toLowerCase().contains("pass")) {
                    input.sendKeys(password);
                }
            } else if ("button".equals(type)) {
                WebElement button = findButtonByText(driver, label);
                button.click();
            }
        }

        System.out.println("✅ Tentativo di login eseguito.");
    }

    private static WebElement findInputByPlaceholder(WebDriver driver, String placeholder) {
        List<WebElement> inputs = driver.findElements(By.tagName("input"));
        for (WebElement input : inputs) {
            String attr = input.getAttribute("placeholder");
            if (attr != null && attr.equalsIgnoreCase(placeholder)) {
                return input;
            }
        }
        throw new RuntimeException("Campo input non trovato per placeholder: " + placeholder);
    }

    private static WebElement findButtonByText(WebDriver driver, String text) {
        List<WebElement> buttons = driver.findElements(By.tagName("button"));
        for (WebElement btn : buttons) {
            if (btn.getText().trim().equalsIgnoreCase(text)) {
                return btn;
            }
        }

        List<WebElement> submits = driver.findElements(By.cssSelector("input[type='submit']"));
        for (WebElement submit : submits) {
            if (submit.getAttribute("value").equalsIgnoreCase(text)) {
                return submit;
            }
        }

        throw new RuntimeException("Bottone con testo \"" + text + "\" non trovato.");
    }
}
