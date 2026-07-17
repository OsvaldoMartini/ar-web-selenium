package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.ConfigPresentation;
import com.allinweb.ch.facade.ConfigPresentationRegistry;
import com.allinweb.ch.facade.MainDashboardService;
import com.allinweb.ch.facade.MainDashboardPresentation;
import com.allinweb.ch.facade.MainDashboardPresentationRegistry;
import com.allinweb.ch.facade.NewBotJobPresentation;
import com.allinweb.ch.facade.NewBotJobPresentationRegistry;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.ScannerShellLifecycle;
import com.allinweb.ch.facade.ScannerTestRunHandlers;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARMainDashboardPane extends ARPane
        implements BotJobDetailsPresentationGateway, MainDashboardPresentation, NewBotJobPresentation, ConfigPresentation {

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
    private static final BotJobDetailsWorkspaceHost botJobDetailsHost = BotJobDetailsWorkspaceHost.getInstance();
    private static final ARWebDriver arWebDriver = ARWebDriver.getInstance();

    protected static volatile ARMainDashboardPane instance;

    private AnchorPane mainPane;
    private ObservableList<WebDriver> webDriverList;
    private boolean isEnabledLicence;
    private String initialSessionId = SESSION_ID;

    private ARMainDashboardPane() {
        super();
        botJobDetailsHost.setPresentationPort(this);
        ConfigPresentationRegistry.getInstance().install(this);
        MainDashboardPresentationRegistry.getInstance().install(this);
        NewBotJobPresentationRegistry.getInstance().install(this);
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
        arWebDriver.initialize(webDriverList);
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        mainPane = new AnchorPane();
        dispatchReactSession(initialSessionId);
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
            dispatchReactSession("organizationManager");
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
            dispatchReactSession("newBotJobManager");
            performDataBase.loadQuickBotJobs();
            pushReactDashboardList();
        });
    }

    public void openCloneBotJob(BotJobLoadDTO botJob) {
        Platform.runLater(() -> dispatchReactSession("cloneJobManager", botJob.getId()));
    }

    @Override
    public void openScanner(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block) {
        ScannerShellLifecycle.getInstance().openShell(homeBanking, botJob, block);
    }

    @Override
    public void closeScannerWebDrivers() {
        ScannerShellLifecycle.getInstance().closeWebDrivers();
    }

    @Override
    public void closeScanner() {
        ScannerShellLifecycle.getInstance().closeModal();
    }

    @Override
    public Integer currentScannerBotJobId() {
        return ScannerShellLifecycle.getInstance().currentBotJobId();
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
    public void openBotJobAndClose(BotJobLoadDTO botJob) {
        openBotJob(botJob);
    }

    @Override
    public void closeModal() {
        Platform.runLater(() -> dispatchReactSession(SESSION_ID));
    }

    @Override
    public void openCloneOrganizations() {
        Platform.runLater(() -> dispatchReactSession("organizationManager"));
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

    public void showSurface(String targetSession, BotJobDetailsReactSessionContext.Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Bot Job React session context is required");
        }
        webSocketSessionManager.sendMessageJson(
                context.homeBankingId(),
                targetSession,
                context.jsonData(),
                "botJobDetails.bootstrapResponse");
        if (!WebSocketSessionManager.isSessionOpen(targetSession)) {
            log.info(
                    "Bot Job React session {} is not connected; bootstrap will be requested by React when available",
                    targetSession);
        }
    }

    @Override
    public void execute(Runnable operation) {
        if (Platform.isFxApplicationThread()) operation.run();
        else Platform.runLater(operation);
    }

    @Override
    public File chooseTransferFolder(String configuredPath) {
        log.info("Bot Job transfer folder chooser ignored; React must provide the transfer path directly");
        return null;
    }

    @Override
    public File chooseReport(File reportFolder) {
        log.info("Bot Job report chooser ignored; React must provide or open the report path directly");
        return null;
    }

    @Override
    public String choosePath(String mode) {
        log.info("Config path chooser request ignored; React must provide the {} path directly", mode);
        return null;
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
            dispatchReactSession("configManager");
            performDataBase.loadQuickBotJobs();
            pushReactDashboardList();
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

    private void dispatchReactSession(String targetSession) {
        dispatchReactSession(targetSession, -9999);
    }

    private void dispatchReactSession(String targetSession, int botJobId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetSession", targetSession);
        payload.put("port", resolveSocketPort());
        payload.put("botJobId", botJobId);
        payload.put("source", SESSION_ID);
        webSocketSessionManager.sendMessageJson(-1, targetSession, gson.toJson(payload), "react.session.open");
        if (!WebSocketSessionManager.isSessionOpen(targetSession)) {
            log.info("React session {} is not connected; external React container should open or bootstrap it", targetSession);
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

    Stage ownerStage() {
        return mainPane != null && mainPane.getScene() != null && mainPane.getScene().getWindow() instanceof Stage
                ? (Stage) mainPane.getScene().getWindow()
                : null;
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
