package com.allinweb.ch.component.pane;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.executors.AppExecutors;
import com.allinweb.ch.executors.ExecutorsManager;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.facade.scanner.browser.ScannerBrowserNotAttachedMessageService;
import com.allinweb.ch.facade.scanner.browser.ScannerBrowserRuntime;
import com.allinweb.ch.facade.scanner.browser.ScannerBrowserTabNavigator;
import com.allinweb.ch.facade.scanner.browser.ScannerBrowserTabSelector;
import com.allinweb.ch.facade.scanner.browser.ScannerPlaywrightBrowserAdapter;
import com.allinweb.ch.facade.scanner.browser.ScannerScreenshotLoop;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchControls;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExcelLoader;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExcelPreparation;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchBotJobSelection;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchDefinitionLoad;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExecutionCoordinator;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExecutionGate;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExecutionTask;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchMultipleRowsConfirmation;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchPreparation;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchRecallAfterReset;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchRunSetup;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchStarter;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchStopper;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchWindowBookkeeping;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchWorkspaceOperations;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchWorkspaceRequests;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerExecutionPreflightMonitor;
import com.allinweb.ch.facade.scanner.ScannerRuntimePort;
import com.allinweb.ch.facade.scanner.support.ScannerSupportAlertAdapter;
import com.allinweb.ch.facade.scanner.support.ScannerSupportSaveFlowAdapter;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunExecutionState;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunExecutionStart;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunExcelPreparation;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunPreparationFlow;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunResultHandler;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunStartupPreparation;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunStopper;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunBotJobPreparation;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunBrowserClosePolicy;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunDefinitionLoad;
import com.allinweb.ch.facade.scanner.testrun.ScannerTestRunDefinitionValidation;
import com.allinweb.ch.facade.scanner.testrun.TestRunExecutionOutcomeTracker;
import com.allinweb.ch.facade.scanner.validation.ScannerValidationEvaluator;
import com.allinweb.ch.facade.actions.InstructionGraph;
import com.allinweb.ch.facade.actions.RuntimeVariableStore;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshotRepository;
import com.allinweb.ch.facade.execution.RunScope;
import com.allinweb.ch.model.*;
import com.allinweb.ch.readersAndWriters.ExcelWriter;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.socket.InstructionRealtimePublisher;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Slf4j
public class ScannerRuntimeBackend
        implements ScannerPreLaunchControls,
                ScannerSupportRequestHandler,
                ScannerTestRunHandler,
                ScannerRuntimePort {

    private static final Logger logLaunch = LoggerFactory.getLogger("com.allinweb.launch");
    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private static final String END_OF_FILE_MARKER = "END OF FILE";
    // Very important sequence on initiation
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final ARPriorities arPriorities = ARPriorities.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final ScannerPreLaunchExcelLoader scannerPreLaunchExcelLoader = new ScannerPreLaunchExcelLoader();
    private static final ScannerPreLaunchPreparation scannerPreLaunchPreparation =
            ScannerPreLaunchPreparation.from(performDBEngine, performLists);
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformActions performActions = PerformActions.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final ExecutionPauseCoordinator executionPauseCoordinator =
            ExecutionPauseCoordinator.getInstance();
    private static final PerformListElements performListElements = PerformListElements.getInstance();
    public static TargetElement targetSelected = new TargetElement();
    protected static volatile ScannerRuntimeBackend instance;
    private static SimpleDateFormat dateFormatter;
    private static String excelPath = null;
    private static JavascriptExecutor jsExecutor;
    private static String[] lstAllPaths;
    public final AtomicBoolean isJobRunning = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicLong jobExecutionSequence =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong activeJobExecutionId =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong lastSubmittedJobExecutionId =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong completedJobExecutionId =
            new java.util.concurrent.atomic.AtomicLong();
    private final TestRunExecutionOutcomeTracker jobExecutionOutcomes = new TestRunExecutionOutcomeTracker();
    private final ScannerTestRunExecutionState scannerTestRunExecutionState =
            new ScannerTestRunExecutionState(
                    activeJobExecutionId,
                    lastSubmittedJobExecutionId,
                    completedJobExecutionId,
                    jobExecutionOutcomes);
    private final ScannerTestRunBrowserClosePolicy scannerTestRunBrowserClosePolicy =
            new ScannerTestRunBrowserClosePolicy(
                    scannerTestRunExecutionState::activeExecutionId,
                    scannerTestRunExecutionState::requestStop,
                    scannerTestRunExecutionState::isExecutionInterrupted);
    private final ScannerPreLaunchExecutionGate scannerPreLaunchExecutionGate =
            new ScannerPreLaunchExecutionGate(
                    isJobRunning,
                    jobExecutionSequence,
                    activeJobExecutionId,
                    lastSubmittedJobExecutionId,
                    jobExecutionOutcomes);
    private final AtomicBoolean testRunStartupActive = new AtomicBoolean(false);
    private final Gson gson = new Gson();
    private final ScannerGridPublisher scannerGridPublisher = new ScannerGridPublisher();
    private final ScannerGridSearchResultsService scannerGridSearchResultsService =
            new ScannerGridSearchResultsService(scannerGridPublisher, new PaneScannerGridBlocksPort());
    private final ScannerBlockValidationService scannerBlockValidationService =
            new ScannerBlockValidationService();
    private final ScannerActionDefaultsService scannerActionDefaultsService =
            new ScannerActionDefaultsService();
    private final ScannerDefaultBlockService scannerDefaultBlockService =
            new ScannerDefaultBlockService();
    private final ScannerBlockOptionSelectionService scannerBlockOptionSelectionService =
            new ScannerBlockOptionSelectionService();
    private final ScannerCreatedBlockSelectionService scannerCreatedBlockSelectionService =
            new ScannerCreatedBlockSelectionService();
    private final ScannerCreateBlockModalPresentationService scannerCreateBlockModalPresentationService =
            new ScannerCreateBlockModalPresentationService();
    private final ScannerDialogPublisher scannerDialogPublisher = ScannerDialogPublisher.getInstance();
    private final ScannerElementPanePublisher scannerElementPanePublisher = new ScannerElementPanePublisher();
    private final ScannerSupportRequestPublisher scannerSupportRequestPublisher = new ScannerSupportRequestPublisher();
    private final ScannerSupportRequestService scannerSupportRequestService = new ScannerSupportRequestService();
    private final ScannerSupportAlertAdapter scannerSupportAlertAdapter = new ScannerSupportAlertAdapter();
    private final ScannerSupportSaveFlowAdapter scannerSupportSaveFlowAdapter =
            new ScannerSupportSaveFlowAdapter();
    private final ScannerSupportCaptureResultService scannerSupportCaptureResultService =
            new ScannerSupportCaptureResultService();
    private final ScannerSupportCaptureSendService scannerSupportCaptureSendService =
            new ScannerSupportCaptureSendService();
    private final ScannerSupportResponseActionService scannerSupportResponseActionService =
            new ScannerSupportResponseActionService();
    private final ScannerLocalPluginInventory scannerLocalPluginInventory = new ScannerLocalPluginInventory();
    private final ScannerBrowserNotAttachedMessageService scannerBrowserNotAttachedMessageService =
            new ScannerBrowserNotAttachedMessageService();
    private final ScannerExecutionPreflightMonitor scannerExecutionPreflightMonitor =
            new ScannerExecutionPreflightMonitor(
                    new ExecutionPreflightSnapshotRepository(performDBEngine::getConnection),
                    new PaneExecutionPreflightOperations());
    private final UiThreadDispatcher uiThreadDispatcher = UiThreadDispatcher.getInstance();
    private final ScannerDomReviewSnapshotService scannerDomReviewSnapshotService =
            new ScannerDomReviewSnapshotService();
    private final ScannerPageReviewFileService scannerPageReviewFileService = new ScannerPageReviewFileService();
    private final ScannerElementsReviewFileService scannerElementsReviewFileService =
            new ScannerElementsReviewFileService();
    private final ScannerPreLaunchStarter scannerPreLaunchStarter =
            new ScannerPreLaunchStarter(new PanePreLaunchStartOperations());
    private final ScannerPreLaunchStopper scannerPreLaunchStopper =
            new ScannerPreLaunchStopper(new PanePreLaunchStopOperations());
    private final ScannerPreLaunchWindowBookkeeping scannerPreLaunchWindowBookkeeping =
            new ScannerPreLaunchWindowBookkeeping(new PanePreLaunchWindowBookkeepingOperations());
    private final ScannerRunningProcessCleanupService scannerRunningProcessCleanupService =
            new ScannerRunningProcessCleanupService();
    private final ScannerTestRunStartupPreparation scannerTestRunStartupPreparation =
            new ScannerTestRunStartupPreparation(new PaneTestRunStartupOperations());
    private final ScannerInstructionMessageService scannerInstructionMessageService =
            new ScannerInstructionMessageService();
    private final ScannerSyntheticReferenceService scannerSyntheticReferenceService =
            new ScannerSyntheticReferenceService();
    private final ScannerCsvContentService scannerCsvContentService =
            new ScannerCsvContentService();
    private final ScannerTestRunBotJobPreparation scannerTestRunBotJobPreparation =
            new ScannerTestRunBotJobPreparation(new PaneTestRunBotJobOperations());
    private final ScannerTestRunExecutionStart scannerTestRunExecutionStart =
            new ScannerTestRunExecutionStart(new PaneTestRunExecutionStartOperations());
    private final ScannerTestRunExcelPreparation scannerTestRunExcelPreparation =
            new ScannerTestRunExcelPreparation(scannerPreLaunchExcelLoader, new PaneTestRunExcelOperations());
    private final ScannerTestRunDefinitionValidation scannerTestRunDefinitionValidation =
            new ScannerTestRunDefinitionValidation();
    private final ScannerTestRunDefinitionLoad scannerTestRunDefinitionLoad =
            new ScannerTestRunDefinitionLoad(new PaneTestRunDefinitionLoadOperations());
    private final ScannerTestRunPreparationFlow scannerTestRunPreparationFlow =
            new ScannerTestRunPreparationFlow(
                    scannerTestRunStartupPreparation,
                    scannerTestRunDefinitionLoad,
                    scannerTestRunDefinitionValidation,
                    scannerTestRunBotJobPreparation,
                    scannerTestRunExcelPreparation,
                    scannerTestRunExecutionStart,
                    this::currentTestRunBotJob,
                    this::currentTestRunExcelPath,
                    performLists);
    private final ScannerTestRunResultHandler scannerTestRunResultHandler =
            new ScannerTestRunResultHandler(new PaneTestRunResultOperations());
    private final ScannerTestRunStopper scannerTestRunStopper =
            new ScannerTestRunStopper(new PaneTestRunStopOperations());
    private static final String DEFINED_NAME_PLACEHOLDER = "PICK AN ELEMENT";
    private String definedNameText = DEFINED_NAME_PLACEHOLDER;
    private String searchAttribValueText = "";
    public String xpathTextPrevious;
    protected AtomicBoolean interceptBotJob = new AtomicBoolean(false);
    double comboWidth = 200;
    String excelFieldName;
    String delimiterCSV = null;
    private Set<String> windowHandles;
    // Shared executors from central manager (DO NOT shutdown them here)
    private final ExecutorService executorServicePreLaunch =
            AppExecutors.get().executor(ExecutorsManager.Pool.PRELAUNCH);
    private final ScannerPreLaunchExecutionCoordinator scannerPreLaunchExecutionCoordinator =
            new ScannerPreLaunchExecutionCoordinator(
                    scannerPreLaunchExecutionGate,
                    executorServicePreLaunch,
                    new PanePreLaunchExecutionOperations(),
                    scannerPreLaunchWindowBookkeeping,
                    this::isTestRunExecutionComplete,
                    new PanePreLaunchExecutionCoordinatorOperations());
    private final ScannerPreLaunchWorkspaceRequests scannerPreLaunchWorkspaceRequests =
            new ScannerPreLaunchWorkspaceRequests(new PanePreLaunchWorkspaceRequestOperations());
    private final ScannerPreLaunchRunSetup scannerPreLaunchRunSetup =
            new ScannerPreLaunchRunSetup(new PanePreLaunchRunSetupOperations());
    private final ScannerPreLaunchBotJobSelection scannerPreLaunchBotJobSelection =
            new ScannerPreLaunchBotJobSelection(new PanePreLaunchBotJobSelectionOperations());
    private final ScannerPreLaunchExcelPreparation scannerPreLaunchExcelPreparation =
            new ScannerPreLaunchExcelPreparation(scannerPreLaunchExcelLoader, new PanePreLaunchExcelPreparationOperations());
    private final ScannerPreLaunchDefinitionLoad scannerPreLaunchDefinitionLoad =
            new ScannerPreLaunchDefinitionLoad(new PanePreLaunchDefinitionLoadOperations());
    private final ScannerPreLaunchMultipleRowsConfirmation scannerPreLaunchMultipleRowsConfirmation =
            new ScannerPreLaunchMultipleRowsConfirmation(
                    scannerPreLaunchExcelLoader,
                    new PanePreLaunchMultipleRowsConfirmationOperations());
    private final ScannerPreLaunchRecallAfterReset scannerPreLaunchRecallAfterReset =
            new ScannerPreLaunchRecallAfterReset(new PanePreLaunchRecallAfterResetOperations());
    private final ScannerBrowserTabSelector scannerBrowserTabSelector =
            new ScannerBrowserTabSelector(new PaneBrowserTabSelectorOperations());
    private final ScannerBrowserTabNavigator scannerBrowserTabNavigator =
            new ScannerBrowserTabNavigator(new PaneBrowserTabNavigatorOperations());
    private final ScannerScreenshotLoop scannerScreenshotLoop =
            new ScannerScreenshotLoop(
                    AppExecutors.get().scheduler(ExecutorsManager.Pool.SCREENSHOT_SCHEDULER),
                    new PaneScreenshotLoopOperations());
    private final ScannerValidationEvaluator scannerValidationEvaluator =
            new ScannerValidationEvaluator(new PaneValidationEvaluatorOperations());
    private final ScannerBrowserRuntime scannerBrowserRuntime =
            new ScannerBrowserRuntime(new PaneBrowserRuntimeOperations());
    private final ScannerTestActionFormatter scannerTestActionFormatter = new ScannerTestActionFormatter();
    private final ScannerCreateBlockPlanner scannerCreateBlockPlanner = new ScannerCreateBlockPlanner();
    private final ScannerEmptyPayloadService scannerEmptyPayloadService = new ScannerEmptyPayloadService();

    private int portSocketInitial = 54525;
    private volatile String pendingDomReviewHtml;
    private BotJobLoadDTO currentBotJob;
    private static String currentBotJobName = null;
    private int currentBlockId;
    private int currentBlockOrder;
    private int executeSpecificBlock;
    /** TEST RUN: when true, executeJob runs only the selected block then stops. */
    private boolean runSingleBlock = false;

    private Integer lastInstructionIdPushed = null;
    private boolean firstPageLoadDone = false;
    private boolean isMobileApp = false;
    private SplitDTO splitDTO = new SplitDTO();
    private ExtractedData extractedData = null;
    private List<BlockLoadDTO> blocksLoaded;
    private List<InstructionLoad> excelDataGoto = new ArrayList<>();
    private volatile BlockOptions selectedBlockOption;
    private boolean cloneSelectionActive = false;
    private volatile boolean preLaunchActionEnabled = true;
    private boolean suppressTestSuccessMessage = true;
    private boolean testActionClick = false;
    private boolean testActionInput = false;
    private boolean testActionOutput = false;

    private String currentUrlText = "";
    private String scannerStatusText = "Pre-Launch status: Ready";
    private String scannerStatusStyle = "info";
    private volatile ElementScanProfile selectedElementScanProfile = ALL_INTERACTIVE_SCAN_PROFILE;
    private String searchTermsText = ALL_INTERACTIVE_SCAN_PROFILE.searchText();
    private String testActionsText = "0001";
    private String coordsText = "";
    private Map<String, String> mapOperators = new HashMap<>();
    private volatile boolean variableMetadataAvailable = true;
    private List<String> currentColumnsCSV = new ArrayList<>(); // set once
    private Map<String, CsvTable> csvTables = new LinkedHashMap<>();
    private String[] defaultSearch;
    private boolean searchHiddenFields;
    private String sessionRowStatus;
    private String jsonStatus;
    private RowStatus rowStatus = new RowStatus();
    private PayloadJson payloadEmpty;
    private volatile ARWebDriver currentARWebDriver;
    WebDriverWait waitXPath = null;

    private static final ElementScanProfile ALL_INTERACTIVE_SCAN_PROFILE = new ElementScanProfile(
            "All - Interactive controls",
            "All common clickable, writable, selectable, menu, tree, grid, and dialog controls.",
            "input",
            "textarea",
            "button",
            "a",
            "select",
            "option",
            // Readable outputs: without this the default profile lost every real form
            // <label> (Tipo di richiesta, Oggetto, Nome, ...) that the pre-ACCETTA
            // default search (input, textarea, button, a, select, label) always captured.
            "label",
            "[contenteditable='true']",
            "[role='button']",
            "[role='link']",
            "[role='option']",
            "[role='menuitem']",
            "[role='tab']",
            "[role='checkbox']",
            "[role='radio']",
            "[role='switch']",
            "[role='treeitem']",
            "[role='combobox']",
            "[role='textbox']",
            "[aria-haspopup]",
            "mat-select",
            "mat-option",
            "mat-radio-button",
            "mat-checkbox",
            "mat-slide-toggle",
            "mat-button-toggle",
            "mat-expansion-panel-header",
            "mat-tab",
            "mat-menu-item",
            "mat-tree-node",
            "svg[role='button']",
            "svg[aria-label]",
            "[mat-icon-button]",
            "mat-icon");

    private static final List<ElementScanProfile> ELEMENT_SCAN_PROFILES = buildElementScanProfiles();

    // Private constructor to prevent instantiation
    private ScannerRuntimeBackend() {
        performActions.setTestRunBrowserClosePolicy(scannerTestRunBrowserClosePolicy);
        ScannerWorkspaceService.getInstance().installExecutionOperations(new ScannerPreLaunchWorkspaceOperations(this));
        ScannerSupportRequestHandlers.getInstance().register(this);
        ScannerTestRunHandlers.getInstance().register(this);
    }

    private static List<ElementScanProfile> buildElementScanProfiles() {
        List<ElementScanProfile> profiles = new ArrayList<>();
        profiles.add(ALL_INTERACTIVE_SCAN_PROFILE);
        profiles.add(new ElementScanProfile(
                "Angular Material - Autocomplete",
                "Autocomplete inputs and their selectable options.",
                "input[role='combobox']",
                "[role='combobox']",
                "[role='listbox'] [role='option']",
                "mat-option",
                ".mat-mdc-autocomplete-panel mat-option"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Checkbox",
                "Angular Material checkbox controls.",
                "mat-checkbox",
                "[role='checkbox']",
                "input[type='checkbox']"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Date picker",
                "Calendar cells and date picker actions.",
                "mat-datepicker-toggle",
                "mat-calendar-body-cell",
                "[role='gridcell']",
                "[aria-selected]"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Expansion panel",
                "Accordion and expansion panel headers.",
                "mat-expansion-panel-header",
                "[role='button'][aria-expanded]"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Menu",
                "Angular Material menu items.",
                "mat-menu-item",
                "[role='menu']",
                "[role='menuitem']"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Radio",
                "Angular Material radio options.",
                "mat-radio-button",
                "[role='radio']",
                "input[type='radio']"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Select",
                "Angular Material select controls and options.",
                "mat-select",
                "mat-option",
                "[role='combobox']",
                "[role='listbox'] [role='option']"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Slide toggle",
                "Switch and slide toggle controls.",
                "mat-slide-toggle",
                "mat-button-toggle",
                "[role='switch']"));
        profiles.add(new ElementScanProfile("Angular Material - Tabs", "Tab headers.", "mat-tab", "[role='tab']"));
        profiles.add(new ElementScanProfile(
                "Angular Material - Tree",
                "Tree nodes and expandable tree options.",
                "mat-tree-node",
                "[role='tree']",
                "[role='treeitem']"));
        profiles.add(new ElementScanProfile(
                "ARIA - Buttons and links",
                "Custom button and link widgets.",
                "[role='button']",
                "[role='link']",
                "[aria-haspopup]",
                "svg[role='button']",
                "svg[aria-label]"));
        profiles.add(new ElementScanProfile(
                "ARIA - Combobox and listbox",
                "Custom dropdown, listbox, and combobox options.",
                "[role='combobox']",
                "[role='listbox']",
                "[role='listbox'] [role='option']",
                "[role='option']"));
        profiles.add(new ElementScanProfile(
                "ARIA - Dialog actions",
                "Modal dialog buttons and close actions.",
                "[role='dialog'] button",
                "[role='dialog'] [role='button']",
                "[role='dialog'] [aria-label]"));
        profiles.add(new ElementScanProfile(
                "ARIA - Grid and table",
                "Interactive grid rows, cells, and row actions.",
                "[role='grid']",
                "[role='row']",
                "[role='gridcell']",
                "[role='row'] button",
                "[role='gridcell'] button"));
        profiles.add(new ElementScanProfile(
                "ARIA - Menus", "Menu bars and menu items.", "[role='menu']", "[role='menubar']", "[role='menuitem']"));
        profiles.add(new ElementScanProfile("ARIA - Tabs", "ARIA tab controls.", "[role='tab']", "[role='tablist']"));
        profiles.add(new ElementScanProfile(
                "ARIA - Tree", "ARIA tree and tree item controls.", "[role='tree']", "[role='treeitem']"));
        profiles.add(new ElementScanProfile(
                "Native - Buttons and links",
                "Native clickable controls.",
                "button",
                "a",
                "input[type='button']",
                "input[type='submit']",
                "input[type='reset']"));
        profiles.add(new ElementScanProfile(
                "Native - Checkbox", "Native checkbox controls.", "input[type='checkbox']", "label[for]"));
        profiles.add(new ElementScanProfile(
                "Native - File upload",
                "File inputs and upload drop zones.",
                "input[type='file']",
                ".dropzone",
                "[aria-label*='upload' i]",
                "[role='button'] input[type='file']"));
        profiles.add(new ElementScanProfile(
                "Native - Inputs",
                "Writable text-like controls.",
                "input[type='text']",
                "input[type='email']",
                "input[type='password']",
                "input[type='search']",
                "input[type='number']",
                "input[type='tel']",
                "input[type='url']",
                "input:not([type])",
                "textarea",
                "[contenteditable='true']",
                "[role='textbox']"));
        profiles.add(new ElementScanProfile(
                "Native - Textarea",
                "Writable multiline text areas.",
                "textarea",
                "[data-slot='textarea']",
                "[role='textbox']"));
        profiles.add(new ElementScanProfile(
                "Native - Radio", "Native radio options.", "input[type='radio']", "label[for]", "[role='radio']"));
        profiles.add(
                new ElementScanProfile("Native - Select", "Native select controls and options.", "select", "option"));
        profiles.add(new ElementScanProfile(
                "SVG and icons - Clickable",
                "Icon buttons and clickable SVG elements.",
                "svg[role='button']",
                "svg[aria-label]",
                "button svg",
                "[mat-icon-button]",
                "mat-icon"));
        profiles.sort(Comparator.comparing(ElementScanProfile::label, String.CASE_INSENSITIVE_ORDER));
        profiles.add(new ElementScanProfile(
                "Search rule - With id", "Scan elements that expose an id attribute.", "with id"));
        profiles.add(new ElementScanProfile(
                "Search rule - With name", "Scan elements that expose a name attribute.", "with name"));
        profiles.add(new ElementScanProfile(
                "Search rule - With test id",
                "Scan elements with test-id, data-testid, or data-test-id attributes.",
                "with test-id"));
        profiles.add(new ElementScanProfile(
                "Search rule - With data attributes",
                "Scan elements with common automation data attributes.",
                "[data-testid]",
                "[data-test-id]",
                "[test-id]",
                "[data-cy]",
                "[data-qa]"));
        return Collections.unmodifiableList(profiles);
    }

    static final class ElementScanProfile {
        private final String label;
        private final String description;
        private final List<String> terms;

        private ElementScanProfile(String label, String description, String... terms) {
            this.label = label;
            this.description = description;
            this.terms = List.of(terms);
        }

        String label() {
            return label;
        }

        String description() {
            return description;
        }

        private String searchText() {
            return String.join(", ", terms);
        }

        private String[] termsArray() {
            return terms.toArray(new String[0]);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static ScannerRuntimeBackend getInstance() {
        if (instance == null) {
            synchronized (ScannerRuntimeBackend.class) {
                if (instance == null) {
                    instance = new ScannerRuntimeBackend();
                }
            }
        }
        return instance;
    }

    public static double jaccardSimilarity(String text1, String text2) {
        Set<Character> set1 = new HashSet<>();
        for (char c : text1.toCharArray()) {
            set1.add(c);
        }

        Set<Character> set2 = new HashSet<>();
        for (char c : text2.toCharArray()) {
            set2.add(c);
        }

        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    public static List<InstructionLoad> getUnexecutedInstructions(
            List<InstructionLoad> instructionsExecuted, List<InstructionLoad> otherList) {
        // Create a set of instructionOrderNumbers from instructionsExecuted
        Set<Integer> executedInstructionOrderNumbers = instructionsExecuted.stream()
                .map(InstructionLoad::getInstructionOrderNumber)
                .collect(Collectors.toSet());

        // Filter the otherList to get instructions where executed is false and not in executedInstructionOrderNumbers
        return otherList.stream()
                //                .filter(instruction -> instruction.getExecuted() != null &&
                // !instruction.getExecuted())
                .filter(instruction ->
                        !executedInstructionOrderNumbers.contains(instruction.getInstructionOrderNumber()))
                .collect(Collectors.toList());
    }

    private static String generateTimestamp() {
        Date date = new Date();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return dateFormatter.format(date);
    }
    //    private static final PerformCloseBrowser performCloseBrowser;

    private static void printLog(String resultActions, boolean result) {
        String resultMsg = result ? ARConstants.SUCCESS : ARConstants.FAIL;
        String log = String.join(ARConstants.FIELDS_SEPARATOR, resultMsg, resultActions);
        logLaunch.info(log);
    }

    private static String loadScriptFromResource(String resourcePath) throws IOException {
        // Use ClassLoader to get the resource as an InputStream
        try (InputStream inputStream =
                ScannerRuntimeBackend.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            // Convert InputStream to String
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void destroy() {
        ScannerSupportRequestHandlers.getInstance().unregister(this);
        ScannerTestRunHandlers.getInstance().unregister(this);
        instance = null;
    }

    @Override
    public void shutdownForApplication() {
        executionPauseCoordinator.cancelAll();
        cancelTestRunStartup();
        stopTestRun();
        stopPreLaunchFromWorkspace();
        stopScreenshotLoop();
        destroy();
    }

    @Override
    public boolean isJobRunning() {
        return isJobRunning.get();
    }

    @Override
    public void closeLaunchWindowIfPresent() {
        log.info("Launch window close requested; React owns scanner window state");
    }

    @Override
    public void setTargetSelected(TargetElement target) {
        targetSelected = target;
    }

    @Override
    public TargetElement targetSelected() {
        return targetSelected;
    }

    @Override
    public ScannerTargetContext scannerTargetContext() {
        return new ScannerPaneTargetContext(this);
    }

    @Override
    public void rememberPreviousXPath(String xpath) {
        xpathTextPrevious = xpath;
    }

    @Override
    public void applyActionDefaults(TargetElement targetElement) {
        applyTestActionDefaults(targetElement);
    }

    private void preTestCoordinates(TargetElement targetPreTest) {

        FieldData filedData = new FieldData("martini", "Martini");
        try {
            if (cloneSelectionActive) {

                performActions.executeActionsAtCoordinates(
                        targetPreTest.getCoordinates(), filedData, ARConstants.CLICK, false);
            } else {
                performActions.executeActionsAtCoordinates(
                        targetPreTest.getCoordinates(), filedData, ARConstants.COORD_MOVE_CLICK_RED, false);
            }

        } catch (Exception e) {
            logOperations.info(e.getMessage());
        }
    }

    public int validateBlockDB(String blockTable, int whereId, String message) {
        ScannerBlockValidationService.Result blockValidation =
                scannerBlockValidationService.validate(blockTable, whereId, new PaneBlockValidationOperations());
        currentBlockId = blockValidation.currentBlockId();
        executeSpecificBlock = blockValidation.executeSpecificBlock();

        if (blockValidation.showNoBlockSelected()) {
                performMessage.errorMessage(
                        "Operation \"" + message + "\" No Block Selected",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>No Block Selected ❌</span>",
                        "<span style='color: #E65100; font-weight: bold;'>You must select a Block from the dropdown list</span> before adding a new command.",
                        "<span style='font-style: italic;'>Context:</span> Bot Job: <b>" + currentBotJob.getName()
                                + "</b>",
                        "<span style='color: #455A64;'>Tip: Use the React block selector above the table to choose the target block.</span>",
                        0);

        }
        return blockValidation.returnBlockId();
    }

    private final class PaneBlockValidationOperations implements ScannerBlockValidationService.Operations {
        @Override
        public int createBlockIfNone(String blockTable, int ownerId) {
            return ScannerRuntimeBackend.this.createBlockIfNone(blockTable, ownerId);
        }

        @Override
        public boolean loadBlocks(int ownerId, String blockTable) {
            ErrorMessage errorMessage = performDataBase.loadBlocks(ownerId, "", blockTable);
            return errorMessage == null;
        }

        @Override
        public void refreshBlocks() {
            ScannerRuntimeBackend.this.refreshBlocks(true);
        }

        @Override
        public ScannerBlockValidationService.SelectedBlock selectedBlock() {
            try {
                BlockOptions selected = selectedBlockOption;
                return new ScannerBlockValidationService.SelectedBlock(
                        selected.getBlockId(),
                        selected.getBlockOrderNumber());
            } catch (Exception error) {
                return null;
            }
        }
    }

    public void prepareToInsertElementDTO(
            List<InstructionLoad> instructionList,
            int currentBlockId,
            int nextInstOrderNumber,
            TargetElement targetInsert,
            boolean manyElements) {

        if (targetInsert.getXPath() == null) {
            targetInsert.setXPath(targetInsert.getSavedReferences().get("currentXPath"));
        }

        if (targetInsert.getCoordinates() == null) {
            targetInsert.setCoordinates(targetInsert.getSavedReferences().get("coordinates"));
        }

        String actionReq = targetInsert.getTagName();
        if (!manyElements) {
            actionReq = selectedTestAction();
        }

        targetInsert.setClickElement(testActionClick);
        WebElementTagNameEnum tagType = targetInsert.getTagType();

        Integer currentBotJobId = currentBotJob.getId();

        InstructionLoad instruction =
                performActions.buildNewInstruction(tagType, actionReq, false, nextInstOrderNumber, targetInsert);

        // force_coordinates now comes exclusively from the per-element S/N/T/E/F
        // badges in GridItemScann — stepsInsertManyDTO copies ElementDTO.forceCoordinates
        // onto the TargetElement before we get here. Empty string means the user
        // didn't toggle anything on this element.
        String perElementFlags = targetInsert.getForceCoordinates();
        instruction.setForceCoordinates(Strings.nullToEmpty(perElementFlags));
        instruction.setCoordinates(targetInsert.getCoordinates());
        instruction.setIFrameXPath(targetInsert.getIFrameXPath());
        instruction.setShadowHost(targetInsert.getShadowHost());
        instruction.setShadowRoot(targetInsert.getShadowRoot());
        instruction.setCssSelector(targetInsert.getCssSelector());
        instruction.setBlockId(currentBlockId);
        instruction.setBotJobId(currentBotJobId);
        instruction.setName(targetInsert.getDefinedName());
        // Roadmap 3 Phase 3d: persist the user's display-only override the picker carried in.
        // Null is the "no override" sentinel — the FE clears clientNamed when the typed value
        // matches definedName / someText / is empty, and the SQL writer treats null accordingly.
        instruction.setClientNamed(targetInsert.getClientNamed());

        if (instruction.getName() == null && targetInsert.getNameLabel() == null) {
            if (targetInsert.getSomeText() != null) {
                instruction.setName(targetInsert.getSomeText());
            } else {
                instruction.setName(targetInsert.getTagName());
            }
        } else if (instruction.getName() == null && targetInsert.getNameLabel() != null) {
            instruction.setName(targetInsert.getNameLabel());
        }

        // Normalise the INSERT action to "I:<fieldName>". The legacy ":E:" / ":S:"
        // tokens are no longer embedded in actions — F/E/T/N/S now live in
        // instruction.force_coordinates (see InputFlags + migration 2026-04-26).
        String actions = instruction.getActions();
        if (actions != null && actions.startsWith("I:")) {
            instruction.setActions("I:" + targetInsert.getDefinedName());
        }

        // Set references
        List<ReferenceLoadDTO> referenceList = new ArrayList<>();
        for (Map.Entry<String, String> entry : targetInsert.getSavedReferences().entrySet()) {
            ReferenceLoadDTO reference = new ReferenceLoadDTO();
            reference.setReferenceType(entry.getKey());
            reference.setValue(entry.getValue());
            reference.setBotJobId(currentBotJobId);
            referenceList.add(reference);
        }

        instruction.setReferenceLoadDTOList(referenceList);
        instructionList.add(instruction);
    }

    /**
     * Run a single realistic exercise of the selected element using the exact
     * same code path {@code EngineRunner} takes during a live bot execution.
     *
     * <p>Instead of calling a parallel set of low-level helpers (which was the
     * old behaviour and diverged from the runner — a test could pass while the
     * real run failed), this delegates to
     * {@link PerformActions#performWebActions} which in turn:
     * <ul>
     *   <li>ensures the {@code actionExecutor} JS plugin is injected (loaded
     *       from {@link ARConstants#ACTION_EXECUTION_RELATIVE_PATH_MIN}),</li>
     *   <li>switches into the iframe if the xpath carries one,</li>
     *   <li>reads the {@code F / E / T / N / S} bits from {@code force_coordinates}
     *       via {@link InputFlags} — honouring whatever the user toggled on the
     *       GridItemScann badge row,</li>
     *   <li>runs Selenium primary, then actionExecutor JS fallback, then the
     *       coordinate fallback — same order the runner uses.</li>
     * </ul>
     *
     * <p>The result is shown once at the end via the modal helper, red on
     * failure, green on success.
     *
     * @param originTarget the element the user picked; we deep-copy so the
     *                     live pane state isn't mutated
     * @param testType     {@code "TEST_CLICK_DTO"} or {@code "TEST_INPUT_DTO"}
     */
    public void testingActions(TargetElement originTarget, String testType) {
        testingActions(originTarget, testType, null);
    }

    public void testingActions(TargetElement originTarget, String testType, String inputValueOverride) {
        WebDriver driverTestActions = performActions.getCurrentDriver();
        logOperations.info(
                "testingActions - entry: testType={}, originTarget.forceCoordinates='{}'",
                testType,
                originTarget == null ? "(null target)" : originTarget.getForceCoordinates());
        TargetElement targetDeepCopy = originTarget.deepCopy();
        String displayAction = ScannerWorkspaceOperations.TEST_CLICK_DTO.equals(testType) ? "CLICK" : "INSERT";
        String inputValue = !Strings.isNullOrEmpty(inputValueOverride)
                ? inputValueOverride
                : Strings.nullToEmpty(testActionsText);

        // In Playwright mode there is no Selenium WebElement handle — the test runs via
        // performWebActions -> tryPlaywrightWebAction using the instruction's xpath/css/coords.
        ARPlaywrightDriver activePlaywright =
                currentARWebDriver == null ? null : currentARWebDriver.currentPlaywrightDriver();
        boolean playwrightEnabled = activePlaywright != null && activePlaywright.isOpen();
        if (playwrightEnabled) {
            performActions.setCurrentARWebDriver(currentARWebDriver);
            performActions.setCurrentDriver(null);
        }

        try {
            // Selenium shadow-DOM pre-resolve (only meaningful when a Selenium driver exists).
            if (driverTestActions != null
                    && targetDeepCopy.getElement() == null
                    && !Strings.isNullOrEmpty(targetDeepCopy.getShadowHost())
                    && !Strings.isNullOrEmpty(targetDeepCopy.getCssSelector())) {
                WebElement elementFound = performActions.findShadowElementByCssSelector(
                        targetDeepCopy.getShadowHost(), targetDeepCopy.getCssSelector());
                targetDeepCopy.setElement(elementFound);
            }

            // Only bail on a missing live element in Selenium mode. Playwright resolves from the
            // instruction locators at action time, so a null handle here is expected and fine.
            if (targetDeepCopy.getElement() == null && !playwrightEnabled) {
                performMessage.errorMessage(
                        "Test Action Failed ❌",
                        "<span style='color:#D32F2F;font-weight:bold;font-size:1.1em;'>Element not found in live DOM</span>",
                        "<span style='color:#E65100;'>Could not resolve the element before running the test.</span>",
                        "<span style='font-style:italic;'>Verify the page is loaded and re-scan.</span>",
                        null,
                        0);
                return;
            }

            // Initialise the shared WebDriverWait singletons on PerformActions.
            // performWebActions and every helper it calls use waitForAction.until(...),
            // which NPEs if executeJob() hasn't run yet (previously the only
            // initialiser). Safe to call on every test click.
            ensureWaitsInitialized();

            // Synthetic InstructionLoad carrying the same xpath / shadow / css /
            // force_coordinates / iframe info that the engine reads at run time.
            // force_coordinates was set on the TargetElement from the GridItemScann
            // S/N/T/E/F badges by stepsInsertManyDTO.
            InstructionLoad synthetic = performActions.buildNewInstruction(
                    targetDeepCopy.getTagType(),
                    ScannerWorkspaceOperations.TEST_CLICK_DTO.equals(testType) ? ARConstantsEngine.CLICK : ARConstantsEngine.INSERT,
                    false,
                    0,
                    targetDeepCopy);
            synthetic.setForceCoordinates(Strings.nullToEmpty(targetDeepCopy.getForceCoordinates()));
            synthetic.setCoordinates(targetDeepCopy.getCoordinates());
            synthetic.setIFrameXPath(targetDeepCopy.getIFrameXPath());
            synthetic.setReferenceLoadDTOList(buildSyntheticReferences(targetDeepCopy));

            // TEST_CLICK and TEST_INPUT resolve against the exact active page in the shared session.
            // The stored scan URL is metadata only; it never replaces the live active page.
            // Resolve the locator against the current active page. A test action may open a new tab
            // as its result, but command startup never navigates or reloads the shared page.

            String[] actions = synthetic.getActions().split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER);
            FieldData fieldData = new FieldData("Test", inputValue);
            String savedCoords = !Strings.isNullOrEmpty(targetDeepCopy.getCoordinates())
                    ? targetDeepCopy.getCoordinates()
                    : "coordinates";

            // Single real exercise — Selenium + actionExecutor.min.js plugin + coord fallback.
            // In Playwright mode this uses clickOnce/fillOnce; the fallback chain below is only for
            // non-Playwright legacy sessions.
            boolean success = playwrightEnabled
                    ? ScannerWorkspaceOperations.TEST_CLICK_DTO.equals(testType)
                            ? activePlaywright.clickOnce(synthetic)
                            : activePlaywright.fillOnce(synthetic, fieldData)
                    : performActions.performWebActions(
                            true, // byPassNotFound: log and continue on not-found, don't abort
                            savedCoords,
                            fieldData,
                            synthetic,
                    new HashMap<>(), // empty mapOperators — OUTPUT isn't tested here
                            targetDeepCopy.getElement(),
                            actions,
                            false, // not a mobile app
                            null); // no SplitDTO context

            InputFlags flags = InputFlags.of(synthetic.getForceCoordinates());
            String flagsLine = describeInputFlags(flags);
            String elementLine = safeTargetLabel(targetDeepCopy);

            if (success) {
                StringBuilder body = new StringBuilder();
                body.append("<span style='color:#6A1B9A;font-weight:bold;'>Element:</span> ")
                        .append(elementLine)
                        .append("<br/><span style='color:#6A1B9A;font-weight:bold;'>Flags honoured:</span> ")
                        .append(flagsLine);
                if ("INSERT".equals(displayAction)) {
                    body.append("<br/><span style='color:#6A1B9A;font-weight:bold;'>Input value:</span> ")
                            .append(Strings.isNullOrEmpty(inputValue) ? "(empty)" : inputValue);
                }
                // "Not Show Test Message" — when ticked (default) the green success
                // modal is suppressed so testers can click Test Input / Test Click
                // repeatedly without dismissing a popup every time. Failures still
                // show via performMessage.errorMessage below.
                if (!suppressTestSuccessMessage) {
                    performMessage.showCustomModalDialogDragWin11(
                            "Test Action Success ✅",
                            "<span style='color:#2E7D32;font-weight:bold;font-size:1.1em;'>" + displayAction
                                    + " performed successfully!</span>",
                            "<span style='color:#1565C0;font-weight:bold;'>Scanner test action used the active Playwright page once.</span>",
                            body.toString(),
                            "<span style='font-style:italic;'>If this passes, the live bot run will take the same route.</span>",
                            false,
                            "OK",
                            null,
                            0);
                }
            } else {
                performMessage.errorMessage(
                        "Test Action Failed ❌",
                        "<span style='color:#D32F2F;font-weight:bold;font-size:1.1em;'>" + displayAction
                                + " could not be performed</span>",
                        "<span style='color:#E65100;font-weight:bold;'>The single Playwright scanner test action did not complete.</span>",
                        "<span style='color:#6A1B9A;font-weight:bold;'>Flags tried:</span> " + flagsLine
                                + "<br/><span style='color:#6A1B9A;font-weight:bold;'>Element:</span> " + elementLine,
                        null,
                        0);
            }
        } catch (Exception e) {
            logOperations.error("testingActions failed: {}", e.getMessage(), e);
            performMessage.errorMessage(
                    "Test Action Error ❌",
                    "<span style='color:#D32F2F;font-weight:bold;font-size:1.1em;'>An exception was thrown during the test</span>",
                    "<span style='color:#E65100;font-weight:bold;'>Details:</span> " + e.getMessage(),
                    null,
                    null,
                    0);
        } finally {
            if (driverTestActions != null) {
                try {
                    driverTestActions.switchTo().defaultContent();
                } catch (Exception ignore) {
                    // driver may be gone — nothing actionable here
                }
            }
            uiThreadDispatcher.execute(() -> {
                definedNameText = DEFINED_NAME_PLACEHOLDER;
                searchAttribValueText = "";
            });
        }
    }

    private List<ReferenceLoadDTO> buildSyntheticReferences(TargetElement target) {
        Integer botJobId = currentBotJob == null ? null : currentBotJob.getId();
        Integer homeBankingId = currentBotJob == null ? null : currentBotJob.getHomeBankingId();
        return scannerSyntheticReferenceService.build(target, botJobId, homeBankingId);
    }

    /** Human-readable list of active F/E/T/N/S bits for the result modal. */
    private String describeInputFlags(InputFlags flags) {
        return scannerTestActionFormatter.describeInputFlags(flags);
    }

    /** Short label for the result modal: defined-name + tag, or just tag, or "(unnamed)". */
    private String safeTargetLabel(TargetElement t) {
        return scannerTestActionFormatter.safeTargetLabel(t);
    }

    public boolean isInterceptBotJob() {
        return interceptBotJob.get();
    }

    public void setInterceptBotJob(boolean value) {
        boolean previous = interceptBotJob.getAndSet(value);
        if (previous != value) {
            log.info("interceptBotJob changed from " + previous + " to " + value);
        }
    }

    public void initialize(ARWebDriver currentARWebDriver, BotJobLoadDTO botJobLoad, int portSocketInitial) {
        this.portSocketInitial = portSocketInitial;
        this.currentARWebDriver = currentARWebDriver;

        searchHiddenFields = false;

        defaultSearch = new String[] {"input", "textarea", "button", "a", "select", "label"};

        log.info("Calling ScannerRuntimeBackend");

        // Ensure botJob and arPriorities are not null before accessing their methods
        if (this.currentBotJob != null && arPriorities != null) {
            // Check if we need to update arPriorities
            if (arPriorities.getJobId() == null || !arPriorities.getJobId().equals(this.currentBotJob.getId())) {
                // Set Job ID in arPriorities
                arPriorities.setJobId(this.currentBotJob.getId());

                // Check for non-null HomeBanking and Priority
                HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(botJobLoad.getHomeBankingId());
                if (homeBanking != null) {
                    String priorityValue = homeBanking.getPriority();
                    String searchConfig = homeBanking.getSearchConfig();

                    if (priorityValue != null) {
                        ARPriorities.loadPrioritiesFromString(priorityValue);
                    } else {
                        arPriorities.loadPriorities();
                    }

                    ARPriorities.loadSearchElementsConfig(searchConfig);
                }

                // Initialize performAction with arPriorities and arWebDriver

                performActions.initialize(arPriorities);
                performActions.setCurrentARWebDriver(currentARWebDriver);
                performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());
            }
        }

        // Assign instance variables
        this.currentBotJob = botJobLoad;
        performActions.initialize(arPriorities);
        performActions.setCurrentARWebDriver(currentARWebDriver);
        performActions.setCurrentDriver(currentARWebDriver.getCurrentDriver());

        if (!openBrowser()) {
            ScannerShellLifecycle.getInstance().closeShell();
            return;
        }

        HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(
                this.currentBotJob.getHomeBankingId(), this.currentBotJob.getHomeUrlId());

        updateSceneTitleWithCurrentURL(homeUrlDTO.getUrl());

        selectPreferredBlockOption(false);
        setPayloadEmpty();
        uiThreadDispatcher.execute(this::refreshGrids);
    }

    private void refreshGrids() {
        scannerGridPublisher.publishScannerGridSearchTermsPayload(this.currentBotJob.getHomeBankingId(), payloadEmpty);
    }

    private boolean openBrowser() {
        return openBrowser(false);
    }

    private boolean openBrowserForTestRun() {
        return openBrowser(true);
    }

    private boolean openBrowser(boolean preserveCurrentPage) {
        String browserType = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
        HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(
                this.currentBotJob.getHomeBankingId(), this.currentBotJob.getHomeUrlId());
        HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(this.currentBotJob.getHomeBankingId());

        boolean browserReady = preserveCurrentPage
                ? currentARWebDriver.openBrowserPreservingCurrentPage(
                        browserType, homeUrlDTO.getUrl(), homeBanking.getOptionsConfig())
                : currentARWebDriver.openBrowser(browserType, homeUrlDTO.getUrl(), homeBanking.getOptionsConfig());
        if (!browserReady) {
            return false;
        }

        performActions.initialize(arPriorities);
        performActions.setCurrentARWebDriver(currentARWebDriver);
        performActions.setCurrentDriver(null);
        return true;
    }

    public void refreshBlocks(boolean secondItem) {
        selectPreferredBlockOption(secondItem);
    }

    private void sendCurrentDomForReview() {
        try {
            ScannerPlaywrightBrowserAdapter browser = scannerPlaywrightBrowser();
            if (!browser.hasCurrentDriver()) {
                scannerSupportAlertAdapter.showNoActiveBrowser();
                return;
            }

            Optional<ScannerDomReviewSnapshotService.Snapshot> snapshot =
                    scannerDomReviewSnapshotService.snapshot(browser);
            if (snapshot.isEmpty()) {
                log.warn("sendCurrentDomForReview — empty page source");
                return;
            }
            ScannerDomReviewSnapshotService.Snapshot domReviewSnapshot = snapshot.get();
            pendingDomReviewHtml = domReviewSnapshot.html();

            int hbId = this.currentBotJob != null ? this.currentBotJob.getHomeBankingId() : 0;
            scannerSupportRequestPublisher.publishDomReview(
                    hbId, domReviewSnapshot.currentUrl(), domReviewSnapshot.title(), domReviewSnapshot.html());
            log.info(
                    "sendCurrentDomForReview — WS message sent to {}, waiting for user response",
                    scannerSupportRequestPublisher.destinationSessionId());

        } catch (Exception ex) {
            log.error("sendCurrentDomForReview failed", ex);
            pendingDomReviewHtml = null;
        }
    }

    public void handleDomReviewResponse(String action) {
        String html = pendingDomReviewHtml;
        pendingDomReviewHtml = null;

        ScannerSupportResponseActionService.Action responseAction =
                scannerSupportResponseActionService.actionOf(action);
        if (scannerSupportResponseActionService.isDomReviewCancelled(html, action)) {
            log.info("DOM review cancelled or no pending HTML");
            return;
        }

        uiThreadDispatcher.execute(() -> {
            try {
                if (responseAction == ScannerSupportResponseActionService.Action.SEND) {
                    SupportCapture.CaptureResult r = scannerSupportCaptureSendService.sendDomCapture();
                    ScannerSupportCaptureResultService.AlertMessage message =
                            scannerSupportCaptureResultService.domCapture(r);
                    scannerSupportAlertAdapter.showCaptureResult(message);

                } else if (responseAction == ScannerSupportResponseActionService.Action.SAVE) {
                    ScannerSupportFileService.SupportFile supportFile =
                            scannerPageReviewFileService.pageReview(html, scannerPlaywrightBrowser());
                    scannerSupportSaveFlowAdapter.savePageReview(supportFile);
                }
            } catch (Exception ex) {
                log.error("handleDomReviewResponse failed", ex);
            }
        });
    }

    private void requestSupport() {
        try {
            int hbId = this.currentBotJob != null ? this.currentBotJob.getHomeBankingId() : 0;
            String destination = scannerSupportRequestService.requestSupport(
                    hbId, scannerPlaywrightBrowser());
            log.info("requestSupport — WS message sent to {}", destination);

        } catch (Exception ex) {
            log.error("requestSupport failed", ex);
        }
    }

    /**
     * Same context payload as {@link #requestSupport()} but emitted under
     * operationId {@code REQUEST_SUPPORT_ELEMENTS} so the React grid opens
     * the elements-scoped support modal (carries the full element list back
     * via {@code SUPPORT_REQUEST_ELEMENTS_RESPONSE}).
     */
    public void requestSupportElements() {
        try {
            int hbId = this.currentBotJob != null ? this.currentBotJob.getHomeBankingId() : 0;
            String destination = scannerSupportRequestService.requestElementsSupport(
                    hbId, scannerPlaywrightBrowser());
            log.info("requestSupportElements — WS message sent to {}", destination);

        } catch (Exception ex) {
            log.error("requestSupportElements failed", ex);
        }
    }

    private ScannerPlaywrightBrowserAdapter scannerPlaywrightBrowser() {
        return new ScannerPlaywrightBrowserAdapter(() -> currentARWebDriver == null
                ? null
                : currentARWebDriver.currentPlaywrightDriver());
    }

    /**
     * Elements counterpart of {@link #handleDomReviewResponse}: reuses the
     * Page-Review endpoint/envelope ({@code /support/dom-capture}) but the
     * payload carries the scanned {@code elementDetails} plus a best-effort
     * live Playwright snapshot per element — no full-page HTML. Same classification
     * on the portal, just {@code kind: "elements-review"}.
     */
    public void handleSupportRequestElementsResponse(String action, String message, String elementDetailsJson) {
        ScannerSupportResponseActionService.Action responseAction =
                scannerSupportResponseActionService.actionOf(action);
        if (scannerSupportResponseActionService.isElementsReviewCancelled(action, message)) {
            log.info("Support request (elements) cancelled");
            return;
        }

        uiThreadDispatcher.execute(() -> {
            try {
                if (responseAction == ScannerSupportResponseActionService.Action.SEND) {
                    SupportCapture.CaptureResult r =
                            scannerSupportCaptureSendService.sendElementsReview(elementDetailsJson, message);

                    ScannerSupportCaptureResultService.AlertMessage alertMessage =
                            scannerSupportCaptureResultService.elementsReview(r);
                    scannerSupportAlertAdapter.showCaptureResult(alertMessage);

                } else if (responseAction == ScannerSupportResponseActionService.Action.SAVE) {
                    ScannerSupportFileService.SupportFile supportFile =
                            scannerElementsReviewFileService.elementsReview(
                                    scannerPlaywrightBrowser(), elementDetailsJson, message);
                    scannerSupportSaveFlowAdapter.saveElementsReview(supportFile);
                }
            } catch (Exception ex) {
                log.error("handleSupportRequestElementsResponse failed", ex);
            }
        });
    }

    public void handleSupportRequestResponse(String action, String message) {
        // MultiPlugins network traffic disabled; UI is gated and this callback is a hard stop.
        log.info("handleSupportRequestResponse disabled; no MultiPlugins call performed (action={})", action);
    }

    private void updateButtonState() {
        logOperations.debug("Scanner tab button state update ignored; React workspace owns tab controls");
    }

    // Method to handle the scenario where the window handles size changes
    private void handleWindowHandlesChange() {
        scannerBrowserTabNavigator.handleWindowHandlesChange();
    }

    private final class PaneBrowserTabNavigatorOperations implements ScannerBrowserTabNavigator.Operations {
        @Override
        public boolean hasCurrentDriver() {
            return performActions.getCurrentDriver() != null;
        }

        @Override
        public int currentWindowHandleCount() {
            return performActions.getCurrentDriver().getWindowHandles().size();
        }

        @Override
        public int knownWindowHandleCount() {
            return performActions.windowHandlesList.size();
        }

        @Override
        public int currentTabIndex() {
            return performActions.currentTabIndex;
        }

        @Override
        public void setCurrentTabIndex(int currentTabIndex) {
            performActions.currentTabIndex = currentTabIndex;
        }

        @Override
        public String windowHandleAt(int index) {
            return performActions.windowHandlesList.get(index);
        }

        @Override
        public void switchToWindow(String windowHandle) {
            performActions.getCurrentDriver().switchTo().window(windowHandle);
        }

        @Override
        public String currentUrl() {
            return performActions.getCurrentDriver().getCurrentUrl();
        }

        @Override
        public void updateSceneTitleWithCurrentUrl(String currentUrl) {
            updateSceneTitleWithCurrentURL(currentUrl);
        }

        @Override
        public void updateWindowHandlesList() {
            performActions.updateWindowHandlesList();
        }
    }

    public void updateSceneTitleWithCurrentURL(String currentUrl) {
        currentUrlText = currentUrl == null ? "" : currentUrl;
        log.debug("Scanner current URL updated: {}", currentUrlText);
    }

    private ScannerWorkspaceResponse runScannerWorkspaceAction(String action) {
        return runScannerWorkspaceAction(action, null);
    }

    private ScannerWorkspaceResponse runScannerWorkspaceAction(String action, String searchTerms) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "scanner-runtime-" + System.nanoTime());
        body.addProperty("botJobId", currentBotJob == null ? -1 : currentBotJob.getId());
        body.addProperty("action", action);
        if (searchTerms != null) {
            body.addProperty(ScannerWorkspaceOperations.SEARCH_TERMS, searchTerms);
        }

        ScannerWorkspaceRequest request = new ScannerWorkspaceRequest(
                ScannerSearchRoute.standardPageScanner().destinationSessionId(),
                body.get("requestId").getAsString(),
                currentBotJob == null ? -1 : currentBotJob.getId(),
                body);
        ScannerWorkspaceResponse response = ScannerWorkspaceService.getInstance().action(request);
        if (!response.ok()) {
            logOperations.warn("Scanner workspace action {} failed: {}", action, response.message());
        }
        return response;
    }

    private void selectBlockOption(BlockOptions selected) {
        selectedBlockOption = scannerBlockOptionSelectionService.isRealBlock(selected) ? selected : null;
    }

    private void selectPreferredBlockOption(boolean secondItem) {
        List<BlockOptions> blockOptions = performLists.loadComboOptions("block", "ScannerPane");
        int preferredIndex = secondItem ? 1 : 0;
        BlockOptions preferred = blockOptions.size() > preferredIndex ? blockOptions.get(preferredIndex) : null;
        if (!scannerBlockOptionSelectionService.isRealBlock(preferred)) {
            preferred = blockOptions.stream()
                    .filter(scannerBlockOptionSelectionService::isRealBlock)
                    .findFirst()
                    .orElse(null);
        }
        selectBlockOption(preferred);
        if (selectedBlockOption == null) {
            log.debug("Scanner block selection cleared; React workspace must provide or create a block before insert");
        } else {
            log.debug("Scanner block selection refreshed: {}", selectedBlockOption.getText());
        }
    }

    private int selectedBlockOrderNumber() {
        BlockOptions selected = selectedBlockOption;
        return selected == null || selected.getBlockOrderNumber() == null ? 0 : selected.getBlockOrderNumber();
    }

    private void selectElementScanProfile(ElementScanProfile selected) {
        selectedElementScanProfile = selected == null ? ALL_INTERACTIVE_SCAN_PROFILE : selected;
        searchTermsText = selectedElementScanProfile.searchText();
    }

    public void requestPreLaunchFromWorkspace(int botJobId) {
        scannerPreLaunchWorkspaceRequests.requestStart(botJobId);
    }

    public void requestStopPreLaunchFromWorkspace(int botJobId) {
        scannerPreLaunchWorkspaceRequests.requestStop(botJobId);
    }

    public void startPreLaunchFromWorkspace() {
        scannerPreLaunchStarter.start();
    }

    private final class PanePreLaunchWorkspaceRequestOperations
            implements ScannerPreLaunchWorkspaceRequests.Operations {
        @Override
        public Integer currentBotJobId() {
            return currentBotJob == null ? null : currentBotJob.getId();
        }

        @Override
        public boolean preLaunchBackendReady() {
            return currentBotJob != null && preLaunchActionEnabled;
        }

        @Override
        public boolean stopPreLaunchBackendReady() {
            return currentBotJob != null;
        }

        @Override
        public void runLater(Runnable task) {
            uiThreadDispatcher.execute(task);
        }

        @Override
        public void startPreLaunch() {
            startPreLaunchFromWorkspace();
        }

        @Override
        public void stopPreLaunch() {
            stopPreLaunchFromWorkspace();
        }
    }

    private final class PanePreLaunchStartOperations implements ScannerPreLaunchStarter.Operations {
        @Override
        public boolean lastBrowserTab() {
            return ScannerRuntimeBackend.this.lastBrowserTab();
        }

        @Override
        public void beginRun() {
            scannerPreLaunchRunSetup.beginRun();
        }

        @Override
        public ErrorMessage loadDefinitions() {
            return loadPreLaunchDefinitions();
        }

        @Override
        public void reportLoadError(ErrorMessage errorMessage) {
            reportPreLaunchLoadError(errorMessage);
        }

        @Override
        public boolean loadCurrentBotJob() {
            return loadCurrentPreLaunchBotJob();
        }

        @Override
        public void observeExecutionPreflight() {
            scannerExecutionPreflightMonitor.observe(
                    "CLASSIC_SCANNER_PRE_LAUNCH",
                    currentBotJob,
                    currentPreLaunchRunScope());
        }

        @Override
        public void prepareExcel() {
            preparePreLaunchExcel();
        }

        @Override
        public boolean validateExcel() {
            return validatePreLaunchExcel();
        }

        @Override
        public boolean confirmMultipleExcelRows() {
            return confirmMultipleExcelRows();
        }

        @Override
        public void resetInstructionsAndRecall() {
            resetPreLaunchInstructionsAndRecall();
        }
    }

    private final class PanePreLaunchRunSetupOperations implements ScannerPreLaunchRunSetup.Operations {
        @Override
        public void disableLaunch() {
            preLaunchActionEnabled = false;
            logOperations.debug("Scanner Pre-Launch action disabled");
        }

        @Override
        public void setInterceptBotJob(boolean intercept) {
            performActions.setInterceptBotJob(intercept);
            ScannerRuntimeBackend.this.setInterceptBotJob(intercept);
        }

        @Override
        public void markNotRunning() {
            isJobRunning.set(false);
        }

        @Override
        public String resolveExcelBasePath() {
            return arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
        }

        @Override
        public void setExcelPath(String resolvedExcelPath) {
            excelPath = resolvedExcelPath;
        }

        @Override
        public void reportExcelPathError(Exception error) {
            log.error("Error Defining Excel or BaseLog File: " + error.getMessage());
        }

        @Override
        public int selectedBlockOrderNumber() {
            return ScannerRuntimeBackend.this.selectedBlockOrderNumber();
        }

        @Override
        public void setExecuteSpecificBlock(int blockIndex) {
            executeSpecificBlock = blockIndex;
        }

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {
            ScannerRuntimeBackend.this.runSingleBlock = runSingleBlock;
        }

        @Override
        public void clearFields() {
            ScannerRuntimeBackend.this.clearFields();
        }
    }

    private ErrorMessage loadPreLaunchDefinitions() {
        return scannerPreLaunchDefinitionLoad.loadDefinitions();
    }

    private void reportPreLaunchLoadError(ErrorMessage errorMessage) {
        scannerPreLaunchDefinitionLoad.reportLoadError(errorMessage);
    }

    private final class PanePreLaunchDefinitionLoadOperations
            implements ScannerPreLaunchDefinitionLoad.Operations {
        @Override
        public BotJobLoadDTO currentBotJob() {
            return currentBotJob;
        }

        @Override
        public ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob) {
            ScannerPreLaunchPreparation.Result result =
                    scannerPreLaunchPreparation.loadDefinitions(currentBotJob);
            variableMetadataAvailable = result.variableLoadWarning() == null;
            return result;
        }

        @Override
        public void setExcelDataGoto(List<InstructionLoad> loadedExcelDataGoto) {
            excelDataGoto = loadedExcelDataGoto;
        }

        @Override
        public void setBlocksLoaded(List<BlockLoadDTO> loadedBlocks) {
            blocksLoaded = loadedBlocks;
        }

        @Override
        public void showOperationFailed(ErrorMessage errorMessage) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        @Override
        public void warn(String message) {
            log.warn(message);
        }

        @Override
        public void error(String message) {
            log.error(message);
        }
    }

    private boolean loadCurrentPreLaunchBotJob() {
        return scannerPreLaunchBotJobSelection.loadCurrentBotJob();
    }

    private final class PanePreLaunchBotJobSelectionOperations
            implements ScannerPreLaunchBotJobSelection.Operations {
        @Override
        public BotJobLoadDTO currentBotJob() {
            return currentBotJob;
        }

        @Override
        public String excelPath() {
            return excelPath;
        }

        @Override
        public ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(
                BotJobLoadDTO currentBotJob, String excelBasePath) {
            return scannerPreLaunchPreparation.loadCurrentBotJob(currentBotJob, excelBasePath);
        }

        @Override
        public void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection) {
            currentBotJob = selection.botJob();
            currentBotJobName = selection.botJobName();
            excelPath = selection.excelPath();
        }

        @Override
        public void reenableLaunchButton() {
            ScannerRuntimeBackend.this.reenableLaunchButton();
        }

        @Override
        public void error(String message) {
            log.error(message);
        }
    }

    private void preparePreLaunchExcel() {
        scannerPreLaunchExcelPreparation.prepareExcel();
    }

    private boolean validatePreLaunchExcel() {
        return scannerPreLaunchExcelPreparation.validateExcel();
    }

    private final class PanePreLaunchExcelPreparationOperations
            implements ScannerPreLaunchExcelPreparation.Operations {
        @Override
        public String excelPath() {
            return excelPath;
        }

        @Override
        public PerformLists performLists() {
            return performLists;
        }

        @Override
        public ExtractedData extractedData() {
            return extractedData;
        }

        @Override
        public void setExtractedData(ExtractedData preparedData) {
            extractedData = preparedData;
        }

        @Override
        public void showExcelProcessingError() {
            performMessage.errorMessage(
                    "Error Processing Excel File",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to Execute Excel File!</span> \u26a0\ufe0f",
                    "<span style='color: #E65100; font-weight: bold;'>Please carefully review all Excel columns and their values for potential errors.</span>",
                    "<span style='font-style: italic;'>Inconsistent or incorrect data can prevent the application from processing the file.</span>",
                    null,
                    0);
        }

        @Override
        public void showExcelValidationError(String errorMessage) {
            performMessage.errorMessage("Excel Error", "Could Not Execute Excel File", errorMessage, null, null, 0);
        }

        @Override
        public void reenableLaunchButton() {
            ScannerRuntimeBackend.this.reenableLaunchButton();
        }

        @Override
        public void error(String message) {
            log.error(message);
        }
    }

    private boolean confirmMultipleExcelRows() {
        return scannerPreLaunchMultipleRowsConfirmation.confirm();
    }

    private final class PanePreLaunchMultipleRowsConfirmationOperations
            implements ScannerPreLaunchMultipleRowsConfirmation.Operations {
        @Override
        public ExtractedData extractedData() {
            return extractedData;
        }

        @Override
        public List<InstructionLoad> excelDataGoto() {
            return excelDataGoto;
        }

        @Override
        public ARExecution.DialogModal showMultipleRowsConfirmation() {
            return performMessage.showCustomModalDialogDragWin11(
                    "Multiple Excel Rows Detected",
                    "<span style='font-weight: bold;'>Your Excel data file contains multiple rows.</span>",
                    "By default, each Excel test row <span style='font-weight: bold; color: #e854c8;'>will be processed through all blocks</span>, and after  will jump back to <span style='font-weight: bold;'>first block (Use Case).</span>",
                    "Add the <span style='font-weight: bold; color: #FF4500;'>'Excel GOTO'</span> operation to your flow to modify the <span style='font-weight: bold;'>default behaviour.</span>",
                    "The <span style='font-weight: bold; color: #FF4500;'>Excel GOTO</span> allows you to specify which block <span style='font-weight: bold;'>the flow should continue from</span>, after the execution of the first row across all blocks.",
                    false,
                    "Continue",
                    "Stop All",
                    0);
        }

        @Override
        public void requestIntercept() {
            performActions.setInterceptBotJob(true);
            setInterceptBotJob(true);
            executionPauseCoordinator.cancelExecution(scannerTestRunExecutionState.activeExecutionId());
        }

        @Override
        public void markNotRunning() {
            isJobRunning.set(false);
        }

        @Override
        public void reenableLaunchButton() {
            ScannerRuntimeBackend.this.reenableLaunchButton();
        }

        @Override
        public boolean lastBrowserTab() {
            return ScannerRuntimeBackend.this.lastBrowserTab();
        }

        @Override
        public void warn(String message) {
            log.warn(message);
        }
    }

    private void resetPreLaunchInstructionsAndRecall() {
        scannerPreLaunchRecallAfterReset.resetInstructionsAndRecall();
    }

    private final class PanePreLaunchRecallAfterResetOperations
            implements ScannerPreLaunchRecallAfterReset.Operations {
        @Override
        public boolean resetInstructionExecutionFlags() {
            return scannerPreLaunchPreparation.resetInstructionExecutionFlags();
        }

        @Override
        public boolean recallJob() {
            return ScannerRuntimeBackend.this.recallJob();
        }
    }

    public void stopPreLaunchFromWorkspace() {
        scannerPreLaunchStopper.stop();
    }

    private final class PanePreLaunchStopOperations implements ScannerPreLaunchStopper.Operations {
        @Override
        public void enableLaunch() {
            enablePreLaunchAction();
        }

        @Override
        public void requestIntercept() {
            performActions.setInterceptBotJob(true);
            setInterceptBotJob(true);
            executionPauseCoordinator.cancelExecution(scannerTestRunExecutionState.activeExecutionId());
        }

        @Override
        public void markNotRunning() {
            isJobRunning.set(false);
        }

        @Override
        public boolean lastBrowserTab() {
            return ScannerRuntimeBackend.this.lastBrowserTab();
        }
    }

    public void initUIBehaviour() {
        logOperations.debug("Scanner action wiring skipped; React workspace owns scanner actions");
    }

    public boolean lastBrowserTab() {
        return scannerBrowserTabSelector.switchToLastBrowserTab();
    }

    private final class PaneBrowserTabSelectorOperations implements ScannerBrowserTabSelector.Operations {
        @Override
        public boolean hasCurrentDriver() {
            return performActions.getCurrentDriver() != null;
        }

        @Override
        public Set<String> windowHandles() {
            windowHandles = performActions.getCurrentDriver().getWindowHandles();
            return windowHandles;
        }

        @Override
        public void switchToWindow(String windowHandle) {
            performActions.getCurrentDriver().switchTo().window(windowHandle);
        }

        @Override
        public void browserNotAttached() {
            ScannerRuntimeBackend.this.browserNotAttached();
        }
    }

    private static final class PaneScannerGridBlocksPort implements ScannerGridSearchResultsService.BlockPort {
        @Override
        public List<BlockLoadDTO> blocks() {
            return performLists.getListBlock();
        }
    }

    private void cloneElementDTO(TargetElement targetToClone) {

        if (targetToClone != null) {

            // convertTargetToElementDTO already copies the original element's canonical
            // fields (definedName, someText, tagName, ...) AND its display-only override
            // (clientNamed). Do NOT collapse them here — definedName must stay the canonical
            // name (instruction.name) and clientNamed must stay the override
            // (instruction.client_named). Polluting someText with the resolved-priority
            // value used to push the override into the wrong DB column.
            ElementDTO elementDTO = performActions.convertTargetToElementDTO(targetToClone);

            var processDTO = new SplitDTO();
            processDTO.setHomeBankingId(this.currentBotJob.getHomeBankingId());
            processDTO.setBotJobId(this.currentBotJob.getId());
            processDTO.setBotJobName(this.currentBotJob.getName());
            processDTO.setSessionId(scannerGridPublisher.destinationSessionId());
            processDTO.setOperationId("clonedElement");

            List<ElementDTO> detailsList = new ArrayList<>();

            if (testActionInput) {
                ElementDTO inputElementDTO = elementDTO.deepCopy(); // Create a copy
                inputElementDTO.setTypeElement(
                        WebElementTagNameEnum.INPUT.getValue().toLowerCase());
                inputElementDTO.setTagName(
                        WebElementTagNameEnum.INPUT.getValue().toLowerCase());
                detailsList.add(inputElementDTO);
            }
            if (testActionClick) {
                ElementDTO buttonElementDTO = elementDTO.deepCopy(); // Create a copy
                buttonElementDTO.setTypeElement(
                        WebElementTagNameEnum.BUTTON.getValue().toLowerCase());
                buttonElementDTO.setTagName(
                        WebElementTagNameEnum.BUTTON.getValue().toLowerCase());
                detailsList.add(buttonElementDTO);
            }
            if (testActionOutput) {
                ElementDTO outputElementDTO = elementDTO.deepCopy(); // Create a copy
                outputElementDTO.setTypeElement(
                        WebElementTagNameEnum.OUTPUT.getValue().toLowerCase());
                outputElementDTO.setTagName(
                        WebElementTagNameEnum.LABEL.getValue().toLowerCase());
                detailsList.add(outputElementDTO);
            }

            ElementDTO[] detailsArray = detailsList.toArray(new ElementDTO[0]);
            processDTO.setElementDetails(detailsArray);

            for (int x = 0; x < detailsArray.length; x++) {
                detailsArray[x].setTypeElement("tagName-Found");
                detailsArray[x].setId(x + 1);
            }

            scannerGridPublisher.publishScannerGrid(this.currentBotJob.getHomeBankingId(), processDTO, "clonedElement");
        }
    }

    public void itPrintsElementDTO() {

        //        for (ARWebElement arWebElement : scannedElements2.getItems()) {
        //            performActions.highlightElement(jsExecutor, arWebElement.getElement(), null);
        //        }
        if (targetSelected != null) {
            StringBuilder sb = new StringBuilder();
            String nameDefined = "";

            if (targetSelected.getElement() != null) {
                // Display priority: clientNamed → definedName → someText → tagName.
                // The label is read-only; renames are made in the React grid and persisted
                // into instruction.client_named (never used for matching/recovery).
                nameDefined = !Strings.isNullOrEmpty(targetSelected.getClientNamed())
                        ? targetSelected.getClientNamed()
                        : !Strings.isNullOrEmpty(targetSelected.getDefinedName())
                                ? targetSelected.getDefinedName()
                                : !Strings.isNullOrEmpty(targetSelected.getSomeText())
                                        ? targetSelected.getSomeText()
                                        : Strings.nullToEmpty(targetSelected.getTagName());

                String finalNameDefined = PerformActions.truncateAndNormalize(nameDefined, 250);
                uiThreadDispatcher.execute(() -> definedNameText = finalNameDefined);
            }

            sb.append("TagType: " + targetSelected.getTagType()).append("\n");
            sb.append("ID: " + targetSelected.getAttribId()).append("\n");
            sb.append("Name: " + targetSelected.getAttribName()).append("\n");
            if (!Strings.isNullOrEmpty(targetSelected.getShadowRoot())) {
                sb.append("ShadowHost: " + targetSelected.getShadowHost()).append("\n");
                sb.append("cssSelector: " + targetSelected.getCssSelector()).append("\n");
            }
            sb.append("Text: " + targetSelected.getSomeText()).append("\n");

            if (!Strings.isNullOrEmpty(targetSelected.getCoordinates())) {
                sb.append("Coordinates: " + targetSelected.getCoordinates()).append("\n");
                coordsText = targetSelected.getCoordinates();
            } else {
                sb.append("Coordinates: EMPTY").append("\n");
            }

            if (!Strings.isNullOrEmpty(targetSelected.getSearchAttributeValue())) {
                sb.append("Search Attrib: " + targetSelected.getSearchAttributeValue())
                        .append("\n");
                searchAttribValueText = targetSelected.getSearchAttributeValue();
            } else {
                sb.append("Search Attrib: No Defined").append("\n");
            }

            sb.append("Named: " + nameDefined).append("\n");
            sb.append("All Attributes Found: ").append("\n");
            if (targetSelected.getAttributeData() != null) {
                for (AttributeData attribute : targetSelected.getAttributeData()) {
                    sb.append("->  ")
                            .append(attribute.getName().trim() + "="
                                    + attribute.getValue().trim())
                            .append("\n");
                }
            }

            uiThreadDispatcher.execute(() -> {
                setScannerStatus(sb.toString(), "info");
            });

            //                contentPane.requestLayout();

            applyTestActionDefaults(targetSelected);
        }
        if (performActions.getCurrentDriver() != null) {
            performActions.getCurrentDriver().switchTo().defaultContent();
        }
    }

    /**
     * Current page URL regardless of driver backend: Selenium when present, otherwise the
     * single Playwright browser. Empty string when no browser is open.
     */
    private String currentPageUrl() {
        return scannerBrowserRuntime.currentPageUrl();
    }

    /**
     * Visual sweep: briefly pulses a red outline on each scanned element so
     * the user can see which nodes the scanner picked up. Runs entirely in the
     * browser (one executeScript call) to avoid per-element WebDriver RTTs.
     * Best-effort — failures are swallowed since this is purely cosmetic.
     */
    /**
     * Run a page script on whichever browser backend is active: Selenium when a driver is present,
     * otherwise the single Playwright browser. The script is Selenium-style (uses {@code arguments[i]}
     * and may have a top-level {@code return}); for Playwright it is wrapped as an arrow function whose
     * single array parameter replaces {@code arguments}. Failures are swallowed (best-effort injection).
     */
    private void runInjectionScript(String seleniumScript, Object... args) {
        WebDriver d = performActions.getCurrentDriver();
        if (d != null) {
            try {
                ((JavascriptExecutor) d).executeScript(seleniumScript, args);
            } catch (Exception ignore) {
                // best-effort
            }
            return;
        }
        if (currentARWebDriver == null || !currentARWebDriver.isPlaywrightEnabled()) {
            return;
        }
        // Playwright takes one arg to an arrow fn; rename arguments[i] -> pwArgs[i].
        String pwScript = "(pwArgs) => { " + seleniumScript.replace("arguments[", "pwArgs[") + " }";
        try {
            currentARWebDriver.getPlaywrightDriver().evaluate(pwScript, java.util.Arrays.asList(args));
        } catch (Exception ignore) {
            // best-effort
        }
    }

    private void flashFoundElements(WebDriver driver, List<ElementDTO> elements) {
        if (elements == null || elements.isEmpty()) return;

        java.util.List<String> xPaths = new java.util.ArrayList<>(elements.size());
        for (ElementDTO el : elements) {
            String xp = el != null ? el.getXPath() : null;
            if (xp != null && !xp.isBlank()) xPaths.add(xp);
        }
        if (xPaths.isEmpty()) return;

        String js = "(function(xs, holdMs){"
                + "  if(!xs||!xs.length) return;"
                + "  var style=document.createElement('style');"
                + "  style.id='__mp_scan_hl';"
                + "  style.textContent='.__mp_scan_flash{outline:4px solid #ff1744 !important;"
                + "    outline-offset:3px;"
                + "    box-shadow:0 0 0 6px rgba(255,23,68,.55), 0 0 18px 4px rgba(255,23,68,.8) !important;"
                + "    background-color:rgba(255,23,68,.12) !important;"
                + "    transition:outline .08s ease, box-shadow .08s ease, background-color .08s ease;}';"
                + "  document.head.appendChild(style);"
                + "  var lo=0, hi=xs.length-1, done=0, workers=0;"
                + "  function cleanup(){ if(done>=2){ style.remove(); } }"
                + "  function flash(idx, next){"
                + "    if(idx<0||idx>=xs.length||lo>hi){ done++; cleanup(); return; }"
                + "    var r=document.evaluate(xs[idx],document,null,XPathResult.FIRST_ORDERED_NODE_TYPE,null);"
                + "    var el=r&&r.singleNodeValue;"
                + "    if(!el||!el.classList){ return next(); }"
                + "    try{ el.scrollIntoView({behavior:'smooth',block:'center',inline:'center'}); }catch(e){}"
                + "    el.classList.add('__mp_scan_flash');"
                + "    setTimeout(function(){"
                + "      el.classList.remove('__mp_scan_flash');"
                + "      setTimeout(next, 20);"
                + "    }, holdMs);"
                + "  }"
                + "  function stepFwd(){"
                + "    if(lo>hi){ done++; cleanup(); return; }"
                + "    var idx=lo++;"
                + "    flash(idx, stepFwd);"
                + "  }"
                + "  function stepBwd(){"
                + "    if(lo>hi){ done++; cleanup(); return; }"
                + "    var idx=hi--;"
                + "    flash(idx, stepBwd);"
                + "  }"
                + "  workers=2;"
                + "  stepFwd();"
                + "  setTimeout(stepBwd, Math.max(40, holdMs/3));"
                + "  if(xs.length<2){ done++; cleanup(); }"
                + "})(arguments[0], arguments[1]);";
        runInjectionScript(js, xPaths, 160);
    }

    public void revertCloneInjections(WebDriver driver) {
        runInjectionScript("window.revertCloneInjections();");
        runInjectionScript("let elem = document.getElementById('Martini-Is-Awesome'); if (elem) { elem.remove(); }");
    }

    public void revertPickInjections(WebDriver driver) {
        runInjectionScript("window.revertPickInjections();");
        runInjectionScript("let elem = document.getElementById('Martini-Is-Awesome'); if (elem) { elem.remove(); }");
    }

    private void revertHoverPickInjections(WebDriver driver) {
        runInjectionScript("window.revertHoverPickInjections();");
    }

    private synchronized boolean recallJob() {
        return recallJobExecutionId() > 0L;
    }

    private final class PanePreLaunchExecutionOperations implements ScannerPreLaunchExecutionTask.Operations {
        @Override
        public boolean executeJob() {
            return ScannerRuntimeBackend.this.executeJob();
        }

        @Override
        public void reportExecutionError(Throwable error) {
            log.error("executeJob() terminated with exception: {}", error.getMessage(), error);
        }

        @Override
        public void completeExecution(long executionId, boolean executionPassed) {
            scannerTestRunExecutionState.completeExecution(executionId, executionPassed);
        }

        @Override
        public void clearActiveExecution(long executionId) {
            scannerTestRunExecutionState.clearActiveExecution(executionId);
        }

        @Override
        public void markNotRunning() {
            isJobRunning.set(false);
        }

        @Override
        public void stopScreenshotLoop() {
            ScannerRuntimeBackend.this.stopScreenshotLoop();
        }

        @Override
        public void reenableLaunchButton() {
            ScannerRuntimeBackend.this.reenableLaunchButton();
        }
    }

    private final class PanePreLaunchWindowBookkeepingOperations implements ScannerPreLaunchWindowBookkeeping.Operations {
        @Override
        public Integer currentWindowHandleCount() {
            return performActions.getCurrentDriver() == null
                    ? null
                    : Integer.valueOf(performActions.getCurrentDriver().getWindowHandles().size());
        }

        @Override
        public int knownWindowHandleCount() {
            return performActions.windowHandlesList.size();
        }

        @Override
        public void updateWindowHandlesList() {
            performActions.updateWindowHandlesList();
        }

        @Override
        public void updateButtonState() {
            ScannerRuntimeBackend.this.updateButtonState();
        }
    }

    private final class PanePreLaunchExecutionCoordinatorOperations
            implements ScannerPreLaunchExecutionCoordinator.Operations {
        @Override
        public void info(String message, Object... args) {
            log.info(message, args);
        }

        @Override
        public void error(String message, Object... args) {
            log.error(message, args);
        }
    }

    private final class PaneExecutionPreflightOperations
            implements ScannerExecutionPreflightMonitor.Operations {
        @Override
        public void info(String message, Object... args) {
            log.info(message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            log.warn(message, args);
        }
    }

    private RunScope currentPreLaunchRunScope() {
        BlockOptions selected = selectedBlockOption;
        if (!scannerBlockOptionSelectionService.isRealBlock(selected)
                || selected.getBlockId() == null
                || selected.getBlockId() <= 0) {
            return RunScope.all();
        }
        return RunScope.fromBlock(selected.getBlockId());
    }

    private final class PaneTestRunStartupOperations implements ScannerTestRunStartupPreparation.Operations {
        @Override
        public long activeExecutionId() {
            return scannerTestRunExecutionState.activeExecutionId();
        }

        @Override
        public boolean isExecutionComplete(long executionId) {
            return ScannerRuntimeBackend.this.isTestRunExecutionComplete(executionId);
        }

        @Override
        public boolean isJobRunning() {
            return ScannerRuntimeBackend.this.isJobRunning.get();
        }

        @Override
        public void ensureDriver() {
            if (currentARWebDriver == null) {
                currentARWebDriver = ARWebDriver.getInstance();
            }
        }

        @Override
        public void setCurrentBotJob(BotJobLoadDTO botJob) {
            currentBotJob = botJob;
        }

        @Override
        public void setInterceptBotJob(boolean intercept) {
            performActions.setInterceptBotJob(intercept);
            ScannerRuntimeBackend.this.setInterceptBotJob(intercept);
        }

        @Override
        public void markNotRunning() {
            isJobRunning.set(false);
        }

        @Override
        public String resolveExcelBasePath() {
            return arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
        }

        @Override
        public void setExcelPath(String resolvedExcelPath) {
            excelPath = resolvedExcelPath;
        }

        @Override
        public void reportExcelPathError(Exception error) {
            log.error("TEST RUN â€” error resolving Excel path: {}", error.getMessage());
        }

        @Override
        public void setExecuteSpecificBlock(int blockIndex) {
            executeSpecificBlock = blockIndex;
        }

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {
            ScannerRuntimeBackend.this.runSingleBlock = runSingleBlock;
        }

        @Override
        public void clearFields() {
            ScannerRuntimeBackend.this.clearFields();
        }
    }

    private final class PaneTestRunBotJobOperations implements ScannerTestRunBotJobPreparation.Operations {
        @Override
        public ScannerPreLaunchPreparation.BotJobSelection loadCurrentBotJob(
                BotJobLoadDTO currentBotJob, String excelBasePath) {
            return scannerPreLaunchPreparation.loadCurrentBotJob(currentBotJob, excelBasePath);
        }

        @Override
        public void applySelection(ScannerPreLaunchPreparation.BotJobSelection selection) {
            currentBotJob = selection.botJob();
            currentBotJobName = selection.botJobName();
            excelPath = selection.excelPath();
        }

        @Override
        public HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId) {
            return performLists.getHomeUrlByBankId(homeBankingId, homeUrlId);
        }
    }

    private final class PaneTestRunExecutionStartOperations implements ScannerTestRunExecutionStart.Operations {
        @Override
        public boolean openBrowser() {
            return ScannerRuntimeBackend.this.openBrowserForTestRun();
        }

        @Override
        public void resetInstructionExecutionFlags() {
            scannerPreLaunchPreparation.resetInstructionExecutionFlags();
        }

        @Override
        public long recallJobExecutionId() {
            return ScannerRuntimeBackend.this.recallJobExecutionId();
        }

        @Override
        public boolean isJobRunning() {
            return ScannerRuntimeBackend.this.isJobRunning.get();
        }

        @Override
        public void setRunSingleBlock(boolean runSingleBlock) {
            ScannerRuntimeBackend.this.runSingleBlock = runSingleBlock;
        }
    }

    private final class PaneTestRunExcelOperations implements ScannerTestRunExcelPreparation.Operations {
        @Override
        public void setExtractedData(ExtractedData preparedData) {
            extractedData = preparedData;
        }
    }

    private final class PaneTestRunDefinitionLoadOperations implements ScannerTestRunDefinitionLoad.Operations {
        @Override
        public ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob) {
            ScannerPreLaunchPreparation.Result result =
                    scannerPreLaunchPreparation.loadDefinitions(currentBotJob);
            variableMetadataAvailable = result.variableLoadWarning() == null;
            return result;
        }

        @Override
        public void setExcelDataGoto(List<InstructionLoad> loadedExcelDataGoto) {
            excelDataGoto = loadedExcelDataGoto;
        }

        @Override
        public void setBlocksLoaded(List<BlockLoadDTO> loadedBlocks) {
            blocksLoaded = loadedBlocks;
        }
    }

    private final class PaneTestRunResultOperations implements ScannerTestRunResultHandler.Operations {
        @Override
        public void error(String message, Object... args) {
            log.error(message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            log.warn(message, args);
        }

        @Override
        public void info(String message, Object... args) {
            log.info(message, args);
        }

        @Override
        public void resetSingleBlock() {
            runSingleBlock = false;
        }
    }

    private final class PaneTestRunStopOperations implements ScannerTestRunStopper.Operations {
        @Override
        public boolean startupActive() {
            return testRunStartupActive.get();
        }

        @Override
        public long activeExecutionId() {
            return scannerTestRunExecutionState.activeExecutionId();
        }

        @Override
        public long lastSubmittedExecutionId() {
            return scannerTestRunExecutionState.lastSubmittedExecutionId();
        }

        @Override
        public long completedExecutionId() {
            return scannerTestRunExecutionState.completedExecutionId();
        }

        @Override
        public boolean requestStop(long executionId) {
            return scannerTestRunBrowserClosePolicy.requestStop(executionId);
        }

        @Override
        public String terminalOutcome(long executionId) {
            return scannerTestRunExecutionState.terminalState(executionId);
        }

        @Override
        public void resetSingleBlock() {
            runSingleBlock = false;
        }

        @Override
        public void requestIntercept() {
            performActions.setInterceptBotJob(true);
            setInterceptBotJob(true);
            executionPauseCoordinator.cancelExecution(scannerTestRunExecutionState.activeExecutionId());
        }

        @Override
        public void info(String message, Object... args) {
            log.info(message, args);
        }
    }

    private long recallJobExecutionId() {
        return scannerPreLaunchExecutionCoordinator.recallJobExecutionId();
    }

    /**
     * TEST RUN — run either all blocks from a selected starting point or ONE selected block through
     * the full pre-launch engine ({@link #executeJob()}) inside the single Playwright browser (no
     * Selenium browser, no external AR_Web_Engine.jar).
     *
     * <p>Mirrors the "Launch" button's preload sequence, but differs in three ways: (a) it opens
     * the Playwright driver itself (Launch assumes scanning already opened a browser); (b) it starts
     * at the selected block ({@code executeSpecificBlock}) and can stop after it ({@link
     * #runSingleBlock}); (c) it tolerates a missing Excel file by injecting the synthetic {@code
     * $EMPTY} row, since GEN FLOW navigation blocks carry no data.
     *
     * <p>Safe to invoke from a background worker thread — the actual job runs on
     * {@code executorServicePreLaunch} via {@link #recallJob()}.
     *
     * @param botJob           the bot job that owns the block
     * @param blockOrderNumber 1-based block order number of the block to run
     * @param endpointUrl      environment URL selected in the pane (informational; the run URL is
     *                         resolved from the loaded home-URL row, exactly like Launch)
     * @param runSingleBlock   when true, stop after the selected block; when false, continue through
     *                         every remaining block
     * @return the exact submitted execution ID, or {@code 0} when startup was rejected
     */
    public long submitTestRunBlockPlaywright(
            BotJobLoadDTO botJob, int blockOrderNumber, String endpointUrl, boolean runSingleBlock) {
        return submitTestRunBlockPlaywright(
                botJob, blockOrderNumber, endpointUrl, runSingleBlock, () -> false);
    }

    public synchronized long submitTestRunBlockPlaywright(
            BotJobLoadDTO botJob,
            int blockOrderNumber,
            String endpointUrl,
            boolean runSingleBlock,
            BooleanSupplier cancellationRequested) {
        testRunStartupActive.set(true);
        try {
            BooleanSupplier cancellation = cancellationRequested == null ? () -> false : cancellationRequested;
            ScannerTestRunPreparationFlow.Result result = scannerTestRunPreparationFlow.prepare(
                    botJob,
                    blockOrderNumber,
                    endpointUrl,
                    runSingleBlock,
                    () -> testRunStartupCancelled(cancellation));
            return scannerTestRunResultHandler.finish(result, endpointUrl);
        } finally {
            testRunStartupActive.set(false);
        }
    }

    @Override
    public long startTestRun(
            BotJobLoadDTO botJob,
            int blockOrderNumber,
            String endpointUrl,
            boolean runSingleBlock,
            BooleanSupplier cancellationRequested) {
        return submitTestRunBlockPlaywright(
                botJob, blockOrderNumber, endpointUrl, runSingleBlock, cancellationRequested);
    }

    private BotJobLoadDTO currentTestRunBotJob() {
        return currentBotJob;
    }

    private String currentTestRunExcelPath() {
        return excelPath;
    }

    /**
     * Compatibility wrapper for callers that only need the startup acceptance decision.
     *
     * @see #submitTestRunBlockPlaywright(BotJobLoadDTO, int, String, boolean)
     */
    public boolean testRunBlockPlaywright(
            BotJobLoadDTO botJob, int blockOrderNumber, String endpointUrl, boolean runSingleBlock) {
        return submitTestRunBlockPlaywright(botJob, blockOrderNumber, endpointUrl, runSingleBlock) > 0L;
    }

    private boolean testRunStartupCancelled(BooleanSupplier cancellationRequested) {
        if (!cancellationRequested.getAsBoolean()) return false;
        log.info("TEST RUN — startup cancellation accepted");
        cancelTestRunStartup();
        return true;
    }

    /** Cancels TEST RUN setup before an execution ID has been allocated. */
    public void cancelTestRunStartup() {
        scannerTestRunStopper.cancelStartup();
    }

    /**
     * STOP for TEST RUN — halts a running {@link #testRunBlockPlaywright} execution.
     *
     * <p>Sets the intercept flag so the executeJob loop breaks at its next checkpoint. The shared
     * Playwright browser, page, and session remain open for Page Scanner/OCR work and a later TEST
     * RUN. Safe to call from any thread.
     */
    public void stopTestRun() {
        stopTestRun(activeJobExecutionId.get());
    }

    public boolean stopTestRun(long expectedExecutionId) {
        return scannerTestRunStopper.stop(expectedExecutionId);
    }

    public long currentTestRunExecutionId() {
        return scannerTestRunStopper.currentExecutionId();
    }

    public boolean isTestRunExecutionComplete(long executionId) {
        return scannerTestRunStopper.isExecutionComplete(executionId);
    }

    @Override
    public boolean isTestRunComplete(long executionId) {
        return isTestRunExecutionComplete(executionId);
    }

    public String testRunExecutionTerminalState(long executionId) {
        return scannerTestRunStopper.terminalState(executionId);
    }

    @Override
    public String testRunTerminalOutcome(long executionId) {
        return testRunExecutionTerminalState(executionId);
    }

    private String currentPlaywrightUrl() {
        return scannerBrowserRuntime.currentPlaywrightUrl();
    }

    private void pauseAfterPlaywrightWebAction(
            InstructionLoad instruction, String action, boolean success, String urlBefore, String urlAfter) {
        scannerBrowserRuntime.pauseAfterPlaywrightWebAction(
                instruction == null ? null : instruction.getName(), action, success, urlBefore, urlAfter);
    }

    private void startScreenshotLoop() {
        scannerScreenshotLoop.start();
    }

    private void stopScreenshotLoop() {
        scannerScreenshotLoop.stop();
    }

    private final class PaneScreenshotLoopOperations implements ScannerScreenshotLoop.Operations {
        @Override
        public boolean isJobRunning() {
            return ScannerRuntimeBackend.this.isJobRunning.get();
        }

        @Override
        public void sendScreenshotIfAvailable() {
            // sendScreenshotToListener();
        }

        @Override
        public void reportScreenshotLoopError(Exception error) {
            log.warn("Error in screenshot loop", error);
        }
    }

    private void clearFields() {
        coordsText = "";
        setScannerStatus("Pre-Launch status: Ready", "info");
    }

    public void stop() throws Exception {
        // Cleanup tasks when the application stops
        executorServicePreLaunch.shutdown();
        try {
            if (!executorServicePreLaunch.awaitTermination(5, TimeUnit.SECONDS)) {
                executorServicePreLaunch.shutdownNow();
                if (!executorServicePreLaunch.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorServicePreLaunch.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void browserNotAttached() {
        log.error("Error: The Browser attached with this Web Scanner is Not Active");
        ScannerBrowserNotAttachedMessageService.Message message =
                scannerBrowserNotAttachedMessageService.message();
        performMessage.errorMessage(
                message.title(),
                message.header(),
                message.detail(),
                message.action(),
                message.cause(),
                message.timeoutSeconds());
    }

    private int handleGreaterThan(String value1, String value2) {
        return scannerValidationEvaluator.handleGreaterThan(value1, value2);
    }

    private int handleLessThan(String value1, String value2) {
        return scannerValidationEvaluator.handleLessThan(value1, value2);
    }

    private String finalLogMessage(String failedMessage, String resultActions) {
        return scannerValidationEvaluator.finalLogMessage(failedMessage, resultActions);
    }

    private String variableVoidDiagnostic(
            String action,
            InstructionLoad instruction,
            Integer variableId,
            String variableField,
            RuntimeVariableValue value) {
        String variable = variableId != null && variableId > 0
                ? "Variable #" + variableId
                : "Unbound variable";
        if (variableField != null && !variableField.isBlank()) {
            variable += " (" + variableField + ")";
        }
        return "IGNORED: "
                + action
                + " Instruction #"
                + instruction.getId()
                + " bypassed because "
                + variable
                + " is VOID ("
                + value.voidReason()
                + "). No producer value is available; execution continues.";
    }

    private static boolean isVariableRuntimeAction(String action) {
        return ARConstantsEngine.GET_VALUE.equalsIgnoreCase(action)
                || ARConstantsEngine.SET_VALUE.equalsIgnoreCase(action)
                || ARConstantsEngine.CHECK_VALUE.equalsIgnoreCase(action)
                || ARConstantsEngine.EXTRACT_FIELD.equalsIgnoreCase(action);
    }

    private static boolean isVariableProducerAction(String action) {
        return ARConstantsEngine.GET_VALUE.equalsIgnoreCase(action)
                || ARConstantsEngine.SET_VALUE.equalsIgnoreCase(action);
    }

    private static String safeThrowableMessage(Throwable error) {
        if (error == null) {
            return "Unknown runtime error";
        }
        String message = error.getMessage();
        return Strings.isNullOrEmpty(message)
                ? error.getClass().getSimpleName()
                : message;
    }

    private final class PaneValidationEvaluatorOperations implements ScannerValidationEvaluator.Operations {
        @Override
        public void warnInvalidNumericValue(String fieldName, String value) {
            logOperations.warn("Invalid numeric value for {}: {}", fieldName, value);
        }
    }

    private final class PaneBrowserRuntimeOperations implements ScannerBrowserRuntime.Operations {
        @Override
        public boolean hasSeleniumDriver() {
            return performActions.getCurrentDriver() != null;
        }

        @Override
        public String currentSeleniumUrl() {
            return performActions.getCurrentDriver().getCurrentUrl();
        }

        @Override
        public boolean isPlaywrightEnabled() {
            return currentARWebDriver != null && currentARWebDriver.isPlaywrightEnabled();
        }

        @Override
        public boolean hasOpenPlaywrightDriver() {
            return currentARWebDriver != null
                    && currentARWebDriver.getPlaywrightDriver() != null
                    && currentARWebDriver.getPlaywrightDriver().isOpen();
        }

        @Override
        public String currentPlaywrightUrl() {
            return currentARWebDriver.getPlaywrightDriver().currentUrl();
        }

        @Override
        public String navigationTimeProperty() {
            return arPropertyManager.getProperty(ARPropertyEnum.NAVIGATION_TIME);
        }

        @Override
        public void warnCurrentPageUrlFailed(String message) {
            log.warn("currentPageUrl failed: {}", message);
        }

        @Override
        public void logPlaywrightStep(
                String action,
                String instructionName,
                boolean success,
                boolean navigationChanged,
                String urlBefore,
                String urlAfter) {
            logOperations.info(
                    "Playwright step action={} instruction='{}' success={} navigationChanged={} urlBefore={} urlAfter={}",
                    action,
                    instructionName,
                    success,
                    navigationChanged,
                    urlBefore,
                    urlAfter);
        }

        @Override
        public void appendLog(String message, String style) {
            ScannerRuntimeBackend.this.appendLog(message, style);
        }
    }

    public void checkRunningProcess() {
        scannerRunningProcessCleanupService.cleanup(new PaneRunningProcessCleanupOperations());
    }

    private final class PaneRunningProcessCleanupOperations
            implements ScannerRunningProcessCleanupService.Operations {
        @Override
        public void clearCloneSelection() {
            cloneSelectionActive = false;
        }

        @Override
        public void enableLaunchAction() {
            enablePreLaunchAction();
        }

        @Override
        public void revertCloneInjections() {
            ScannerRuntimeBackend.this.revertCloneInjections(performActions.getCurrentDriver());
        }

        @Override
        public void revertHoverPickInjections() {
            ScannerRuntimeBackend.this.revertHoverPickInjections(performActions.getCurrentDriver());
        }

        @Override
        public boolean isJobRunning() {
            return isJobRunning.get();
        }

        @Override
        public void interceptBotJob() {
            ScannerRuntimeBackend.this.setInterceptBotJob(true);
        }
    }

    private void reenableLaunchButton() {
        enablePreLaunchAction();
    }

    private void enablePreLaunchAction() {
        preLaunchActionEnabled = true;
        logOperations.debug("Scanner Pre-Launch action enabled");
    }

    private FieldData updateMSGInstruction(FieldData msgInstruction, String failedMessage) {
        return scannerInstructionMessageService.prependFailure(msgInstruction, failedMessage);
    }

    public void setPayloadEmpty() {
        this.payloadEmpty = scannerEmptyPayloadService.buildDefault(
                this.currentBotJob,
                new PaneEmptyPayloadOperations());
    }

    private final class PaneEmptyPayloadOperations implements ScannerEmptyPayloadService.Operations {
        @Override
        public boolean hasBotJobs() {
            return !performLists.getListBotJob().isEmpty();
        }

        @Override
        public boolean hasComponentJobs() {
            return !performLists.getListBotJobComp().isEmpty();
        }

        @Override
        public List<BlockLoadDTO> botJobBlocks() {
            return performLists.getListBlock();
        }

        @Override
        public List<BlockLoadDTO> componentBlocks() {
            return performLists.getListBlockComp();
        }

        @Override
        public void loadBotJobBlocks(int botJobId) {
            performDataBase.loadBlocks(botJobId, "", "block");
        }

        @Override
        public void loadComponentBlocks(int homeBankingId) {
            performDataBase.loadBlocks(homeBankingId, "", "component_block");
        }
    }

    /**
     * Generates the CSV content as a formatted String.
     *
     * The first line contains the header row (column names),
     * prefixed with "0: ".
     *
     * Each subsequent line (when enabled) represents a data row,
     * prefixed with its sequential row number.
     *
     * The method appends the configured end-of-file marker
     * at the end of the generated content.
     *
     * @return the complete CSV content as a String
     */
    public String getCsvContent(CsvTable tableCSV) {
        return scannerCsvContentService.headerOnlyContent(tableCSV);
    }

    /** Returns true when the React/WebSocket scanner state has a real block selected. */
    public boolean isRealBlockSelectedForInsert() {
        return scannerBlockOptionSelectionService.isRealBlock(selectedBlockOption);
    }

    /**
     * Entry point used by the save-on-grid flow to ensure a valid block is
     * selected before executing {@code afterBlockReady}. If a real block is
     * already selected the runnable fires synchronously. If the backend has no
     * selection, the React create-block request opens in reactive mode and
     * {@code afterBlockReady} fires when the block is created.
     */
    public void ensureBlockSelectedOrPrompt(Runnable afterBlockReady) {
        if (isRealBlockSelectedForInsert()) {
            if (afterBlockReady != null) afterBlockReady.run();
            return;
        }
        openCreateBlockModal(afterBlockReady);
    }

    /**
     * Publishes the create-block request to React/WebSocket. Block creation
     * reuses the split-block plumbing, refreshes {@link PerformLists}, and
     * broadcasts {@code UPDATE_BLOCKS} so React sessions refresh.
     */
    private void openCreateBlockModal(Runnable afterCreate) {
        final boolean reactive = afterCreate != null;
        ScannerCreateBlockModalPresentationService.Presentation presentation =
                scannerCreateBlockModalPresentationService.presentation(reactive);

        List<BlockLoadDTO> existingSorted =
                scannerCreateBlockPlanner.sortedBlocksForBotJob(currentBotJob.getId(), performLists.getListBlock());

        List<String> positionOptions = scannerCreateBlockPlanner.positionOptions(existingSorted);
        Map<String, String> previews = new LinkedHashMap<>();
        for (String option : positionOptions) {
            int targetOrder = scannerCreateBlockPlanner.computeInsertOrderNumber(option, existingSorted);
            previews.put(option, scannerCreateBlockPlanner.buildCreateBlockPreview(targetOrder, existingSorted));
        }

        boolean sent = scannerDialogPublisher.createBlock(
                reactive,
                currentBotJob.getId(),
                currentBotJob.getHomeBankingId(),
                presentation,
                existingSorted,
                positionOptions,
                previews);
        if (!sent) {
            logOperations.info("Create block request was not shown because no React scanner session is available");
        }
    }

    private String selectedProfileSearchText() {
        return selectedElementScanProfile.searchText();
    }

    private void revertSearchTermsInjections(WebDriver driver) {
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("window.revertSearchInjections();");
        } catch (Exception ignore) {
        }
    }

    public void applyTestActionDefaults(TargetElement targetCheck) {
        ScannerActionDefaultsService.Decision decision =
                scannerActionDefaultsService.decide(targetCheck, isClickable(targetCheck.getElement()));
        testActionClick = decision.click();
        testActionInput = decision.input();
        testActionOutput = decision.output();
    }

    private String selectedTestAction() {
        return testActionClick
                ? ARConstants.CLICK
                : testActionInput ? ARConstants.INSERT : testActionOutput ? ARConstants.OUTPUT : ARConstants.OTHER;
    }

    private boolean isClickable(WebElement element) {
        try {
            List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
            boolean isClickableTag =
                    clickableTags.stream().anyMatch(t -> t.getValue().equals(element.getTagName()));
            List<WebElementAttributeTypeValueEnum> clickableValues =
                    WebElementAttributeTypeValueEnum.getClickableValues();
            boolean isClickableValue = clickableValues.stream()
                    .anyMatch(v -> v.getValue().equals(element.getAttribute(WebElementAttributeEnum.TYPE.getValue())));
            boolean isInputTag = element.getTagName().equals(WebElementTagNameEnum.INPUT.getValue());
            return (isClickableTag && !isInputTag) || (isInputTag && isClickableValue && isClickableTag);

        } catch (Exception ignore) {
        }
        return false;
        // Signal for Force Click or Not from the Target Definitions
    }

    public int createBlockIfNone(String blockTable, int whereId) {
        return scannerDefaultBlockService.createIfNone(blockTable, whereId, new PaneDefaultBlockOperations());
    }

    private final class PaneDefaultBlockOperations implements ScannerDefaultBlockService.Operations {
        @Override
        public ErrorMessage loadBlocks(int ownerId, String blockTable) {
            return performDataBase.loadBlocks(ownerId, null, blockTable);
        }

        @Override
        public boolean blocksEmpty() {
            return performLists.getListBlock().isEmpty();
        }

        @Override
        public ErrorMessage initiateBlock(
                String blockTable,
                int ownerId,
                String blockName,
                String blockDescription,
                int blockOrder,
                boolean forceOrder) {
            return performDataBase.initiateNewBlock(
                    blockTable,
                    ownerId,
                    blockName,
                    blockDescription,
                    blockOrder,
                    forceOrder);
        }

        @Override
        public List<Integer> createdBlockIds() {
            return performDataBase.getIdsBlockAfter();
        }

        @Override
        public void showOperationFailed(ErrorMessage error) {
            performMessage.errorMessageOperationFailed(error);
        }
    }

    public enum LocatorType {
        TAGNAME,
        ID,
        CLASSNAME,
        CSSSELECTOR,
        XPATH
    }

    public void updateHasAnyInput() {
        if (blocksLoaded == null) return;

        blocksLoaded.forEach(block -> {
            boolean hasInput = block.getInstructionLoad() != null
                    && block.getInstructionLoad().stream()
                            .anyMatch(instr -> instr.getActions() != null
                                    && instr.getActions().startsWith("I:"));

            block.setHasAnyInput(hasInput);
        });
    }

    private void updateRowStatusAndNotify(String color) {
        rowStatus.setColor(color);
        InstructionRealtimePublisher.getInstance().publishExecutionStatus(
                this.currentBotJob.getHomeBankingId(),
                sessionRowStatus,
                rowStatus.getInstructionId(),
                color);
    }

    /**
     * Lazily create the two shared {@link WebDriverWait} singletons on
     * {@link PerformActions}. They are required by every helper that calls
     * {@code waitForAction.until(...)} or {@code waitForPage.until(...)} —
     * running a DOM action before they exist NPEs deep inside Selenium.
     *
     * <p>Previously this block lived only in {@link #executeJob()}, which meant
     * a user could hit "Test Input" or "Test Click" on a scanned element
     * before ever starting a bot job and crash the test path. Extracted so
     * {@code testingActions} can share the same init.
     */
    private void ensureWaitsInitialized() {
        if (PerformActions.waitForPage != null && PerformActions.waitForAction != null) return;
        // Playwright-only mode has no Selenium driver; WebDriverWait(null,...) would throw.
        // The waits are only used by Selenium-path helpers, none reached in Playwright-only.
        if (performActions.getCurrentDriver() == null) return;
        String updateTimeout = arPropertyManager.getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
        String interactionTimeout = arPropertyManager.getProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC);
        WebDriver driver = performActions.getCurrentDriver();
        if (PerformActions.waitForPage == null) {
            PerformActions.waitForPage = new WebDriverWait(driver, Duration.ofSeconds(Integer.parseInt(updateTimeout)));
        }
        if (PerformActions.waitForAction == null) {
            PerformActions.waitForAction =
                    new WebDriverWait(driver, Duration.ofSeconds(Integer.parseInt(interactionTimeout)));
        }
    }

    private boolean executeJob() {
        // Reset the first-call log flag so the first searchListAsync injection is logged
        performListElements.resetFirstCallLog();
        rowStatus = new RowStatus();

        ensureWaitsInitialized();

        Labels.initializeLabelsInSpecLang("en");
        Properties labelsValue = Labels.labelsValue;

        // Playwright-only run (TEST RUN, no Selenium browser): skip Selenium element location
        // and let performWebActions act via tryPlaywrightWebAction using the instruction locator.
        final boolean pwOnly = currentARWebDriver != null && currentARWebDriver.isPlaywrightOnly();

        String baseLogString =
                currentBotJobName + ARConstantsEngine.FIELDS_SEPARATOR + labelsValue.getProperty(Labels.START);

        logLaunch.info(baseLogString);

        ExcelWriter.ExcelChain writerReport = new ExcelWriter(currentBotJobName, false).withPurpose("report");
        writerReport.insertReportHead();

        Set<String> mapIgnore = new HashSet<>();

        String mainMsg = "";
        boolean byPassNotFound = false;
        boolean byPassFlagLoop = false;
        boolean success = true;
        boolean stopAll = false;
        boolean firstRound = true;
        boolean anyFailure = false;
        boolean anyVariableVoid = false;
        String previousInstructionCompletionColor = "green";
        boolean alreadyLogged = false;
        long botJobStartTime = System.nanoTime();
        long totalExecutionTime = 0;
        String resultActions = "No instruction executed yet";
        String failedMessage = "";
        Map<String, String> dataExcel = null;
        Integer lastBlockOrderPushed = null;
        TargetElement matchScanned = null;
        TargetElement matchXPath = null;
        WebElement webElementFound = null;
        int navTime = getNavigationTimeSeconds();
        String newExcelFieldName = "";
        CsvTable currentTableCSV = null;

        //        List<InputInfo> inputs = new ArrayList<>();

        sessionRowStatus = ScannerWorkspaceSessions.BOT_JOB_TASKS; // + botJobId;

        List<VariableLoadDTO> variableDefinitions = performLists.getListVariable();
        final List<VariableLoadDTO> runtimeVariableDefinitions = variableDefinitions == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(variableDefinitions));
        final boolean runtimeVariableMetadataAvailable = variableMetadataAvailable;
        RuntimeVariableStore runtimeVariables = new RuntimeVariableStore(
                this.currentBotJob.getHomeBankingId(),
                this.currentBotJob.getId());
        //        Map<String, String> mapSavedLocators = new HashMap<>();

        Set<Integer> parentIdsForLoop = null;
        Set<Integer> allOutPuts = null;

        Map<String, List<Integer>> mapConditional = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Map<String, Integer> mapLoops = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Map<String, Integer> mapRefresh = new HashMap<>(); // <parentId:Limit Loops> -> <1|5 Times>
        Set<String> loopBlockActive = new HashSet<>();
        Map<String, Integer> loopBlockLimits = new HashMap<>();

        ARExecution.ConditionStatus currentCondition = ARExecution.ConditionStatus.NONE;
        ARExecution.ConditionStatus previousCondition;
        ARExecution.ConditionStatus progressCondition;
        ARExecution.DialogModal respModal = null;

        int exportIndex = 1;
        boolean webElementWork = false;

        if (extractedData.getNumberOfDataRows() > 0) {

            // Execute All Blocks starting from executeSpecificBlock if Defined
            currentBlockOrder = (executeSpecificBlock > -1) ? executeSpecificBlock : 0;
            int blockRecall = currentBlockOrder;
            int blockExcelGoto = blockRecall;

            // BLOCK DEFINED BY "DEFAULT" OR "EXCEL GOTO"
            if (!excelDataGoto.isEmpty() && !blocksLoaded.isEmpty()) {
                Integer parentBlockId =
                        excelDataGoto.get(excelDataGoto.size() - 1).getParentBlockId();
                excelDataGoto
                        .get(0)
                        .setParentBlockId(excelDataGoto.get(0).getBlockId()); // overwrite/fix using block table

                blockExcelGoto = performActions.getBlockOrderNumber(blocksLoaded, parentBlockId) - 1;

                // PREVENTID  LATGER DELETION
                if (blockExcelGoto < 0) {

                    Integer blockOrder = (excelDataGoto != null && !excelDataGoto.isEmpty())
                            ? excelDataGoto.get(0).getBlockOrderNumber()
                            : null;

                    blockExcelGoto = (blockOrder != null && blockOrder > 0) ? blockOrder : 1;
                }
            }

            int xExcelCurrentRow = 0;
            int xExcelDataSize = extractedData.getNumberOfDataRows();
            int runtimeValueRow = 0;
            mapOperators.clear();
            runtimeVariables.reconcileDefinitions(
                    runtimeVariableDefinitions,
                    runtimeVariableMetadataAvailable);
            currentColumnsCSV.clear();
            csvTables.clear();

            while (xExcelCurrentRow <= xExcelDataSize - 1 && !blocksLoaded.isEmpty() && !stopAll) {
                // Clear's Up Any Loop as Per New Line
                mapLoops.clear();
                mapRefresh.clear();

                if (firstRound) {
                    firstRound = false;
                    currentBlockOrder = blockRecall; // start blocks from initial for this row
                } else {
                    currentBlockOrder = blockExcelGoto; // start blocks from initial for this row
                }

                blockLoop:
                while (currentBlockOrder <= blocksLoaded.size() - 1 && !blocksLoaded.isEmpty() && !stopAll) {
                    long blockStartTime = System.nanoTime();
                    failedMessage = "";

                    currentCondition = ARExecution.ConditionStatus.NONE;
                    previousCondition = ARExecution.ConditionStatus.NONE;
                    progressCondition = ARExecution.ConditionStatus.NONE;

                    respModal = ARExecution.DialogModal.NONE;

                    int parentBlockCondition = -1;

                    BlockLoadDTO blockLoad = blocksLoaded.get(currentBlockOrder);

                    String blockName = blocksLoaded.get(currentBlockOrder).getName();
                    int blockOrder = blocksLoaded.get(currentBlockOrder).getBlockOrderNumber();
                    String blockReportName = "#" + blockOrder + " " + blockName;

                    int blockWait = blocksLoaded.get(currentBlockOrder).getWait() > 0
                            ? blocksLoaded.get(currentBlockOrder).getWait()
                            : 2;

                    boolean blockActive = blocksLoaded.get(currentBlockOrder).getActive();

                    if (blockActive) {

                        // Fire only when the block CHANGES, and only for ACTIVE blocks
                        if (lastBlockOrderPushed == null || !lastBlockOrderPushed.equals(currentBlockOrder)) {

                            // RESET instruction-level first-load flag
                            firstPageLoadDone = false;

                            if (!pwOnly) {
                                performActions.waitPage();
                            }
                            lastBlockOrderPushed = currentBlockOrder;

                            performLists.resetListElements();

                            logOperations.info("Total Target Elements: "
                                    + performLists.getListTargetElements().size());

                            // Inputs-only list with inferred labels
                            //                            inputs.clear();
                            //                            inputs =
                            //
                            //
                            // DomIntrospectionUtil.listAllRelevantElements(performActions.getCurrentDriver());
                        }

                        // Always loads ExcelWrite columns per block
                        ErrorMessage errorMessage = performDBEngine.loadAllColumnsExcelWrite(
                                "instruction", currentBotJob.getId(), blockLoad.getId());
                        if (errorMessage == null) {
                            setCurrentColumns(performLists.getExcelColumnNames());
                        }

                        // Resolve the complete persisted destination on every block. A block without
                        // Excel Export must not inherit the previous block's file or writer.
                        Optional<ExcelExportTarget> exportTarget =
                                ExcelExportTarget.decode(blockLoad.getExportFile());
                        currentTableCSV = null;
                        newExcelFieldName = "";
                        if (exportTarget.isPresent()) {
                            ExcelExportTarget target = exportTarget.get();
                            newExcelFieldName = target.path().toString();
                            delimiterCSV = target.delimiter();
                            String finalNewExcelFieldName = newExcelFieldName;
                            String finalDelimiter = delimiterCSV;
                            currentTableCSV = csvTables.computeIfAbsent(finalNewExcelFieldName, f ->
                                    new CsvTable(f, finalNewExcelFieldName, finalDelimiter));
                            currentTableCSV.addColumns(currentColumnsCSV);
                        }
                    }

                    // It Searches the Block That have finished the Loops to Avoid recursivity
                    if (!loopBlockActive.isEmpty()) {
                        for (String blocLoopKey : loopBlockActive) {
                            if (mapLoops.containsKey(blocLoopKey)) {
                                if (mapLoops.get(blocLoopKey) == 0) {
                                    stopAll = true;
                                    int limit = loopBlockLimits.get(blocLoopKey);

                                    FieldData msgBlock = new FieldData(blocLoopKey, "0");

                                    // Excel Report and Log
                                    performActions.logAndReport(
                                            currentCondition,
                                            true,
                                            true,
                                            blockStartTime,
                                            blockReportName,
                                            success,
                                            new String[] {ARConstantsEngine.GOTO},
                                            msgBlock,
                                            dataExcel,
                                            writerReport,
                                            "GOTO Limit Reached",
                                            blocLoopKey + " Reached: 0");

                                    msgBlock = new FieldData(
                                            String.format("Exit at Block Name: \"%s\"", blockLoad.getName()),
                                            ARConstantsEngine.EXIT);

                                    // Excel Report and Log
                                    performActions.logAndReport(
                                            currentCondition,
                                            true,
                                            true,
                                            blockStartTime,
                                            blockReportName,
                                            success,
                                            new String[] {ARConstantsEngine.EXIT},
                                            msgBlock,
                                            dataExcel,
                                            writerReport,
                                            "Stopping App",
                                            String.format("Exit at Block Name: \"%s\"", blockName));

                                    // performActions.gotoLimitExecution(limit, resultActions);

                                    continue blockLoop;
                                }
                            }
                        }
                    }

                    if (!blockActive) {
                        currentBlockOrder++;

                        FieldData msgBlock = new FieldData(
                                String.format("Ignore: \"%s\"", blockLoad.getName()), ARConstantsEngine.IGNORE);

                        // Excel Report and Log
                        performActions.logAndReport(
                                currentCondition,
                                true,
                                true,
                                blockStartTime,
                                blockReportName,
                                success,
                                new String[] {ARConstantsEngine.IGNORE},
                                msgBlock,
                                dataExcel,
                                writerReport,
                                "BLOCK IGNORED",
                                String.format("Block: \"%s\" is Inactive: ", blockName));

                        continue;
                    }

                    try {

                        FieldData msgBlock = new FieldData(blockLoad.getName(), ARConstantsEngine.EXCEL_BLOCK_HEADER);

                        // Block Header Format
                        performActions.logAndReport(
                                currentCondition,
                                true,
                                false,
                                blockStartTime,
                                blockReportName,
                                success,
                                new String[] {ARConstantsEngine.EXCEL_BLOCK_HEADER},
                                msgBlock,
                                null,
                                writerReport,
                                null,
                                null);

                        performActions.onHoldInSeconds(blockWait);

                        msgBlock = new FieldData(
                                String.format("Default Wait: \"%s\" ->  %d Seconds", blockLoad.getName(), blockWait),
                                ARConstantsEngine.HOLD);

                        // Excel Report and Log
                        performActions.logAndReport(
                                currentCondition,
                                true,
                                true,
                                blockStartTime,
                                blockReportName,
                                success,
                                new String[] {ARConstantsEngine.HOLD},
                                msgBlock,
                                dataExcel,
                                writerReport,
                                "BLOCK DEFAULT WAIT",
                                String.format("Block: \"%s\" Wait %s Seconds: ", blockName, blockWait));

                    } catch (Exception ex) {

                        logOperations.error(String.format("Error Wait Block for :\"%s\"", blockLoad.getName()));
                    }

                    allOutPuts = performActions.getAllOutputsPerBlock(
                            blocksLoaded.get(currentBlockOrder).getInstructionLoad());

                    // Step 1: Get all ParentIds For LOOPs Filter rows where actions = "REFRESH_LOOP" or "LOOP" on
                    // current
                    // Block
                    parentIdsForLoop = performActions.getParentIdsForLoop(
                            blocksLoaded.get(currentBlockOrder).getInstructionLoad());

                    // Step 2: Get all Conditional By parentId for Index Locator on current Block Relocate "IF",
                    // "ELSEIF",
                    // "ELSE", and "ENDIF"
                    mapConditional = performActions.getConditionIndexMapByParentId(blockLoad);

                    // Step 3: Get all Instructions Ids on current Block
                    int[] instructionIds = blockLoad.getInstructionLoad().stream()
                            .mapToInt(InstructionLoad::getId)
                            .toArray();

                    // Step 2: Filter rows where actions = "REFRESH_LOOP" or "LOOP" and collect into the map

                    //                mapLoops = performActions.getLoopAndRefreshLoops(
                    //                        blocksLoaded.get(currentBlockOrder).getBlockLoopInstructionLoadS());

                    //                executionTimes++;
                    boolean jumpGoto = false;
                    boolean forwardGoto = false;
                    int forwardGotoTargetIndex = -1;
                    boolean jumpLoop = false;
                    boolean jumpGotoError = false;
                    boolean jumpLoopError = false;
                    boolean refreshLoop = false;
                    boolean refreshOnly = false;

                    while (xExcelCurrentRow < extractedData.getNumberOfDataRows() && !stopAll) {
                        if (runtimeValueRow != xExcelCurrentRow) {
                            mapOperators.clear();
                            runtimeValueRow = xExcelCurrentRow;
                        }
                        failedMessage = "";
                        //                        tableCSV.clear();

                        //                    writerReport.insertBlockSeparation(blockLoad.getName());

                        dataExcel = extractedData.getRowFieldValues(xExcelCurrentRow);

                        int currentIndex = 0;

                        instructionLoop:
                        while (currentIndex < instructionIds.length && !stopAll) {
                            // Resets the success

                            stopAll = isInterceptBotJob();
                            if (stopAll) {
                                break;
                            }

                            success = true;
                            webElementWork = false;

                            long currentInstructionStartTime = System.nanoTime();

                            InstructionLoad currentInstruction =
                                    blockLoad.getInstructionLoad().get(currentIndex);

                            byPassFlagLoop = parentIdsForLoop.contains(currentInstruction.getId());

                            mainMsg =
                                    currentInstruction.getOptional() ? "optional instruction" : "mandatory instruction";

                            if (!currentInstruction.getInstructionActive()) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();
                                FieldData msgBlock = new FieldData(
                                        String.format("Ignore: \"%s\"", nameInstruc), ARConstantsEngine.IGNORE);

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.IGNORE},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "INSTRUCTION IGNORED",
                                        String.format("Instruction: \"%s\" is Inactive: ", nameInstruc));

                                currentIndex++;

                                continue;
                            }

                            // FIRST IMMEDIATE ATTEMPT TO LOCATE
                            if (isWebElementInstruction(currentInstruction)) {
                                webElementFound = immediateXPath(currentInstruction.getXpath());
                            }

                            if (!pwOnly) {
                                performActions.waitPage();
                            }
                            try {
                                if (navTime > 0) {
                                    performActions.onHoldInSeconds(navTime);
                                    logOperations.info("Navigation Time : {}", navTime);
                                }
                            } catch (Exception ignore) {
                            }

                            // Fire on FIRST page load OR when the INSTRUCTION changes
                            // and only for web-element work (INPUT / OUTPUT / CLICK / GET / SET)
                            if (isWebElementInstruction(currentInstruction) && webElementFound == null) {

                                Integer currentInstructionId = currentInstruction.getId();

                                if (!firstPageLoadDone
                                        || lastInstructionIdPushed == null
                                        || !lastInstructionIdPushed.equals(currentInstructionId)) {

                                    firstPageLoadDone = true;
                                    lastInstructionIdPushed = currentInstructionId;

                                    performLists.resetListElements();

                                    logOperations.info("Total Target Elements: "
                                            + performLists
                                                    .getListTargetElements()
                                                    .size());

                                    // runYourScript(currentInstructionId);
                                }
                            }

                            //                            mapSavedLocators.clear();
                            //
                            //                            // Loop through the instructionReferenceLoadDTOList
                            //                            if (currentInstruction.getReferenceLoadDTOList() != null) {
                            //                                for (ReferenceLoadDTO reference :
                            // currentInstruction.getReferenceLoadDTOList()) {
                            //                                    // Populate the map with referenceType as the key and
                            // value as the value
                            //                                    mapSavedLocators.put(reference.getReferenceType(),
                            // reference.getValue());
                            //                                }
                            //                            }

                            currentIndex++;

                            // Allow Re-Execute Instructions in Previous Blocks
                            //                        if (currentInstruction.getExecuted() == null ||
                            // !currentInstruction.getExecuted()) {
                            boolean execGetOrSet = false;
                            boolean execCheckValue = false;
                            boolean execPDFCheck = false;
                            boolean execCSVCheck = false;
                            boolean execOutPut = false;
                            boolean execExcellWrite = false;
                            boolean pauseOperation = false;
                            boolean nextEnter = false;
                            boolean swipeUp = false;
                            boolean swipeDown = false;
                            boolean variableVoidBypass = false;
                            boolean variableVoidObserved = false;

                            String xPathOperation = null;
                            String[] parentActions = null;
                            String parentField = null;
                            String parentFieldLoop = null;
                            String variableField = null;
                            //                            delimiterCSV = null;
                            String fieldName = null;
                            int parentId = InstructionGraph.executionParentId(currentInstruction);
                            Integer variableId = currentInstruction.getVariableId();

                            if (mapIgnore.contains(currentInstruction.getId() + "-" + currentInstruction.getName())) {
                                continue;
                            }

                            // webSocketSessionManager.sendMessageJson(int homeBankingId, String sessionId, String msg1,
                            // String msg2)
                            if (rowStatus.getInstructionId() == null) {
                                rowStatus.setInstructionId(currentInstruction.getId());
                                rowStatus.setColor("yellow"); // #fcba03 deep carmine yellow
                                InstructionRealtimePublisher.getInstance().publishExecutionStatus(
                                        this.currentBotJob.getHomeBankingId(),
                                        sessionRowStatus,
                                        rowStatus.getInstructionId(),
                                        "yellow");
                            } else {
                                // Previous
                                rowStatus.setColor(previousInstructionCompletionColor);
                                InstructionRealtimePublisher.getInstance().publishExecutionStatus(
                                        this.currentBotJob.getHomeBankingId(),
                                        sessionRowStatus,
                                        rowStatus.getInstructionId(),
                                        previousInstructionCompletionColor);
                                try {
                                    if (!pwOnly || navTime > 0) {
                                        Thread.sleep(300);
                                    }
                                } catch (Exception e) {
                                }
                                // Current
                                rowStatus.setInstructionId(currentInstruction.getId());
                                rowStatus.setColor("yellow"); // #fcba03 deep carmine yellow
                                InstructionRealtimePublisher.getInstance().publishExecutionStatus(
                                        this.currentBotJob.getHomeBankingId(),
                                        sessionRowStatus,
                                        rowStatus.getInstructionId(),
                                        "yellow");
                            }

                            //                        String[] operation =
                            // UtilsMethods.splitIfContains(instruction.getOperation(),
                            // ARConstants.ACTION_SPECIFICATIONS_SPLITTER);
                            String[] actions = currentInstruction
                                    .getActions()
                                    .split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER);
                            String[] operations = currentInstruction.getOperation() != null
                                    ? currentInstruction
                                            .getOperation()
                                            .split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER)
                                    : null;

                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.IF)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.ELSEIF)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.ELSE)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.ENDIF)) {
                                currentCondition = ARExecution.ConditionStatus.valueOf(actions[0]);
                                if (previousCondition.equals(ARExecution.ConditionStatus.NONE)) {
                                    previousCondition = currentCondition;
                                    parentBlockCondition = parentId;
                                } else if (!previousCondition.equals(
                                        currentCondition)) { // To Reset the Progress to the Next Block
                                    previousCondition = currentCondition;
                                }

                                // Conditions When Pass to any of then
                                if (progressCondition.equals(ARExecution.ConditionStatus.IF_PASSED)
                                        || progressCondition.equals(ARExecution.ConditionStatus.ELSEIF_PASSED)) {
                                    int jumpPassed = performActions.checkActionToJump(
                                            actions[0],
                                            progressCondition,
                                            mapConditional,
                                            parentBlockCondition,
                                            currentIndex);

                                    // Any Error
                                    if (jumpPassed < 0) {
                                        stopAll = true;
                                        continue blockLoop;
                                    }
                                    // Found Next Block
                                    if (jumpPassed > 0) {
                                        currentIndex = jumpPassed;
                                        // reset all Conditional
                                        currentCondition = ARExecution.ConditionStatus.NONE;
                                        progressCondition = ARExecution.ConditionStatus.NONE;
                                        continue instructionLoop;
                                    }
                                } else if (currentCondition.equals(ARExecution.ConditionStatus.ENDIF)) {
                                    currentCondition = ARExecution.ConditionStatus.NONE;
                                    previousCondition = ARExecution.ConditionStatus.NONE;
                                    progressCondition = ARExecution.ConditionStatus.NONE;
                                    parentBlockCondition = -1;
                                }
                                continue;
                            }

                            // Case for Inputs.
                            // Post-migration 2026-04-26, INSERT actions are always "I:<reference>".
                            // The legacy "I:E:<reference>" shape (ENTER after input) no longer
                            // exists in actions. ENTER is a bit in force_coordinates now.
                            //
                            // Block scoped lookup. Same canonical name in two blocks is now a
                            // valid scenario, each block owns its own column. The lookup key
                            // is displayKey, which is clientNamed when the user set an override
                            // and the canonical name otherwise. It matches what the Excel writer
                            // used as the column header. Falls back to the canonical name for
                            // legacy Excel files written before clientNamed was set.
                            // ExtractedData uses lenient matching, trim and case insensitive,
                            // on both block and field name, so cosmetic Excel edits do not break
                            // the lookup.
                            String valueInsert = "CHANGE ME";
                            if (actions[0].equals(ARConstantsEngine.INSERT)) {
                                String displayKey = currentInstruction.displayKey();
                                String currentBlockName = blockLoad.getName();
                                valueInsert =
                                        extractedData.getFieldValue(currentBlockName, displayKey, xExcelCurrentRow);
                                if (valueInsert == null) {
                                    valueInsert = extractedData.getFieldValue(
                                            currentBlockName, currentInstruction.getName(), xExcelCurrentRow);
                                }
                                if (valueInsert == null) {
                                    log.warn(
                                            "Excel lookup miss block [{}] displayKey [{}] canonical [{}] row {} availableBlocks {} fieldsInBlock {}",
                                            currentBlockName,
                                            displayKey,
                                            currentInstruction.getName(),
                                            xExcelCurrentRow,
                                            extractedData.getBlocks(),
                                            extractedData.getExtractedFields(currentBlockName));
                                }
                            }

                            FieldData msgInstruction = null;
                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.EXCEL_GOTO)) {

                                //                                currentIndex++;
                                continue instructionLoop;

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.NEXT_ROW)) {
                                // <currentId:blockId:blockOrderNumber:bockName>
                                xExcelCurrentRow++;

                                String bodyMsg = "Excel Data Calling Next Row: " + (xExcelCurrentRow + 1);

                                if (xExcelCurrentRow >= xExcelDataSize - 1) {
                                    xExcelCurrentRow = xExcelDataSize - 1;
                                    msgInstruction = new FieldData(
                                            "Excel Data (limit reached) keeping last row",
                                            String.valueOf(xExcelCurrentRow + 1));
                                    bodyMsg = "Excel Data (limit reached) keeping last row: " + xExcelCurrentRow + 1;
                                } else {
                                    msgInstruction =
                                            new FieldData("Excel Data next row", String.valueOf(xExcelCurrentRow + 1));
                                }

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.NEXT_ROW},
                                        msgInstruction,
                                        dataExcel,
                                        writerReport,
                                        "Excel Data Calling Next Row",
                                        bodyMsg);

                                //                                currentIndex++;
                                continue instructionLoop;

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.GOTO)) {
                                // <currentId:blockId:blockOrderNumber:bockName>
                                msgInstruction = performActions.getBlockDetailsById(blocksLoaded, currentInstruction);
                                if (msgInstruction == null) {
                                    msgInstruction = new FieldData("GO TO Block \"Unknown\"", "Unknown");
                                    success = false;
                                    jumpGotoError = true;
                                    jumpGoto = true;
                                } else if ((forwardGotoTargetIndex = InstructionGraph.gotoTargetIndex(msgInstruction))
                                        > currentBlockOrder) {
                                    // A forward GOTO is a branch, not a bounded backwards loop. A
                                    // count of 1 must enter the destination block once.
                                    jumpGoto = true;
                                    jumpGotoError = false;
                                    forwardGoto = true;
                                } else if (!mapLoops.containsKey(msgInstruction.getKey())) {
                                    jumpGoto = true;
                                    jumpGotoError = false;
                                    mapLoops.put(
                                            msgInstruction.getKey(),
                                            Integer.valueOf(msgInstruction.getValue())); // <id:orderId:blockName>
                                } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                    // Updates the msgInstruction
                                    jumpGoto = true;
                                    msgInstruction = new FieldData(
                                            msgInstruction.getKey(),
                                            String.valueOf(mapLoops.get(msgInstruction.getKey())));
                                }

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.LOOP)) {
                                // <currentId:parentId:parentName>
                                msgInstruction = performActions.getInstructionDetailsById(
                                        blocksLoaded.get(currentBlockOrder).getInstructionLoad(), currentInstruction);

                                if (msgInstruction == null) {
                                    msgInstruction = new FieldData("Jump To Parent \"Unknown\"", "Unknown");
                                    success = false;
                                } else if (!mapLoops.containsKey(msgInstruction.getKey())) {
                                    jumpLoopError = false;
                                    String[] parts = msgInstruction.getValue().split(":"); // Split by ':'
                                    mapLoops.put(msgInstruction.getKey(), Integer.valueOf(parts[1])); // Loop Times
                                    mapRefresh.put(msgInstruction.getKey(), Integer.valueOf(parts[0])); // Wait Time
                                } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                    // Updates the msgInstruction
                                    msgInstruction = new FieldData(
                                            msgInstruction.getKey(),
                                            String.valueOf(mapLoops.get(msgInstruction.getKey())));
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.REFRESH_LOOP)) {
                                msgInstruction = performActions.getInstructionDetailsById(
                                        blocksLoaded.get(currentBlockOrder).getInstructionLoad(), currentInstruction);
                                if (msgInstruction == null) {
                                    msgInstruction = new FieldData("Jump To Parent \"Unknown\"", "Unknown");
                                    success = false;
                                } else if (!mapLoops.containsKey(msgInstruction.getKey())) {
                                    jumpLoopError = false;
                                    String[] parts = msgInstruction.getValue().split(":"); // Split by ':'
                                    mapLoops.put(msgInstruction.getKey(), Integer.valueOf(parts[1])); // Loop Times
                                    mapRefresh.put(msgInstruction.getKey(), Integer.valueOf(parts[0])); // Wait Time
                                } else if (mapLoops.containsKey(msgInstruction.getKey())) {
                                    // Updates the msgInstruction
                                    // Refresh Loop  <5:5> <WAIT:LOOP>
                                    String updMsg = mapRefresh.get(msgInstruction.getKey()) + ":"
                                            + mapLoops.get(msgInstruction.getKey());
                                    msgInstruction = new FieldData(msgInstruction.getKey(), updMsg);
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.GET_VALUE)) {
                                // GET is relationship-authored. Its operation text is retained only
                                // for rollback/audit and may describe an obsolete Web Element or
                                // Variable, so execution diagnostics use the authoritative IDs.
                                msgInstruction = new FieldData(
                                        currentInstruction.getName(),
                                        "(Parent ID " + parentId + ") -> Variable ID "
                                                + (variableId == null ? "Missing" : variableId));
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.SET_VALUE)) {
                                msgInstruction = new FieldData(
                                        currentInstruction.getName(),
                                        (currentInstruction.getOperation() != null
                                                ? "(" + parentId + ")-" + operations[0] + ":" + operations[1]
                                                : (actions[0].equalsIgnoreCase(ARConstantsEngine.INSERT))
                                                        ? valueInsert
                                                        : ""));
                            } else {
                                msgInstruction = new FieldData(
                                        "(" + currentInstruction.getId() + ")-" + currentInstruction.getName(),
                                        (currentInstruction.getOperation() != null
                                                ? currentInstruction.getOperation()
                                                : (actions[0].equalsIgnoreCase(ARConstantsEngine.INSERT))
                                                        ? valueInsert
                                                        : ""));
                            }

                            resultActions = performActions.actionResultMessage(blockName, actions, msgInstruction);

                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.PAUSE)) {
                                pauseOperation = true;
                                ExecutionPauseCoordinator.Decision pauseDecision = executionPauseCoordinator.pause(
                                        currentBotJob.getId(),
                                        activeJobExecutionId.get(),
                                        blockLoad.getName(),
                                        currentInstruction.getName(),
                                        this::isInterceptBotJob);
                                respModal = pauseDecision == ExecutionPauseCoordinator.Decision.CONTINUE
                                        ? ARExecution.DialogModal.OK
                                        : ARExecution.DialogModal.STOP;
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.NEXT_ENTER)) {
                                nextEnter = true;
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.SWIPE_UP)) {
                                swipeUp = true;
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.SWIPE_DOWN)) {
                                swipeDown = true;
                            }

                            if (actions[0].equalsIgnoreCase(ARConstantsEngine.LOOP)) {
                                parentFieldLoop =
                                        performActions.getInstructionParentField(currentInstruction, blockLoad);
                                if (parentField == null && parentFieldLoop == null) {
                                    parentFieldLoop = "Unknown parent";
                                    parentField = parentFieldLoop;
                                } else {
                                    parentField = parentFieldLoop;
                                }

                                parentFieldLoop = currentInstruction.getId() + ":" + parentId + ":" + parentFieldLoop;

                                if (mapLoops.containsKey(parentFieldLoop)) {
                                    int currentLoop = mapLoops.get(parentFieldLoop);
                                    if (currentLoop > 0) {
                                        jumpLoop = true;
                                        refreshLoop = false;
                                    } else if (forwardGoto) {
                                        currentBlockOrder = forwardGotoTargetIndex;
                                        currentInstruction.setExecuted(true);
                                        failedMessage = "";
                                        success = true;

                                        performActions.logAndReport(
                                                currentCondition,
                                                true,
                                                true,
                                                currentInstructionStartTime,
                                                blockReportName,
                                                true,
                                                actions,
                                                msgInstruction,
                                                dataExcel,
                                                writerReport,
                                                mainMsg,
                                                finalLogMessage(failedMessage, resultActions));
                                        continue blockLoop;
                                    } else {

                                        jumpLoop = false;
                                        refreshLoop = false;

                                        continue;
                                    }

                                } else {
                                    jumpLoopError = true;
                                }

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.REFRESH_ONLY)) {
                                refreshOnly = true;
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.REFRESH_LOOP)) {
                                parentFieldLoop =
                                        performActions.getInstructionParentField(currentInstruction, blockLoad);
                                if (parentField == null && parentFieldLoop == null) {
                                    parentFieldLoop = "Unknown parent";
                                    parentField = parentFieldLoop;
                                } else {
                                    parentField = parentFieldLoop;
                                }

                                parentFieldLoop = currentInstruction.getId() + ":" + parentId + ":" + parentFieldLoop;

                                if (mapLoops.containsKey(parentFieldLoop)) {
                                    int currentLoop = mapLoops.get(parentFieldLoop);
                                    if (currentLoop > 0) {
                                        jumpLoop = true;
                                        refreshLoop = true;
                                    } else {

                                        jumpLoop = false;
                                        refreshLoop = false;

                                        continue;
                                    }

                                } else {
                                    jumpLoopError = true;
                                }
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.GET_VALUE)
                                    || actions[0].equalsIgnoreCase(ARConstantsEngine.SET_VALUE)) {

                                execGetOrSet = true;

                                xPathOperation = performActions.getXPathInstruction(currentInstruction, blockLoad);
                                String actionsParent =
                                        performActions.getInstructionParentActions(currentInstruction, blockLoad);
                                parentActions = actionsParent != null
                                        ? actionsParent.split(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER)
                                        : null;

                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(
                                                currentInstruction,
                                                runtimeVariableDefinitions);

                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.OUTPUT)) {
                                execOutPut = true;
                                fieldName = currentInstruction.getId() + "-" + currentInstruction.getName();
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.CHECK_VALUE)) {
                                execCheckValue = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(
                                                currentInstruction,
                                                runtimeVariableDefinitions);
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.PDF_CHECK)) {
                                execPDFCheck = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(
                                                currentInstruction,
                                                runtimeVariableDefinitions);
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.CSV_CHECK)) {
                                execCSVCheck = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(
                                                currentInstruction,
                                                runtimeVariableDefinitions);
                            } else if (actions[0].equalsIgnoreCase(ARConstantsEngine.EXTRACT_FIELD)) {
                                execExcellWrite = true;
                                parentField = performActions.getInstructionParentField(currentInstruction, blockLoad);
                                variableField =
                                        performActions.getInstructionVariableField(
                                                currentInstruction,
                                                runtimeVariableDefinitions);
                                //                                if (delimiterCSV == null) {
                                //                                    delimiterCSV =
                                // performActions.getInstructionVariableDelimiter(
                                //                                            currentInstruction, variablesLoaded);
                                //                                }
                            }

                            try {
                                if (jumpGoto) {

                                    if (jumpGotoError) {
                                        success = false;
                                        failedMessage = "Failed: GO TO ";
                                        resultActions = performActions.blockGotoFailed(resultActions);
                                    } else {
                                        if (!loopBlockActive.contains(msgInstruction.getKey())) {
                                            loopBlockActive.add(msgInstruction.getKey());
                                            loopBlockLimits.put(
                                                    msgInstruction.getKey(),
                                                    Integer.valueOf(msgInstruction.getValue()));
                                        }
                                        int repeat = mapLoops.get(msgInstruction.getKey()) - 1;
                                        if (repeat > 0) {
                                            mapLoops.put(msgInstruction.getKey(), repeat);
                                            try {

                                                String[] parts =
                                                        msgInstruction.getKey().split(":");
                                                int blockOrderNumber = Integer.parseInt(parts[2]);

                                                currentBlockOrder = blockOrderNumber - 1;
                                                currentInstruction.setExecuted(true);

                                                failedMessage = "";
                                                success = true;

                                            } catch (Exception ex) {
                                                failedMessage = "Failed: GO TO ";
                                                msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);

                                                success = false;

                                                resultActions = performActions.blockGotoFailed(resultActions);
                                            }

                                            FieldData currentPair = new FieldData(
                                                    msgInstruction.getKey(),
                                                    String.valueOf(mapLoops.get(msgInstruction.getKey())));

                                            // Excel Report and Log
                                            performActions.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    actions,
                                                    currentPair,
                                                    dataExcel,
                                                    writerReport,
                                                    mainMsg,
                                                    finalLogMessage(failedMessage, resultActions));

                                            if (success) {
                                                continue blockLoop;
                                            } else {
                                                stopAll = true;
                                                if (stopAll) {
                                                    continue blockLoop;
                                                }
                                            }

                                        } else {
                                            mapLoops.put(msgInstruction.getKey(), repeat);
                                            continue blockLoop;
                                        }
                                    }

                                } else if (jumpLoop) {

                                    if (mapRefresh.containsKey(parentFieldLoop)) {
                                        int timerLoop = mapRefresh.get(parentFieldLoop);
                                        performActions.onHoldInSeconds(timerLoop);
                                    }

                                    if (mapLoops.containsKey(parentFieldLoop)) {

                                        int repeat = mapLoops.get(parentFieldLoop) - 1;
                                        String[] parts = parentFieldLoop.split(":");
                                        if (repeat > 0) {
                                            mapLoops.put(parentFieldLoop, repeat);

                                            logOperations.info(String.format(
                                                    "Loop to Parent :\"%s\" - %d Times",
                                                    parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                    mapLoops.get(parentFieldLoop)));

                                            if (refreshLoop) {

                                                String extraLog = performActions.actionResultMessage(
                                                        blockName,
                                                        new String[] {ARConstantsEngine.REFRESH_HOLD},
                                                        msgInstruction);

                                                performActions.performOtherActions(
                                                        byPassNotFound,
                                                        currentInstruction,
                                                        new String[] {ARConstantsEngine.REFRESH_HOLD});

                                                // Excel Report and Log
                                                performActions.logAndReport(
                                                        currentCondition,
                                                        true,
                                                        true,
                                                        currentInstructionStartTime,
                                                        blockReportName,
                                                        success,
                                                        new String[] {ARConstantsEngine.REFRESH_HOLD},
                                                        msgInstruction,
                                                        dataExcel,
                                                        writerReport,
                                                        mainMsg,
                                                        extraLog);

                                                // Refresh For REFRESH_LOOP
                                                extraLog = performActions.actionResultMessage(
                                                        blockName,
                                                        new String[] {ARConstantsEngine.REFRESH_ONLY},
                                                        msgInstruction);

                                                performActions.performOtherActions(
                                                        byPassNotFound,
                                                        currentInstruction,
                                                        new String[] {ARConstantsEngine.REFRESH_ONLY});

                                                // Excel Report and Log
                                                performActions.logAndReport(
                                                        currentCondition,
                                                        true,
                                                        true,
                                                        currentInstructionStartTime,
                                                        blockReportName,
                                                        success,
                                                        new String[] {ARConstantsEngine.REFRESH_ONLY},
                                                        msgInstruction,
                                                        dataExcel,
                                                        writerReport,
                                                        mainMsg,
                                                        extraLog);

                                                refreshLoop = false;
                                            }

                                            for (int x = 0; x < instructionIds.length; x++) {
                                                if (instructionIds[x] == parentId) {
                                                    currentIndex = x;
                                                    break; // Exit the loop once the value is found
                                                }
                                            }

                                            // Get Correct Updated Pair for REFRESH_LOOP ACTION
                                            FieldData currentPair = new FieldData(
                                                    msgInstruction.getKey(),
                                                    String.valueOf(mapLoops.get(msgInstruction.getKey())));

                                            // Excel Report and Log
                                            performActions.logAndReport(
                                                    currentCondition,
                                                    true,
                                                    true,
                                                    currentInstructionStartTime,
                                                    blockReportName,
                                                    success,
                                                    actions,
                                                    currentPair,
                                                    dataExcel,
                                                    writerReport,
                                                    mainMsg,
                                                    finalLogMessage(failedMessage, resultActions));

                                        } else {
                                            mapLoops.put(parentFieldLoop, repeat);
                                        }

                                        jumpLoop = false;
                                        refreshLoop = false;

                                        if (repeat > 0) {
                                            continue instructionLoop;
                                        } else {

                                            logOperations.info(String.format(
                                                    "IGNORING Loop to Parent :\"%s\" - %d Times",
                                                    parts[0] + "-(" + parts[1] + ") " + parts[2],
                                                    mapLoops.get(parentFieldLoop)));
                                            continue;
                                        }

                                    } else {
                                        resultActions = performActions.parentValueIsNotDefined(
                                                currentInstruction.getName(),
                                                "(" + parentId + ")-" + parentField,
                                                resultActions);

                                        success = false;
                                    }

                                } else if (refreshOnly) {

                                    performActions.performOtherActions(byPassNotFound, currentInstruction, actions);

                                    resultActions = "Refresh Current Web Page ->  inside Block :\""
                                            + blockLoad.getName() + "\"";

                                    refreshOnly = false;

                                } else if (actions[0].equals(ARConstantsEngine.HOLD)
                                        || actions[0].equals(ARConstantsEngine.QUIT)
                                        || actions[0].equals(ARConstantsEngine.SCREEN)
                                        || actions[0].equals(ARConstantsEngine.BACK)
                                        || actions[0].equals(ARConstantsEngine.REFRESH_ONLY)) {

                                    performActions.performOtherActions(byPassNotFound, currentInstruction, actions);

                                    if (actions[0].equals(ARConstantsEngine.QUIT)) {
                                        stopAll = true;
                                        success = true;
                                    }

                                } else if (!jumpGotoError
                                        && !jumpLoopError
                                        && !execGetOrSet
                                        && !execCheckValue
                                        && !execPDFCheck
                                        && !execCSVCheck
                                        && !execExcellWrite
                                        && !pauseOperation
                                        && !nextEnter
                                        && !swipeUp
                                        && !swipeDown) {

                                    webElementWork = true;

                                    // Block scoped, clientNamed aware extractFieldData. Resolves
                                    // the cell by block displayKey then by block canonical, so
                                    // two blocks with the same column name no longer collide,
                                    // and a renamed field with clientNamed set in React still
                                    // resolves to the right Excel column.
                                    FieldData fieldData = performActions.extractFieldData(
                                            extractedData,
                                            blockLoad.getName(),
                                            xExcelCurrentRow,
                                            currentInstruction,
                                            actions,
                                            currentInstruction.getDefaultValue(),
                                            currentInstruction.getCodified());

                                    //                                    webElementFound = null;
                                    boolean forceCoordinates = InputFlags.of(currentInstruction.getForceCoordinates())
                                            .hasForce();

                                    if (!isMobileApp) {

                                        if (isWebElementInstruction(currentInstruction)
                                                && webElementFound == null
                                                && !pwOnly) {
                                            try {
                                                performActions.waitPage();

                                                matchXPath = InstructionLoadMatcher.findMatchingTargetElementByXPath(
                                                        performLists.getListTargetElements(), currentInstruction);
                                                matchScanned = null;
                                                //                                            InputInfo match =
                                                // findMatchingInput(inputs, currentInstruction);

                                                if (matchXPath == null) {
                                                    matchScanned = InstructionLoadMatcher.findMatchingTargetElement(
                                                            performLists.getListTargetElements(), currentInstruction);

                                                    if (matchScanned != null) {
                                                        InstructionLoadUpdater.applyMatchToInstruction(
                                                                currentInstruction, matchScanned);

                                                        // SECOND IMMEDIATE ATTEMPT TO LOCATE
                                                        webElementFound = immediateXPath(matchScanned.getXPath());
                                                    }
                                                }

                                                // VERY IMPORTANT TO VALIDATE IF THE ELEMENT IS ON TEH PAGE FIRST
                                                //                                            if (matchXPath != null ||
                                                // matchScanned != null || match != null) {
                                                if (webElementFound == null) {
                                                    webElementFound = performActions.searchElement(
                                                            currentInstruction,
                                                            this.currentBotJob.getId(),
                                                            forceCoordinates,
                                                            byPassFlagLoop);
                                                }
                                            } catch (Exception ex) {
                                                success = false;
                                            }
                                        }
                                    } else {
                                        // Safely extract the first element ID (if present)
                                        //                                        Integer elementId =
                                        // Optional.ofNullable(splitDTO.getElementDetails())
                                        //                                                .filter(arr -> arr.length > 0)
                                        //                                                .map(arr -> arr[0])
                                        //                                                .map(ElementDTO::getId)
                                        //                                                .orElse(null);

                                        // Find matching instruction by variableId
                                        //                                        InstructionLoad matchingInstruction =
                                        // Optional.ofNullable(
                                        //
                                        // performLists.getListInstruction())
                                        //
                                        // .orElse(Collections.emptyList())
                                        //                                                .stream()
                                        //                                                .filter(i ->
                                        // Objects.equals(i.getId(), elementId))
                                        //                                                .findFirst()
                                        //                                                .orElse(null);

                                        // 2) Apply only non-empty values into splitDTO and elementDetails[0]
                                        //                                        if (matchingInstruction != null) {
                                        // >>> Add AttrData:* references into elementDetails.attributesData
                                        SplitDTO.applyAttrDataFromReferences(splitDTO, currentInstruction);

                                        SplitDTO.applyInstructionToSplit(splitDTO, currentInstruction);
                                        //                                        }

                                        // Already Maps List<TargetElement>
                                        // androidHelper.scanElementsWithCanonicalXmlOnly(androidDevice.getCurrentDriver());

                                        // webElementFound = androidDevice.searchElement(splitDTO, actions,
                                        // performLists.getListTargetElements());

                                        if (webElementFound == null) {
                                            appendLog(
                                                    currentInstruction.getName() + "- Not Found- using coordinates",
                                                    "warn");
                                            // androidDevice.executeAction(webElementFound, splitDTO, actions);
                                        }
                                    }

                                    if (webElementFound == null && forceCoordinates && !isMobileApp) {

                                        // Enter-after-input flag now lives in force_coordinates,
                                        // not in the actions string. See migration 2026-04-26.
                                        Boolean pressEnterAfter = InputFlags.of(
                                                        currentInstruction.getForceCoordinates())
                                                .hasEnter();
                                        if (actions[0].equalsIgnoreCase(ARConstantsEngine.VISUALIZE)
                                                || actions[0].equalsIgnoreCase(ARConstantsEngine.CLICK)
                                                || actions[0].equalsIgnoreCase(ARConstantsEngine.INSERT)) {

                                            List<WebElement> smartSearch = performActions.findBySmartLocator(
                                                    currentInstruction.getCssSelector());
                                            if (!smartSearch.isEmpty()) {
                                                success = performActions.executeActionsAtCoordinates(
                                                        "coordinates", fieldData, actions[0], pressEnterAfter);
                                            }
                                        }
                                    }

                                    byPassNotFound = byPassFlagLoop
                                            || !currentCondition.equals(ARExecution.ConditionStatus.NONE);

                                    if ((webElementFound != null || pwOnly) && success) {

                                        String playwrightUrlBefore = pwOnly ? currentPlaywrightUrl() : "";
                                        success = performActions.performWebActions(
                                                byPassNotFound,
                                                "coordinates",
                                                fieldData,
                                                currentInstruction,
                                                mapOperators,
                                                webElementFound,
                                                actions,
                                                isMobileApp,
                                                splitDTO);
                                        if (pwOnly) {
                                            pauseAfterPlaywrightWebAction(
                                                    currentInstruction,
                                                    actions[0],
                                                    success,
                                                    playwrightUrlBefore,
                                                    currentPlaywrightUrl());
                                        }

                                        if (execOutPut) {
                                            if (mapOperators.containsKey(fieldName)) {
                                                msgInstruction = new FieldData(fieldName, mapOperators.get(fieldName));
                                            } else {
                                                msgInstruction = new FieldData(fieldName, "TEXT OUTPUT NOT FOUND");
                                            }
                                        }
                                    }
                                    // Special Cases for Select Responses
                                    // It could be Improved the case
                                    if (resultActions.contains("FAIL")
                                            || (!pwOnly
                                                    && (performLists
                                                                    .getListTargetElements()
                                                                    .isEmpty()
                                                            || (matchXPath == null
                                                                    && matchScanned == null
                                                                    && webElementFound == null)
                                                            || (webElementFound == null && !forceCoordinates)))) {
                                        failedMessage = "Failed execution Web Element ";
                                        msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                        if (resultActions.contains("PASSED")) {
                                            resultActions = resultActions.replaceAll("PASSED", "FAIL");
                                        }
                                        success = false;

                                        if (performLists.getListTargetElements().isEmpty()) {
                                            String reason = performActions.buildMessageResult(
                                                    success, blockName, "TIME OUT", "Device timeout", "TIME OUT");
                                            appendLog("[TEST]" + reason, "error");
                                            stopAll = true;
                                        }
                                    } else if (resultActions != null && success) {
                                        failedMessage = "";
                                        currentInstruction.setExecuted(true);
                                    }

                                } else if (execGetOrSet) {
                                    // GET && SET Special Operators

                                    if (parentField != null && parentId != 0) {
                                        parentField = parentId + "-" + parentField;
                                    }
                                    boolean variableBindingAvailable =
                                            variableId != null
                                                    && variableId > 0;
                                    InstructionLoad variableTargetInstruction =
                                            blockLoad.getInstructionLoad().stream()
                                                    .filter(candidate ->
                                                            candidate.getId() != null
                                                                    && candidate.getId() == parentId)
                                                    .findFirst()
                                                    .orElse(null);
                                    if (!variableBindingAvailable
                                            && actions[0].equalsIgnoreCase(
                                                    ARConstantsEngine.GET_VALUE)) {
                                        RuntimeVariableValue voidValue =
                                                variableId != null && variableId > 0
                                                        ? runtimeVariables.read(variableId)
                                                        : RuntimeVariableValue.voidValue(
                                                                VoidReason.MISSING_BINDING);
                                        resultActions = variableVoidDiagnostic(
                                                actions[0],
                                                currentInstruction,
                                                variableId,
                                                variableField,
                                                voidValue);
                                        variableVoidBypass = true;
                                        success = true;
                                    } else if (!pwOnly
                                            && xPathOperation == null
                                            && actions[0].equalsIgnoreCase(ARConstantsEngine.GET_VALUE)) {
                                        runtimeVariables.markVoid(variableId, VoidReason.MISSING_PARENT);
                                        resultActions = variableVoidDiagnostic(
                                                actions[0],
                                                currentInstruction,
                                                variableId,
                                                variableField,
                                                runtimeVariables.read(variableId));
                                        variableVoidBypass = true;
                                        success = true;
                                    } else if (parentField == null) {
                                        runtimeVariables.markVoid(variableId, VoidReason.MISSING_PARENT);
                                        resultActions = variableVoidDiagnostic(
                                                actions[0],
                                                currentInstruction,
                                                variableId,
                                                variableField,
                                                runtimeVariables.read(variableId));
                                        variableVoidBypass = true;
                                        success = true;
                                    } else {
                                        // A producer attempt invalidates any earlier value before touching the page.
                                        // A failed GET/SET can therefore never leak a stale value to later consumers.
                                        if (variableBindingAvailable) {
                                            runtimeVariables.markVoid(
                                                    variableId,
                                                    VoidReason.PRODUCER_FAILED);
                                        }

                                        webElementFound = null;
                                        if (isMobileApp
                                                && variableTargetInstruction != null) {
                                            SplitDTO.applyAttrDataFromReferences(
                                                    splitDTO,
                                                    variableTargetInstruction);
                                            SplitDTO.applyInstructionToSplit(
                                                    splitDTO,
                                                    variableTargetInstruction);

                                            // webElementFound = androidDevice.searchElement(splitDTO, actions,
                                            // performLists.getListTargetElements());
                                        }

                                        resultActions = performActions.performOperatorActions(
                                                byPassNotFound,
                                                currentInstruction,
                                                variableTargetInstruction,
                                                xPathOperation,
                                                parentActions,
                                                actions[0],
                                                operations,
                                                parentField,
                                                variableField,
                                                variableId,
                                                runtimeVariables,
                                                mapOperators,
                                                webElementFound);

                                        RuntimeVariableValue producedValue =
                                                runtimeVariables.read(variableId);
                                        if ((resultActions != null
                                                        && resultActions.contains("FAIL"))
                                                || producedValue.isVoid()) {
                                            if (resultActions != null
                                                    && resultActions.contains("FAIL")) {
                                                runtimeVariables.markVoid(
                                                        variableId,
                                                        VoidReason.PRODUCER_FAILED);
                                                producedValue =
                                                        runtimeVariables.read(variableId);
                                            }
                                            resultActions = variableVoidDiagnostic(
                                                    actions[0],
                                                    currentInstruction,
                                                    variableId,
                                                    variableField,
                                                    producedValue);
                                            variableVoidBypass = true;
                                            success = true;
                                        } else {
                                            // Runtime memory is the canonical browser value. Keep
                                            // its exact raw text (including whitespace, currency
                                            // symbols, separators, and an empty String). Locale
                                            // conversion belongs only to a consumer performing an
                                            // explicit comparison/calculation and must never
                                            // overwrite the produced value.
                                            failedMessage = "";
                                            success = true;
                                        }
                                    }

                                } else if (execCheckValue) {
                                    // Check Validation Operator

                                    RuntimeVariableValue checkValue =
                                            runtimeVariables.read(variableId);
                                    if (checkValue.isVoid()) {
                                        resultActions = variableVoidDiagnostic(
                                                actions[0],
                                                currentInstruction,
                                                variableId,
                                                variableField,
                                                checkValue);
                                        variableVoidBypass = true;
                                        success = true;
                                    } else {
                                        //                                    fieldName = parentField;

                                        resultActions = "Check Value for " + String.join(" ", operations);
                                        boolean isOperationValid = false;
                                        String invalidValues = null;
                                        String actualVariableValue = checkValue.value();

                                        if (operations[1].equalsIgnoreCase("=")) {
                                            isOperationValid = actualVariableValue
                                                    .trim()
                                                    .equalsIgnoreCase(operations[2].trim());

                                        } else if (operations[1].equalsIgnoreCase(">")) {
                                            int resp = handleGreaterThan(
                                                    actualVariableValue.trim(),
                                                    operations[2].trim());
                                            if (resp == 1) {
                                                isOperationValid = true;
                                            } else if (resp == 0) {
                                                isOperationValid = false;
                                            } else {
                                                isOperationValid = false;
                                                invalidValues = "Invalid Numbers";
                                            }
                                        } else if (operations[1].equalsIgnoreCase("!=")) {
                                            isOperationValid = !actualVariableValue
                                                    .trim()
                                                    .equalsIgnoreCase(operations[2].trim());
                                        } else if (operations[1].equalsIgnoreCase("<")) {
                                            int resp = handleLessThan(
                                                    actualVariableValue.trim(),
                                                    operations[2].trim());
                                            if (resp == 1) {
                                                isOperationValid = true;
                                            } else if (resp == 0) {
                                                isOperationValid = false;
                                            } else {
                                                isOperationValid = false;
                                                invalidValues = "Invalid Numbers";
                                            }
                                        } else if (operations[1].equalsIgnoreCase("contains")) {
                                            // Case-insensitive substring match — consistent with "=" / "!="
                                            // which compare via equalsIgnoreCase above.
                                            String actual = actualVariableValue
                                                    .trim()
                                                    .toLowerCase();
                                            String expected =
                                                    operations[2].trim().toLowerCase();
                                            isOperationValid = actual.contains(expected);
                                        }

                                        if (isOperationValid) {
                                            currentInstruction.setExecuted(true);
                                            failedMessage = "";

                                            resultActions = performActions.buildValidationReason(
                                                    invalidValues,
                                                    parentField,
                                                    actualVariableValue,
                                                    operations[2].trim(),
                                                    resultActions, // lastInstructionExecuted
                                                    operations,
                                                    currentCondition,
                                                    byPassNotFound,
                                                    true,
                                                    blockName,
                                                    currentInstruction.getId(),
                                                    isOperationValid);

                                            appendLog("[TEST]" + resultActions, "info");
                                            alreadyLogged = true;

                                            success = true;
                                        } else {
                                            failedMessage = "Failed: Check Validation ";
                                            msgInstruction = updateMSGInstruction(msgInstruction, failedMessage);
                                            //                                            resultActions =
                                            // performActions.checkValidationFailed(
                                            //                                                    invalidValues,
                                            //                                                    parentField,
                                            //
                                            // mapOperators.get(variableField),
                                            //                                                    resultActions,
                                            //                                                    operations,
                                            //                                                    currentCondition,
                                            //                                                    byPassNotFound);

                                            resultActions = performActions.buildValidationReason(
                                                    invalidValues,
                                                    parentField,
                                                    actualVariableValue,
                                                    operations[2].trim(),
                                                    resultActions, // lastInstructionExecuted
                                                    operations,
                                                    currentCondition,
                                                    byPassNotFound,
                                                    true,
                                                    blockName,
                                                    currentInstruction.getId(),
                                                    isOperationValid);

                                            appendLog("[TEST]" + resultActions, "error");
                                            alreadyLogged = true;

                                            logOperations.error("Validation failed: {}", resultActions);

                                            success = false;
                                        }
                                    }

                                } else if (execCSVCheck || execPDFCheck) {
                                    String msgCSVPrefix = "CSV ";
                                    if (execPDFCheck) {
                                        msgCSVPrefix = "PDF ";
                                    }
                                    boolean validationEvaluated = false;
                                    boolean validationSourceMissing = false;
                                    List<String> missingValidationFields = new ArrayList<>();

                                    // If fieldsToValidate is null/empty => ignore (no log)
                                    Map<String, FieldsToValidate> fMap = splitDTO.getFieldsToValidate();
                                    if (fMap == null || fMap.isEmpty()) {
                                        // ignore
                                        resultActions = resultActions.replaceAll("PASSED", "IGNORED");
                                    } else {

                                        for (Map.Entry<String, FieldsToValidate> entry : fMap.entrySet()) {

                                            FieldsToValidate expectedField = entry.getValue();

                                            // Only run if parentField exists as a key. If not found => ignore (no log).
                                            if (expectedField == null || expectedField.getValue() == null) {
                                                // ignore
                                            } else {

                                                String parentFieldCSV = entry.getKey();

                                                String foundKey = null;
                                                if (allOutPuts != null && !allOutPuts.isEmpty()) {
                                                    for (Integer outId : allOutPuts) {
                                                        String k = outId + "-" + parentFieldCSV;
                                                        if (mapOperators.containsKey(k)) {
                                                            foundKey = k;
                                                            break;
                                                        }
                                                    }
                                                }

                                                if (foundKey == null) {
                                                    validationSourceMissing = true;
                                                    missingValidationFields.add(parentFieldCSV);
                                                } else {

                                                    String actualValue = mapOperators.get(foundKey);

                                                    // A produced empty String is valid Web data and can be
                                                    // compared with an expected empty value. Only absence is VOID.
                                                    if (actualValue == null) {
                                                        validationSourceMissing = true;
                                                        missingValidationFields.add(parentFieldCSV);

                                                    } else {
                                                        validationEvaluated = true;
                                                        // actual/current value on the web/app side

                                                        // expected value comes from
                                                        // splitDTO.fieldsToValidate[parentField].value
                                                        String expectedValue = expectedField.getValue();

                                                        // operator comes from your parsed operations array
                                                        String operator = operations[1];

                                                        String msgCSV = msgCSVPrefix + parentFieldCSV;
                                                        resultActions = "Check Value for " + parentFieldCSV;
                                                        ScannerValidationEvaluator.ValidationResult vr =
                                                                evaluateOperation(actualValue, operator, expectedValue);

                                                        if (vr.valid) {
                                                            currentInstruction.setExecuted(true);
                                                            failedMessage = "";
                                                            success = true;

                                                            resultActions = performActions.buildValidationReason(
                                                                    vr.invalidReason,
                                                                    msgCSV,
                                                                    actualValue, // actual/current web value
                                                                    expectedValue,
                                                                    resultActions,
                                                                    operations,
                                                                    currentCondition,
                                                                    byPassNotFound,
                                                                    true,
                                                                    blockName,
                                                                    currentInstruction.getId(),
                                                                    true);

                                                            appendLog("[TEST]" + resultActions, "info");
                                                            alreadyLogged = true;

                                                            logOperations.info(
                                                                    msgCSVPrefix
                                                                            + " Validation SUCCESS for field '{}': actual='{}' {} expected='{}'",
                                                                    parentFieldCSV,
                                                                    actualValue,
                                                                    operator,
                                                                    expectedValue);

                                                        } else {
                                                            failedMessage = "Failed: Check Validation ";
                                                            msgInstruction =
                                                                    updateMSGInstruction(msgInstruction, failedMessage);

                                                            resultActions = performActions.buildValidationReason(
                                                                    vr.invalidReason,
                                                                    msgCSV,
                                                                    actualValue, // actual/current web value
                                                                    expectedValue,
                                                                    resultActions,
                                                                    operations,
                                                                    currentCondition,
                                                                    byPassNotFound,
                                                                    true,
                                                                    blockName,
                                                                    currentInstruction.getId(),
                                                                    false);

                                                            appendLog("[TEST]" + resultActions, "error");
                                                            alreadyLogged = true;

                                                            logOperations.error(
                                                                    msgCSVPrefix
                                                                            + " Values Validation FAILED for field '{}': actual='{}' {} expected='{}'. Reason: {}",
                                                                    msgCSV,
                                                                    actualValue,
                                                                    operator,
                                                                    expectedValue,
                                                                    resultActions);

                                                            success = false;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (!validationEvaluated && validationSourceMissing) {
                                            resultActions = variableVoidDiagnostic(
                                                    actions[0],
                                                    currentInstruction,
                                                    variableId,
                                                    variableField,
                                                    RuntimeVariableValue.voidValue(
                                                            VoidReason.NO_PRODUCER_YET));
                                            variableVoidBypass = true;
                                            success = true;
                                        }
                                        if (!missingValidationFields.isEmpty()) {
                                            List<String> distinctMissingFields =
                                                    missingValidationFields.stream()
                                                            .distinct()
                                                            .toList();
                                            String visibleFields = distinctMissingFields.stream()
                                                    .limit(5)
                                                    .collect(Collectors.joining(", "));
                                            String extraFields = distinctMissingFields.size() > 5
                                                    ? " +" + (distinctMissingFields.size() - 5) + " more"
                                                    : "";
                                            String missingSourceDiagnostic = "IGNORED: "
                                                    + actions[0]
                                                    + " Instruction #"
                                                    + currentInstruction.getId()
                                                    + " has VOID source field(s): "
                                                    + visibleFields
                                                    + extraFields
                                                    + ". Available fields were still evaluated; execution continues.";
                                            logOperations.warn(missingSourceDiagnostic);
                                            if (success) {
                                                appendLog(
                                                        "[TEST]" + missingSourceDiagnostic,
                                                        "warn");
                                            }
                                            variableVoidObserved = true;
                                        }
                                    }
                                } else if (execExcellWrite) {
                                    // Excel Write Operator

                                    if (parentField == null) {
                                        RuntimeVariableValue voidValue = RuntimeVariableValue.voidValue(
                                                VoidReason.MISSING_PARENT);
                                        resultActions = variableVoidDiagnostic(
                                                actions[0],
                                                currentInstruction,
                                                variableId,
                                                variableField,
                                                voidValue);
                                        variableVoidBypass = true;
                                        success = true;
                                    } else {
                                        RuntimeVariableValue exportValue =
                                                runtimeVariables.read(variableId);
                                        if (exportValue.isVoid()) {
                                            resultActions = variableVoidDiagnostic(
                                                    actions[0],
                                                    currentInstruction,
                                                    variableId,
                                                    variableField,
                                                    exportValue);
                                            variableVoidBypass = true;
                                            success = true;
                                        } else {
                                            String actualVariableValue = exportValue.value();

                                            if (!Strings.isNullOrEmpty(newExcelFieldName)) {
                                                // Only create Columns if Have a file to write
                                                String webData = performActions.sanitizeValue(
                                                        actualVariableValue.trim());

                                                currentTableCSV.put(
                                                        xExcelCurrentRow,
                                                        parentField.trim(),
                                                        webData); // may add new columns later too
                                            }

                                            resultActions = performActions.messageExcel(
                                                    "Excel Write",
                                                    currentInstruction,
                                                    parentField,
                                                    variableField,
                                                    actualVariableValue,
                                                    blockName,
                                                    currentInstruction.getId(),
                                                    (currentTableCSV != null));

                                            performActions.onHoldForSeconds(null);

                                            if (resultActions != null
                                                    && resultActions.contains("PASSED")) {
                                                currentInstruction.setExecuted(true);
                                                failedMessage = "";
                                                success = true;
                                            } else {
                                                failedMessage = "Failed: Generate File -> Excel/CSV ";
                                                msgInstruction =
                                                        updateMSGInstruction(msgInstruction, failedMessage);
                                                updateRowStatusAndNotify("red"); // #FF3131 deep carmine red
                                                success = false;
                                            }
                                        }
                                    }
                                }

                            } catch (Throwable t) {
                                String action = actions.length > 0 ? actions[0] : "";
                                if (isVariableRuntimeAction(action)) {
                                    RuntimeVariableValue voidValue =
                                            RuntimeVariableValue.voidValue(VoidReason.EVALUATION_FAILED);
                                    if (isVariableProducerAction(action)
                                            && variableId != null
                                            && variableId > 0) {
                                        runtimeVariables.markVoid(
                                                variableId,
                                                VoidReason.PRODUCER_FAILED);
                                        voidValue = runtimeVariables.read(variableId);
                                    }
                                    resultActions = variableVoidDiagnostic(
                                            action,
                                            currentInstruction,
                                            variableId,
                                            variableField,
                                            voidValue);
                                    variableVoidBypass = true;
                                    success = true;
                                    failedMessage = "";
                                    logOperations.warn(
                                            "VARIABLE_STEP_BYPASSED instructionId={} action={} "
                                                    + "reason={} executionContinues=true",
                                            currentInstruction.getId(),
                                            action,
                                            safeThrowableMessage(t));
                                } else {
                                    success = false;

                                    String[] lines = safeThrowableMessage(t).split("\n");
                                    String msg1 = "";
                                    String msg2 = "";

                                    for (String line : lines) {
                                        if (Strings.isNullOrEmpty(msg1)) {
                                            msg1 = line;
                                        } else if (Strings.isNullOrEmpty(msg2)) {
                                            msg2 = line;
                                        }
                                    }

                                    String msg3 = resultActions;

                                    if (Strings.isNullOrEmpty(failedMessage)) {
                                        failedMessage = "Failed: General Execution ";
                                        msgInstruction =
                                                updateMSGInstruction(msgInstruction, failedMessage);
                                    }
                                    logOperations.error(
                                            "Error: {} - {} - {} - {}",
                                            resultActions,
                                            msg1,
                                            msg2,
                                            msg3);
                                }
                            }

                            if (variableVoidBypass) {
                                variableVoidObserved = true;
                            }
                            if (success && !alreadyLogged) {
                                if (resultActions.contains("IGNORED")) {
                                    appendLog("[TEST]" + resultActions, "warn");
                                } else {
                                    appendLog("[TEST]" + resultActions, "info");
                                }
                            } else if (!alreadyLogged) {
                                appendLog("[TEST]" + resultActions, "error");
                                anyFailure = true;
                            }
                            if (variableVoidObserved) {
                                anyVariableVoid = true;
                            }
                            previousInstructionCompletionColor =
                                    !success
                                            ? "red"
                                            : variableVoidObserved ? "yellow" : "green";

                            alreadyLogged = false;

                            if (variableVoidObserved) {
                                logLaunch.warn(String.join(
                                        ARConstants.FIELDS_SEPARATOR,
                                        "VOID",
                                        finalLogMessage(failedMessage, resultActions)));
                            } else {
                                printLog(finalLogMessage(failedMessage, resultActions), success);
                            }

                            // Here mark the Status of a progress Condition Fail or Success at the end of each Kind
                            // of Execution
                            if (variableVoidObserved
                                    && !currentCondition.equals(ARExecution.ConditionStatus.NONE)) {
                                // Unknown variable data is fail-closed for control flow: take the
                                // FALSE/ELSE path, but keep the runtime step warning-only.
                                progressCondition =
                                        performActions.updateProgressSuccess(false, currentCondition);
                            } else if (!jumpGotoError
                                    && !jumpLoopError
                                    && !currentCondition.equals(ARExecution.ConditionStatus.NONE)) {
                                progressCondition = performActions.updateProgressSuccess(success, currentCondition);
                                //                                continue instructionLoop;
                            } else {
                                progressCondition = ARExecution.ConditionStatus.NONE;
                            }

                            // Excel Report and Log
                            performActions.logAndReport(
                                    !byPassFlagLoop ? progressCondition : ARExecution.ConditionStatus.BY_PASS,
                                    !variableVoidObserved,
                                    !variableVoidObserved,
                                    currentInstructionStartTime,
                                    blockReportName,
                                    success,
                                    actions,
                                    msgInstruction,
                                    dataExcel,
                                    writerReport,
                                    mainMsg,
                                    finalLogMessage(failedMessage, resultActions));

                            failedMessage = "";

                            if (pauseOperation && respModal.equals(ARExecution.DialogModal.STOP)) {

                                scannerTestRunBrowserClosePolicy.requestStop(activeJobExecutionId.get());
                                performActions.setInterceptBotJob(true);
                                setInterceptBotJob(true);

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("STOP ALL PROCESSES: \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.PAUSE);

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.PAUSE},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "PAUSE -> STOP",
                                        String.format("STOP ALL CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                stopAll = true;
                                break;
                            }

                            if (nextEnter) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("Device : \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.NEXT_ENTER);

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.NEXT_ENTER},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "DEVICE -> NEXT/ENTER",
                                        String.format("NEXT/ENTER CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                continue;
                            } else if (swipeUp) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("Device : \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.SWIPE_UP);

                                int timesSwipe = 1;

                                String operation = currentInstruction.getOperation();
                                if (operation != null && !operation.trim().isEmpty()) {
                                    try {
                                        timesSwipe = Integer.parseInt(operation.trim());
                                    } catch (NumberFormatException ignored) {
                                        appendLog("Invalid swipe count: " + operation + ". Defaulting to 1.", "warn");
                                    }
                                }

                                for (int i = 0; i < timesSwipe; i++) {
                                    //// androidDevice.swipeUp();
                                    //// androidDevice.swipeVertical(true); // false = down
                                    // androidDevice.swipeADB(splitDTO.getDeviceId(), true);
                                }

                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.SWIPE_UP},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "DEVICE -> SWIPE UP",
                                        String.format("SWIPE UP CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                continue;
                            } else if (swipeDown) {

                                String nameInstruc =
                                        "(" + currentInstruction.getId() + ") " + currentInstruction.getName();

                                resultActions = String.format("Device : \"%s\"", nameInstruc);

                                FieldData msgBlock = new FieldData(resultActions, ARConstantsEngine.SWIPE_DOWN);

                                int timesSwipe = 1;

                                String operation = currentInstruction.getOperation();
                                if (operation != null && !operation.trim().isEmpty()) {
                                    try {
                                        timesSwipe = Integer.parseInt(operation.trim());
                                    } catch (NumberFormatException ignored) {
                                        appendLog("Invalid swipe count: " + operation + ". Defaulting to 1.", "warn");
                                    }
                                }

                                for (int i = 0; i < timesSwipe; i++) {
                                    //// androidDevice.swipeDown();
                                    //// androidDevice.swipeVertical(false); // false = down
                                    // androidDevice.swipeADB(splitDTO.getDeviceId(), false);
                                }
                                // Excel Report and Log
                                performActions.logAndReport(
                                        currentCondition,
                                        true,
                                        true,
                                        blockStartTime,
                                        blockReportName,
                                        success,
                                        new String[] {ARConstantsEngine.SWIPE_DOWN},
                                        msgBlock,
                                        dataExcel,
                                        writerReport,
                                        "DEVICE -> SWIPE DOWN",
                                        String.format("SWIPE DOWN CALLED AT: \"%s\" : ", nameInstruc));

                                respModal = ARExecution.DialogModal.NONE;
                                continue;
                            }

                            // It decides Here if ByPass as per Loop or Per IF-ELSEIF-ELSE-ENDIF blocks
                            // Does not block other executions if it fails for any reason and jumps to the beginning or
                            // Excel GOTO position block
                            if (!success
                                    && !byPassFlagLoop
                                    && currentCondition.equals(ARExecution.ConditionStatus.NONE)) {

                                // Record failure but do NOT alter execution flow
                                anyFailure = true;

                                // Reset success so execution can continue
                                success = true;

                                // Continue with next instruction
                                continue instructionLoop;
                            }

                            // It decides Here if ByPass as per Loop or Per IF-ELSEIF-ELSE-ENDIF blocks
                            if (jumpGotoError || jumpLoopError) {
                                stopAll = true;
                                break;
                            }

                            // Close Browser Action
                            if (resultActions.equalsIgnoreCase("Close Browser")) {
                                stopAll = true;
                                break;
                            }

                            // Here it Call the next block of IF, ELSIF, ELSE OR ENDIF as Per the Machine State
                            // Conditions When Pass to any of then
                            if (progressCondition.equals(ARExecution.ConditionStatus.IF_PASSED)
                                    || progressCondition.equals(ARExecution.ConditionStatus.ELSEIF_PASSED)) {
                                int jumpPassed = performActions.checkActionToJump(
                                        actions[0],
                                        progressCondition,
                                        mapConditional,
                                        parentBlockCondition,
                                        currentIndex);

                                // Any Error
                                if (jumpPassed < 0) {
                                    stopAll = true;
                                    continue blockLoop;
                                }
                                // Found Next Block
                                if (jumpPassed > 0) {
                                    currentIndex = jumpPassed;
                                    // reset all Conditional
                                    currentCondition = ARExecution.ConditionStatus.NONE;
                                    progressCondition = ARExecution.ConditionStatus.NONE;
                                    continue instructionLoop;
                                }
                            }

                            // Conditions When Fails to any of then and Look for the next Correct Block
                            if (progressCondition.equals(ARExecution.ConditionStatus.IF_FAILED)
                                    || progressCondition.equals(ARExecution.ConditionStatus.ELSEIF_FAILED)) {

                                // Goes to the next ELSEIF IF EXIST (ELSEIF index + 1);
                                int index = performActions.searchMapConditional(
                                        mapConditional,
                                        parentBlockCondition,
                                        ARExecution.ConditionStatus.ELSEIF,
                                        currentIndex,
                                        false);

                                // Goes to the next ELSE IF ELSEIF  DOES NOT EXIST  (ELSE index + 1);
                                if (index < 0) {
                                    index = performActions.searchMapConditional(
                                            mapConditional,
                                            parentBlockCondition,
                                            ARExecution.ConditionStatus.ELSE,
                                            currentIndex,
                                            true);
                                }
                                if (index < 0 && variableVoidBypass) {
                                    // A VOID conditional is unknown, not fatal. With no ELSE branch,
                                    // leave only this conditional family through its ENDIF.
                                    index = performActions.searchMapConditional(
                                            mapConditional,
                                            parentBlockCondition,
                                            ARExecution.ConditionStatus.ENDIF,
                                            currentIndex,
                                            true);
                                }
                                if (index < 0) {
                                    if (variableVoidBypass) {
                                        logOperations.warn(
                                                "VARIABLE_CONDITION_BYPASSED instructionId={} "
                                                        + "reason=missing conditional boundary "
                                                        + "currentBlockSkipped=true executionContinues=true",
                                                currentInstruction.getId());
                                        currentCondition = ARExecution.ConditionStatus.NONE;
                                        progressCondition = ARExecution.ConditionStatus.NONE;
                                        continue blockLoop;
                                    }
                                    stopAll = true;
                                    continue blockLoop;
                                }
                                currentIndex = index;
                                currentCondition = ARExecution.ConditionStatus.NONE;
                                progressCondition = ARExecution.ConditionStatus.NONE;
                                continue instructionLoop;

                            } else if (progressCondition.equals(ARExecution.ConditionStatus.ELSE_FAILED)) {
                                // Goes to the ENDIF (ENDIF index + 1);
                                int index = performActions.searchMapConditional(
                                        mapConditional,
                                        parentBlockCondition,
                                        ARExecution.ConditionStatus.ENDIF,
                                        currentIndex,
                                        true);

                                if (index < 0) {
                                    stopAll = true;
                                    continue blockLoop;
                                }
                                currentIndex = index;
                                currentCondition = ARExecution.ConditionStatus.NONE;
                                progressCondition = ARExecution.ConditionStatus.NONE;
                                continue instructionLoop;
                            }
                        }

                        // Has Transversed All Columns in the Block
                        // Way Out from the Current Excel Data Row to another Block keeping the Same Excel Data Row
                        break;
                    }
                    currentBlockOrder++;

                    // TEST RUN: run only the selected block, then stop the whole job.
                    // One-shot: consume the flag here, otherwise it survives a successful
                    // TEST RUN and truncates the NEXT full launch after its first block.
                    if (runSingleBlock) {
                        runSingleBlock = false;
                        stopAll = true;
                        break blockLoop;
                    }

                    currentTableCSV = csvTables.get(newExcelFieldName);

                    if (currentTableCSV != null
                            && currentTableCSV.getRows() != null
                            && !currentTableCSV.getRows().isEmpty()) {
                        saveExcelWrite(newExcelFieldName, currentTableCSV, exportIndex);
                    }
                }

                currentBlockOrder = blockExcelGoto; // BLOCK DEFINED BY "DEFAULT" OR "EXCEL GOTO"
                xExcelCurrentRow++;
                currentTableCSV = csvTables.get(newExcelFieldName);
                if (currentTableCSV != null
                        && currentTableCSV.getRows() != null
                        && !currentTableCSV.getRows().isEmpty()) {
                    saveExcelWrite(newExcelFieldName, currentTableCSV, exportIndex);
                }
            }
        }

        // Last Recall For to avoid Multirow Cycles
        // Iterate over all stored CsvTables
        for (Map.Entry<String, CsvTable> entry : csvTables.entrySet()) {

            currentTableCSV = entry.getValue();

            if (currentTableCSV != null
                    && currentTableCSV.getRows() != null
                    && !currentTableCSV.getRows().isEmpty()) {

                saveExcelWrite(currentTableCSV.getFullPath(), currentTableCSV, exportIndex);
            }
        }

        totalExecutionTime = performActions.getTotalExecutionTime();

        if (totalExecutionTime == 0) {
            writerReport.insertTotalExecutionTimes(botJobStartTime, botJobStartTime);
        } else {
            writerReport.insertTotalExecutionTimes(botJobStartTime, System.nanoTime());
        }

        // PRINT END BASE LOG//
        boolean executionInterrupted = isInterceptBotJob();
        if (!anyFailure) {
            baseLogString = blocksLoaded.get(0).getName()
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.OK);

            if (isInterceptBotJob()) {
                updateRowStatusAndNotify("yellow"); // #fcba03 deep carmine yellow
                performMessage.showCustomModalDialogDragWin11Timer(
                        "Bot-Job Interrupted successfully",
                        currentBotJobName,
                        "Last Execution:",
                        resultActions,
                        "The Device Connection is going to close in",
                        false,
                        "OK",
                        null,
                        300,
                        25);
            } else if (anyVariableVoid) {
                updateRowStatusAndNotify("yellow"); // completed with non-blocking variable warnings
                respModal = performMessage.showCustomModalDialogDragWin11Timer(
                        "Bot-Job Finished with variable warnings",
                        currentBotJobName,
                        "Last Execution:",
                        resultActions,
                        "The Device Connection is going to close in",
                        false,
                        "Continue scan",
                        "Close Connection",
                        300,
                        25);
            } else {
                updateRowStatusAndNotify("green"); // #1d9c06 deep carmine green
                respModal = performMessage.showCustomModalDialogDragWin11Timer(
                        "Bot-Job Finished - successfully",
                        currentBotJobName,
                        "Last Execution:",
                        resultActions,
                        "The Device Connection is going to close in",
                        false,
                        "Continue scan",
                        "Close Connection",
                        300,
                        25);
            }

            performActions.setInterceptBotJob(false);
            setInterceptBotJob(false);
            isJobRunning.set(false);

        } else {
            baseLogString = blocksLoaded.get(0).getName()
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.END)
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + labelsValue.getProperty(Labels.KO)
                    + ARConstantsEngine.FIELDS_SEPARATOR
                    + resultActions;

            if (isInterceptBotJob()) {
                updateRowStatusAndNotify("yellow"); // #fcba03 deep carmine yellow
                performMessage.showCustomModalDialogDragWin11Timer(
                        "Bot-Job Interrupted successfully",
                        currentBotJobName,
                        "Last Execution:",
                        resultActions,
                        "The Device Connection is going to close in",
                        false,
                        "OK",
                        null,
                        300,
                        25);

            } else {
                updateRowStatusAndNotify("red"); // #FF3131 deep carmine red
                if (webElementWork) {
                    respModal = performMessage.showCustomModalDialogDragWin11Timer(
                            "Bot-Job Finished - successfully",
                            currentBotJobName,
                            "Last Execution:",
                            resultActions,
                            "The Device Connection is going to close in",
                            false,
                            "Continue scan",
                            "Close Connection",
                            300,
                            25);
                } else {
                    respModal = performMessage.showCustomModalDialogDragWin11Timer(
                            "Process Execution Terminated",
                            !Strings.isNullOrEmpty(failedMessage) ? failedMessage : "Failed:",
                            "Last Execution:",
                            resultActions,
                            "The Device Connection is going to close in",
                            true,
                            "Continue scan",
                            "Close Connection",
                            350,
                            25);
                }
            }
        }

        logLaunch.info(baseLogString);

        boolean browserCloseRequested = "Close Browser".equalsIgnoreCase(resultActions)
                || ARExecution.DialogModal.STOP.equals(respModal);
        ARWebDriver browser = currentARWebDriver;
        if (browser != null) {
            scannerTestRunBrowserClosePolicy.closeBrowserIfAllowed(browserCloseRequested, browser::closeBrowser);
        }

        performActions.setInterceptBotJob(true);
        setInterceptBotJob(false);
        isJobRunning.set(false);
        return !anyFailure && !executionInterrupted;
    }

    private ScannerValidationEvaluator.ValidationResult evaluateOperation(
            String actualRaw, String operator, String expectedRaw) {
        return scannerValidationEvaluator.evaluateOperation(actualRaw, operator, expectedRaw);
    }

    private void appendLog(String message, String style) {
        setScannerStatus(message, style);
    }

    private void setScannerStatus(String message, String style) {
        scannerStatusText = Strings.nullToEmpty(message);
        scannerStatusStyle = Strings.nullToEmpty(style);
    }

    // Optional: small struct to track renames (for logs/inspection)
    private static final class RenameEntry {
        final int id;
        final String originalTag;
        final String newTag;
        final String reason;

        RenameEntry(int id, String originalTag, String newTag, String reason) {
            this.id = id;
            this.originalTag = originalTag;
            this.newTag = newTag;
            this.reason = reason;
        }
    }

    private void saveCanonicalXmlToAppFolder(String canonicalXml) {
        if (canonicalXml == null || canonicalXml.isBlank()) {
            appendLog("canonicalXml is empty, nothing to save.", "warn");
            return;
        }

        try {
            String base = arPropertyManager.getProperty(ARPropertyEnum.PATH_APPIUM);
            Path baseDir = Paths.get(base, "appium-xml-dumps");
            Files.createDirectories(baseDir);

            // Timestamped filename
            String fileName = "pageSource_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
                    + ".xml";

            Path filePath = baseDir.resolve(fileName);

            Files.writeString(
                    filePath,
                    canonicalXml,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            appendLog("Canonical XML saved to: " + filePath.toAbsolutePath(), "info");

        } catch (Exception ex) {
            appendLog("Failed to save canonical XML: " + ex.getMessage(), "error");
        }
    }

    // =======================================================
    // 2) PARSER: Appium pageSource XML -> ElementDTOs
    //    - Dedup via "seenKeys"
    //    - Generates: id (sequential), typeElement, xPath, someText, attribId, attribName, coordinates, attributeData
    // =======================================================
    private void parseAppiumPageSourceXml(
            String xml, List<ElementDTO> results, List<RenameEntry> renameReport, Set<String> seenKeys) {
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            appendLog("Failed to parse pageSource XML: " + ex.getMessage(), "warn");
            return;
        }

        Element root = doc.getDocumentElement();
        if (root == null) return;

        // We want all UI nodes under <hierarchy> (skip the hierarchy node itself)
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseUiNode((Element) n, results, renameReport, seenKeys);
            }
        }
    }

    private void parseWebPageSourceXhtml(String xhtml, List<ElementDTO> results, Set<String> seenKeys) {

        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(xhtml)));
        } catch (Exception ex) {
            appendLog("Failed to parse WEB XHTML: " + ex.getMessage(), "warn");
            return;
        }

        Element root = doc.getDocumentElement();
        if (root == null) return;

        traverseWebNode(root, results, seenKeys);
    }

    private void traverseWebNode(Element el, List<ElementDTO> results, Set<String> seenKeys) {

        String tag = nz(el.getTagName()).toLowerCase(Locale.ROOT);

        // Skip non-UI / noisy tags
        if (tag.equals("head")
                || tag.equals("script")
                || tag.equals("style")
                || tag.equals("meta")
                || tag.equals("link")) {
            recurseWebChildren(el, results, seenKeys);
            return;
        }

        String id = nz(el.getAttribute("id"));
        String name = nz(el.getAttribute("name"));
        String cls = nz(el.getAttribute("class"));
        String role = nz(el.getAttribute("role"));
        String aria = nz(el.getAttribute("aria-label"));
        String title = nz(el.getAttribute("title"));
        String href = nz(el.getAttribute("href"));
        String type = nz(el.getAttribute("type"));
        String value = nz(el.getAttribute("value"));
        String onclick = nz(el.getAttribute("onclick"));

        boolean isClickable = tag.equals("a")
                || tag.equals("button")
                || tag.equals("select")
                || tag.equals("textarea")
                || tag.equals("label")
                || (tag.equals("input") && !type.equalsIgnoreCase("hidden"))
                || !href.isEmpty()
                || !onclick.isEmpty()
                || role.equalsIgnoreCase("button")
                || role.equalsIgnoreCase("link");

        // Display text: prefer visible-ish sources
        String text = nz(el.getTextContent()).trim();
        String someText = firstNonEmpty(text, aria, title, value, name, id);
        if (isNullishText(someText)) someText = "";

        // Dedup key (WEB)
        String dedupeKey = tag + "|" + id + "|" + name + "|" + aria + "|" + title + "|" + href + "|" + someText;
        if (!seenKeys.add(dedupeKey)) {
            recurseWebChildren(el, results, seenKeys);
            return;
        }

        // Build WEB XPath (prefer id/name/aria, else structural)
        String xPath = buildWebXPath(el, tag, id, name, aria, someText);

        // attribId (WEB) - keep simple but stable
        String attribId = buildWebAttribId(tag, id, name, aria);

        // coords not available from pageSource -> leave 0
        String coords = "0.00,0.00";

        // attributeData for WEB
        List<AttributeData> attrs = new ArrayList<>();
        attrs.add(new AttributeData("tag", tag));
        if (!id.isEmpty()) attrs.add(new AttributeData("id", id));
        if (!name.isEmpty()) attrs.add(new AttributeData("name", name));
        if (!cls.isEmpty()) attrs.add(new AttributeData("class", cls));
        if (!role.isEmpty()) attrs.add(new AttributeData("role", role));
        if (!aria.isEmpty()) attrs.add(new AttributeData("aria-label", aria));
        if (!title.isEmpty()) attrs.add(new AttributeData("title", title));
        if (!href.isEmpty()) attrs.add(new AttributeData("href", href));
        if (!type.isEmpty()) attrs.add(new AttributeData("type", type));
        if (!value.isEmpty()) attrs.add(new AttributeData("value", value));
        attrs.add(new AttributeData("clickable", isClickable ? "true" : "false"));

        ElementDTO dto = new ElementDTO();
        dto.setId(results.size() + 1);
        dto.setTypeElement("tagName-Found");
        dto.setTagName(tag); // WEB tagName = real HTML tag
        dto.setXPath(xPath);
        dto.setAttribId(attribId);
        dto.setSomeText(someText);
        dto.setAttribName("");
        dto.setCoordinates(coords);
        dto.setAttributeData(attrs.toArray(new AttributeData[0]));

        // WEB: clear Android-specific fields
        dto.setAndroidData(null);

        dto.setCustomXPath("");
        dto.setIFrameXPath("");
        dto.setShadowHost("");
        dto.setShadowRoot("false");
        dto.setNestedShadow("false");
        dto.setCssSelector(""); // optional: you can add later
        dto.setAttributeValue("");
        dto.setAttributeType("");
        dto.setSearchAttributeValue("");

        results.add(dto);

        // IMPORTANT: do NOT call addVariantsLikeSecret() for web.
        // It creates fake tagName "link" which collides with real <link> and is also not needed.

        recurseWebChildren(el, results, seenKeys);
    }

    private void recurseWebChildren(Element el, List<ElementDTO> results, Set<String> seenKeys) {
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseWebNode((Element) n, results, seenKeys);
            }
        }
    }

    private String buildWebAttribId(String tag, String id, String name, String aria) {
        if (id != null && !id.isEmpty()) return "//*[@id=\"" + id.replace("\"", "") + "\"]";
        if (name != null && !name.isEmpty()) return "//" + tag + "[@name=\"" + name.replace("\"", "") + "\"]";
        if (aria != null && !aria.isEmpty()) return "//" + tag + "[@aria-label=\"" + aria.replace("\"", "") + "\"]";
        return "//" + tag;
    }

    private String buildWebXPath(Element el, String tag, String id, String name, String aria, String text) {
        if (id != null && !id.isEmpty()) {
            return "//*[@id='" + escapeXPathSQ(id) + "']";
        }
        if (name != null && !name.isEmpty()) {
            return "//" + tag + "[@name='" + escapeXPathSQ(name) + "']";
        }
        if (aria != null && !aria.isEmpty()) {
            return "//" + tag + "[@aria-label='" + escapeXPathSQ(aria) + "']";
        }
        // optional: text match if short
        if (text != null && !text.isEmpty() && text.length() <= 60 && !isNullishText(text)) {
            return "//" + tag + "[normalize-space(.)='" + escapeXPathSQ(text) + "']";
        }
        return buildWebStructuralPathWithIndex(el);
    }

    private String buildWebStructuralPathWithIndex(Element el) {
        ArrayDeque<String> parts = new ArrayDeque<>();
        org.w3c.dom.Node cur = el;

        while (cur != null && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element ce = (Element) cur;
            String tag = nz(ce.getTagName()).toLowerCase(Locale.ROOT);

            int idx = computeSiblingIndexSameTag(ce, tag);
            parts.addFirst("/" + tag + "[" + idx + "]");

            cur = cur.getParentNode();
            // stop at html root
            if (cur != null
                    && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "html".equalsIgnoreCase(((Element) cur).getTagName())) {
                parts.addFirst("/html[1]");
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p);
        return sb.length() > 0 ? sb.toString() : "/";
    }

    private int computeSiblingIndexSameTag(Element el, String tag) {
        org.w3c.dom.Node parent = el.getParentNode();
        if (parent == null) return 1;

        NodeList siblings = parent.getChildNodes();
        int count = 0;

        for (int i = 0; i < siblings.getLength(); i++) {
            org.w3c.dom.Node n = siblings.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;

            Element sib = (Element) n;
            String sibTag = nz(sib.getTagName()).toLowerCase(Locale.ROOT);

            if (tag.equals(sibTag)) count++;
            if (sib == el) return Math.max(1, count);
        }
        return 1;
    }

    private void addVariantsLikeSecret(ElementDTO dto, List<ElementDTO> results) {
        try {
            ElementDTO dtoInput = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoInput.setId(results.size() + 1);
            dtoInput.setTagName("input");
            VisionElementMapper.overrideClassAttribute(dtoInput, "android.widget.EditText");
            results.add(dtoInput);

            ElementDTO dtoButton = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoButton.setId(results.size() + 1);
            dtoButton.setTagName("button");
            VisionElementMapper.overrideClassAttribute(dtoButton, "android.widget.Button");
            results.add(dtoButton);

            ElementDTO dtoLabel = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoLabel.setId(results.size() + 1);
            dtoLabel.setTagName("label");
            VisionElementMapper.overrideClassAttribute(dtoLabel, "android.widget.TextView");
            results.add(dtoLabel);

            ElementDTO dtoLink = gson.fromJson(gson.toJson(dto), ElementDTO.class);
            dtoLink.setId(results.size() + 1);
            dtoLink.setTagName("link");
            VisionElementMapper.overrideClassAttribute(dtoLink, "android.widget.ImageView");
            results.add(dtoLink);

        } catch (Exception cloneEx) {
            appendLog("Variant clone failed for XML element #" + dto.getId() + ": " + cloneEx.getMessage(), "warn");
        }
    }

    // =======================================================
    // traverseUiNode (ONLY clickables, but steals nested TextView text/desc)
    // Ready to copy/paste
    // =======================================================
    // =======================================================
    // traverseUiNode (ONLY clickables, steals nested TextView text/desc)
    // Uses recurseChildren(...) consistently
    // =======================================================
    private void traverseUiNode(
            Element el, List<ElementDTO> results, List<RenameEntry> renameReport, Set<String> seenKeys) {

        // Resolve class
        String cls = nz(el.getAttribute("class"));
        if (cls.isEmpty()) cls = nz(el.getTagName());
        if (cls.isEmpty()) cls = "android.view.View";

        // Ignore ProgressBar as in secret/scanContext
        if ("android.widget.ProgressBar".equals(cls)) {
            recurseChildren(el, results, renameReport, seenKeys);
            return;
        }

        // RAW attributes (ONLY these are allowed for XPath/attribId)
        String resId = nz(el.getAttribute("resource-id"));
        String rawText = nz(el.getAttribute("text"));
        String rawDesc = nz(el.getAttribute("content-desc"));
        String clickableStr = nz(el.getAttribute("clickable"));
        String enabled = nz(el.getAttribute("enabled"));
        String password = nz(el.getAttribute("password"));
        String bounds = nz(el.getAttribute("bounds"));
        String focused = nz(el.getAttribute("focused"));

        boolean isClickable = "true".equalsIgnoreCase(clickableStr);

        // ✅ ALSO include drawer group rows even if clickable="false"
        boolean includeAsAction = isClickable || isDrawerGroupContainer(el, resId);

        if (includeAsAction) {

            // -----------------------------
            // 1) DISPLAY TEXT (frontend only)
            // -----------------------------
            String effectiveText = rawText;
            String effectiveDesc = rawDesc;

            boolean localTextNullish = effectiveText.isEmpty() || isNullishText(effectiveText);
            boolean localDescNullish = effectiveDesc.isEmpty() || isNullishText(effectiveDesc);

            // If container has no text/desc, steal from nested children (TextView preferred)
            if (localTextNullish && localDescNullish) {
                NestedText nt = extractNestedText(el);
                if (!nt.text.isEmpty()) effectiveText = nt.text;
                if (!nt.desc.isEmpty()) effectiveDesc = nt.desc;
            }

            // someText (frontend-only)
            String someText = !effectiveText.isEmpty() ? effectiveText : effectiveDesc;
            if (isNullishText(someText)) {
                someText = "";
            }

            // Semantic fallbacks for toolbar icons (DISPLAY only)
            if (someText.isEmpty()) {
                if (isBackElement(cls, resId, rawDesc)) {
                    someText = "back";
                } else if (isMenuElement(cls, resId)) {
                    someText = "menu";
                }
            }

            // Menu fallback for empty labels (hamburger)
            if (someText.isEmpty() && isClickable && isMenuElement(cls, resId)) {
                someText = "menu";
            }

            // ✅ Prefix menu context (DO NOT affect XPath / attribId)
            boolean inMenu = isInDrawerMenu(el);
            if (inMenu && !someText.isEmpty()) {
                someText = "MENU -> " + someText;
            }

            // -----------------------------
            // 2) DEDUPE KEY (RAW only)
            // -----------------------------
            // Important for drawer: many items share resource-id="...:id/container".
            // Bounds disambiguates; if bounds missing, structural XPath will.
            String dedupeKey = cls + "|" + resId + "|" + rawText + "|" + rawDesc + "|" + bounds;
            if (seenKeys.add(dedupeKey)) {

                // -----------------------------
                // 3) XPATH + attribId (RAW only)
                // -----------------------------
                String xPath = buildSafeXPathWithIndex(el, cls, resId, rawText, rawDesc, bounds);

                String attribId = buildAttribId(cls, resId, rawText);
                if ((bounds == null || bounds.isEmpty()) && (resId == null || resId.isEmpty())) {
                    attribId = xPath; // last fallback uniqueness
                }

                String coords = computeCoordinatesFromBounds(bounds);

                // Tag mapping should use RAW text/desc too (not stolen)
                String mappedTag = mapTagName(cls, xPath, includeAsAction, rawText, rawDesc);

                // attributeData should store RAW values (so locators stay honest)
                List<AttributeData> attrs = new ArrayList<>();
                attrs.add(new AttributeData("class", cls));
                attrs.add(new AttributeData("resource-id", resId.isEmpty() ? "null" : resId));
                attrs.add(new AttributeData("text", rawText.isEmpty() ? "null" : rawText));
                attrs.add(new AttributeData("content-desc", rawDesc.isEmpty() ? "null" : rawDesc));
                attrs.add(new AttributeData("clickable", boolString(clickableStr)));
                attrs.add(new AttributeData("enabled", boolString(enabled)));
                attrs.add(new AttributeData("focused", boolString(focused)));
                if (!password.isEmpty()) attrs.add(new AttributeData("password", boolString(password)));
                attrs.add(new AttributeData("bounds", bounds.isEmpty() ? "null" : bounds));

                ElementDTO dto = new ElementDTO();
                dto.setId(results.size() + 1);
                dto.setTypeElement("tagName-Found");
                dto.setTagName(mappedTag.toLowerCase());

                dto.setXPath(xPath);
                dto.setAttribId(attribId);

                // ✅ DISPLAY ONLY
                dto.setSomeText(someText);

                dto.setAttribName("");
                dto.setCoordinates(coords);
                dto.setAttributeData(attrs.toArray(new AttributeData[0]));

                dto.setCustomXPath("");
                dto.setIFrameXPath("");
                dto.setShadowHost("");
                dto.setShadowRoot("false");
                dto.setNestedShadow("false");
                dto.setCssSelector("");
                dto.setAttributeValue("");
                dto.setAttributeType("");
                dto.setSearchAttributeValue("");

                results.add(dto);

                // ⚠️ If you want ONLY clickables, consider disabling variants here.
                addVariantsLikeSecret(dto, results);
            }
        }

        recurseChildren(el, results, renameReport, seenKeys);
    }

    private void recurseChildren(
            Element el, List<ElementDTO> results, List<RenameEntry> renameReport, Set<String> seenKeys) {
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseUiNode((Element) n, results, renameReport, seenKeys);
            }
        }
    }

    private boolean isDrawerGroupContainer(Element el, String resId) {
        if (resId == null) return false;

        // Your drawer rows all share resource-id "...:id/container"
        if (!resId.toLowerCase(Locale.ROOT).endsWith(":id/container")) {
            return false;
        }

        // If it contains a drawer_group_title with non-empty text => treat as a menu group item
        ArrayDeque<Element> q = new ArrayDeque<>();
        q.add(el);

        while (!q.isEmpty()) {
            Element cur = q.removeFirst();

            String childResId = nz(cur.getAttribute("resource-id"));
            String childText = nz(cur.getAttribute("text"));

            if (!childResId.isEmpty()
                    && childResId.toLowerCase(Locale.ROOT).contains("drawer_group_title")
                    && !childText.isEmpty()
                    && !isNullishText(childText)) {
                return true;
            }

            NodeList kids = cur.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                org.w3c.dom.Node n = kids.item(i);
                if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    q.add((Element) n);
                }
            }
        }
        return false;
    }

    private boolean isBackElement(String cls, String resId, String desc) {
        String id = (resId == null) ? "" : resId.toLowerCase(Locale.ROOT);
        String cd = (desc == null) ? "" : desc.toLowerCase(Locale.ROOT);

        // resource-id patterns
        if (id.contains("back")
                || id.contains("up")
                || id.contains("navigate_up")
                || id.contains("nav_up")
                || id.contains("action_back")
                || id.contains("drawer_back")) {
            return true;
        }

        // content-desc patterns (common for toolbar icons)
        if (cd.contains("back") || cd.contains("navigate up") || cd.contains("up")) {
            return true;
        }

        // Often used as icon button (optional heuristic)
        if ((cls.endsWith("ImageView") || cls.endsWith("ImageButton") || cls.endsWith("FrameLayout"))
                && (id.contains("arrow") || id.contains("chevron"))) {
            return true;
        }

        return false;
    }

    private boolean isMenuElement(String cls, String resId) {
        if (resId == null) return false;

        String id = resId.toLowerCase(Locale.ROOT);

        // Common menu identifiers
        if (id.contains("menu") || id.contains("drawer") || id.contains("hamburger") || id.contains("nav")) {
            return true;
        }

        // Clickable FrameLayout/ImageView used as toolbar menu
        if ((cls.endsWith("FrameLayout") || cls.endsWith("ImageView")) && id.contains("drawer")) {
            return true;
        }

        return false;
    }

    private boolean isInDrawerMenu(Element el) {
        if (el == null) return false;

        // First: identify if THIS element is itself a drawer structural node
        String selfResId = nz(el.getAttribute("resource-id")).toLowerCase(Locale.ROOT);

        if (isDrawerRootId(selfResId)) {
            return false; // drawer root itself is NOT "in menu"
        }

        // Now: walk ancestors ONLY
        org.w3c.dom.Node parent = el.getParentNode();

        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element pe = (Element) parent;

            String resId = nz(pe.getAttribute("resource-id")).toLowerCase(Locale.ROOT);

            if (isDrawerRootId(resId)) {
                return true; // element is a child of drawer
            }

            parent = parent.getParentNode();

            // stop at hierarchy root
            if (parent != null
                    && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "hierarchy".equalsIgnoreCase(((Element) parent).getTagName())) {
                break;
            }
        }
        return false;
    }

    private boolean isDrawerRootId(String resId) {
        if (resId == null || resId.isEmpty()) return false;

        return resId.endsWith(":id/drawer") || resId.endsWith(":id/drawer_recyclerview");
    }

    // =======================================================
    // 3) SMALL HELPERS (copy/paste)
    // =======================================================

    // =======================================================
    // Nested text extractor (required by traverseUiNode)
    // Prefers TextView[@text], falls back to any @text, then @content-desc
    // =======================================================
    private static final class NestedText {
        final String text;
        final String desc;

        NestedText(String text, String desc) {
            this.text = text == null ? "" : text;
            this.desc = desc == null ? "" : desc;
        }
    }

    private NestedText extractNestedText(Element container) {
        ArrayDeque<Element> q = new ArrayDeque<>();
        q.add(container);

        String bestTextViewText = "";
        String bestAnyText = "";
        String bestAnyDesc = "";

        while (!q.isEmpty()) {
            Element cur = q.removeFirst();

            String cls = nz(cur.getAttribute("class"));
            String t = nz(cur.getAttribute("text"));
            String d = nz(cur.getAttribute("content-desc"));

            if (!t.isEmpty() && !isNullishText(t)) {
                if ("android.widget.TextView".equals(cls) && bestTextViewText.isEmpty()) {
                    bestTextViewText = t;
                } else if (bestAnyText.isEmpty()) {
                    bestAnyText = t;
                }
            }

            if (!d.isEmpty() && !isNullishText(d) && bestAnyDesc.isEmpty()) {
                bestAnyDesc = d;
            }

            // Early exit: we found best possible (TextView text)
            if (!bestTextViewText.isEmpty()) break;

            NodeList kids = cur.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                org.w3c.dom.Node n = kids.item(i);
                if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    q.add((Element) n);
                }
            }
        }

        String chosenText = !bestTextViewText.isEmpty() ? bestTextViewText : bestAnyText;
        return new NestedText(chosenText, bestAnyDesc);
    }

    private String boolString(String v) {
        if (v == null || v.isBlank()) return "false";
        return "true".equalsIgnoreCase(v) ? "true" : "false";
    }

    private String buildSafeXPathWithIndex(
            Element el, String cls, String resId, String text, String desc, String bounds) {

        String safeClass = (cls == null || cls.isEmpty()) ? "android.view.View" : cls;

        boolean hasResId = resId != null && !resId.isEmpty();
        boolean hasBounds = bounds != null && !bounds.isEmpty();

        String predicate = null;
        if (hasResId) {
            predicate = "@resource-id='" + escapeXPathSQ(resId) + "'";
        } else if (desc != null && !desc.isEmpty() && !isNullishText(desc)) {
            predicate = "@content-desc='" + escapeXPathSQ(desc) + "'";
        } else if (text != null && !text.isEmpty() && !isNullishText(text)) {
            predicate = "@text='" + escapeXPathSQ(text) + "'";
        }

        // 1) If we have predicate + bounds, return immediately
        if (predicate != null && hasBounds) {
            return "//" + safeClass + "[" + predicate + " and @bounds='" + escapeXPathSQ(bounds) + "']";
        }

        // 2) If we have bounds only, return immediately
        if (predicate == null && hasBounds) {
            return "//" + safeClass + "[@bounds='" + escapeXPathSQ(bounds) + "']";
        }

        // 3) If we have predicate only, return (short & stable)
        if (predicate != null) {
            return "//" + safeClass + "[" + predicate + "]";
        }

        // 4) Otherwise fallback to structural (only here)
        return buildStructuralPathWithIndex(el);
    }

    private String buildStructuralPathWithIndex(Element el) {
        // Build absolute-ish (but stable) path from root element downwards,
        // using class names + sibling index among same-class siblings.
        ArrayDeque<String> parts = new ArrayDeque<>();

        org.w3c.dom.Node cur = el;

        while (cur != null && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element ce = (Element) cur;

            String cls = nz(ce.getAttribute("class"));
            if (cls.isEmpty()) cls = nz(ce.getTagName());
            if (cls.isEmpty()) cls = "android.view.View";

            int index = computeSiblingIndexSameClass(ce, cls);

            // Use /<class>[<idx>] style
            parts.addFirst("/" + cls + "[" + index + "]");

            cur = cur.getParentNode();

            // Stop once we reach <hierarchy> (the document root)
            if (cur != null
                    && cur.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "hierarchy".equalsIgnoreCase(((Element) cur).getTagName())) {
                break;
            }
        }

        // Start at //hierarchy (Appium root) then append
        StringBuilder sb = new StringBuilder("//hierarchy");
        for (String p : parts) sb.append(p);

        return sb.toString();
    }

    private int computeSiblingIndexSameClass(Element el, String cls) {
        org.w3c.dom.Node parent = el.getParentNode();
        if (parent == null) return 1;

        NodeList siblings = parent.getChildNodes();
        int count = 0;

        for (int i = 0; i < siblings.getLength(); i++) {
            org.w3c.dom.Node n = siblings.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;

            Element sib = (Element) n;
            String sibCls = nz(sib.getAttribute("class"));
            if (sibCls.isEmpty()) sibCls = nz(sib.getTagName());
            if (sibCls.isEmpty()) sibCls = "android.view.View";

            if (cls.equals(sibCls)) {
                count++;
            }

            // Once we reach the current element, that count is its 1-based index
            if (sib == el) {
                return Math.max(1, count);
            }
        }
        return 1;
    }

    // XPath escaping for single quotes
    private String escapeXPathSQ(String s) {
        if (s == null) return "";
        return s.replace("'", "&apos;");
    }

    // Helper: text considered "nullish" if null, empty, whitespace or literal "null"
    private static boolean isNullishText(String s) {
        if (s == null) return true;
        String t = s.trim();
        return t.isEmpty() || t.equalsIgnoreCase("null") || t.equalsIgnoreCase("(null)");
    }

    public void sendChunks(
            List<ElementDTO> elements,
            int chunkSize,
            SplitDTO splitDTO,
            WebSocketSessionManager webSocketSessionManager,
            String server,
            String routingKey) {
        if (elements == null || elements.isEmpty()) {
            appendLog("No elements to send.", "warn");
            return;
        }

        appendLog("Sending " + elements.size() + " elements in chunks of " + chunkSize, "info");

        for (int i = 0; i < elements.size(); i += chunkSize) {

            int end = Math.min(i + chunkSize, elements.size());
            List<ElementDTO> chunk = elements.subList(i, end);

            // update DTO
            splitDTO.setElementDetails(chunk.toArray(new ElementDTO[0]));

            // serialize
            String jsonData = new Gson().toJson(splitDTO);

            // log
            appendLog("Sending chunk " + (i / chunkSize + 1) + " containing " + chunk.size() + " elements", "info");

            // send
            webSocketSessionManager.sendMessageJson(0, server, jsonData, routingKey);
        }
    }

    private String buildAttribId(String cls, String resId, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("//").append(cls);

        boolean first = true;

        if (resId != null && !resId.isEmpty()) {
            sb.append(first ? "[@" : " and @")
                    .append("resource-id=\"")
                    .append(resId.replace("\"", ""))
                    .append("\"");
            first = false;
        }
        if (text != null && !text.isEmpty()) {
            sb.append(first ? "[@" : " and @")
                    .append("text=\"")
                    .append(text.replace("\"", ""))
                    .append("\"");
            first = false;
        }

        if (!first) sb.append("]");
        return sb.toString();
    }

    // =======================================================
    // Tag mapping (clickable-aware, closer to secret rules)
    // Now: ✅ if clickable (and not input), default is "button"
    // =======================================================
    private String mapTagName(String cls, String xPath, boolean isClickable, String text, String desc) {
        boolean xpathSaysButton = xPath != null && xPath.contains("android.widget.Button");

        String someText = (text != null && !text.isEmpty()) ? text : (desc == null ? "" : desc);
        boolean nullish = isNullishText(someText);

        // ---- Strong class rules ----
        // input always stays input even if clickable
        if ("android.widget.EditText".equals(cls) || cls.endsWith("EditText")) {
            return "input";
        }

        // Buttons
        if ("android.widget.Button".equals(cls) || xpathSaysButton) {
            return "button";
        }
        if ("android.widget.ImageButton".equals(cls) || cls.endsWith("ImageButton")) {
            return "button";
        }

        // ImageView: many are icon-buttons when clickable
        if ("android.widget.ImageView".equals(cls) || cls.endsWith("ImageView")) {
            return isClickable ? "button" : "label";
        }

        // TextView: clickable -> button, else label
        if ("android.widget.TextView".equals(cls) || cls.endsWith("TextView")) {
            return isClickable ? "button" : "label";
        }

        // Spinner-like: clickable -> button, else label
        if ("android.widget.Spinner".equals(cls) || cls.endsWith("Spinner")) {
            return isClickable ? "button" : "label";
        }

        // ---- Containers / generic views ----
        // If container is clickable, treat it as a button (your requirement),
        // otherwise label. (nullish not needed anymore for the decision)
        if (cls.endsWith("LinearLayout")
                || cls.endsWith("FrameLayout")
                || cls.endsWith("RelativeLayout")
                || cls.endsWith("ConstraintLayout")
                || cls.endsWith("ViewGroup")
                || cls.endsWith("ScrollView")
                || "android.view.View".equals(cls)
                || cls.endsWith("View")) {
            return isClickable ? "button" : "label";
        }

        // ---- Final fallback ----
        return isClickable ? "button" : "label";
    }

    private String computeCoordinatesFromBounds(String bounds) {
        int[] b = parseBounds(bounds);
        if (b == null) return "0.00,0.00";
        int w = b[2] - b[0];
        int h = b[3] - b[1];
        if (w <= 0 || h <= 0) return "0.00,0.00";
        int cx = b[0] + w / 2;
        int cy = b[1] + h / 2;
        return String.format(Locale.US, "%.2f,%.2f", (double) cx, (double) cy);
    }

    private static int[] parseBounds(String bounds) {
        // format: [x1,y1][x2,y2]
        // returns {x1,y1,x2,y2} or null if invalid
        try {
            if (bounds == null || bounds.isEmpty()) return null;
            String cleaned = bounds.replace("[", "").replace("]", ",");
            String[] parts = cleaned.split(",");
            if (parts.length < 4) return null;
            int x1 = Integer.parseInt(parts[0].trim());
            int y1 = Integer.parseInt(parts[1].trim());
            int x2 = Integer.parseInt(parts[2].trim());
            int y2 = Integer.parseInt(parts[3].trim());
            return new int[] {x1, y1, x2, y2};
        } catch (Exception ignore) {
            return null;
        }
    }

    public List<ElementDTO> extractAllTextElementsFromCanonicalXml(String canonicalXml, List<ElementDTO> results) {
        if (canonicalXml == null || canonicalXml.isBlank()) {
            appendLog("Canonical XML is empty", "warn");
            return results;
        }

        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(canonicalXml)));
        } catch (Exception ex) {
            appendLog("Failed to parse canonical XML: " + ex.getMessage(), "error");
            return results;
        }

        Element root = doc.getDocumentElement();
        if (root == null) return results;

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseForTextOnly((Element) n, results);
            }
        }

        appendLog("Text-only extraction complete. Elements found: " + results.size(), "info");
        return results;
    }

    private void traverseForTextOnly(Element el, List<ElementDTO> results) {

        String tag = nz(el.getTagName()).toLowerCase();

        // Skip non-visible-content tags (DOM-only heuristic)
        if (tag.equals("script") || tag.equals("style") || tag.equals("noscript")) return;

        // HTML "text": for inputs use value/placeholder; otherwise textContent
        String rawText = extractHtmlText(el);

        // Keep ONLY elements with meaningful text
        if (!rawText.isEmpty() && !isNullishText(rawText)) {

            String cls = nz(el.getAttribute("class"));
            if (cls.isEmpty()) cls = tag;

            String id = nz(el.getAttribute("id"));
            String name = nz(el.getAttribute("name"));
            String ariaLabel = nz(el.getAttribute("aria-label"));
            String title = nz(el.getAttribute("title"));
            String alt = nz(el.getAttribute("alt"));
            String href = nz(el.getAttribute("href"));
            String role = nz(el.getAttribute("role"));
            String onclick = nz(el.getAttribute("onclick"));
            String tabindex = nz(el.getAttribute("tabindex"));

            boolean clickable = isHtmlClickable(tag, href, onclick, role, tabindex);

            // Build XPath for HTML (prefer id, then stable attributes)
            String xPath = buildSafeHtmlXPathWithIndex(el, tag, id, cls, rawText, ariaLabel);

            // attribId: use id if exists; otherwise XPath
            String attribId = !id.isEmpty() ? id : xPath;

            // No bounds in DOM-only parsing
            String coords = "null";

            List<AttributeData> attrs = new ArrayList<>();
            attrs.add(new AttributeData("tag", tag));
            attrs.add(new AttributeData("id", id.isEmpty() ? "null" : id));
            attrs.add(new AttributeData("class", cls.isEmpty() ? "null" : cls));
            attrs.add(new AttributeData("name", name.isEmpty() ? "null" : name));
            attrs.add(new AttributeData("aria-label", ariaLabel.isEmpty() ? "null" : ariaLabel));
            attrs.add(new AttributeData("title", title.isEmpty() ? "null" : title));
            attrs.add(new AttributeData("alt", alt.isEmpty() ? "null" : alt));
            attrs.add(new AttributeData("href", href.isEmpty() ? "null" : href));
            attrs.add(new AttributeData("role", role.isEmpty() ? "null" : role));
            attrs.add(new AttributeData("clickable", String.valueOf(clickable)));
            attrs.add(new AttributeData("text", rawText));

            ElementDTO dto = new ElementDTO();
            dto.setId(results.size() + 1);
            dto.setTypeElement("tagName-Found");
            dto.setTagName(mapTagName(tag, xPath, clickable, rawText, ariaLabel));
            dto.setXPath(xPath);
            dto.setAttribId(attribId);
            dto.setSomeText(rawText);

            dto.setAttribName("");
            dto.setCoordinates(coords);
            dto.setAttributeData(attrs.toArray(new AttributeData[0]));

            dto.setCustomXPath("");
            dto.setIFrameXPath("");
            dto.setShadowHost("");
            dto.setShadowRoot("false");
            dto.setNestedShadow("false");
            dto.setCssSelector(""); // optional: you can build from id/class if you want
            dto.setAttributeValue("");
            dto.setAttributeType("");
            dto.setSearchAttributeValue("");

            results.add(dto);
        }

        // Continue traversal
        org.w3c.dom.Node child = el.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseForTextOnly((Element) child, results);
            }
            child = child.getNextSibling();
        }
    }

    private String extractHtmlText(Element el) {
        String tag = nz(el.getTagName()).toLowerCase();

        // For inputs, text is in value/placeholder
        if (tag.equals("input") || tag.equals("textarea")) {
            String v = nz(el.getAttribute("value"));
            if (!v.isEmpty()) return normalizeSpace(v);
            String ph = nz(el.getAttribute("placeholder"));
            if (!ph.isEmpty()) return normalizeSpace(ph);
            // fallback
        }
        String txt = nz(el.getTextContent());
        return normalizeSpace(txt);
    }

    private boolean isHtmlClickable(String tag, String href, String onclick, String role, String tabindex) {
        if (tag == null) return false;
        tag = tag.toLowerCase();

        // Native clickable HTML elements
        if (tag.equals("a") && !href.isEmpty()) return true;
        if (tag.equals("button")) return true;
        if (tag.equals("input")) return true;
        if (tag.equals("select")) return true;
        if (tag.equals("textarea")) return true;

        // JS or accessibility-based clickability
        if (!onclick.isEmpty()) return true;
        if ("button".equalsIgnoreCase(role)) return true;
        if ("link".equalsIgnoreCase(role)) return true;

        // Focusable elements are often interactive
        if (!tabindex.isEmpty()) return true;

        return false;
    }

    private String buildSafeHtmlXPathWithIndex(
            Element el, String tag, String id, String cls, String text, String ariaLabel) {
        // Strong preference: //*[@id='...']
        if (!id.isEmpty()) {
            return "//*[@" + "id='" + escapeXPathLiteral(id) + "']";
        }

        // Next: aria-label / title / name / class + index
        // Use your existing buildSafeXPathWithIndex pattern but with HTML attributes.
        // Minimal example (you likely already have an index builder):
        return buildXPathByTagAndIndex(el, tag);
    }

    /**
     * Builds an XPath like:
     * /html[1]/body[1]/div[2]/span[1]
     *
     * It uses tag names + 1-based index among same-tag siblings.
     * Works on an org.w3c.dom.Element tree.
     */
    private String buildXPathByTagAndIndex(Element el, String tagIgnored) {
        if (el == null) return "";

        StringBuilder path = new StringBuilder();
        org.w3c.dom.Node current = el;

        while (current != null && current.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element curEl = (Element) current;

            String tag = nz(curEl.getTagName());
            if (tag.isEmpty()) tag = "*";

            int index = getIndexAmongSameTagSiblings(curEl);

            // prepend segment
            String segment = "/" + tag + "[" + index + "]";
            path.insert(0, segment);

            current = current.getParentNode();
            if (current != null && current.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) break;
        }
        return path.toString();
    }

    public List<ElementDTO> extractWebControls(Document doc) {
        List<ElementDTO> results = new ArrayList<>();
        Element root = doc.getDocumentElement();
        traverseWebControls(root, results);
        return results;
    }

    private void traverseWebControls(Element el, List<ElementDTO> results) {
        String tag = nz(el.getTagName()).toLowerCase();

        // skip non-content tags
        if (tag.equals("script") || tag.equals("style") || tag.equals("noscript")) {
            return;
        }

        if (isWantedWebElement(tag, el)) {
            ElementDTO dto = buildWebElementDTO(el, tag, results.size() + 1);
            if (dto != null) {
                results.add(dto);
            }
        }

        // DFS traversal
        org.w3c.dom.Node child = el.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                traverseWebControls((Element) child, results);
            }
            child = child.getNextSibling();
        }
    }

    private boolean isWantedWebElement(String tag, Element el) {
        if (tag.equals("label")) return true;
        if (tag.equals("input") || tag.equals("textarea") || tag.equals("select")) return true;
        if (tag.equals("button")) return true;
        if (tag.equals("a")) return true;
        // optional: include elements that act like buttons
        String role = nz(el.getAttribute("role"));
        String onclick = nz(el.getAttribute("onclick"));
        if ("button".equalsIgnoreCase(role) || "link".equalsIgnoreCase(role)) return true;
        if (!onclick.isEmpty()) return true;
        return false;
    }

    private ElementDTO buildWebElementDTO(Element el, String tag, int idSeq) {

        // Common attributes
        String id = nz(el.getAttribute("id"));
        String name = nz(el.getAttribute("name"));
        String cls = nz(el.getAttribute("class"));
        String role = nz(el.getAttribute("role"));
        String tabindex = nz(el.getAttribute("tabindex"));
        String ariaLabel = nz(el.getAttribute("aria-label"));
        String title = nz(el.getAttribute("title"));
        String disabled = nz(el.getAttribute("disabled"));

        // Element text (labels/buttons/links)
        String text = normalizeSpace(el.getTextContent());

        // Input specific
        String type = "";
        String value = "";
        String placeholder = "";
        if (tag.equals("input")) {
            type = nz(el.getAttribute("type")).toLowerCase();
            value = nz(el.getAttribute("value"));
            placeholder = nz(el.getAttribute("placeholder"));
        } else if (tag.equals("textarea")) {
            value = normalizeSpace(el.getTextContent());
            placeholder = nz(el.getAttribute("placeholder"));
        }

        // Link specific
        String href = "";
        if (tag.equals("a")) {
            href = nz(el.getAttribute("href"));
        }

        // Label specific (for="...")
        String forId = "";
        if (tag.equals("label")) {
            forId = nz(el.getAttribute("for"));
        }

        // For inputs: try to find label text by matching <label for="id">
        String linkedLabel = "";
        if ((tag.equals("input") || tag.equals("textarea") || tag.equals("select")) && !id.isEmpty()) {
            linkedLabel = findLabelForInput(el.getOwnerDocument(), id);
        }

        // Decide "someText" (what you display)
        String someText;
        if (tag.equals("label")) someText = text;
        else if (tag.equals("button")) someText = text;
        else if (tag.equals("a")) someText = !text.isEmpty() ? text : href;
        else { // input/select/textarea
            // prefer label, then aria-label/title/placeholder/value
            someText = firstNonEmpty(linkedLabel, ariaLabel, title, placeholder, value, name, id);
        }
        someText = nz(someText);

        // If still empty, skip it (you said you want exactly labels/inputs/buttons but "nice" list)
        if (someText.isEmpty()) {
            // If you truly want EVERYTHING regardless of text, comment this out
            // return null;
        }

        // XPath
        String xPath = !id.isEmpty() ? "//*[@" + "id=" + escapeXPathLiteral(id) + "]" : buildXPathByTagAndIndex(el);

        // Build AttributeData list
        List<AttributeData> attrs = new ArrayList<>();
        attrs.add(new AttributeData("tag", tag));
        attrs.add(new AttributeData("id", id.isEmpty() ? "null" : id));
        attrs.add(new AttributeData("name", name.isEmpty() ? "null" : name));
        attrs.add(new AttributeData("class", cls.isEmpty() ? "null" : cls));
        attrs.add(new AttributeData("type", type.isEmpty() ? "null" : type));
        attrs.add(new AttributeData("value", value.isEmpty() ? "null" : value));
        attrs.add(new AttributeData("placeholder", placeholder.isEmpty() ? "null" : placeholder));
        attrs.add(new AttributeData("for", forId.isEmpty() ? "null" : forId));
        attrs.add(new AttributeData("href", href.isEmpty() ? "null" : href));
        attrs.add(new AttributeData("role", role.isEmpty() ? "null" : role));
        attrs.add(new AttributeData("tabindex", tabindex.isEmpty() ? "null" : tabindex));
        attrs.add(new AttributeData("aria-label", ariaLabel.isEmpty() ? "null" : ariaLabel));
        attrs.add(new AttributeData("title", title.isEmpty() ? "null" : title));
        attrs.add(new AttributeData("disabled", disabled.isEmpty() ? "false" : "true"));

        // Create DTO (kept close to your original shape)
        ElementDTO dto = new ElementDTO();
        dto.setId(idSeq);
        dto.setTypeElement("web-control");
        dto.setTagName(tag);
        dto.setXPath(xPath);
        dto.setAttribId(!id.isEmpty() ? id : xPath);
        dto.setSomeText(someText);
        dto.setAttributeData(attrs.toArray(new AttributeData[0]));

        // Not available in pure DOM parsing
        dto.setCoordinates("null");
        dto.setCustomXPath("");
        dto.setIFrameXPath("");
        dto.setShadowHost("");
        dto.setShadowRoot("false");
        dto.setNestedShadow("false");
        dto.setCssSelector("");
        dto.setAttribName("");
        dto.setAttributeValue("");
        dto.setAttributeType("");
        dto.setSearchAttributeValue("");

        return dto;
    }

    private String findLabelForInput(Document doc, String inputId) {
        if (doc == null || inputId == null || inputId.isEmpty()) return "";

        Element root = doc.getDocumentElement();
        return findLabelForInputRec(root, inputId);
    }

    private String findLabelForInputRec(Element el, String inputId) {
        String tag = nz(el.getTagName()).toLowerCase();
        if (tag.equals("label")) {
            String f = nz(el.getAttribute("for"));
            if (inputId.equals(f)) {
                return normalizeSpace(el.getTextContent());
            }
        }

        org.w3c.dom.Node child = el.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                String found = findLabelForInputRec((Element) child, inputId);
                if (!found.isEmpty()) return found;
            }
            child = child.getNextSibling();
        }
        return "";
    }

    private String escapeXPathLiteral(String s) {
        s = nz(s);
        if (!s.contains("'")) return "'" + s + "'";
        if (!s.contains("\"")) return "\"" + s + "\"";

        StringBuilder sb = new StringBuilder("concat(");
        boolean first = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String part;
            if (c == '\'') part = "\"'\"";
            else if (c == '"') part = "'\"'";
            else {
                int j = i;
                while (j < s.length()) {
                    char cj = s.charAt(j);
                    if (cj == '\'' || cj == '"') break;
                    j++;
                }
                part = "'" + s.substring(i, j) + "'";
                i = j - 1;
            }
            if (!first) sb.append(", ");
            sb.append(part);
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildXPathByTagAndIndex(Element el) {
        if (el == null) return "";
        StringBuilder path = new StringBuilder();
        org.w3c.dom.Node current = el;

        while (current != null && current.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element curEl = (Element) current;
            String tag = nz(curEl.getTagName());
            if (tag.isEmpty()) tag = "*";

            int index = getIndexAmongSameTagSiblings(curEl);
            path.insert(0, "/" + tag + "[" + index + "]");

            current = current.getParentNode();
            if (current != null && current.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) break;
        }
        return path.toString();
    }

    private int getIndexAmongSameTagSiblings(Element el) {
        org.w3c.dom.Node parent = el.getParentNode();
        if (parent == null) return 1;

        String tag = nz(el.getTagName());
        int idx = 0;

        org.w3c.dom.Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element ce = (Element) child;
                if (tag.equals(nz(ce.getTagName()))) {
                    idx++;
                    if (ce == el) return idx;
                }
            }
            child = child.getNextSibling();
        }
        return 1;
    }

    private String firstNonEmpty(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            v = nz(v);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private String normalizeSpace(String s) {
        s = nz(s).replace('\u00A0', ' ');
        return s.replaceAll("\\s+", " ").trim();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private Document parseXhtmlToDocument(String xhtml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            // security hardening
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            dbf.setNamespaceAware(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(xhtml)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XHTML into Document: " + e.getMessage(), e);
        }
    }

    public static InputInfo findMatchingInput(List<InputInfo> inputs, InstructionLoad currentInstruction) {
        if (inputs == null || inputs.isEmpty() || currentInstruction == null) {
            return null;
        }

        String instrName = normalize(currentInstruction.getName());
        String instrTag = normalize(currentInstruction.getTagName());

        for (InputInfo info : inputs) {
            if (info == null) continue;

            String inputName = normalize(info.name());
            String inputTag = normalize(info.tag());

            if (instrName.equalsIgnoreCase(inputName) && instrTag.equalsIgnoreCase(inputTag)) {
                return info;
            }
        }

        return null;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private WebElement immediateXPath(String xPath) {
        // Selenium-only helper: no Selenium driver in Playwright-only mode, so there is no live
        // WebElement to return (the caller falls through to the Playwright locate path). Returning
        // null here avoids a NullPointerException on the (uninitialised) waitXPath.
        if (performActions.getCurrentDriver() == null) {
            return null;
        }
        try {
            if (waitXPath == null) {
                waitXPath = new WebDriverWait(performActions.getCurrentDriver(), Duration.ofSeconds(0));
            }
            waitXPath.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xPath)));
            List<WebElement> foundElementList =
                    performActions.getCurrentDriver().findElements(By.xpath(xPath));
            if (foundElementList.size() > 0) {
                return foundElementList.get(0);
            }
        } catch (TimeoutException ignored) {
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isWebElementInstruction(InstructionLoad instr) {
        if (instr == null) return false;

        String actions = instr.getActions();
        if (actions == null) return false;

        String raw = actions.trim();
        if (raw.isEmpty()) return false;

        // split() takes a regex, so quote the splitter to treat it literally
        String[] parts = raw.split(Pattern.quote(ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER), 2);
        String first = parts[0].trim();
        if (first.isEmpty()) return false;

        String upper = first.toUpperCase(Locale.ROOT);

        // Required prefixes: "C" (including "C:"), "I:", "O:"
        if (upper.startsWith("C") || upper.startsWith("I") || upper.startsWith("O")) {
            return true;
        }

        // Optional: support plain operation tokens
        return upper.equals("SET") || upper.equals("GET");
    }

    private int getNavigationTimeSeconds() {
        return scannerBrowserRuntime.navigationTimeSeconds();
    }

    public void writeToFileCSV(String filename, String content) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("CSV export filename is required");
        }
        Path target = Path.of(filename).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalStateException("CSV export folder does not exist: " + parent);
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent, ".arweb-export-", ".tmp");
            Files.writeString(
                    temporary,
                    content == null ? "" : content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            logOperations.info("CSV written to file: {}", target);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to write CSV export: " + target, error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The incomplete file was never published as the configured destination.
                }
            }
        }
    }

    /**
     * Generates the CSV content formatted according to the BancaStato specification.
     *
     * Format rules:
     * - The first line is the header and always starts with "KEY"
     *   followed by the configured delimiter and the ordered column names.
     *
     * - Each data row starts with:
     *     • "EXTERNAL"       if there is only one row
     *     • "EXTERNAL_n"     if multiple rows exist (1-based index)
     *
     * - Column values are written in the exact order defined by {@code tableCSV.columnsCSV}.
     *   Missing values are rendered as empty strings.
     *
     * - The configured end-of-file marker is appended at the end.
     *
     * Example (single row):
     *   KEY|User number
     *   EXTERNAL|434234
     *
     * Example (multiple rows):
     *   KEY|User number
     *   EXTERNAL_1|434234
     *   EXTERNAL_2|353534
     *
     * @param delimiter the column delimiter (e.g. "|")
     * @return the formatted CSV content as a String
     */
    public String getBancaStatoCsvContent(CsvTable tableCSV, String delimiter) {
        return scannerCsvContentService.bancaStatoContent(tableCSV, delimiter);
    }

    private void saveExcelWrite(String newExcelFieldName, CsvTable tableCSV, int exportIndex) {
        if (newExcelFieldName != null && newExcelFieldName.toLowerCase().endsWith(".csv")) {
            String delimiter = tableCSV.getDelimiter();
            if (Strings.isNullOrEmpty(tableCSV.getDelimiter())) {
                delimiter = ",";
            }

            String csvContent = getBancaStatoCsvContent(tableCSV, delimiter);
            logOperations.info(csvContent);
            if (csvContent != null) {
                writeToFileCSV(newExcelFieldName, csvContent);
            }
        } else if (newExcelFieldName != null && newExcelFieldName.toLowerCase().endsWith(".xlsx")) {
            //
            //                    writerExport.insertFieldNameAndValueLastColumn(mapExportRows, exportIndex -
            // 1);
            if (tableCSV != null) {
                // Create a writer bound to this exact target. Reusing the last block's writer can
                // silently publish one configured export into a different workbook.
                ExcelWriter.ExcelChain writerExport = new ExcelWriter(newExcelFieldName, true)
                        .withPurpose("export");
                writerExport.insertCSVContentIntoExcel(tableCSV.getColumns(), tableCSV, exportIndex - 1);
            }
        }
    }

    public void setCurrentColumns(List<String> columns) {
        currentColumnsCSV.clear();
        currentColumnsCSV.addAll(columns);
    }

}
