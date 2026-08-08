package com.allinweb.ch.socket;

import com.allinweb.ch.component.pane.BotJobDetailsWorkspaceHost;
import com.allinweb.ch.facade.ApplicationShutdownCoordinator;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/**
 * Authoritative inventory and control surface for AR Web application windows.
 *
 * <p>A page ID is bound to the exact currently registered WebSocket transport. Reusing a logical
 * session after a refresh therefore creates a different page ID, and a stale Pages Open row can
 * never close the replacement window.
 */
@Slf4j
public final class PagesOpenWorkspaceService {

    public static final String WORKSPACE_SESSION_ID = DetachedWorkspaceSessions.PAGES_OPEN_MANAGER;
    public static final String SNAPSHOT_OPERATION = "pagesOpen.snapshot";
    public static final String INLINE_CLOSE_OPERATION = "pagesOpen.inlineClose";
    public static final String WORKSPACE_CLOSE_OPERATION = "application.workspaceClose";
    public static final String WORKSPACE_FOCUS_OPERATION = "application.workspaceFocus";

    private static final String MAIN_DASHBOARD_SESSION = "mainDashboard";
    private static final String BOT_JOB_DETAILS_DATA_SESSION = "botJobTasks";
    private static final String AUTO_TEST_KEY = "inline:auto-test";
    private static final String AUTO_TEST_KIND = "AUTO_TEST";
    private static final long LAUNCH_PENDING_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    private static final long FIXED_WORKSPACE_LAUNCH_PENDING_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    private static final int MAX_REQUEST_ID_CHARACTERS = 160;

    private static final Set<String> STANDALONE_VISIBLE_SESSIONS = Set.of(
            "activationRequired",
            ScannerWorkspaceSessions.SCANNER_GRID,
            ScannerWorkspaceSessions.PRE_SCANNER_GRID,
            ScannerWorkspaceSessions.MOBILE_SCANNER_GRID,
            "apiTestToolAI",
            "capiApiTestToolAI");
    private static final List<String> LOGICAL_MAIN_SESSIONS = List.of(
            MAIN_DASHBOARD_SESSION,
            "activationRequired",
            ScannerWorkspaceSessions.SCANNER_GRID,
            ScannerWorkspaceSessions.PRE_SCANNER_GRID,
            ScannerWorkspaceSessions.MOBILE_SCANNER_GRID,
            "apiTestToolAI",
            "capiApiTestToolAI");

    private static final Map<String, PagePresentation> FIXED_PRESENTATIONS = Map.ofEntries(
            Map.entry(
                    DetachedWorkspaceSessions.ORGANIZATION_MANAGER,
                    new PagePresentation(
                            "Organizations",
                            "ORGANIZATIONS",
                            "Detached workspace",
                            false,
                            true)),
            Map.entry(
                    DetachedWorkspaceSessions.NEW_BOT_JOB_MANAGER,
                    new PagePresentation("New Bot Job", "NEW_BOT_JOB", "Detached workspace", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.CLONE_JOB_MANAGER,
                    new PagePresentation("Clone Job", "CLONE_JOB", "Detached workspace", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.CONFIG_MANAGER,
                    new PagePresentation("Configuration", "CONFIG", "Detached workspace", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.A_TEMPLATE_MANAGER,
                    new PagePresentation("TEMP", "TEMPLATE", "Detached workspace", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.COMPONENTS_MANAGER,
                    new PagePresentation("Components", "COMPONENTS", "Bot Job component library", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.COMMAND_EDITOR_MANAGER,
                    new PagePresentation(
                            "Command Editor",
                            "COMMAND_EDITOR",
                            "Detached instruction command workspace",
                            false,
                            true)),
            Map.entry(
                    DetachedWorkspaceSessions.VARIABLES_MANAGER,
                    new PagePresentation(
                            "Variables",
                            "VARIABLES",
                            "Bot Job variable relationships",
                            false,
                            true)),
            Map.entry(
                    DetachedWorkspaceSessions.EXCEL_DATA_MANAGER,
                    new PagePresentation("Excel Data", "EXCEL_DATA", "Retained Bot Job execution dataset", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                    new PagePresentation("Smoke Test", "SMOKE_TEST", "Bot Job Smoke Test template", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.RUNTIME_VARIABLES_MANAGER,
                    new PagePresentation(
                            "Runtime Variables",
                            "RUNTIME_VARIABLES",
                            "Live Bot Job runtime-variable memory",
                            false,
                            true)),
            Map.entry(
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    new PagePresentation(
                            "Page Mappings",
                            "PAGE_MAPPINGS",
                            "Owner-scoped Page Scanner history",
                            false,
                            true)),
            Map.entry(
                    DetachedWorkspaceSessions.MEMORY_LIST_MANAGER,
                    new PagePresentation("Memory List", "MEMORY_LIST", "Detached workspace", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.PAGES_OPEN_MANAGER,
                    new PagePresentation("Pages Open", "PAGES_OPEN", "Detached workspace", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.ABOUT_PANEL,
                    new PagePresentation("Info", "INFO", "Detached workspace", false, true)),
            Map.entry(
                    DetachedWorkspaceSessions.LICENSE_MANAGER,
                    new PagePresentation("License", "LICENSE", "Detached workspace", false, true)));

    private static final PagesOpenWorkspaceService INSTANCE = new PagesOpenWorkspaceService();

    private final Gson gson = new Gson();
    private final DesktopWindowFocusService desktopWindowFocusService =
            new DesktopWindowFocusService();
    private final Map<String, PageHandle> handlesByKey = new LinkedHashMap<>();
    private final Map<String, PageHandle> handlesById = new LinkedHashMap<>();
    private final Map<String, Long> fixedWorkspaceLaunchPendingSince = new LinkedHashMap<>();
    private boolean autoTestOpen;
    private boolean launchPending;
    private long launchPendingSince;

    private PagesOpenWorkspaceService() {}

    public static PagesOpenWorkspaceService getInstance() {
        return INSTANCE;
    }

    static boolean isFixedPresentationSession(String sessionId) {
        return FIXED_PRESENTATIONS.containsKey(sessionId);
    }

    synchronized boolean isDetachedWorkspaceLaunchPending(String sessionId) {
        Long pendingSince = fixedWorkspaceLaunchPendingSince.get(sessionId);
        if (pendingSince == null) return false;
        if (System.nanoTime() - pendingSince >= FIXED_WORKSPACE_LAUNCH_PENDING_NANOS) {
            fixedWorkspaceLaunchPendingSince.remove(sessionId);
            return false;
        }
        return !WebSocketSessionManager.isSessionOpen(sessionId);
    }

    /** Revokes a fixed workspace's pending-launch marker after an explicit invalidation. */
    synchronized void clearDetachedWorkspaceLaunchPending(String sessionId) {
        if (sessionId != null) {
            fixedWorkspaceLaunchPendingSince.remove(sessionId);
        }
    }

    /**
     * Opens one fixed detached workspace once, or raises its exact existing native window.
     *
     * <p>The short pending window prevents a rapid double click from launching two Chromium app
     * windows before the first window has connected its authoritative WebSocket.
     */
    public synchronized boolean openOrFocusDetachedWorkspace(
            String sessionId, int sourceBotJobId, String reason) {
        return openOrFocusDetachedWorkspace(sessionId, sourceBotJobId, reason, null);
    }

    /** Opens/focuses a fixed workspace with an optional server-issued window capability. */
    public synchronized boolean openOrFocusDetachedWorkspace(
            String sessionId,
            int sourceBotJobId,
            String reason,
            String windowCapability) {
        if (!DetachedWorkspaceSessions.isDetachedWorkspaceSession(sessionId)
                || !FIXED_PRESENTATIONS.containsKey(sessionId)) {
            throw new IllegalArgumentException("A fixed detached workspace session is required.");
        }

        if (WebSocketSessionManager.isSessionOpen(sessionId)) {
            fixedWorkspaceLaunchPendingSince.remove(sessionId);
            focusSession(sessionId, reason);
            return true;
        }

        long now = System.nanoTime();
        Long pendingSince = fixedWorkspaceLaunchPendingSince.get(sessionId);
        if (pendingSince != null
                && now - pendingSince < FIXED_WORKSPACE_LAUNCH_PENDING_NANOS) {
            return true;
        }

        fixedWorkspaceLaunchPendingSince.put(sessionId, now);
        boolean launched = ARWebSocketServer.getInstance()
                .openDetachedWorkspaceDesktopShell(
                        sessionId, sourceBotJobId, windowCapability);
        if (!launched) {
            fixedWorkspaceLaunchPendingSince.remove(sessionId);
        }
        return launched;
    }

    /** Raises one exact fixed workspace using the same native focus handshake as a row click. */
    public synchronized boolean focusSession(String sessionId, String reason) {
        reconcileHandles();
        PageHandle target = handlesByKey.get(sessionId);
        if (target == null || !isCurrentHandle(target)) return false;
        return focusHandle(target, reason).browserFocusRequested();
    }

    /** Requests that one detached window close itself without touching the main application. */
    public synchronized boolean closeDetachedWorkspaceSession(String sessionId, String reason) {
        reconcileHandles();
        PageHandle target = handlesByKey.get(sessionId);
        if (target == null || !isCurrentHandle(target) || target.presentation().main()) {
            return false;
        }
        return sendWindowOperation(target, WORKSPACE_CLOSE_OPERATION, reason);
    }

    /** Opens or reuses the one fixed Pages Open detached workspace. */
    public synchronized JsonObject open(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject validation = validateVisibleRequester(body, requesterSessionId, requesterTransport);
        if (validation != null) return validation;

        boolean alreadyOpen = WebSocketSessionManager.isSessionOpen(WORKSPACE_SESSION_ID);
        boolean launchRequired;
        long now = System.nanoTime();
        if (alreadyOpen) {
            launchPending = false;
            launchRequired = false;
        } else if (launchPending && now - launchPendingSince < LAUNCH_PENDING_NANOS) {
            launchRequired = false;
        } else {
            launchPending = true;
            launchPendingSince = now;
            launchRequired = true;
        }

        boolean launched = !launchRequired
                || ARWebSocketServer.getInstance().openDetachedWorkspaceDesktopShell(WORKSPACE_SESSION_ID);
        if (!launched) {
            launchPending = false;
            return failure(body, "Pages Open workspace could not be opened.");
        }
        FocusResult focusResult = alreadyOpen
                ? focusWorkspace()
                : FocusResult.notRequested();

        JsonObject response = snapshotResponse(
                body,
                alreadyOpen
                        ? focusResult.nativeFocused()
                                ? "Pages Open workspace brought to front."
                                : "Pages Open workspace already open; focus requested."
                        : launchRequired
                                ? "Pages Open workspace opened."
                                : "Pages Open workspace is opening.");
        response.addProperty("alreadyOpen", alreadyOpen);
        response.addProperty("browserFocusRequested", focusResult.browserFocusRequested());
        response.addProperty("nativeFocused", focusResult.nativeFocused());
        return response;
    }

    /** Returns the authoritative page inventory without opening the Pages Open workspace. */
    public synchronized JsonObject summary(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject validation = validateVisibleRequester(body, requesterSessionId, requesterTransport);
        if (validation != null) return validation;
        return snapshotResponse(body, "Open pages summary loaded.");
    }

    /** Bootstraps the fixed Pages Open page from its exact registered transport. */
    public synchronized JsonObject bootstrap(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject validation = validatePagesOpenRequester(body, requesterSessionId, requesterTransport);
        if (validation != null) return validation;
        launchPending = false;
        return snapshotResponse(body, "Open pages loaded.");
    }

    /** Tracks the Auto Test panel that lives inside the Main Dashboard browser window. */
    public synchronized JsonObject inlineState(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        if (!MAIN_DASHBOARD_SESSION.equals(requesterSessionId)
                || !isRegisteredTransport(requesterSessionId, requesterTransport)) {
            return failure(body, "Only the Main Dashboard can update inline page state.");
        }
        if (body == null || !body.has("open") || body.get("open").isJsonNull()) {
            return failure(body, "Inline page state requires an open flag.");
        }
        try {
            autoTestOpen = body.get("open").getAsBoolean();
        } catch (RuntimeException invalidOpenFlag) {
            return failure(body, "Inline page state requires a valid open flag.");
        }
        publishSnapshot();
        return snapshotResponse(
                body, autoTestOpen ? "Auto Test is open." : "Auto Test is closed.");
    }

    /** Closes one page only after validating its opaque ID against the exact live transport. */
    public synchronized JsonObject closePage(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject validation = validatePagesOpenRequester(body, requesterSessionId, requesterTransport);
        if (validation != null) return validation;
        String pageId = string(body, "pageId");
        if (pageId.isEmpty()) return failure(body, "A page ID is required.");

        reconcileHandles();
        PageHandle target = handlesById.get(pageId);
        if (target == null) return failure(body, "The selected page is no longer open.");
        String claimedSessionId = string(body, "sessionId");
        if (!claimedSessionId.isEmpty() && !claimedSessionId.equals(target.sessionId())) {
            return failure(body, "The selected page mapping is stale.");
        }
        if (!isCurrentHandle(target)) {
            reconcileHandles();
            return failure(body, "The selected page is no longer authoritative.");
        }

        if (target.presentation().main()) {
            ApplicationShutdownCoordinator.getInstance().requestShutdown();
            return success(body, "Application shutdown requested.");
        }
        if (AUTO_TEST_KEY.equals(target.key())) {
            boolean delivered = sendWindowOperation(
                    target, INLINE_CLOSE_OPERATION, "Pages Open requested Auto Test close.");
            if (delivered) autoTestOpen = false;
            publishSnapshot();
            return delivered
                    ? success(body, "Auto Test close requested.")
                    : failure(body, "Auto Test could not be closed.");
        }

        String cleanupFailure = cleanupOwnedWorkspace(target, false);
        if (cleanupFailure != null) return failure(body, cleanupFailure);
        boolean delivered = sendWindowOperation(
                target, WORKSPACE_CLOSE_OPERATION, "Pages Open requested workspace close.");
        return delivered
                ? success(body, target.presentation().title() + " close requested.")
                : failure(body, target.presentation().title() + " could not be closed.");
    }

    /** Focuses one page only after validating its opaque ID against the exact live transport. */
    public synchronized JsonObject focusPage(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject validation = validatePagesOpenRequester(body, requesterSessionId, requesterTransport);
        if (validation != null) return validation;
        String pageId = string(body, "pageId");
        if (pageId.isEmpty()) return failure(body, "A page ID is required.");

        reconcileHandles();
        PageHandle target = handlesById.get(pageId);
        if (target == null) return failure(body, "The selected page is no longer open.");
        String claimedSessionId = string(body, "sessionId");
        if (!claimedSessionId.isEmpty() && !claimedSessionId.equals(target.sessionId())) {
            return failure(body, "The selected page mapping is stale.");
        }
        String claimedKind = string(body, "kind");
        if (!claimedKind.isEmpty()
                && !claimedKind.equalsIgnoreCase(target.presentation().kind())) {
            return failure(body, "The selected page type is stale.");
        }
        if (!isCurrentHandle(target)) {
            reconcileHandles();
            return failure(body, "The selected page is no longer authoritative.");
        }

        FocusResult focusResult =
                focusHandle(target, "Pages Open requested workspace focus.");

        JsonObject response;
        if (focusResult.nativeFocused()) {
            response = success(body, target.presentation().title() + " brought to front.");
        } else if (focusResult.browserFocusRequested()) {
            response = success(
                    body,
                    target.presentation().title()
                            + " received the focus request, but Windows did not confirm foreground activation.");
        } else {
            response = failure(body, target.presentation().title() + " could not be focused.");
        }
        response.addProperty("nativeFocused", focusResult.nativeFocused());
        response.addProperty(
                "browserFocusRequested", focusResult.browserFocusRequested());
        return response;
    }

    /**
     * Closes every visible database-dependent page after a successful save/restore while preserving
     * the Main Dashboard and the exact Config/TEMP transport that performed the operation.
     */
    public synchronized int closeForDatabaseReload(String requesterSessionId) {
        if (!DetachedWorkspaceSessions.CONFIG_MANAGER.equals(requesterSessionId)
                && !DetachedWorkspaceSessions.A_TEMPLATE_MANAGER.equals(requesterSessionId)) {
            throw new IllegalArgumentException(
                    "Only Configuration or TEMP can own a database reload.");
        }
        Session requester = WebSocketSessionManager.getSession(requesterSessionId);
        if (requester == null || !requester.isOpen()) {
            throw new IllegalStateException("The database reload requester is no longer connected.");
        }

        reconcileHandles();
        int requested = 0;
        List<String> warnings = new ArrayList<>();
        if (autoTestOpen) {
            PageHandle autoTest = handlesByKey.get(AUTO_TEST_KEY);
            if (autoTest != null && isCurrentHandle(autoTest)) {
                if (sendWindowOperation(
                        autoTest, INLINE_CLOSE_OPERATION, "Database configuration reloaded.")) {
                    autoTestOpen = false;
                    requested++;
                } else {
                    warnings.add("Auto Test did not accept the close request.");
                }
            } else {
                warnings.add("Auto Test no longer has an authoritative Main Dashboard transport.");
            }
        }

        List<PageHandle> targets = new ArrayList<>(handlesById.values());
        for (PageHandle target : targets) {
            if (AUTO_TEST_KEY.equals(target.key())
                    || target.presentation().main()
                    || requesterSessionId.equals(target.sessionId())) {
                continue;
            }
            if (!isCurrentHandle(target)) continue;
            String cleanupFailure = cleanupOwnedWorkspace(target, true);
            if (cleanupFailure != null) {
                log.warn(
                        "Database reload workspace cleanup warning for {}: {}",
                        target.sessionId(),
                        cleanupFailure);
                warnings.add(target.presentation().title() + ": " + cleanupFailure);
                continue;
            }
            if (sendWindowOperation(
                    target, WORKSPACE_CLOSE_OPERATION, "Database configuration reloaded.")) {
                requested++;
            } else {
                warnings.add(
                        target.presentation().title()
                                + " did not accept the close request.");
            }
        }
        publishSnapshot();
        if (!warnings.isEmpty()) {
            throw new IllegalStateException(
                    "Some pages remain open: " + String.join(" ", warnings));
        }
        return requested;
    }

    /** Publishes a fresh snapshot after an authoritative WebSocket registration/removal. */
    public synchronized void sessionRegistryChanged() {
        if (!WebSocketSessionManager.isSessionOpen(MAIN_DASHBOARD_SESSION)) {
            autoTestOpen = false;
        }
        fixedWorkspaceLaunchPendingSince
                .keySet()
                .removeIf(WebSocketSessionManager::isSessionOpen);
        reconcileHandles();
        publishSnapshot();
    }

    private JsonObject validateVisibleRequester(
            JsonObject body, String sessionId, Session transport) {
        if (!isRegisteredTransport(sessionId, transport)) {
            return failure(body, "The Pages Open requester is not authoritative.");
        }
        if (presentation(sessionId) == null
                && !BOT_JOB_DETAILS_DATA_SESSION.equals(sessionId)
                && !BotJobDetailsWindowCoordinator.isControlSessionId(sessionId)
                && !ScannerWorkspaceSessions.isPageScannerSession(sessionId)
                && !OcrWorkspaceCoordinator.isWorkspaceSessionId(sessionId)) {
            return failure(body, "This session cannot open Pages Open.");
        }
        return null;
    }

    private JsonObject validatePagesOpenRequester(
            JsonObject body, String sessionId, Session transport) {
        if (!WORKSPACE_SESSION_ID.equals(sessionId)) {
            return failure(body, "Only the Pages Open workspace can use this operation.");
        }
        if (!isRegisteredTransport(sessionId, transport)) {
            return failure(body, "The Pages Open requester is not authoritative.");
        }
        return null;
    }

    private boolean isRegisteredTransport(String sessionId, Session transport) {
        return sessionId != null
                && transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession(sessionId) == transport;
    }

    private void reconcileHandles() {
        Map<String, Candidate> candidates = visibleCandidates();
        Map<String, PageHandle> nextByKey = new LinkedHashMap<>();
        Map<String, PageHandle> nextById = new LinkedHashMap<>();
        for (Candidate candidate : candidates.values()) {
            PageHandle previous = handlesByKey.get(candidate.key());
            PageHandle next = previous != null
                            && previous.transport() == candidate.transport()
                            && previous.sessionId().equals(candidate.sessionId())
                    ? previous.withPresentation(candidate.presentation())
                    : new PageHandle(
                            UUID.randomUUID().toString(),
                            candidate.key(),
                            candidate.sessionId(),
                            candidate.transport(),
                            candidate.presentation());
            nextByKey.put(next.key(), next);
            nextById.put(next.pageId(), next);
        }
        handlesByKey.clear();
        handlesByKey.putAll(nextByKey);
        handlesById.clear();
        handlesById.putAll(nextById);
    }

    private Map<String, Candidate> visibleCandidates() {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        boolean physicalMainOpen =
                WebSocketSessionManager.isSessionOpen(MainApplicationControlLifecycle.SESSION_ID);
        String fallbackLogicalMain = physicalMainOpen ? null : firstOpenLogicalMainSession();
        for (Map.Entry<String, Session> entry : WebSocketSessionManager.getAllSessions().entrySet()) {
            String sessionId = entry.getKey();
            Session transport = entry.getValue();
            if (transport == null || !transport.isOpen()) continue;
            PagePresentation presentation = presentation(sessionId);
            if (presentation == null) continue;
            if (presentation.main()) {
                if (physicalMainOpen
                        && !MainApplicationControlLifecycle.SESSION_ID.equals(sessionId)) {
                    continue;
                }
                if (!physicalMainOpen && !sessionId.equals(fallbackLogicalMain)) {
                    continue;
                }
            }
            if (ScannerWorkspaceSessions.isPageScannerSession(sessionId)
                    && !PageScannerWorkspaceCoordinator.getInstance().isActiveWorkspace(sessionId)) {
                continue;
            }
            if (OcrWorkspaceCoordinator.isWorkspaceSessionId(sessionId)
                    && !OcrWorkspaceCoordinator.getInstance().isActiveWorkspace(sessionId)) {
                continue;
            }
            if (BotJobDetailsWindowCoordinator.isControlSessionId(sessionId)
                    && !BotJobDetailsWindowCoordinator.getInstance().isActiveControlSession(sessionId)) {
                continue;
            }
            candidates.put(sessionId, new Candidate(sessionId, sessionId, transport, presentation));
        }

        Session mainTransport = WebSocketSessionManager.getSession(MAIN_DASHBOARD_SESSION);
        if (autoTestOpen && mainTransport != null && mainTransport.isOpen()) {
            candidates.put(
                    AUTO_TEST_KEY,
                    new Candidate(
                            AUTO_TEST_KEY,
                            MAIN_DASHBOARD_SESSION,
                            mainTransport,
                            new PagePresentation(
                                    "Auto Test",
                                    AUTO_TEST_KIND,
                                    "Main Dashboard inline workspace",
                                    false,
                                    true)));
        }
        return candidates;
    }

    private String firstOpenLogicalMainSession() {
        for (String sessionId : LOGICAL_MAIN_SESSIONS) {
            if (WebSocketSessionManager.isSessionOpen(sessionId)) return sessionId;
        }
        return null;
    }

    private PagePresentation presentation(String sessionId) {
        if (MainApplicationControlLifecycle.isControlSessionId(sessionId)) {
            return new PagePresentation(
                    "AR Web", "MAIN_APPLICATION", "Main application window", true, true);
        }
        if (MAIN_DASHBOARD_SESSION.equals(sessionId)) {
            return new PagePresentation(
                    "AR Web", "MAIN_DASHBOARD", "Main Dashboard", true, true);
        }
        PagePresentation fixed = FIXED_PRESENTATIONS.get(sessionId);
        if (fixed != null) return fixed;
        if (BotJobDetailsWindowCoordinator.isControlSessionId(sessionId)) {
            BotJobDetailsWindowCoordinator.Target target =
                    BotJobDetailsWindowCoordinator.getInstance().activeTarget();
            String detail = target == null
                    ? "Bot Job Details"
                    : "Bot Job " + target.botJobId();
            return new PagePresentation(
                    "Bot Job Details", "BOT_JOB_DETAILS", detail, false, true);
        }
        if (ScannerWorkspaceSessions.isPageScannerSession(sessionId)) {
            return new PagePresentation(
                    "Page Scanner", "PAGE_SCANNER", "Detached scanner workspace", false, true);
        }
        OcrWorkspaceCoordinator.Kind ocrKind =
                OcrWorkspaceCoordinator.Kind.fromSessionId(sessionId);
        if (ocrKind != null) {
            return new PagePresentation(
                    "OCR Config",
                    "OCR_CONFIG",
                    "Detached OCR workspace",
                    false,
                    true);
        }
        if (STANDALONE_VISIBLE_SESSIONS.contains(sessionId)) {
            return standalonePresentation(sessionId);
        }
        return null;
    }

    private PagePresentation standalonePresentation(String sessionId) {
        return switch (sessionId) {
            case "activationRequired" ->
                new PagePresentation("Activation Required", "ACTIVATION", "Main application view", true, true);
            case ScannerWorkspaceSessions.SCANNER_GRID ->
                new PagePresentation("Page Scanner Grid", "SCANNER_GRID", "Main application view", true, true);
            case ScannerWorkspaceSessions.PRE_SCANNER_GRID ->
                new PagePresentation("Pre Scan", "PRE_SCANNER", "Main application view", true, true);
            case ScannerWorkspaceSessions.MOBILE_SCANNER_GRID ->
                new PagePresentation("Mobile Scanner", "MOBILE_SCANNER", "Main application view", true, true);
            case "apiTestToolAI" ->
                new PagePresentation("API Test Tool", "API_TEST", "Main application view", true, true);
            case "capiApiTestToolAI" ->
                new PagePresentation("CAPI Test Tool", "CAPI_TEST", "Main application view", true, true);
            default ->
                new PagePresentation(sessionId, "WORKSPACE", "Main application view", true, true);
        };
    }

    private String cleanupOwnedWorkspace(PageHandle target, boolean databaseReload) {
        String sessionId = target.sessionId();
        try {
            if (BotJobDetailsWindowCoordinator.isControlSessionId(sessionId)) {
                boolean closed = BotJobDetailsWorkspaceHost.getInstance().closeWorkspaceIfIdle();
                if (!closed) {
                    return "Stop the active Bot Job operation before closing Bot Job Details.";
                }
                BotJobDetailsWindowCoordinator.getInstance().retire(sessionId);
            } else if (ScannerWorkspaceSessions.isPageScannerSession(sessionId)) {
                if (!PageScannerWorkspaceCoordinator.getInstance().close(sessionId)) {
                    return "The Page Scanner workspace is no longer authoritative.";
                }
            } else if (OcrWorkspaceCoordinator.isWorkspaceSessionId(sessionId)) {
                if (!OcrWorkspaceCoordinator.getInstance().close(sessionId)) {
                    return "The OCR workspace is no longer authoritative.";
                }
            }
            return null;
        } catch (IllegalArgumentException | IllegalStateException cleanupFailure) {
            if (databaseReload) return cleanupFailure.getMessage();
            return "The selected workspace could not be retired: " + cleanupFailure.getMessage();
        }
    }

    private boolean sendWindowOperation(PageHandle target, String operation, String reason) {
        return sendWindowOperation(target, operation, reason, "");
    }

    private boolean sendWindowOperation(
            PageHandle target, String operation, String reason, String nativeWindowTitleToken) {
        if (!isCurrentHandle(target)) return false;
        JsonObject payload = new JsonObject();
        payload.addProperty("pageId", target.pageId());
        payload.addProperty("sessionId", target.sessionId());
        payload.addProperty("targetSessionId", target.sessionId());
        payload.addProperty("reason", reason);
        if (nativeWindowTitleToken != null && !nativeWindowTitleToken.isBlank()) {
            payload.addProperty("nativeWindowTitleToken", nativeWindowTitleToken);
        }
        JsonObject envelope = new JsonObject();
        envelope.addProperty("homeBankingId", -1);
        envelope.addProperty("sessionId", target.sessionId());
        envelope.addProperty("body", gson.toJson(payload));
        envelope.addProperty("operationId", operation);
        try {
            WebSocketSessionManager.sendText(target.transport(), gson.toJson(envelope));
            return true;
        } catch (IOException deliveryFailure) {
            log.warn(
                    "Unable to send {} to {}: {}",
                    operation,
                    target.sessionId(),
                    deliveryFailure.getMessage());
            return false;
        }
    }

    private boolean isCurrentHandle(PageHandle handle) {
        return handle != null
                && handle.transport().isOpen()
                && WebSocketSessionManager.getSession(handle.sessionId()) == handle.transport()
                && handlesById.get(handle.pageId()) == handle;
    }

    private void publishSnapshot() {
        JsonObject snapshot = snapshotResponse(null, "Open pages updated.");
        Map<String, Session> recipients = new LinkedHashMap<>();
        for (PageHandle handle : handlesById.values()) {
            if (isCurrentHandle(handle)) {
                recipients.put(handle.sessionId(), handle.transport());
            }
        }
        addRegisteredRecipient(recipients, MAIN_DASHBOARD_SESSION);
        addRegisteredRecipient(recipients, BOT_JOB_DETAILS_DATA_SESSION);

        for (Map.Entry<String, Session> recipient : recipients.entrySet()) {
            WebSocketSessionManager.sendMessageJson(
                    -1,
                    recipient.getValue(),
                    recipient.getKey(),
                    gson.toJson(snapshot),
                    SNAPSHOT_OPERATION);
        }
    }

    private void addRegisteredRecipient(Map<String, Session> recipients, String sessionId) {
        Session transport = WebSocketSessionManager.getSession(sessionId);
        if (transport != null && transport.isOpen()) {
            recipients.put(sessionId, transport);
        }
    }

    /**
     * Uses the same native-window handshake as a Pages Open row click. A browser-only
     * {@code window.focus()} is not sufficient when another detached desktop shell owns the
     * Windows foreground.
     */
    private FocusResult focusWorkspace() {
        reconcileHandles();
        PageHandle target = handlesByKey.get(WORKSPACE_SESSION_ID);
        if (target == null || !isCurrentHandle(target)) {
            return FocusResult.notRequested();
        }
        return focusHandle(target, "Pages button requested Pages Open workspace focus.");
    }

    private FocusResult focusHandle(PageHandle target, String reason) {
        String nativeTitleToken = desktopWindowFocusService.createTitleToken();
        boolean delivered =
                sendWindowOperation(target, WORKSPACE_FOCUS_OPERATION, reason, nativeTitleToken);
        boolean nativeFocused = delivered
                && desktopWindowFocusService.focusWindow(
                        nativeTitleToken, Duration.ofMillis(1800));
        return new FocusResult(delivered, nativeFocused);
    }

    private JsonObject snapshotResponse(JsonObject request, String message) {
        reconcileHandles();
        JsonObject response = success(request, message);
        JsonArray pages = new JsonArray();
        handlesById.values().stream()
                .sorted(Comparator
                        .comparing((PageHandle page) -> !page.presentation().main())
                        .thenComparing(page -> page.presentation().title())
                        .thenComparing(PageHandle::sessionId))
                .map(this::pageJson)
                .forEach(pages::add);
        response.add("pages", pages);
        return response;
    }

    private JsonObject pageJson(PageHandle page) {
        JsonObject json = new JsonObject();
        json.addProperty("pageId", page.pageId());
        json.addProperty("title", page.presentation().title());
        json.addProperty("kind", page.presentation().kind());
        json.addProperty("sessionId", page.sessionId());
        json.addProperty("detail", page.presentation().detail());
        json.addProperty("main", page.presentation().main());
        json.addProperty("closeable", page.presentation().closeable());
        return json;
    }

    private JsonObject success(JsonObject request, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.addProperty("sessionId", WORKSPACE_SESSION_ID);
        copyRequestId(request, response);
        return response;
    }

    private JsonObject failure(JsonObject request, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("message", Objects.toString(message, "Pages Open operation failed."));
        response.addProperty("sessionId", WORKSPACE_SESSION_ID);
        copyRequestId(request, response);
        return response;
    }

    private void copyRequestId(JsonObject request, JsonObject response) {
        String requestId = string(request, "requestId");
        if (!requestId.isEmpty() && requestId.length() <= MAX_REQUEST_ID_CHARACTERS) {
            response.addProperty("requestId", requestId);
        }
    }

    private static String string(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return "";
        try {
            return source.get(name).getAsString().trim();
        } catch (RuntimeException invalidValue) {
            return "";
        }
    }

    private record PagePresentation(
            String title, String kind, String detail, boolean main, boolean closeable) {}

    private record Candidate(
            String key,
            String sessionId,
            Session transport,
            PagePresentation presentation) {}

    private record PageHandle(
            String pageId,
            String key,
            String sessionId,
            Session transport,
            PagePresentation presentation) {
        PageHandle withPresentation(PagePresentation nextPresentation) {
            return presentation.equals(nextPresentation)
                    ? this
                    : new PageHandle(pageId, key, sessionId, transport, nextPresentation);
        }
    }

    private record FocusResult(boolean browserFocusRequested, boolean nativeFocused) {
        private static FocusResult notRequested() {
            return new FocusResult(false, false);
        }
    }
}
