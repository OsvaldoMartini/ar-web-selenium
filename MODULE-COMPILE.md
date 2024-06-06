# MODULE COMPILE
```bash
    // COMPILE  THAT WORKED
    javac --module-path lib --add-modules javafx.controls,javafx.fxml -d build/com.example.javafxapp src/module-info.java
    javac --module-path lib --add-modules javafx.controls,javafx.fxml -d build src/module-info.java "D:\Projects\AllinWeb\abr-web-selenium\src\main\java\com\example\javafxapp\MainApp.java"
    
    javac --module-path lib --add-modules javafx.controls,javafx.fxml -d build/com.allinweb src/module-info.java src/com/allinweb/ch/javafxapp/JavaFxApp.java


    // RUN
    java --module-path build -m com.example.javafxapp/com.example.javafxapp.MainApp


    java --module-path /path/to/javafx-sdk-19/lib --add-modules javafx.controls,javafx.fxml -cp build com.example.javafxapp.MainApp
```