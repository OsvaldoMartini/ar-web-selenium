package com.allinweb.ch.component.pane;

import javafx.concurrent.Task;

final class ScannerPluginBackgroundThreadAdapter {

    void start(Thread thread, String name) {
        thread.setDaemon(true);
        thread.setName(name);
        thread.start();
    }

    void start(Task<?> task, String name) {
        start(new Thread(task), name);
    }
}
