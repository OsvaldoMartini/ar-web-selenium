package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.ConfigPresentation;
import com.allinweb.ch.facade.ConfigPresentationRegistry;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.WebBuildExtractor;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARConfigManagerPane extends ARPane implements ConfigPresentation {

    private static final String SESSION_ID = "configManager";
    private static final int DEFAULT_PORT = 54525;
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();

    protected static volatile ARConfigManagerPane instance;

    private final WebView webView = new WebView();
    private AnchorPane mainPane;
    private Stage modalStage;
    private boolean isEnabledLicence;

    private ARConfigManagerPane() {
        super();
        ConfigPresentationRegistry.getInstance().install(this);
    }

    public static ARConfigManagerPane getInstance() {
        if (instance == null) {
            synchronized (ARConfigManagerPane.class) {
                if (instance == null) {
                    instance = new ARConfigManagerPane();
                }
            }
        }
        return instance;
    }

    public void initialize(Stage modalStage, boolean isEnabledLicence) {
        this.modalStage = modalStage;
        this.isEnabledLicence = isEnabledLicence;
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

    public String choosePath(String mode) {
        log.info("Config path chooser request ignored; React must provide the {} path directly", mode);
        return null;
    }

    public void openOrganizations() {
        Platform.runLater(() -> {
            ErrorMessage errorMessage = performDataBase.loadAllDataUsers();
            if (errorMessage == null) {
                errorMessage = performDBEngine.loadHomeBanking(null);
            }
            if (errorMessage == null) {
                errorMessage = performDBEngine.loadHomeUrls(null);
            }
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
                return;
            }
            log.info("Organization manager request ignored from config shell; use the React dashboard container");
        });
    }

    public void closeModal() {
        Platform.runLater(() -> {
            if (modalStage != null) {
                modalStage.close();
            }
        });
    }

    private void dispatchReactBootstrap(WebEngine webEngine) {
        int port = resolveSocketPort();
        try {
            webEngine.executeScript("setTimeout(function() { window.receiveDataFromJava(JSON.stringify([]), "
                    + port + ", '" + SESSION_ID + "', -1, '', -9999, '' ) }, 250)");
        } catch (Exception e) {
            log.error("Config manager React bootstrap failed: {}", e.getMessage());
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

    public boolean isEnabledLicence() {
        return isEnabledLicence;
    }
}
