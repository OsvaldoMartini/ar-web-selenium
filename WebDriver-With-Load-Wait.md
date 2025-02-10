 private void setupWebDriver() {
        String logFolder = "D:\\Projects\\AllinWeb\\ARWeb\\Logs";
        String webDriverPath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        // Set the path to the ChromeDriver executable
        ChromeOptions options = new ChromeOptions();
        //        options.addArguments("--headless"); // Run in headless mode
        System.setProperty("webdriver.chrome.verboseLogging", "true");
        System.setProperty("webdriver.chrome.logfile", logFolder + "\\_chrome_browser.log");

        //                        options.setBinary(ARConstants.CURRENT_PATH + "\\chrome\\chrome.exe");
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
        driver = new ChromeDriver(options);

        // Load a webpage
        driver.get("https://ME-34272.dev.marginedge.com"); // Replace with your target URL


        Wait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(10))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class);

        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
    }