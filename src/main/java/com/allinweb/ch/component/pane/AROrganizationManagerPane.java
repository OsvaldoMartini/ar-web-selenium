package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.WebBuildExtractor;
import com.google.common.base.Strings;
import javafx.concurrent.Worker;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AROrganizationManagerPane extends ARPane {

    private static final String SESSION_ID = "organizationManager";
    private static final int DEFAULT_PORT = 54525;
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();

    protected static volatile AROrganizationManagerPane instance;

    private final WebView webView = new WebView();
    private AnchorPane mainPane;

    private AROrganizationManagerPane() {
        super();
    }

    public static AROrganizationManagerPane getInstance() {
        if (instance == null) {
            synchronized (AROrganizationManagerPane.class) {
                if (instance == null) {
                    instance = new AROrganizationManagerPane();
                }
            }
        }
        return instance;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        mainPane = new AnchorPane(webView);
        AnchorPane.setTopAnchor(webView, 0D);
        AnchorPane.setRightAnchor(webView, 0D);
        AnchorPane.setBottomAnchor(webView, 0D);
        AnchorPane.setLeftAnchor(webView, 0D);

        webView.setContextMenuEnabled(false);
        WebEngine webEngine = webView.getEngine();
        webEngine.javaScriptEnabledProperty().set(true);
        webEngine.load(WebBuildExtractor.getIndexUrl());
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                dispatchReactBootstrap(webEngine);
            }
        });
    }

    @Override
    public void initUIBehaviour() {}

    private void dispatchReactBootstrap(WebEngine webEngine) {
        int port = resolveSocketPort();
        try {
            webEngine.executeScript("setTimeout(function() { window.receiveDataFromJava(JSON.stringify([]), "
                    + port + ", '" + SESSION_ID + "', -1, '', -9999, '' ) }, 250)");
        } catch (Exception e) {
            log.error("Organization manager React bootstrap failed: {}", e.getMessage());
        }
    }

    private int resolveSocketPort() {
        try {
            String port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
            if (!Strings.isNullOrEmpty(port)) {
                return Integer.parseInt(port);
            }
        } catch (Exception e) {
            log.warn("Invalid PORT_SOCKET, falling back to {}: {}", DEFAULT_PORT, e.getMessage());
        }
        return DEFAULT_PORT;
    }
}
