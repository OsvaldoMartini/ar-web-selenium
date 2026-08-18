# Webdriver Options
```java
        // Set the path to the ChromeDriver executable
        ChromeOptions options = new ChromeOptions();
        //        options.addArguments("--headless"); // Run in headless mode
        options.setBinary(ARConstants.USER_PATH + "\\chrome\\chrome.exe");
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        //        options.setBinary("C:/Program Files (x86)/Google/Chrome/Application/chrome.exe");

        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        // options.addArguments("--headless"); // Optional: run Chrome in headless mode
        options.addArguments("start-maximized");
        WebDriver driver = new ChromeDriver(options);
```

## With Logs
```java		
		
		
		  String logFolder = "C:\\AllinWeb\\ARWeb\\Logs";
        String webDriverPath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        // Set the path to the ChromeDriver executable
        ChromeOptions options = new ChromeOptions();
        //        options.addArguments("--headless"); // Run in headless mode
        System.setProperty("webdriver.chrome.verboseLogging", "true");
        System.setProperty("webdriver.chrome.logfile", logFolder + "\\_chrome_browser.log");

        //                        options.setBinary(ARConstants.USER_PATH + "\\chrome\\chrome.exe");
        options.setBinary(webDriverPath);
        //                                                options.setBinary("C:/Program
        // Files/Google/Chrome/Application/chrome.exe");
        //                        options.setBinary("C:/Program Files
        // (x86)/Google/Chrome/Application/chrome.exe");
        //                        options.addArguments("headless");
        //                        options.addArguments("--disable-infobars");
        //                        options.addArguments("--disable-dev-shm-usage");
        //                        options.addArguments("--no-sandbox");
        //                        options.addArguments("--remote-debugging-port=9222");
        options.setExperimentalOption(
                "excludeSwitches", Collections.singletonList("enable-automation"));
        WebDriver driver = new ChromeDriver(options);
```
## Search Result from  "FindElementsWithAttributes.java!

```text		
		
Tag: input, id: username, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/div[1]/form[1]/div[1]/div[1]/div[1]/div[1]/input[1]
Tag: input, id: password, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/div[1]/form[1]/div[1]/div[1]/div[2]/div[1]/input[1]
Tag: script, id: client-scripts, XPath: /html[1]/body[1]/script[1]
Tag: img, id: prompt-logo-center, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/header[1]/img[1]
Tag: div, id: custom-prompt-logo, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/header[1]/div[1]
Tag: style, id: custom-styles-container, XPath: /html[1]/head[1]/style[1]
Tag: meta, name: ulp-version, XPath: /html[1]/head[1]/meta[4]
Tag: meta, name: robots, XPath: /html[1]/head[1]/meta[5]
Tag: input, name: username, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/div[1]/form[1]/div[1]/div[1]/div[1]/div[1]/input[1]
Tag: meta, name: viewport, XPath: /html[1]/head[1]/meta[3]
Tag: input, name: password, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/div[1]/form[1]/div[1]/div[1]/div[2]/div[1]/input[1]
Tag: button, name: action, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/div[1]/form[1]/div[2]/button[1]
Tag: input, name: state, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/div[1]/form[1]/input[1]
Tag: button, button: null, XPath: /html[1]/body[1]/main[1]/section[1]/div[1]/div[1]/div[1]/form[1]/div[1]/div[1]/div[2]/div[1]/button[1]
		
```