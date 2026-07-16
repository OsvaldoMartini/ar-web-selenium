package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.MainDashboardService;
import com.allinweb.ch.facade.MainDashboardPresentation;
import com.allinweb.ch.facade.MainDashboardPresentationRegistry;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.ScannerTestRunHandlers;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.WebBuildExtractor;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.File;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARMainDashboardPane extends ARPane implements BotJobDetailsPresentationPort, MainDashboardPresentation {

    private static final String SESSION_ID = "mainDashboard";
    private static final int DEFAULT_PORT = 54525;
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final MainDashboardService mainDashboardService = MainDashboardService.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final Gson gson = new Gson();
    private static final ARConfigurationScene arConfigurationScene = ARConfigurationScene.getInstance();
    private static final ARConfigManagerScene arConfigManagerScene = ARConfigManagerScene.getInstance();
    private static final BotJobDetailsWorkspaceHost botJobDetailsHost = BotJobDetailsWorkspaceHost.getInstance();
    private static final ARNewBotJobManagerScene arNewBotJobManagerScene = ARNewBotJobManagerScene.getInstance();
    private static final AROrganizationManagerScene arOrganizationManagerScene = AROrganizationManagerScene.getInstance();
    private static final ARWebDriver arWebDriver = ARWebDriver.getInstance();

    protected static volatile ARMainDashboardPane instance;

    private final WebView webView = new WebView();
    private final ListView<BotJobLoadDTO> legacyBotJobListView = new ListView<>();
    private AnchorPane mainPane;
    private ObservableList<WebDriver> webDriverList;
    private boolean isEnabledLicence;
    private String initialSessionId = SESSION_ID;

    private ARMainDashboardPane() {
        super();
        botJobDetailsHost.setPresentationPort(this);
        MainDashboardPresentationRegistry.getInstance().install(this);
    }

    public static ARMainDashboardPane getInstance() {
        if (instance == null) {
            synchronized (ARMainDashboardPane.class) {
                if (instance == null) {
                    instance = new ARMainDashboardPane();
                }
            }
        }
        return instance;
    }

    public void initialize(ObservableList<WebDriver> webDriverList, boolean isEnabledLicence) {
        initialize(webDriverList, isEnabledLicence, SESSION_ID);
    }

    public void initialize(
            ObservableList<WebDriver> webDriverList, boolean isEnabledLicence, String initialSessionId) {
        this.webDriverList = webDriverList;
        this.isEnabledLicence = isEnabledLicence;
        this.initialSessionId = initialSessionId == null ? SESSION_ID : initialSessionId;
        if (SESSION_ID.equals(this.initialSessionId)) {
            refreshLegacyListView();
            arConfigurationScene.initialize(legacyBotJobListView, isEnabledLicence);
        }
        arWebDriver.initialize(webDriverList);
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

    @Override
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

    public void openNewBotJob() {
        Platform.runLater(() -> {
            if (performLists.getListHomeUrl().isEmpty()) {
                performDBEngine.loadHomeUrls(null);
            }
            if (performLists.getListHomeUrl().isEmpty()) {
                performMessage.errorMessage(
                        "Environments Are Empty",
                        "Please add at least one Organization Environment.",
                        "Select an Organization and add an Environment.",
                        null,
                        null,
                        0);
                return;
            }
            arNewBotJobManagerScene.initialize(isEnabledLicence);
            arNewBotJobManagerScene.showModal(currentStage());
            performDataBase.loadQuickBotJobs();
            refreshLegacyListView();
        });
    }

    public void openCloneBotJob(BotJobLoadDTO botJob) {
        Platform.runLater(() -> dispatchReactSession("cloneJobManager", botJob.getId()));
    }

    @Override
    public void openScanner(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block) {
        ARScannedElementScene scanner = ARScannedElementScene.getInstance();
        scanner.initialize(homeBanking, botJob, block);
        scanner.showModal();
    }

    @Override
    public void closeScannerWebDrivers() {
        ARScannedElementScene.getInstance().closeWebDrivers();
    }

    @Override
    public void closeScanner() {
        ARScannedElementScene.getInstance().closeModal();
    }

    @Override
    public Integer currentScannerBotJobId() {
        BotJobLoadDTO current = ARScannedElementScene.getInstance().getCurrentBotJob();
        return current == null ? null : current.getId();
    }

    @Override
    public long startTestRun(
            BotJobLoadDTO botJob,
            int blockOrderNumber,
            String endpointUrl,
            boolean runSingleBlock,
        BooleanSupplier cancellationRequested) {
        return ScannerTestRunHandlers.getInstance()
                .startTestRun(botJob, blockOrderNumber, endpointUrl, runSingleBlock, cancellationRequested);
    }

    @Override
    public void cancelTestRunStartup() {
        ScannerTestRunHandlers.getInstance().cancelTestRunStartup();
    }

    @Override
    public boolean stopTestRun(long executionId) {
        return ScannerTestRunHandlers.getInstance().stopTestRun(executionId);
    }

    @Override
    public boolean isTestRunComplete(long executionId) {
        return ScannerTestRunHandlers.getInstance().isTestRunComplete(executionId);
    }

    @Override
    public String testRunTerminalOutcome(long executionId) {
        return ScannerTestRunHandlers.getInstance().testRunTerminalOutcome(executionId);
    }

    public void closeCloneJob() {
        Platform.runLater(() -> dispatchReactSession(SESSION_ID));
    }

    @Override
    public void openCloneOrganizations() {
        Platform.runLater(() -> arOrganizationManagerScene.showModal(currentStage()));
    }

    public void openBotJob(BotJobLoadDTO botJob) {
        Platform.runLater(() -> {
            reloadBlocks(botJob);
            botJobDetailsHost.initialize(botJob, isEnabledLicence);
            showSurface(
                    ScannerWorkspaceSessions.BOT_JOB_TASKS,
                    botJobDetailsHost.reactContext(ScannerWorkspaceSessions.BOT_JOB_TASKS));
        });
    }

    public void showSurface(String targetSession, BotJobDetailsWebViewBootstrap.Context context) {
        int port = resolveSocketPort();
        try {
            webView.getEngine().executeScript(BotJobDetailsWebViewBootstrap.initializationScript(context, port, gson));
        } catch (RuntimeException error) {
            log.error("Bot Job React session dispatch failed for {}: {}", targetSession, error.getMessage());
            throw error;
        }
    }

    @Override
    public void execute(Runnable operation) {
        if (Platform.isFxApplicationThread()) operation.run();
        else Platform.runLater(operation);
    }

    @Override
    public File chooseTransferFolder(String configuredPath) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Bot Job transfer folder");
        if (!Strings.isNullOrEmpty(configuredPath)) {
            File initial = new File(configuredPath);
            if (initial.isDirectory()) chooser.setInitialDirectory(initial);
        }
        return chooser.showDialog(currentStage());
    }

    @Override
    public File chooseReport(File reportFolder) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Bot Job report");
        if (reportFolder != null && reportFolder.isDirectory()) chooser.setInitialDirectory(reportFolder);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        return chooser.showOpenDialog(currentStage());
    }

    @Override
    public void updateTitle(int homeBankingId, int botJobId) {
        Stage owner = ownerStage();
        if (owner != null) owner.setTitle("Bot Job Details WebSite Id: " + homeBankingId + " Id: " + botJobId);
    }

    public void showMainDashboard() {
        dispatchReactSession(SESSION_ID);
    }

    public void openConfig() {
        Platform.runLater(() -> {
            refreshLegacyListView();
            arConfigManagerScene.initialize(isEnabledLicence);
            arConfigManagerScene.showModal(currentStage());
            performDataBase.loadQuickBotJobs();
            refreshLegacyListView();
        });
    }

    public void openInfo() {
        Platform.runLater(() -> dispatchReactSession("aboutPanel"));
    }

    public void openLicense() {
        Platform.runLater(() -> dispatchReactSession("licenseManager"));
    }

    public void launchBotJob(BotJobLoadDTO botJob) {
        Platform.runLater(() -> performMessage.showCustomModalDialogDragWin11(
                "Launch Requested",
                "<span style='color: #2E7D32; font-weight: bold;'>Launch requested from React dashboard.</span>",
                "<span style='font-weight: bold;'>Bot Job: (" + botJob.getId() + ") " + botJob.getName() + "</span>",
                "<span style='font-style: italic;'>The Java engine launch command remains unchanged and should be wired after Linux path validation.</span>",
                null,
                false,
                "OK",
                null,
                0));
    }

    public void exitApplication() {
        Platform.runLater(() -> {
            for (WebDriver driver : arWebDriver.getWebDriverList()) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Error closing WebDriver: {}", e.getMessage());
                }
            }
            arWebDriver.getWebDriverList().clear();
            System.exit(0);
        });
    }

    private void dispatchReactBootstrap(WebEngine webEngine) {
        dispatchReactSession(initialSessionId);
    }

    private void dispatchReactSession(String targetSession) {
        dispatchReactSession(targetSession, -9999);
    }

    private void dispatchReactSession(String targetSession, int botJobId) {
        int port = resolveSocketPort();
        try {
            webView.getEngine().executeScript("setTimeout(function() { window.receiveDataFromJava(JSON.stringify([]), "
                    + port + ", '" + targetSession + "', -1, '', " + botJobId + ", '' ) }, 250)");
        } catch (Exception e) {
            log.error("React session dispatch failed for {}: {}", targetSession, e.getMessage());
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

    private Stage currentStage() {
        if (webView.getScene() != null && webView.getScene().getWindow() instanceof Stage) {
            return (Stage) webView.getScene().getWindow();
        }
        return new Stage();
    }

    Stage ownerStage() {
        return webView.getScene() != null && webView.getScene().getWindow() instanceof Stage
                ? (Stage) webView.getScene().getWindow()
                : null;
    }

    private void refreshLegacyListView() {
        performDataBase.loadQuickBotJobs();
        legacyBotJobListView.setItems(FXCollections.observableArrayList(performLists.getQuickBotJobs()));
    }

    private void pushReactDashboardList() {
        try {
            webSocketSessionManager.sendMessageJson(
                    -1,
                    SESSION_ID,
                    gson.toJson(mainDashboardService.list()),
                    "mainDashboard.listResponse");
        } catch (Exception e) {
            log.warn("Main dashboard list push after clone failed: {}", e.getMessage());
        }
    }

    private void reloadBlocks(BotJobLoadDTO botJob) {
        if (performLists.getListHomeUrl().isEmpty()) {
            performDBEngine.loadHomeUrls(null);
        }
        if (performLists.getQuickBotJobs().isEmpty()) {
            performDataBase.loadQuickBotJobs();
        }
        ErrorMessage errorMessage = performDataBase.loadBlocks(botJob.getId(), botJob.getName(), "block");
        if (errorMessage == null) {
            performDataBase.loadBlocks(botJob.getHomeBankingId(), botJob.getName(), "component_block");
        }
        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
        if (performLists.getListBlock().isEmpty()) {
            errorMessage = performDataBase.initiateNewBlock(
                    "block", botJob.getId(), "Default Block", "Default Block", 1, false);
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }
    }
}
