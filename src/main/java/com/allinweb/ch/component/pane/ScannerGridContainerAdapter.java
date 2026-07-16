package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerGridBootstrapService;
import com.allinweb.ch.util.WebBuildExtractor;
import com.google.gson.Gson;
import javafx.concurrent.Worker;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerGridContainerAdapter {
    private final WebView webView = new WebView();
    private final ScannerGridBootstrapService bootstrapService = new ScannerGridBootstrapService();
    private final Gson gson;
    private WebEngine webEngine;

    ScannerGridContainerAdapter(Gson gson) {
        this.gson = gson;
    }

    void load(ScannerGridBootstrapService.Request request) {
        webEngine = webView.getEngine();
        webEngine.javaScriptEnabledProperty().set(true);

        String indexUrl = WebBuildExtractor.getIndexUrl();
        log.info(
                "buildWebView — loading scanner grid WebView: url={} port={} session={}",
                indexUrl,
                request.port(),
                request.sessionId());

        webEngine.setOnError(e -> log.error("[webview-js] {}", e.getMessage()));
        webEngine.setOnAlert(e -> log.info("[webview-alert] {}", e.getData()));
        webEngine.load(indexUrl);

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            log.info("buildWebView — WebView load state {} -> {}", oldState, newState);
            if (newState == Worker.State.FAILED) {
                Throwable ex = webEngine.getLoadWorker().getException();
                log.error(
                        "buildWebView — WebView FAILED to load {} : {}", indexUrl, ex == null ? "?" : ex.getMessage());
            }
            if (newState == Worker.State.SUCCEEDED) {
                dispatchBootstrap(request);
            }
        });
    }

    HBox componentBox() {
        HBox box = new HBox(webView);
        HBox.setHgrow(webView, Priority.ALWAYS);
        VBox.setVgrow(webView, Priority.ALWAYS);
        return box;
    }

    void attachTo(HBox box) {
        box.getChildren().clear();
        box.getChildren().addAll(webView);
    }

    boolean isInitialized() {
        return webEngine != null;
    }

    private void dispatchBootstrap(ScannerGridBootstrapService.Request request) {
        try {
            webEngine.executeScript(bootstrapService.bootstrapScript(request, gson));
            log.info("buildWebView — receiveDataFromJava dispatched to scanner grid");
        } catch (Exception e) {
            log.error("buildWebView  Error: " + e.getMessage());
        }
    }
}
