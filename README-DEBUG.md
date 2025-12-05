# PARAMS  DEBUG TEST
```bash
    // ENGINE Params for Debugging
    cmd.exe /c .\java\bin\java.exe -jar "C:/Program Files/ARWeb/AR_Web_Engine.jar" execute/j 52 31 "C:/ARWeb/Excel/CA-Next-Bank-Test.xlsx" -c C:/ARWeb/ARWeb.config


    // VM Option  For Web Scann and Others 
    WINDOW
    --module-path "C:/Projects/Full-Backup/ProgramFiles/javaFX/lib" --add-modules javafx.controls,javafx.web,javafx.fxml
    
    LINUX
    --module-path "/opt/ARWeb/ARWeb-Scanner/javaFX/lib/" --add-modules javafx.controls,javafx.web,javafx.fxml
    
    WINDOW
    // Param Config for Debugging
    -c "C:/ARWeb/Config-4.7/ARWeb.config"
    
    LINUX
    -c "/opt/ARWeb/Config-4.7/ARWeb.config"
    
    
    // VM Option  For Web JCEF Maven
    -Djava.library.path="/opt/ARWeb/ARWeb-Scanner/javaJCE/" 
    
```

## Arguments
```bash
-c "D:\Projects\ARWeb-Martini\Config-4.7\ARWeb.config"
```

# VM Options

## Java JCE 
```bash
-Djava.library.path="D:\Projects\ARWeb-Martini\ARWeb-Scanner\javaJCE"
```

## JavaFX 
```bash
--module-path "D:\Projects\ARWeb-Martini\ARWeb-Scanner\javaFX\lib" --add-modules javafx.controls,javafx.web,javafx.fxml
```



##  CSS Selectors
```text
Access Does Not Accept *[contains(@idCOMMA 'mat-input')]


Use  "" Instead

    *[contains(@idCOMMA \"mat-input\")]

Example:
 //Search Criteria
 
        // PRIORITY FOR ENGINE FIND ELEMENT
        #numero priorità, categoria, identificativo
        1,xpath,currentXPath
        2,coordinates,coordinates
        3,attribute,test-id
        4,attributeID,attributeID
        5,attributeName,attributeName
        6,attributes,allAttributes
        
        // SEARCH TERMS
        1,ByAttribute,test-id
        2,ByChained,By.tagName:input,By.className:mat-mdc-input-element
        3,ByChained,By.xpath://*[contains(@idCOMMA "mat-input")]
        4,ByTagName,input
        5,ByTagName,button
        6,ByChained,By.cssSelector:[id^="mat-input"]
 
        // WEB DRIVER POSSIBLE CONFIGS
        #proxy:proxy_address:proxy_port
        #argument:--disable-infobars
        #argument:--disable-dev-shm-usage
        #argument:--no-sandbox
        #systemProps:webdriver.chrome.logfile:logFolder
        #systemProps:webdriver.chrome.verboseLogging:true
 
        //EXTRA PARAMS & MISC
        #numero priorità, categoria, criterioricerca
        1,ByAttribute,test-id
        2,ByChained,By.tagName:input,By.className:mat-mdc-input-element
        3,ByChained,By.xpath://*[contains(@idCOMMA "mat-input")]
        4,ByTagName,button
        5,ByChained,By.cssSelector:[id^="mat-input"]
        6,ByChained,By.cssSelector:[id*="mat-input"]

        //Others
        1,ByXPath,//a[@href],a[href]
        2,ByAttribute,test-id
        3,ByChained,By.tagName:button,By.className:mdc-button
        4,ByTagName,button,label,a
        5,ByTagName,input
```
 ## Priorities Example
```text
    #numero priorità, categoria, identificativo
    1,attribute,test-id
    2,xpath,xpath
    3,coordinates,coordinates
```

## Search element terms
```text
   #numero priorità, categoria, identificativo
   1,ByXPath,//a[@href],a[href]
   2,ByLabels,label,spam,div,p
   3,attribute,martini-id
```	

## WebDrive Options
```text
   # Type : First Arg : Second Arg

    proxy:proxy_address:proxy_port
    #browser_log:active
    #argument:--disable-infobars
    #argument:--disable-dev-shm-usage
    #argument:--no-sandbox
    #systemProps:webdriver.chrome.logfile:logFolder
    #systemProps:webdriver.chrome.verboseLogging:true
    #experimentalOption:excludeSwitches,Collections.singletonList('enable-automation');
```

## Example Proxy Edge
```bash
     System.setProperty("webdriver.edge.driver", "path/to/msedgedriver.exe");

        // Proxy details
        String proxyAddress = "proxy_address:proxy_port";

        // Configure proxy settings
        Proxy proxy = new Proxy();
        proxy.setHttpProxy(proxyAddress)
             .setFtpProxy(proxyAddress)
             .setSslProxy(proxyAddress);

        // Configure Edge options
        EdgeOptions options = new EdgeOptions();
        options.setProxy(proxy);

        // Initialize the WebDriver with the options
        WebDriver driver = new EdgeDriver(options);
```