package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.AROrganizationManagerScene;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.WebBuildExtractor;
import com.google.common.base.Strings;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARConfigManagerPane extends ARPane {

    private static final String SESSION_ID = "configManager";
    private static final int DEFAULT_PORT = 54525;
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final AROrganizationManagerScene arOrganizationManagerScene = AROrganizationManagerScene.getInstance();

    protected static volatile ARConfigManagerPane instance;

    private final WebView webView = new WebView();
    private AnchorPane mainPane;
    private Stage modalStage;
    private boolean isEnabledLicence;

    private ARConfigManagerPane() {
        super();
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
        AtomicReference<String> selectedPath = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                File startingPoint = startingPoint();
                if ("file".equalsIgnoreCase(mode)) {
                    FileChooser chooser = new FileChooser();
                    if (startingPoint != null && startingPoint.exists()) {
                        chooser.setInitialDirectory(startingPoint.isDirectory() ? startingPoint : startingPoint.getParentFile());
                    }
                    File chosen = chooser.showOpenDialog(currentStage());
                    selectedPath.set(chosen != null ? chosen.getAbsolutePath() : null);
                } else {
                    DirectoryChooser chooser = new DirectoryChooser();
                    if (startingPoint != null && startingPoint.exists()) {
                        chooser.setInitialDirectory(startingPoint.isDirectory() ? startingPoint : startingPoint.getParentFile());
                    }
                    File chosen = chooser.showDialog(currentStage());
                    selectedPath.set(chosen != null ? chosen.getAbsolutePath() : null);
                }
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return selectedPath.get();
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
            arOrganizationManagerScene.showModal(currentStage());
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

    private File startingPoint() {
        String folderBase = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        if (Strings.isNullOrEmpty(folderBase)) {
            folderBase = System.getProperty("user.dir");
        }
        return new File(folderBase);
    }

    private Stage currentStage() {
        if (webView.getScene() != null && webView.getScene().getWindow() instanceof Stage) {
            return (Stage) webView.getScene().getWindow();
        }
        return modalStage != null ? modalStage : new Stage();
    }

    public boolean isEnabledLicence() {
        return isEnabledLicence;
    }
}
