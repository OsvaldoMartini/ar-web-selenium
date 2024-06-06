package com.allinweb.ch.tests;

public class JavaScriptXPathExample {

    // Method to find XPath using JavaScript
    //    public static CompletableFuture<String> findXPathAsync(WebElement element) {
    //        CompletableFuture<String> future = new CompletableFuture<>();
    //        CompletableFuture.runAsync(() -> {
    //            String xpath = findXPath(element);
    //            future.complete(xpath);
    //        });
    //        return future;
    //    }

    // Method to execute JavaScript to find XPath
    //    private static String findXPath(WebElement element) {
    //        WebDriver driver = ((ChromeDriver) element.getWrappedDriver());
    //        String script = "function getXPath(element) {" + "  if (element === document.body)"
    //                + "    return '/html';"
    //                + "  var ix = 0;"
    //                + "  var siblings = element.parentNode.childNodes;"
    //                + "  for (var i = 0; i < siblings.length; i++) {"
    //                + "    var sibling = siblings[i];"
    //                + "    if (sibling === element)"
    //                + "      return getXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix +
    // 1) + ']';"
    //                + "    if (sibling.nodeType === 1 && sibling.tagName === element.tagName)"
    //                + "      ix++;"
    //                + "  }"
    //                + "}"
    //                + "return getXPath(arguments[0]);";
    //        ScriptEngineManager manager = new ScriptEngineManager();
    //        ScriptEngine engine = manager.getEngineByName("javascript");
    //        try {
    //            return (String) engine.eval(script + "getXPath(arguments[0]);", element);
    //        } catch (ScriptException e) {
    //            e.printStackTrace();
    //            return null;
    //        }
    //    }

    public static void main(String[] args) {
        // Set up WebDriver
        //        System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");
        //        WebDriver driver = new ChromeDriver(options);
        //        driver.get("https://www.example.com");
        //
        //        // Find WebElement (example)
        //        WebElement element = driver.findElement(By.tagName("h1"));
        //
        //        // Find XPath asynchronously
        //        CompletableFuture<String> future = findXPathAsync(element);
        //        future.thenAccept(xpath -> System.out.println("XPath: " + xpath));
        //
        //        // Wait for the computation to complete
        //        future.join(); // This waits for the CompletableFuture to complete
        //
        //        // Close WebDriver
        //        driver.quit();
    }
}
