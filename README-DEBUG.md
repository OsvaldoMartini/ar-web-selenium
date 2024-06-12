# PARAMS  DEBUG TEST
```bash
    // ENGINE Params for Debugging
    cmd.exe /c .\java\bin\java.exe -jar "C:/Program Files/ABRWeb\ABR_Web_Engine.jar" execute/j 52 31 "C:/ABRWeb/Excel/CA-Next-Bank-Test.xlsx" -c C:/ABRWeb/ABRWeb.config


    // VM Option  For Web Scann and Others 
    --module-path "C:/Projects/Full-Backup/ProgramFiles/javaFX/lib" --add-modules javafx.controls,javafx.fxml

    // Param Config for Debugging
    -c "C:/ABRWeb/ABRWeb.config"
```



        // Find elements with ID containing 'mat-input'
//
List<WebElement> elements = driver.findElements(By.xpath("//*[contains(@id, 'mat-input')]"));
// Find elements with ID starting with 'mat-input'
List<WebElement> elements = driver.findElements(By.cssSelector("[id^='mat-input']"));


        // Iterate through the elements and print their IDs
        for (WebElement element : elements) {
            System.out.println("Element ID: " + element.getAttribute("id"));
        }

        // Close the driver
        driver.quit();
    }