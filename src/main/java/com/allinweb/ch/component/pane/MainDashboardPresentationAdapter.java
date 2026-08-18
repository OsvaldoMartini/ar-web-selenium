package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ApplicationShutdownCoordinator;
import com.allinweb.ch.facade.botjob.BotJobDetailsPresentationGateway;
import com.allinweb.ch.facade.botjob.BotJobDetailsReactSessionContext;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.BotJobTransferFolderResolver;
import com.allinweb.ch.facade.ConfigPresentation;
import com.allinweb.ch.facade.ConfigPresentationRegistry;
import com.allinweb.ch.facade.MainDashboardService;
import com.allinweb.ch.facade.MainDashboardPresentation;
import com.allinweb.ch.facade.MainDashboardPresentationRegistry;
import com.allinweb.ch.facade.NativePathChooser;
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
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.socket.ARWebSocketServer;
import com.allinweb.ch.socket.InstructionRealtimePublisher;
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

@Slf4j
public class MainDashboardPresentationAdapter
        implements BotJobDetailsPresentationGateway, MainDashboardPresentation, NewBotJobPresentation, ConfigPresentation {

    private static final String SESSION_ID = "mainDashboard";
    private static final String ORGANIZATION_MANAGER_SESSION_ID =
            DetachedWorkspaceSessions.ORGANIZATION_MANAGER;
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

    protected static volatile MainDashboardPresentationAdapter instance;

    private final UiThreadDispatcher uiThreadDispatcher = UiThreadDispatcher.getInstance();
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

    public void initialize(boolean isEnabledLicence) {
        initialize(isEnabledLicence, SESSION_ID);
    }

    public void initialize(boolean isEnabledLicence, String initialSessionId) {
        this.isEnabledLicence = isEnabledLicence;
        this.initialSessionId = initialSessionId == null ? SESSION_ID : initialSessionId;
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
            if (WebSocketSessionManager.isSessionOpen(ORGANIZATION_MANAGER_SESSION_ID)) {
                webSocketSessionManager.sendMessageJson(
                        -1,
                        ORGANIZATION_MANAGER_SESSION_ID,
                        "{}",
                        "application.workspaceFocus");
                return;
            }
            if (!ARWebSocketServer.getInstance()
                    .openDetachedWorkspaceDesktopShell(ORGANIZATION_MANAGER_SESSION_ID)) {
                log.error(
                        "Organizations detached workspace could not be opened; "
                                + "the Main Dashboard will remain unchanged");
            }
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
            if (!ARWebSocketServer.getInstance().openDetachedWorkspaceDesktopShell("newBotJobManager")) {
                dispatchReactSession("newBotJobManager");
            }
            performDataBase.loadQuickBotJobs();
            pushReactDashboardList();
        });
    }

    public void openCloneBotJob(BotJobLoadDTO botJob) {
        uiThreadDispatcher.execute(() -> {
            String sessionId = DetachedWorkspaceSessions.CLONE_JOB_MANAGER;
            if (WebSocketSessionManager.isSessionOpen(sessionId)) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sourceBotJobId", botJob.getId());
                webSocketSessionManager.sendMessageJson(
                        -1,
                        sessionId,
                        gson.toJson(payload),
                        "cloneJob.retarget");
                return;
            }
            if (!ARWebSocketServer.getInstance()
                    .openDetachedWorkspaceDesktopShell(sessionId, botJob.getId())) {
                log.error(
                        "Clone Job detached workspace could not be opened; "
                                + "the Main Dashboard will remain unchanged");
            }
        });
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
        openOrganizations();
    }

    public void openBotJob(BotJobLoadDTO botJob) {
        uiThreadDispatcher.execute(() -> {
            BotJobDetailsWorkspaceRegistry.Snapshot workspace;
            try {
                workspace = BotJobDetailsWorkspaceRegistry.getInstance().require(botJob.getId());
            } catch (IllegalArgumentException inactiveOrDifferentBotJob) {
                reloadBlocks(botJob);
                botJobDetailsHost.initialize(botJob, isEnabledLicence);
                workspace = BotJobDetailsWorkspaceRegistry.getInstance().require(botJob.getId());
            }

            // Keep Main Dashboard alive and reuse the application's one Bot Job Details native
            // window. A persistent control session retargets the existing physical panel when it
            // is connected; Chromium is launched only when that panel does not exist.
            ARWebSocketServer.getInstance().openBotJobDesktopShell(
                    workspace.homeBankingId(), workspace.botJobId(), workspace.workspaceEpoch());
        });
    }

    public void showSurface(String targetSession, BotJobDetailsReactSessionContext.Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Bot Job React session context is required");
        }
        InstructionRealtimePublisher.getInstance()
                .publishSerializedSnapshot(context.homeBankingId(), targetSession, context.jsonData());
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
        File selected = BotJobTransferFolderResolver.resolve(configuredPath);
        log.info("Using configured Bot Job transfer folder: {}", selected.getAbsolutePath());
        return selected;
    }

    @Override
    public File chooseReport(File reportFolder) {
        File selected = NativePathChooser.chooseReport(reportFolder);
        if (selected != null) {
            log.info("Selected Bot Job report file: {}", selected.getAbsolutePath());
        }
        return selected;
    }

    @Override
    public String choosePath(String mode) {
        return choosePath(mode, "");
    }

    @Override
    public String choosePath(String mode, String currentPath) {
        File initialPath = Strings.isNullOrEmpty(currentPath) ? null : new File(currentPath.trim());
        File selected = "file".equalsIgnoreCase(mode)
                ? NativePathChooser.chooseFile(initialPath)
                : NativePathChooser.chooseDirectory(initialPath);
        if (selected != null) {
            log.info("Selected Config {} path: {}", mode, selected.getAbsolutePath());
            return selected.getAbsolutePath();
        }
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
            if (!ARWebSocketServer.getInstance().openDetachedWorkspaceDesktopShell("configManager")) {
                dispatchReactSession("configManager");
            }
            performDataBase.loadQuickBotJobs();
            pushReactDashboardList();
        });
    }

    @Override
    public void openTemplate() {
        uiThreadDispatcher.execute(() -> {
            if (!ARWebSocketServer.getInstance().openDetachedWorkspaceDesktopShell("aTemplateManager")) {
                dispatchReactSession("aTemplateManager");
            }
            performDataBase.loadQuickBotJobs();
            pushReactDashboardList();
        });
    }

    @Override
    public void openInfo() {
        uiThreadDispatcher.execute(() ->
                openOrFocusDetachedWorkspace(DetachedWorkspaceSessions.ABOUT_PANEL, "Info"));
    }

    @Override
    public void openLicense() {
        uiThreadDispatcher.execute(() ->
                openOrFocusDetachedWorkspace(DetachedWorkspaceSessions.LICENSE_MANAGER, "License Manager"));
    }

    private void openOrFocusDetachedWorkspace(String sessionId, String workspaceName) {
        if (WebSocketSessionManager.isSessionOpen(sessionId)) {
            webSocketSessionManager.sendMessageJson(
                    -1,
                    sessionId,
                    "{}",
                    "application.workspaceFocus");
            return;
        }
        if (!ARWebSocketServer.getInstance().openDetachedWorkspaceDesktopShell(sessionId)) {
            log.error("Could not open the detached {} workspace ({})", workspaceName, sessionId);
        }
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
        ApplicationShutdownCoordinator.getInstance().requestShutdown();
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
            String port = System.getProperty("ARWebChosenPort");
            if (Strings.isNullOrEmpty(port)) {
                port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
            }
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
