# MODULE COMPILE
```bash
    // COMPILE
    javac --module-path /lib --add-modules javafx.controls,javafx.fxml -d build/com.allinweb src/module-info.java src/com/allinweb/ch/javafxapp/JavaFxApp.java


    // RUN
    java --module-path /path/to/javafx-sdk-19/lib --add-modules javafx.controls,javafx.fxml -cp build com.example.javafxapp.MainApp
```