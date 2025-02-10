package com.allinweb.ch.component.pane;

import javafx.application.Application;
import javafx.stage.Stage;

public abstract class AbstractARScannedElementPane extends Application {

    // Abstract methods to force subclasses to implement start and stop
    @Override
    public abstract void start(Stage primaryStage) throws Exception;

    @Override
    public abstract void stop() throws Exception;
}
