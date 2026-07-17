package com.allinweb.ch.component.pane;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.botjob.BotJobDetailsPresentationGateway;
import com.allinweb.ch.facade.botjob.BotJobDetailsReactSessionContext;
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
import com.allinweb.ch.facade.UiThreadDispatcher;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.socket.ReactReplyChannel;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class MainDashboardPresentationAdapter
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

    protected static volatile MainDashboardPresentationAdapter instance;

    private final UiThreadDispatcher uiThreadDispatcher = UiThreadDispatcher.getInstance();
    private List<WebDriver> webDriverList;
    private boolean isEnabledLicence;
    private String initialSessionId = SESSION_ID;

    private MainDashboardPresentationAdapter() {
        botJobDetailsHost.setPresentationPort(this);
        ConfigPresentationRegistry.getInstance().install(this);
        MainDashboardPresentationRegistry.getInstance().install(this);
        NewBotJobPresentationRegistry.getInstance().install(this);
    }

    public static MainDashboardPresentationAdapter getInstance() {
        if (instance == null) {
            synchronized (MainDashboardPresentationAdapter.class) {
                if (instance == null) {
                    instance = new MainDashboardPresentationAdapter();
                }
            }
        }
        return instance;
    }

    public void initialize(List<WebDriver> webDriverList, boolean isEnabledLicence) {
        initialize(webDriverList, isEnabledLicence, SESSION_ID);
    }

    public void initialize(
            List<WebDriver> webDriverList, boolean isEnabledLicence, String initialSessionId) {
        this.webDriverList = webDriverList;
        this.isEnabledLicence = isEnabledLicence;
        this.initialSessionId = initialSessionId == null ? SESSION_ID : initialSessionId;
        arWebDriver.initialize(webDriverList);
        dispatchReactSession(this.initialSessionId);
    }

    @Override
    public void openOrganizations() {
        uiThreadDispatcher.execute(() -> {
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
        uiThreadDispatcher.execute(() -> {
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
        uiThreadDispatcher.execute(() -> dispatchReactSession("cloneJobManager", botJob.getId()));
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
        uiThreadDispatcher.execute(() -> dispatchReactSession(SESSION_ID));
    }

    @Override
    public void openBotJobAndClose(BotJobLoadDTO botJob) {
        openBotJob(botJob);
    }

    @Override
    public void closeModal() {
        uiThreadDispatcher.execute(() -> dispatchReactSession(SESSION_ID));
    }

    @Override
    public void openCloneOrganizations() {
        uiThreadDispatcher.execute(() -> dispatchReactSession("organizationManager"));
    }

    public void openBotJob(BotJobLoadDTO botJob) {
        uiThreadDispatcher.execute(() -> {
            // Open Job opens a real new browser tab (see MainDashboard's Open Job handler); the
            // dashboard's own session/tab is not switched anymore. Just do the backend-side
            // preparation -- the new tab's own "botJobTasks" socket connects independently and
            // requests its data via botJobDetails.bootstrap.
            reloadBlocks(botJob);
            botJobDetailsHost.initialize(botJob, isEnabledLicence);
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
        uiThreadDispatcher.execute(operation);
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
        log.info("Bot Job Details title update requested: homeBankingId={} botJobId={}", homeBankingId, botJobId);
    }

    public void showMainDashboard() {
        dispatchReactSession(SESSION_ID);
    }

    public void openConfig() {
        uiThreadDispatcher.execute(() -> {
            dispatchReactSession("configManager");
            performDataBase.loadQuickBotJobs();
            pushReactDashboardList();
        });
    }

    public void openInfo() {
        uiThreadDispatcher.execute(() -> dispatchReactSession("aboutPanel"));
    }

    public void openLicense() {
        uiThreadDispatcher.execute(() -> dispatchReactSession("licenseManager"));
    }

    public void launchBotJob(BotJobLoadDTO botJob) {
        uiThreadDispatcher.execute(() -> performMessage.showCustomModalDialogDragWin11(
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
        uiThreadDispatcher.execute(() -> {
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
        // Deliver on whichever session actually triggered this navigation (it's the only one
        // guaranteed to be open right now) rather than the target session, which the frontend
        // hasn't connected yet -- it only connects once it learns to switch there from this reply.
        String replyTo = ReactReplyChannel.getOrDefault(SESSION_ID);
        webSocketSessionManager.sendMessageJson(-1, replyTo, gson.toJson(payload), "react.session.open");
        if (!WebSocketSessionManager.isSessionOpen(replyTo)) {
            log.info(
                    "React shell session {} is not connected; navigation to {} will be requested by React when available",
                    replyTo,
                    targetSession);
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
