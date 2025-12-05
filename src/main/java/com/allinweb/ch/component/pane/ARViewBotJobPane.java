package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARElementValueScene;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;

@Slf4j
public class ARViewBotJobPane extends ARPane {

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    //    private static final ARScannedElementScene arScannedElementScene = ARScannedElementScene.getInstance();
    //    private static final ARNewCommandScene arNewCommandScene = ARNewCommandScene.getInstance();
    private static final ARElementValueScene arElementValueScene = ARElementValueScene.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final ARNewHomeBankingScene arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();

    // JCEF singletons
    private static CefApp cefApp;
    private static CefClient cefClient;
    private static boolean cefStarted = false;

    static {
        try {
            if (!cefStarted) {
                // Same style as JcefTest
                CefApp.startup(new String[0]);
                cefStarted = true;

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        CefApp instance = CefApp.getInstance();
                        if (instance != null) {
                            instance.dispose();
                        }
                    } catch (Throwable ignored) {}
                }));
            }

            cefApp = CefApp.getInstance();
            if (cefApp != null) {
                cefClient = cefApp.createClient();
            }
        } catch (Exception e) {
            log.error("Error initializing JCEF", e);
        }
    }

    protected static volatile ARViewBotJobPane instance;

    // Swing controls
    JButton refreshButton;
    JButton openScannerButton;
    JButton editBotJobButton;
    JButton launchBotJobButton;
    JButton saveBotJobButton;
    JButton openExcelFileButton;
    JButton generateExcelButton;
    JButton closeBotJobButton;
    JButton createBATButton;
    JButton componentButton;
    JButton refreshEnvsButton;
    JButton insertSitesdButton;

    JLabel webSiteInfoLabel;
    JLabel botJobNameLabel;
    JLabel botJobDescriptionLabel;
    JLabel currentUrlLabel;

    JTextField botJobNameTextField;
    JTextField botJobDescriptionTextField;

    JComboBox<HomeUrlDTO> homeURLCombo;

    JPanel mainPanel;
    JPanel componentBox; // center container
    JPanel componentContainer; // right side container with components browser

    boolean isComponentBoxVisible = false;

    // JCEF browsers
    private CefBrowser tasksBrowser;
    private CefBrowser compBrowser;
    private Component tasksBrowserUI;
    private Component compBrowserUI;

    private int portInitial;
    private String sessionId;
    private final Gson gson = new Gson();
    private PayloadJson payloadEmpty;
    private boolean firstLoad = true;
    private String previousBotTasks;
    private BlockLoadDTO blockLoad;
    private boolean isEnabledLicence;
    private boolean isEditingBotJob = false;
    private boolean isScannerButtonClicked = false;

    private ARScene arScene;
    private BotJobLoadDTO selectedBotJob;

    // Private constructor to prevent instantiation
    private ARViewBotJobPane() {
        super();
    }

    public static ARViewBotJobPane getInstance() {
        if (instance == null) {
            synchronized (ARViewBotJobPane.class) {
                if (instance == null) {
                    instance = new ARViewBotJobPane();
                }
            }
        }
        return instance;
    }

    private static int getMajorJavaVersion(String version) {
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3)); // e.g., "1.8" -> 8
        } else {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]); // e.g., "17.0.1" -> 17
        }
    }

    public void initialize(ARScene arScene, BotJobLoadDTO selectedBotJob, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.arScene = arScene;
        this.selectedBotJob = selectedBotJob;

        ExcelUtils.createExcelDataFile(selectedBotJob, null);

        ErrorMessage errorMessage = null;
        if (performLists.getListVariablesUser().isEmpty()) {
            errorMessage = performDataBase.loadAllVariablesByCriteria("variable", selectedBotJob.getId(), -1, "");
        }

        if (errorMessage == null && performLists.getListWebPageItems().isEmpty()) {
            errorMessage = performDataBase.loadWebPageFields(selectedBotJob.getId(), "bot_job");
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        //        if (arNewCommandScene.getSplitDTO() != null) {
        //            arNewCommandScene.closeModal();
        //        }

        if (arElementValueScene.getSplitDTO() != null) {
            arElementValueScene.closeModal();
        }

        //        if (arScannedElementScene.getCurrentBotJob() != null
        //                && !arScannedElementScene.getCurrentBotJob().getId().equals(selectedBotJob.getId())) {
        //            callScannerTool();
        //        }

        if (webSiteInfoLabel != null) {
            webSiteInfoLabel.setText(
                    "Web-site Id: " + selectedBotJob.getHomeBankingId() + " Bot Job Id: " + selectedBotJob.getId());
            botJobNameLabel.setText("Bot Job name: " + selectedBotJob.getName());
            botJobDescriptionLabel.setText("Description: " + selectedBotJob.getDescription());
            botJobNameTextField.setText(selectedBotJob.getName());
            botJobDescriptionTextField.setText(selectedBotJob.getDescription());
        }

        if (Strings.isNullOrEmpty(previousBotTasks)) {
            String portSocket = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
            if (portSocket != null) {
                portInitial = Integer.parseInt(portSocket);
            }
        }

        updateHomeUrlLabels();

        if (!webSocketSessionManager.getAllSessions().isEmpty()) {
            refreshGrids();
        }
    }

    private void updateHomeUrlLabels() {
        if (currentUrlLabel != null) {
            HomeUrlDTO homeUrlDTO =
                    performLists.getHomeUrlByBankId(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
            String urlEntryPoint = homeUrlDTO != null
                    ? homeUrlDTO.getUrl()
                    : selectedBotJob.getHomeBankingLoadDTO().getUrl();
            currentUrlLabel.setText(urlEntryPoint);
        }

        if (homeURLCombo != null) {
            populateHomeUrlCombo(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        }
    }

    private void refreshGrids() {
        ErrorMessage errorMessage = performDBEngine.loadCompleteJobs(selectedBotJob.getId());

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        // Updates the Grid After Load
        if (!firstLoad) {
            setPayloadEmpty("botJobTasks");
            String jsonData = gson.toJson(payloadEmpty);

            if (!performLists.getListBotJob().isEmpty()) {
                List<InstructionLoad> instructions = performLists.buildJsonViewData(performLists.getListBotJob());
                if (!instructions.isEmpty()) {
                    jsonData = gson.toJson(instructions);
                }
            }

            webSocketSessionManager.sendMessageJson(
                    selectedBotJob.getHomeBankingId(), "botJobTasks", jsonData, "updateInstructions");
        }

        errorMessage = performDataBase.loadComponentsComplete(
                selectedBotJob.getHomeBankingId(), selectedBotJob.getId(), selectedBotJob.getName());

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        if (!firstLoad) {
            setPayloadEmpty("componentTasks");
            String jsonData = gson.toJson(payloadEmpty);

            if (!performLists.getListBotJobComp().isEmpty()) {
                List<InstructionLoad> instructions = performLists.buildJsonViewData(performLists.getListBotJobComp());
                if (!instructions.isEmpty()) {
                    jsonData = gson.toJson(instructions);
                }
            }

            webSocketSessionManager.sendMessageJson(
                    selectedBotJob.getHomeBankingId(), "componentTasks", jsonData, "componentsUpdate");
        }
    }

    /**
     * Create / load JSON for tasks & components and send it to the JCEF browsers.
     */
    private void buildViewComponent() {
        if (performLists.getListBotJob().isEmpty()) {
            ErrorMessage errorMessage = performDBEngine.loadCompleteJobs(selectedBotJob.getId());
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }

        setPayloadEmpty("botJobTasks");
        String jsonData = gson.toJson(payloadEmpty);

        if (!performLists.getListBotJob().isEmpty()) {
            List<InstructionLoad> instructions = performLists.buildJsonViewData(performLists.getListBotJob());
            if (!instructions.isEmpty()) {
                String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                performMessage.outputJson(instructions, "botJobTasks-" + selectedBotJob.getId(), jsonPath, false);
                jsonData = gson.toJson(instructions);
            }
        }

        if (tasksBrowser == null && cefClient != null) {
            tasksBrowser = cefClient.createBrowser("about:blank", false, false);
            tasksBrowserUI = tasksBrowser.getUIComponent();
        }

        sessionId = "botJobTasks";
        if (tasksBrowser != null) {
            buildBrowser(
                    tasksBrowser,
                    jsonData,
                    portInitial,
                    sessionId,
                    selectedBotJob.getHomeBankingId(),
                    selectedBotJob.getId(),
                    selectedBotJob.getName());
        }

        if (performLists.getListBotJobComp().isEmpty()) {
            ErrorMessage errorMessage = performDataBase.loadComponentsComplete(
                    selectedBotJob.getHomeBankingId(), selectedBotJob.getId(), selectedBotJob.getName());

            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }

        setPayloadEmpty("componentTasks");
        jsonData = gson.toJson(payloadEmpty);

        if (!performLists.getListBotJobComp().isEmpty()) {
            List<InstructionLoad> instructions = performLists.buildJsonViewData(performLists.getListBotJobComp());
            if (!instructions.isEmpty()) {
                String jsonPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                performMessage.outputJson(instructions, "componentTasks-" + selectedBotJob.getId(), jsonPath, false);
                jsonData = gson.toJson(instructions);
            }
        }

        if (compBrowser == null && cefClient != null) {
            compBrowser = cefClient.createBrowser("about:blank", false, false);
            compBrowserUI = compBrowser.getUIComponent();
        }

        sessionId = "componentTasks";
        if (compBrowser != null) {
            buildBrowser(
                    compBrowser,
                    jsonData,
                    portInitial,
                    sessionId,
                    selectedBotJob.getHomeBankingId(),
                    selectedBotJob.getId(),
                    selectedBotJob.getName());
        }

        previousBotTasks = sessionId;
    }

    /**
     * Equivalent of the old WebEngine-based method, but using JCEF.
     */
    private void buildBrowser(
            CefBrowser browser,
            String jsonData,
            int finalPort,
            String sessionIdFromJava,
            int homeBanking,
            int botJobId,
            String botJobName) {

        // 1) Use local HTTP server instead of file
        //    Example: React/SPA served on http://localhost:<finalPort>
        //    If your dev server is *always* 3000, you can hardcode 3000 here.
        String url = "http://localhost:" + finalPort;

        // Load the web app
        browser.loadURL(url);

        // 2) Inject the JSON payload as before
        String safeBotJobName = botJobName.replace("'", "\\'");

        String js =
                "setTimeout(function() { " +
                        "  if (window.receiveDataFromJava) {" +
                        "    window.receiveDataFromJava(" +
                        "      JSON.stringify(" + jsonData + ")," +
                        "      " + finalPort + "," +
                        "      '" + sessionIdFromJava + "'," +
                        "      " + homeBanking + "," +
                        "      " + botJobId + "," +
                        "      '" + safeBotJobName + "'" +
                        "    );" +
                        "  }" +
                        "}, 1000);";

        browser.executeJavaScript(js, url, 0);
    }


    // ==== ARPane overrides ==================================================

    @Override
    public JPanel getPaneReference() {
        return mainPanel;
    }

    @Override
    public void initUIComponents() {
        // Fonts/colors equivalent of the old CSS
        Font labelFont = new Font("SansSerif", Font.BOLD, 14);
        Color labelColor = Color.BLUE;

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(
                new EmptyBorder(ARConstants.SPACE_M, ARConstants.SPACE_M, ARConstants.SPACE_M, ARConstants.SPACE_M));

        // Build JCEF browsers + payloads
        sessionId = "botJobTasks";
        if (Strings.isNullOrEmpty(previousBotTasks) || !previousBotTasks.equals(sessionId)) {
            buildViewComponent();
        }

        // Buttons toolbar (top)
        refreshButton = builder.buildButton("Refresh", ARConstants.SPACE_ZERO);
        openScannerButton = builder.buildButton("Scanner", ARConstants.SPACE_ZERO);
        editBotJobButton = builder.buildButton("Edit Job", ARConstants.SPACE_ZERO);
        launchBotJobButton = builder.buildButton("Launch", ARConstants.SPACE_ZERO);
        saveBotJobButton = builder.buildButton("Save Job", ARConstants.SPACE_ZERO);
        saveBotJobButton.setEnabled(false);
        openExcelFileButton = builder.buildButton("Excel File", ARConstants.SPACE_ZERO);
        generateExcelButton = builder.buildButton("Generate", ARConstants.SPACE_ZERO);
        closeBotJobButton = builder.buildButton("Close", ARConstants.SPACE_ZERO);

        JPanel buttonsPanel = new JPanel(new GridLayout(2, 5, 10, 10));
        buttonsPanel.add(refreshButton);
        buttonsPanel.add(openScannerButton);
        buttonsPanel.add(saveBotJobButton);
        buttonsPanel.add(editBotJobButton);
        buttonsPanel.add(launchBotJobButton);

        buttonsPanel.add(new JLabel()); // spacer
        buttonsPanel.add(new JLabel()); // spacer
        buttonsPanel.add(openExcelFileButton);
        buttonsPanel.add(generateExcelButton);
        buttonsPanel.add(closeBotJobButton);

        // BAT button + website info
        createBATButton = builder.buildButton("", ARConstants.SPACE_L);
        webSiteInfoLabel = new JLabel(
                "Web-site Id: " + selectedBotJob.getHomeBankingId() + " Bot Job Id: " + selectedBotJob.getId());
        webSiteInfoLabel.setFont(labelFont);
        webSiteInfoLabel.setForeground(new Color(0x006400)); // dark green

        JPanel batPanel = new JPanel(new BorderLayout(10, 0));
        batPanel.add(createBATButton, BorderLayout.WEST);
        batPanel.add(webSiteInfoLabel, BorderLayout.CENTER);

        // Home URL combo + refresh + manage envs
        homeURLCombo = new JComboBox<>();
        homeURLCombo.setToolTipText("Select the target URL / environment for the Bot Job");

        refreshEnvsButton = builder.buildButton("↻", ARConstants.SPACE_L);
        insertSitesdButton = builder.buildButton("Orgs / Environments", ARConstants.SPACE_L);

        populateHomeUrlCombo(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());

        HomeUrlDTO homeUrlDTO =
                performLists.getHomeUrlByBankId(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        String urlEntryPoint = homeUrlDTO != null
                ? homeUrlDTO.getUrl()
                : selectedBotJob.getHomeBankingLoadDTO().getUrl();

        currentUrlLabel = new JLabel(urlEntryPoint);
        currentUrlLabel.setFont(labelFont);
        currentUrlLabel.setForeground(labelColor);

        JPanel urlPanel = new JPanel();
        urlPanel.setLayout(new BoxLayout(urlPanel, BoxLayout.X_AXIS));
        urlPanel.add(currentUrlLabel);
        urlPanel.add(Box.createHorizontalStrut(10));
        urlPanel.add(homeURLCombo);
        urlPanel.add(Box.createHorizontalStrut(5));
        urlPanel.add(refreshEnvsButton);
        urlPanel.add(Box.createHorizontalStrut(5));
        urlPanel.add(insertSitesdButton);

        // BotJob name / description labels + edit fields
        botJobNameLabel = new JLabel(selectedBotJob.getName());
        botJobNameLabel.setFont(labelFont);
        botJobNameLabel.setForeground(labelColor);

        botJobNameTextField = new JTextField(selectedBotJob.getName());

        botJobDescriptionLabel = new JLabel(selectedBotJob.getDescription());
        botJobDescriptionLabel.setFont(labelFont);
        botJobDescriptionLabel.setForeground(labelColor);

        botJobDescriptionTextField = new JTextField(selectedBotJob.getDescription());

        JPanel namePanel = new JPanel();
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.X_AXIS));
        namePanel.add(botJobNameLabel);
        namePanel.add(Box.createHorizontalStrut(5));
        namePanel.add(botJobNameTextField);

        JPanel descPanel = new JPanel();
        descPanel.setLayout(new BoxLayout(descPanel, BoxLayout.X_AXIS));
        descPanel.add(botJobDescriptionLabel);
        descPanel.add(Box.createHorizontalStrut(5));
        descPanel.add(botJobDescriptionTextField);

        // Component toggle button
        initComponentButton();

        // Info box (name + desc + URL)
        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.add(namePanel);
        infoBox.add(Box.createVerticalStrut(5));
        infoBox.add(descPanel);
        infoBox.add(Box.createVerticalStrut(5));
        infoBox.add(urlPanel);

        JPanel mainInfoRow = new JPanel();
        mainInfoRow.setLayout(new BoxLayout(mainInfoRow, BoxLayout.X_AXIS));
        mainInfoRow.add(infoBox);
        mainInfoRow.add(Box.createHorizontalStrut(10));
        mainInfoRow.add(componentButton);

        // Center area with tasks browser (always) + components browser (toggle)
        componentBox = new JPanel(new BorderLayout());
        if (tasksBrowserUI != null) {
            componentBox.add(tasksBrowserUI, BorderLayout.CENTER);
        }

        componentContainer = new JPanel(new BorderLayout());
        componentContainer.setPreferredSize(new Dimension(400, 400));
        componentContainer.setVisible(false); // initially hidden

        componentBox.add(componentContainer, BorderLayout.EAST);

        firstLoad = false;

        // NORTH: buttons + BAT + info
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.add(buttonsPanel);
        northPanel.add(Box.createVerticalStrut(ARConstants.SPACE_SM));
        northPanel.add(batPanel);
        northPanel.add(Box.createVerticalStrut(ARConstants.SPACE_SM));
        northPanel.add(mainInfoRow);

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(componentBox, BorderLayout.CENTER);

        // Start in view mode (not editing)
        applyEditMode(false);
    }

    @Override
    public void initUIBehaviour() {
        // Reload envs
        refreshEnvsButton.addActionListener(e -> reloadEnvs());

        // Open "Orgs / Environments" scene
        insertSitesdButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            if (performLists.getListHomeBanking().isEmpty()) {
                performDBEngine.loadHomeBanking(null);
            }
            if (performLists.getListHomeUrl().isEmpty()) {
                performDBEngine.loadHomeUrls(null);
            }

            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
            arNewHomeBankingScene.initialize(homeBank);

            Window parent = SwingUtilities.getWindowAncestor(mainPanel);
            // assuming you adapted ARNewHomeBankingScene to Swing:
            arNewHomeBankingScene.showModal(parent);

            if (homeURLCombo != null) {
                populateHomeUrlCombo(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
            }
        });

        // Create BAT
        createBATButton.addActionListener(e -> {
            ARPropertyManager managerProps = arPropertyManager;
            String enginePath = managerProps.getProperty(ARPropertyEnum.PATH_ENGINE);
            String excelPath = managerProps.getProperty(ARPropertyEnum.PATH_EXCEL);
            excelPath = excelPath + "\\" + selectedBotJob.getName() + ".xlsx";
            if (!new File(excelPath).exists()) {
                log.error("Action Required: Prepare Excel Data");
                performMessage.errorMessage(
                        "Action Required: Prepare Excel Data",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Crucial Step: Prepare Excel Data Before Launch!</span>",
                        "<span style='color: #2E7D32; font-weight: bold;'>To successfully initiate the bot job, the Excel data file must be generated and compiled *first*.</span>",
                        "<span style='font-style: italic;'>Ensure this preparation is complete before attempting to launch the automation process.</span>",
                        null,
                        0);

                return;
            }

            String configPath = System.getProperty("ARWebConfig");
            createBatFile(excelPath, enginePath, configPath);
        });

        // Refresh grids
        refreshButton.addActionListener(e -> refreshGrids());

        // Edit / Save Bot Job
        editBotJobButton.addActionListener(e -> {
            isEditingBotJob = !isEditingBotJob;
            applyEditMode(isEditingBotJob);
            reloadEnvs();
        });

        saveBotJobButton.addActionListener(e -> {
            isEditingBotJob = false;
            applyEditMode(false);

            String rawName = nameFileOnWindows(botJobNameTextField.getText().trim());
            botJobNameTextField.setText(rawName);

            HomeUrlDTO selectedUrl = (HomeUrlDTO) homeURLCombo.getSelectedItem();
            if (selectedUrl != null && selectedUrl.getId() > 0) {
                ErrorMessage errorMessage = performDataBase.updateBotJobDetails(
                        selectedBotJob.getId(),
                        selectedUrl.getId(),
                        botJobNameTextField.getText().trim(),
                        botJobDescriptionTextField.getText().trim());

                if (errorMessage == null) {
                    botJobNameLabel.setText(botJobNameTextField.getText());
                    botJobDescriptionLabel.setText(botJobDescriptionTextField.getText());

                    selectedBotJob.setName(botJobNameLabel.getText());
                    selectedBotJob.setDescription(botJobDescriptionLabel.getText());
                    selectedBotJob.setHomeUrlId(selectedUrl.getId());

                    updateHomeUrlLabels();

                    performMessage.showCustomModalDialogDragWin11(
                            "Update Bot Job Details ✅",
                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Bot Job updated successfully!</span>",
                            "<span style='color: #1565C0; font-weight: bold;'>The Bot Job details have been saved and are now active.</span>",
                            "<span style='color: #6A1B9A; font-weight: bold;'>Bot Job:</span> "
                                    + botJobNameTextField.getText(),
                            "<span style='color: #E65100; font-weight: bold;'>💡 Tip:</span> You can now refresh your view to see the updated details.",
                            false,
                            "OK",
                            null,
                            0);
                } else {
                    if (errorMessage.getErrorMessage() != null
                            && errorMessage.getErrorMessage().contains("unique constraint")) {
                        errorMessage.setErrorMessage("Verify existents name for: " + botJobNameTextField.getText());
                    }
                    performMessage.showCustomModalDialogDragWin11(
                            "Update Bot Job Details ❌",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to update Bot Job!</span>",
                            "<span style='color: #1565C0; font-weight: bold;'>There was an error while saving the Bot Job details.</span>",
                            "<span style='color: #6A1B9A; font-weight: bold;'>Bot Job:</span> "
                                    + botJobNameTextField.getText(),
                            "<span style='font-style: italic;'>Details: " + botJobNameTextField.getText() + "</span>",
                            false,
                            "OK",
                            null,
                            0);
                }

                if (performDataBase.isConnDBWorks()) {
                    try {
                        performDataBase.loadQuickBotJobs();
                    } catch (Exception error) {
                        throw error;
                    }
                }
            }
        });

        // Scanner
        openScannerButton.addActionListener(e -> callScannerTool());

        // Generate Excel
        generateExcelButton.addActionListener(e -> {
            ErrorMessage errorMessage = null;
            if (performLists.getQuickBotJobs().isEmpty()) {
                errorMessage = performDataBase.loadQuickBotJobs();
            }

            if (errorMessage == null && !performLists.getListBotJob().isEmpty()) {
                if (performLists.getListBlock().isEmpty()) {
                    errorMessage =
                            performDataBase.loadBlocks(selectedBotJob.getId(), selectedBotJob.getName(), "block");
                }

                if (errorMessage == null) {
                    errorMessage = performDBEngine.loadAllActionsPerBlock(performLists.getListBlock());
                }

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }

                ExtractedData extractedData =
                        ExcelUtils.isFileExists(selectedBotJob.getName(), performLists.getAllActions());

                if (extractedData != null && extractedData.getErrorMessage() != null) {
                    performMessage.errorMessage(
                            "Excel Error",
                            "Could Not Execute Excel File",
                            extractedData.getErrorMessage(),
                            null,
                            null,
                            0);
                    return;
                }

                Runnable excelTask = () -> {
                    try {
                        new ExcelUtils().generateExcelFiles(extractedData, selectedBotJob.getName(), null, true);
                    } catch (Exception ex) {
                        log.error("Error generating Excel files", ex);
                    }
                };

                String excelFile = selectedBotJob.getName() + ARConstants.FILE_FORMAT_EXCEL;

                if (extractedData != null) {
                    ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                            "Warning: Excel File Already Exists",
                            "<span style='color: #000080; font-weight: bold; font-size: 14px;'>An Excel file with this name already exists. Do you want to overwrite it?</span>",
                            "<span style='color: #000080; font-weight: bold;'>" + excelFile + "</span>",
                            "<span style='color: red; font-weight: bold;'>OVERWRITING WILL DELETE ANY DATA NOT PRESENT IN THE CURRENT JOB.</span>",
                            "<span style='color: red; font-weight: bold;'>NEW COLUMNS WILL BE ADDED AND VALUE SET AS \"CHANGE ME\".</span>",
                            true,
                            "Overwrite",
                            "Cancel",
                            0);

                    if (!respModal.equals(ARExecution.DialogModal.STOP)) {
                        new Thread(excelTask).start();
                        log.warn("Warning: Excel File Already Exists");
                        performMessage.errorMessage(
                                "Warning: Excel File Already Exists",
                                "<span style='color: #000080; font-weight: bold; font-size: 14px;'>Success Excel File Override.</span>",
                                "<span style='color: #000080; font-weight: bold;'>" + excelFile + "</span>",
                                null,
                                null,
                                0);
                    }
                } else {
                    new Thread(excelTask).start();
                    log.warn("Warning: New Excel File Created!");
                    performMessage.errorMessage(
                            "Warning: New Excel File Created!",
                            "<span style='color: #000080; font-weight: bold; font-size: 14px;'>Success Excel File Generated.</span>",
                            "<span style='color: #000080; font-weight: bold;'>" + excelFile + "</span>",
                            null,
                            null,
                            0);
                }
            }
        });

        // Launch Bot Job
        launchBotJobButton.addActionListener(e -> {
            ARPropertyManager managerProps = arPropertyManager;
            String enginePath = managerProps.getProperty(ARPropertyEnum.PATH_ENGINE);
            String excelPath = managerProps.getProperty(ARPropertyEnum.PATH_EXCEL);
            excelPath = excelPath + "\\" + selectedBotJob.getName() + ".xlsx";
            if (!new File(excelPath).exists()) {
                log.error("Action Required: Prepare Excel Data");
                performMessage.errorMessage(
                        "Action Required: Prepare Excel Data",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Crucial Step: Prepare Excel Data Before Launch!</span>",
                        "<span style='color: #2E7D32; font-weight: bold;'>To successfully initiate the bot job, the Excel data file must be generated and compiled *first*.</span>",
                        "<span style='font-style: italic;'>Ensure this preparation is complete before attempting to launch the automation process.</span>",
                        null,
                        0);
                return;
            }

            String version = System.getProperty("java.version");
            log.info("Detected Java Version: " + version);

            int majorVersion = getMajorJavaVersion(version);
            if (majorVersion >= 17) {
                log.info("✅ Java 17 or higher is installed.");
            } else {
                log.error("Compatibility Issue: Incompatible Java Version");
                performMessage.errorMessage(
                        "Compatibility Issue: Incompatible Java Version",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: Your Java version is lower than the required 17!</span>",
                        "<span style='color: #2E7D32; font-weight: bold;'>Attempting to execute the Engine with this older version may lead to unexpected behavior or failures.</span>",
                        "<span style='font-style: italic;'>Please upgrade your Java installation to version 17 or higher for optimal performance and stability.</span>",
                        null,
                        0);
            }

            String webDriverPath = managerProps.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
            if (!(new File(webDriverPath)).exists()) {
                log.error("Action Required: Missing WebDriver");
                performMessage.errorMessage(
                        "Action Required: Missing WebDriver",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: The WebDriver file is missing!</span>",
                        "<span style='color: #2E7D32; font-weight: bold;'>To execute automated browser interactions, the WebDriver is absolutely essential.</span>",
                        "<span style='font-style: italic;'>Please download the correct WebDriver for your browser and ensure it is accessible by the application.</span>",
                        null,
                        0);
                return;
            }

            if (!selectedBotJob.getPriority().equalsIgnoreCase("Web App")) {
                performMessage.errorMessage(
                        "Mobile Bot Job Selected",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Mobile Bot Jobs can only be executed from AR Mobile!</span>",
                        "<span style='color: #2E7D32; font-weight: bold;'>Please run \"AR Mobile\" to launch the Bot Job tests.</span>",
                        null,
                        null,
                        0);
                return;
            }

            String[] command = new String[] {
                "cmd.exe",
                "/c",
                "java.exe",
                "-jar",
                "\"" + enginePath + "\"",
                "execute/j",
                String.valueOf(selectedBotJob.getHomeBankingLoadDTO().getId()),
                String.valueOf(selectedBotJob.getId()),
                String.valueOf(1),
                "\"" + excelPath + "\"",
                "-c",
                arPropertyManager.getConfigurationFileName()
            };
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(ARConstants.USER_PATH));
            String logPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LOG);
            File output = new File(logPath + "\\engine_debug_log_output.log");
            File error = new File(logPath + "\\engine_debug_log_error.log");
            File input = new File(logPath + "\\engine_debug_log_input.log");
            List<File> files = new ArrayList<>();
            files.add(output);
            files.add(error);
            files.add(input);

            for (File file : files) {
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }

            processBuilder.redirectOutput(output);
            processBuilder.redirectError(error);
            processBuilder.redirectInput(input);

            try {
                processBuilder.start();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        // Close
        closeBotJobButton.addActionListener(e -> {
            log.info("Close Bot Job Button");
            SwingUtilities.invokeLater(() -> {
                Window window = SwingUtilities.getWindowAncestor(mainPanel);
                if (window != null) {
                    window.dispose();
                }
            });
        });

        // Open Excel
        openExcelFileButton.addActionListener(e -> {
            String excelFolderPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
            String fileName =
                    String.format("%s/%s%s", excelFolderPath, selectedBotJob.getName(), ARConstants.FILE_FORMAT_EXCEL);

            File fileCheck = new File(fileName);
            if (!fileCheck.exists() && !fileCheck.isDirectory()) {
                log.error("File Not Found: {}", fileName);
                performMessage.errorMessage(
                        "File Not Found",
                        "<span style='color: #000080; font-weight: bold; font-size: 14px;'>File does not exist:</span>",
                        "<span style='color: #000080; font-weight: bold;'>" + fileName + "</span>",
                        "<span style='font-style: italic;'>Details:</span>",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please ensure the file path is correct and the file is present.</span>",
                        0);
            } else {
                try {
                    String excelFilePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
                    excelFilePath = excelFilePath + "\\" + selectedBotJob.getName() + ".xlsx";
                    File file = new File(excelFilePath);
                    Desktop.getDesktop().open(file);
                } catch (IOException ex) {
                    log.error("Error loading Excel Rows. Maybe it is better to re-generate the file: {}", fileName);
                    performMessage.errorMessage(
                            "Excel File Error",
                            "<span style='color: #000080; font-weight: bold; font-size: 14px;'>Check All Excel Columns and Values!</span>",
                            "<span style='color: #000080; font-weight: bold;'></span>",
                            "<span style='font-style: italic;'>Details:</span>",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Error loading Excel Rows. Maybe it is better to re-generate the file.</span>",
                            0);
                }
            }
        });

        // Toggle components panel
        componentButton.addActionListener(e -> {
            isComponentBoxVisible = !isComponentBoxVisible;
            componentContainer.setVisible(isComponentBoxVisible);
            componentBox.revalidate();
            componentBox.repaint();
        });
    }

    // ==== helpers ===========================================================

    private void reloadEnvs() {
        performDBEngine.loadHomeUrls(null);
        if (homeURLCombo != null) {
            populateHomeUrlCombo(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        }
    }

    public void createBatFile(String excelFilePath, String enginePath, String configPath) {
        String batFileName =
                "execute_Website_" + selectedBotJob.getHomeBankingId() + "_Botjob_" + selectedBotJob.getId() + ".bat";

        String basePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String batFilePath = basePath + File.separator + batFileName;

        String javaCommand = "java.exe -jar \"" + enginePath + "\" execute/j "
                + selectedBotJob.getHomeBankingId() + " " + selectedBotJob.getId() + " " + 1 + " \"" + excelFilePath
                + "\" -c \"" + configPath + "\"";

        try (FileWriter writer = new FileWriter(batFilePath)) {
            writer.write(javaCommand);

            performMessage.showCustomModalDialogDragWin11(
                    "BAT File Creation",
                    "<span style='color: #00695C; font-weight: bold; font-size: 1.1em;'>BAT file created at:</span>",
                    "<span style='color: #2E7D32; font-weight: bold;'></span> <span style='font-weight: bold;'>"
                            + basePath + "</span>",
                    "<span style='color: #00695C; font-weight: bold; font-size: 1.1em;'>BAT file name:</span>",
                    "<span style='color: #2E7D32; font-weight: bold;'></span> <span style='font-weight: bold;'>"
                            + batFileName + "</span>",
                    false,
                    "OK",
                    null,
                    0);

            log.info("BAT file created at: " + batFilePath);
        } catch (IOException error) {
            log.error("Error creating BAT file: " + error.getMessage());
            performMessage.errorMessage(
                    "BAT File Creation Error",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to create file:</span>",
                    "<span style='font-weight: bold;'>" + batFilePath + "</span>.",
                    "<span style='color: #E65100; font-weight: bold;'>Please verify the application has the necessary write permissions for the directory.</span>",
                    "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                    0);
        }
    }

    private void callScannerTool() {
        if (arPropertyManager.missingMandatoryPats()) {
            return;
        }

        if (!isScannerButtonClicked) {
            isScannerButtonClicked = true;
            log.info("Calling openScannerButton");

            String threadName = "botJob-" + selectedBotJob.getId();
            arScene.startNewThread(threadName, () -> {
                executeScannerTask();
                isScannerButtonClicked = false;
            });
        }
    }

    public HomeUrlDTO findMatchingHomeUrlDTO(BotJobLoadDTO botJobLoadDTO) {
        Integer targetHomeUrlId = botJobLoadDTO.getHomeUrlId();
        HomeBankingLoadDTO homeBanking = botJobLoadDTO.getHomeBankingLoadDTO();

        if (homeBanking != null && homeBanking.getHomeUrlDTOs() != null) {
            return homeBanking.getHomeUrlDTOs().stream()
                    .filter(dto -> dto.getId().equals(targetHomeUrlId))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private void executeScannerTask() {
        ErrorMessage errorMessage = performDBEngine.loadHomeBanking(selectedBotJob.getHomeBankingId());
        if (errorMessage == null) {
            errorMessage = performDBEngine.loadHomeUrls(selectedBotJob.getHomeBankingId());
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        HomeBankingLoadDTO homeBanking = performLists.getListHomeBanking().isEmpty()
                ? null
                : performLists.getListHomeBanking().get(0);

        HomeUrlDTO homeUrlDTO = findMatchingHomeUrlDTO(selectedBotJob);
        if (homeUrlDTO != null) {
            selectedBotJob.setHomeUrlId(homeUrlDTO.getId());
            homeBanking.setUrl(homeUrlDTO.getUrl());
        }

        if (selectedBotJob.getBlockLoadDTOList() != null
                && !selectedBotJob.getBlockLoadDTOList().isEmpty()) {
            this.blockLoad = selectedBotJob.getBlockLoadDTOList().get(0);
        } else {
            errorMessage = performDataBase.loadBlocks(selectedBotJob.getId(), selectedBotJob.getName(), "block");
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
            if (!performLists.getListBlock().isEmpty()) {
                this.blockLoad = performLists.getListBlock().get(0);
            }
        }

        try {
            //            arScannedElementScene.initialize(homeBanking, selectedBotJob, this.blockLoad);
            //            arScannedElementScene.showModal();
        } catch (Exception ex) {
            handleExceptionScan(ex);
        }
    }

    private void handleExceptionScan(Exception error) {
        log.error("ERROR Calling openScannerButton -> Cause: " + error.getMessage());

        String browser = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
        String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);

        if (error.getMessage().contains("no such window: target window already closed")
                || error.getMessage().contains("web view not found")) {
            log.error("Error Calling SCANNER: {}", webDriverPath);
            performMessage.errorMessage(
                    "Error Calling SCANNER",
                    "<span style='font-style: italic;'>Web Browser was closed before the Scanner Tool!</span>",
                    "<span style='color: #E65100; font-weight: bold;'>WebDriver path:</span> <span style='font-weight: bold;'>"
                            + webDriverPath + "</span>",
                    "<span style='font-style: italic;'>Please close and Re-Open the Scanner Tool.</span>",
                    "<span style='font-style: italic;'>Details: " + "Web Browser was closed before the Scanner Tool"
                            + "</span>",
                    0);
        } else {
            if (!error.getMessage().contains("Current browser version")) {
                log.error("Error Open URL: " + error.getMessage());

                if (error.getMessage().contains("session deleted as the browser has closed the connection")
                        || error.getMessage().contains("Expected condition failed: waiting for com")) {
                    log.error("Interruption Calling SCANNER: {}", webDriverPath);
                    performMessage.errorMessage(
                            "Interruption Calling SCANNER",
                            "<span style='font-style: italic;'>Session deleted as the browser has closed the connection!</span>",
                            "<span style='color: #E65100; font-weight: bold;'>WebDriver path:</span> <span style='font-weight: bold;'>"
                                    + webDriverPath + "</span>",
                            "<span style='font-style: italic;'>Please close and Re-Open the Scanner Tool.</span>",
                            "<span style='font-style: italic;'>Details: "
                                    + "Web Browser was closed before the Scanner Tool" + "</span>",
                            0);
                } else {
                    log.error("WebDriver Access Issue: Browser: {} - {}", browser, webDriverPath);
                    performMessage.errorMessage(
                            "WebDriver Access Issue",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to access WebDriver.</span>",
                            "<span style='font-weight: bold;'>Please ensure the following:</span>",
                            "<ul>"
                                    + "<li>No other instances of the browser or WebDriver are currently running.</li>"
                                    + "<li>The specified WebDriver path is correct and accessible: <span style='font-weight: bold;'>"
                                    + webDriverPath + "</span></li>"
                                    + "<li>The configured browser is: <span style='font-weight: bold;'>"
                                    + browser + "</span></li>"
                                    + "</ul>",
                            "<span style='font-style: italic;'>If the issue persists, try closing all related browser processes and restarting the application.</span>",
                            0);
                }
            } else {
                log.error("Error Open URL: " + error.getMessage());

                int lastSlashIndex = webDriverPath.lastIndexOf('\\');
                String directoryPath = webDriverPath.substring(0, lastSlashIndex + 1);
                String fileName = webDriverPath.substring(lastSlashIndex + 1);

                log.error("Invalid URL or Navigation Error: {} - {} - {}", browser, directoryPath, fileName);
                performMessage.errorMessage(
                        "Invalid URL or Navigation Error",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The provided URL is invalid or cannot be reached.</span>",
                        "<span style='font-weight: bold;'>Please verify the following:</span>",
                        "<ul>"
                                + "<li>The entered URL is valid and accessible.</li>"
                                + "<li>The installed browser version: <span style='color: #008b8b; font-weight: bold;'>"
                                + browser + "</span></li>"
                                + "<li>The WebDriver path:<br><span style='color: #008b8b; font-weight: bold;'>"
                                + directoryPath + "</span></li>"
                                + "<li>The WebDriver file:<br><span style='color: #008b8b; font-weight: bold;'>"
                                + fileName + "</span></li>"
                                + "<li>Ensure the WebDriver and browser are compatible and correctly configured.</li>"
                                + "</ul>",
                        "<span style='font-style: italic;'>Check the URL format (e.g., including https://) and review browser/WebDriver logs for more details.</span>",
                        0);
            }
        }
    }

    public BotJobLoadDTO getBotJobDTO() {
        return selectedBotJob;
    }

    public void destroy() {
        clearPane(getPaneReference());
        pane = null;
        //        scene = null;
        instance = null;
    }

    private void setPayloadEmpty(String destination) {
        int blockId = -1;
        String blockName = "1# Default Block";
        if (destination.equalsIgnoreCase("botJobTasks")) {
            if (!performLists.getListBotJob().isEmpty()
                    && performLists.getListBlock().isEmpty()) {
                ErrorMessage errorMessage = performDataBase.loadBlocks(selectedBotJob.getId(), "", "block");
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }
            if (selectedBotJob.getBlockId() == null
                    && !performLists.getListBlock().isEmpty()) {
                blockId = performLists.getListBlock().get(0).getId();
                blockName = performLists.getListBlock().get(0).getName();
            }
        } else if (destination.equalsIgnoreCase("componentTasks")) {
            if (!performLists.getListBotJobComp().isEmpty()
                    && performLists.getListBlockComp().isEmpty()) {
                ErrorMessage errorMessage =
                        performDataBase.loadBlocks(selectedBotJob.getHomeBankingId(), "", "component_block");

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }

            if (selectedBotJob.getBlockId() == null
                    && !performLists.getListBlockComp().isEmpty()) {
                blockId = performLists.getListBlockComp().get(0).getId();
                blockName = performLists.getListBlockComp().get(0).getName();
            }
        }

        this.payloadEmpty = new PayloadJson(selectedBotJob.getId(), blockId, blockName, 0);
    }

    private void populateHomeUrlCombo(int homeBankId, int currentHomeUrlId) {
        homeURLCombo.removeAllItems();

        List<HomeUrlDTO> homeUrlFiltered = performLists.getHomeUrlsByBankId(homeBankId);

        if (homeUrlFiltered.isEmpty()) {
            HomeUrlDTO noEnv = new HomeUrlDTO(-1, null, -1, "No Environment Defined");
            homeURLCombo.addItem(noEnv);
            homeURLCombo.setEnabled(false);
        } else {
            for (HomeUrlDTO dto : homeUrlFiltered) {
                homeURLCombo.addItem(dto);
            }
            homeURLCombo.setEnabled(true);

            for (int i = 0; i < homeURLCombo.getItemCount(); i++) {
                HomeUrlDTO item = homeURLCombo.getItemAt(i);
                if (item.getId() == currentHomeUrlId) {
                    homeURLCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        homeURLCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel lbl = new JLabel();
            if (value != null) {
                if (value.getUrl() != null) {
                    lbl.setText(value.getOrgName() + " | " + value.getUrl());
                } else {
                    lbl.setText(value.getOrgName());
                }
            } else {
                lbl.setText("");
            }
            if (isSelected) {
                lbl.setBackground(list.getSelectionBackground());
                lbl.setForeground(list.getSelectionForeground());
                lbl.setOpaque(true);
            }
            return lbl;
        });
    }

    private boolean checkLicense() {
        try {
            String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
            if (Strings.isNullOrEmpty(licensePath)) {
                licensePath = System.getProperty("user.dir");
            }

            LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

            String msgValid = "The license file is valid and the application is authorized for use.";
            String msgNextStep = "You can now proceed with normal application usage.";
            String msgColor = "#0277BD";

            if (!licenseStatus.equals(LicenceVal.VALID)) {
                msgValid = "The license file is not valid and the application is not authorized for use.";
                msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
                msgColor = "#C62828";

                performMessage.showCustomModalDialogDragWin11(
                        "License Status Verification",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>License status has been successfully verified.</span>",
                        "<span style='color: " + msgColor + "; font-weight: bold;'>" + msgValid + "</span>",
                        "<span style='font-style: italic;'>" + msgNextStep + "</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Current license status:</span> <span style='font-weight: bold;'>"
                                + licenseStatus.getStaus() + "</span>",
                        false,
                        "OK",
                        null,
                        0);
                return false;
            }
            return true;
        } catch (Exception error) {
            log.error("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }

    private void initComponentButton() {
        componentButton = builder.buildButton("Components", ARConstants.SPACE_L);

        JLabel searchLabel = new JLabel("Find");
        JTextField searchField = new JTextField();

        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new BoxLayout(searchPanel, BoxLayout.X_AXIS));
        searchPanel.add(searchLabel);
        searchPanel.add(Box.createHorizontalStrut(5));
        searchPanel.add(searchField);

        // searchField listener – currently only log
        searchField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void update() {
                String text = searchField.getText();
                if (text != null && !text.isEmpty()) {
                    log.info(text + " Text");
                }
            }
        });

        // componentContainer already created in initUIComponents; fill with JCEF browser
        if (compBrowserUI != null) {
            componentContainer.add(searchPanel, BorderLayout.NORTH);
            componentContainer.add(compBrowserUI, BorderLayout.CENTER);
        } else {
            componentContainer.add(searchPanel, BorderLayout.NORTH);
        }
    }

    private void applyEditMode(boolean editing) {
        botJobNameLabel.setVisible(!editing);
        botJobDescriptionLabel.setVisible(!editing);
        currentUrlLabel.setVisible(!editing);

        botJobNameTextField.setVisible(editing);
        botJobDescriptionTextField.setVisible(editing);
        homeURLCombo.setVisible(editing);
        refreshEnvsButton.setVisible(editing);
        insertSitesdButton.setVisible(editing);

        saveBotJobButton.setEnabled(editing);
    }

    private String nameFileOnWindows(String rawName) {
        String safeFileName = rawName.replaceAll("[\\\\/:*?\"<>|]", "");
        safeFileName = safeFileName.replaceAll("[\\p{Cntrl}]", "").trim();

        if (safeFileName.isEmpty()) {
            safeFileName = "default_name";
        }

        if (safeFileName.length() > 100) {
            safeFileName = safeFileName.substring(0, 100);
        }
        return safeFileName;
    }

    /**
     * Simple DocumentListener adapter to use lambdas.
     */
    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update();

        @Override
        default void insertUpdate(javax.swing.event.DocumentEvent e) {
            update();
        }

        @Override
        default void removeUpdate(javax.swing.event.DocumentEvent e) {
            update();
        }

        @Override
        default void changedUpdate(javax.swing.event.DocumentEvent e) {
            update();
        }
    }
}
