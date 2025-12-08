package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARConfigurationScene;
import com.allinweb.ch.component.scene.ARInfoScene;
import com.allinweb.ch.component.scene.ARNewBotJobScene;
import com.allinweb.ch.component.scene.ARSaveCloneScene;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

import static java.awt.Component.LEFT_ALIGNMENT;

@Slf4j
public class ARMainPane extends ARPane {

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private static final ARInfoScene arInfoScene;
    private static final ARPropertyManager arPropertyManager;
    private static final PerformLists performLists;
    private static final PerformDBEngine performDBEngine;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final ARConfigurationScene arConfigurationScene;
    private static final ARViewBotJobScene arViewBotJobScene;
    private static final ARSaveCloneScene arSaveCloneScene;
    private static final ARNewBotJobScene arNewBotJobScene;
    private static final ARWebDriver arWebDriver;
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();

    private BotJobListCell botJobListCellRenderer;

    protected static volatile ARMainPane instance;

    static {
        arInfoScene = ARInfoScene.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
        arNewBotJobScene = ARNewBotJobScene.getInstance();
        performLists = PerformLists.getInstance();
        performDBEngine = PerformDBEngine.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        arConfigurationScene = ARConfigurationScene.getInstance();
        arViewBotJobScene = ARViewBotJobScene.getInstance();
        arSaveCloneScene = ARSaveCloneScene.getInstance();
        arWebDriver = ARWebDriver.getInstance();
    }

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed

    // UI components (Swing)
    private JButton newBotJobButton;
    private JButton cloneBotJobButton;
    private JButton configureButton;
    private JButton infoButton;
    private JButton editBotJobButton;
    private JButton launchBotJobButton;
    private JButton exitButton;
    private JButton aiButton;
    private JTextArea aiTextArea;

    private JPanel buttonPane;
    private JPanel headerPanel;
    private JPanel panelPane;

    private JList<BotJobLoadDTO> viewBotJobListView;
    private DefaultListModel<BotJobLoadDTO> botJobListModel = new DefaultListModel<>();

    private boolean isEnabledLicence;

    @Getter
    private List<WebDriver> webDriverList;

    private Properties properties = new Properties();
    private BotJobLoadDTO selecBotJobDTO;

    private ARMainPane() {
        super();
    }

    public static ARMainPane getInstance() {
        if (instance == null) {
            synchronized (ARMainPane.class) {
                if (instance == null) {
                    instance = new ARMainPane();
                }
            }
        }
        return instance;
    }

    private static int getMajorJavaVersion(String version) {
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        } else {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]);
        }
    }

    public void initialize(List<WebDriver> webDriverList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.webDriverList = webDriverList;

        botJobListModel.clear();
        ErrorMessage errorMessage = performDataBase.loadQuickBotJobs();
        if (!performDataBase.dbFailed && errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
        for (BotJobLoadDTO dto : performLists.getQuickBotJobs()) {
            botJobListModel.addElement(dto);
        }
    }

    @Override
    public void initUIComponents() {
        int smallHeight = ARConstants.SPACE_S;
        int smallIconSize = ARConstants.SPACE_S;
        Insets smallPadding = new Insets(4, 6, 4, 6);

        newBotJobButton = builder.buildButton("New", smallHeight, ARConstants.ICON_NEW, smallIconSize, smallPadding);
        cloneBotJobButton =
                builder.buildButton("Clone Job", smallHeight, ARConstants.ICON_SAVE, smallIconSize, smallPadding);
        configureButton =
                builder.buildButton("Config", smallHeight, ARConstants.ICON_CONFIG, smallIconSize, smallPadding);
        infoButton = builder.buildButton("Info", smallHeight, ARConstants.ICON_INFO, smallIconSize, smallPadding);
        editBotJobButton =
                builder.buildButton("Open Job", smallHeight, ARConstants.ICON_EDIT, smallIconSize, smallPadding);
        launchBotJobButton =
                builder.buildButton("Launch", smallHeight, ARConstants.ICON_PLAY, smallIconSize, smallPadding);
        exitButton = builder.buildButton("Exit", smallHeight, ARConstants.ICON_CROSS, smallIconSize, smallPadding);

        aiButton = builder.buildButton(
                "AI", ARConstants.SPACE_L, ARConstants.ICON_AI, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        aiButton.setVisible(false);

        aiTextArea = new JTextArea();
        aiTextArea.setText("AI Tool: Upgrade your version to access this premium feature.");
        aiTextArea.setEditable(false);
        aiTextArea.setLineWrap(true);
        aiTextArea.setWrapStyleWord(true);
        aiTextArea.setVisible(false);
        aiTextArea.setRows(4);

        int buttonWidth = 100;
        newBotJobButton.setPreferredSize(new Dimension(buttonWidth, (int) smallHeight));
        cloneBotJobButton.setPreferredSize(new Dimension(buttonWidth, (int) smallHeight));
        configureButton.setPreferredSize(new Dimension(buttonWidth, (int) smallHeight));
        infoButton.setPreferredSize(new Dimension(buttonWidth, (int) smallHeight));
        launchBotJobButton.setPreferredSize(new Dimension(buttonWidth, (int) smallHeight));
        editBotJobButton.setPreferredSize(new Dimension(buttonWidth, (int) smallHeight));
        exitButton.setPreferredSize(new Dimension(buttonWidth, (int) smallHeight));
        aiButton.setPreferredSize(new Dimension(buttonWidth, (int) ARConstants.SPACE_L));

        buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.X_AXIS));
        buttonPane.add(newBotJobButton);
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(cloneBotJobButton);
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(configureButton);
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(infoButton);
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(launchBotJobButton);
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(editBotJobButton);
        buttonPane.add(Box.createHorizontalStrut(5));
        buttonPane.add(exitButton);

        initHeader();

        viewBotJobListView = new JList<>(botJobListModel);
        botJobListCellRenderer = new BotJobListCell(arViewBotJobScene, arWebDriver, isEnabledLicence);
        viewBotJobListView.setCellRenderer(botJobListCellRenderer);

        // Double-click to open Bot Job scene
        viewBotJobListView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = viewBotJobListView.locationToIndex(e.getPoint());
                if (index < 0) return;

                BotJobLoadDTO value = viewBotJobListView.getModel().getElementAt(index);

                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                // 1) Click on delete column → delete
                if (isInDeleteColumn(e)) {
                    botJobListCellRenderer.performDelete(value, viewBotJobListView);
                    return;
                }

                // 2) Double-click elsewhere → open job
                if (e.getClickCount() == 2) {
                    Window parentWindow = SwingUtilities.getWindowAncestor(panelPane);
                    Frame parentFrame = (parentWindow instanceof Frame) ? (Frame) parentWindow : null;

                    arViewBotJobScene.initialize(arWebDriver, value, isEnabledLicence);
                    arViewBotJobScene.showModal(parentFrame);
                }
            }
        });

// Hover for delete button styling
        viewBotJobListView.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = viewBotJobListView.locationToIndex(e.getPoint());
                if (index < 0 || !isInDeleteColumn(e)) {
                    botJobListCellRenderer.setHoverDeleteIndex(-1);
                } else {
                    botJobListCellRenderer.setHoverDeleteIndex(index);
                }
                viewBotJobListView.repaint();
            }
        });


        arConfigurationScene.initialize(viewBotJobListView, isEnabledLicence);
        arNewBotJobScene.initialize(arViewBotJobScene, arWebDriver, webDriverList, isEnabledLicence);
        arWebDriver.initialize(webDriverList);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.add(buttonPane, BorderLayout.NORTH);
        topSection.add(new JScrollPane(aiTextArea), BorderLayout.CENTER);

        // --- NEW: put header + list into a single container in CENTER ---
        JScrollPane listScrollPane = new JScrollPane(viewBotJobListView);

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.add(headerPanel, BorderLayout.NORTH);
        listContainer.add(listScrollPane, BorderLayout.CENTER);

        panelPane = new JPanel(new BorderLayout());
        panelPane.add(topSection, BorderLayout.NORTH);
        panelPane.add(listContainer, BorderLayout.CENTER);
    }

    @Override
    public void initUIBehaviour() {
        aiButton.addActionListener(e -> {
            boolean visible = aiTextArea.isVisible();
            aiTextArea.setVisible(!visible);
            panelPane.revalidate();
            panelPane.repaint();
        });

        newBotJobButton.addActionListener(e -> {
            if (performLists.getListHomeUrl().isEmpty()) {
                performDBEngine.loadHomeUrls(null);
            }

            if (!performLists.getListHomeUrl().isEmpty()) {
                arNewBotJobScene.initialize(arViewBotJobScene, arWebDriver, webDriverList, isEnabledLicence);

                Window parentWindow = SwingUtilities.getWindowAncestor(panelPane);
                Frame parentFrame = (parentWindow instanceof Frame) ? (Frame) parentWindow : null;

                arNewBotJobScene.showModal(parentFrame);

                botJobListModel.clear();
                performDataBase.loadQuickBotJobs();
                for (BotJobLoadDTO dto : performLists.getQuickBotJobs()) {
                    botJobListModel.addElement(dto);
                }
            } else {
                performMessage.showCustomModalDialogDragWin11(
                        "Environments Are Empty",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Please add at least one Organization Environment.</span>",
                        "<span style='font-style: italic;'>Go to the Configuration and add Organizations.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Select an Organization and add an </span><span style='font-weight: bold;'>Environment</span>.",
                        null,
                        false,
                        "OK",
                        null,
                        0);
            }
        });

        cloneBotJobButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }

            String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

            try {
                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    log.error(dataBaseType + " Database connected!");
                } else {
                    log.error(dataBaseType + " Database NOT connected!");
                }
            } catch (Exception error) {
                log.error(dataBaseType + " Database error!" + error.getMessage());
            }

            BotJobLoadDTO selecBotJobDTO = viewBotJobListView.getSelectedValue();
            if (selecBotJobDTO != null) {
                if (performDataBase.isConnDBWorks()) {
                    arSaveCloneScene.initialize(selecBotJobDTO, botJobListModel, isEnabledLicence);

                    Window parentWindow = SwingUtilities.getWindowAncestor(panelPane);
                    Frame parentFrame = (parentWindow instanceof Frame) ? (Frame) parentWindow : null;

                    arSaveCloneScene.showModal(parentFrame);

                    performDataBase.loadQuickBotJobs();
                    botJobListModel.clear();
                    for (BotJobLoadDTO dto : performLists.getQuickBotJobs()) {
                        botJobListModel.addElement(dto);
                    }
                }
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
            }
        });

        configureButton.addActionListener(e -> {
            arConfigurationScene.initialize(viewBotJobListView, isEnabledLicence);
            arConfigurationScene.showModal();

            String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
            try {
                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    log.info(dataBaseType + " Database connected!");
                }
            } catch (Exception error) {
                log.error(dataBaseType + " Database Connection failed : " + error.getMessage());
            }

            if (performDataBase.isConnDBWorks()) {
                try {
                    if (performLists.getQuickBotJobs().isEmpty()) {
                        performDataBase.loadQuickBotJobs();
                        botJobListModel.clear();
                        for (BotJobLoadDTO dto : performLists.getQuickBotJobs()) {
                            botJobListModel.addElement(dto);
                        }
                    }
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            }
        });

        infoButton.addActionListener(e -> arInfoScene.showModal());

        exitButton.addActionListener(e -> closeWebDrivers());

        editBotJobButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }

            selecBotJobDTO = viewBotJobListView.getSelectedValue();

            if (selecBotJobDTO != null) {
                try {
                    reloadList();

                    Window parentWindow = SwingUtilities.getWindowAncestor(panelPane);
                    Frame parentFrame = (parentWindow instanceof Frame) ? (Frame) parentWindow : null;

                    arViewBotJobScene.initialize(arWebDriver, selecBotJobDTO, isEnabledLicence);
                    arViewBotJobScene.showModal(parentFrame);

                } catch (Exception e2) {
                    JOptionPane.showMessageDialog(
                            panelPane,
                            "Error " + selecBotJobDTO.getName() + "  " + e2.getMessage(),
                            "Warning",
                            JOptionPane.WARNING_MESSAGE);
                }
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
            }
        });

        launchBotJobButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }

            BotJobLoadDTO selecBotJobDTO = viewBotJobListView.getSelectedValue();
            if (selecBotJobDTO != null) {

                if (!selecBotJobDTO.getPriority().equalsIgnoreCase("Web App")) {
                    performMessage.errorMessage(
                            "Mobile Bot Job Selected",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Mobile Bot Jobs can only be executed from AR Mobile!</span>",
                            "<span style='color: #2E7D32; font-weight: bold;'>Please run \"AR Mobile\" to launch the Bot Job tests.</span>",
                            null,
                            null,
                            0);
                    return;
                }

                String enginePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_ENGINE);
                String excelPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
                excelPath = excelPath + "\\" + selecBotJobDTO.getName() + ".xlsx";
                if (!new File(excelPath).exists()) {
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
                    performMessage.errorMessage(
                            "Compatibility Issue: Incompatible Java Version",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: Your Java version is lower than the required 17!</span>",
                            "<span style='color: #2E7D32; font-weight: bold;'>Attempting to execute the Engine with this older version may lead to unexpected behavior or failures.</span>",
                            "<span style='font-style: italic;'>Please upgrade your Java installation to version 17 or higher for optimal performance and stability.</span>",
                            null,
                            0);
                }
                String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
                if (!(new File(webDriverPath)).exists()) {
                    performMessage.errorMessage(
                            "Action Required: Missing WebDriver",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: The WebDriver file is missing!</span>",
                            "<span style='color: #2E7D32; font-weight: bold;'>To execute automated browser interactions, the WebDriver is absolutely essential.</span>",
                            "<span style='font-style: italic;'>Please download the correct WebDriver for your browser and ensure it is accessible by the application.</span>",
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
                        String.valueOf(selecBotJobDTO.getHomeBankingLoadDTO().getId()),
                        String.valueOf(selecBotJobDTO.getId()),
                        String.valueOf(1), // block execution
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
                            log.info("Error : " + ex);
                        }
                    }
                }
                processBuilder.redirectOutput(output);
                processBuilder.redirectError(error);
                processBuilder.redirectInput(input);
                try {
                    processBuilder.start();
                } catch (IOException ex) {
                    log.info("Error : " + ex);
                }
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
            }
        });
    }

    private void closeWebDrivers() {
        for (WebDriver driver : arWebDriver.getWebDriverList()) {
            try {
                driver.quit();
                log.info("WebDriver closed.");
            } catch (Exception e) {
                log.warn("Error closing WebDriver: " + e.getMessage());
            }
        }
        arWebDriver.getWebDriverList().clear();
        System.exit(0);
    }

    private void initHeader() {
        headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setOpaque(true);
        headerPanel.setBackground(new Color(235, 235, 235)); // light gray like JTable header
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);

        Font headerFont = headerPanel.getFont().deriveFont(Font.BOLD);

        JLabel nameLabel = new JLabel("Name");
        nameLabel.setFont(headerFont);
        fixColumnSize(nameLabel, BotJobListCell.ColumnWidths.NAME);

        JLabel descriptionLabel = new JLabel("Description");
        descriptionLabel.setFont(headerFont);
        fixColumnSize(descriptionLabel, BotJobListCell.ColumnWidths.DESCRIPTION);

        JLabel environmentLabel = new JLabel("Organization");
        environmentLabel.setFont(headerFont);
        fixColumnSize(environmentLabel, BotJobListCell.ColumnWidths.ORGANIZATION);

        JLabel statusLabel = new JLabel("Status");
        statusLabel.setFont(headerFont);
        fixColumnSize(statusLabel, BotJobListCell.ColumnWidths.STATUS);

        JLabel actionsLabel = new JLabel("Actions");
        actionsLabel.setFont(headerFont);
        fixColumnSize(actionsLabel, BotJobListCell.ColumnWidths.ACTION);

        headerPanel.add(Box.createHorizontalStrut(10));
        headerPanel.add(nameLabel);
        headerPanel.add(Box.createHorizontalStrut(BotJobListCell.ColumnWidths.GAP));
        headerPanel.add(descriptionLabel);
        headerPanel.add(Box.createHorizontalStrut(BotJobListCell.ColumnWidths.GAP));
        headerPanel.add(environmentLabel);
        headerPanel.add(Box.createHorizontalStrut(BotJobListCell.ColumnWidths.GAP));
        headerPanel.add(statusLabel);

        headerPanel.add(Box.createHorizontalGlue()); // align Actions with delete button
        headerPanel.add(actionsLabel);
        headerPanel.add(Box.createHorizontalStrut(10));

        // subtle bottom line separator + padding
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 0, 3, 0)
        ));
    }

    private void fixColumnSize(JComponent comp, int width) {
        Dimension d = new Dimension(width, BotJobListCell.ColumnWidths.ROW_HEIGHT);
        comp.setPreferredSize(d);
        comp.setMinimumSize(d);
        comp.setMaximumSize(d);
    }

    public void setProperty(String propertyName, String value) {
        this.properties.setProperty(propertyName, value);
    }

    @Override
    public JPanel getPaneReference() {
        return panelPane;
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

    private void reloadList() {
        if (performLists.getListHomeUrl().isEmpty()) {
            performDBEngine.loadHomeUrls(null);
        }

        if (performLists.getQuickBotJobs().isEmpty()) {
            performDataBase.loadQuickBotJobs();
        }

        ErrorMessage errorMessage =
                performDataBase.loadBlocks(selecBotJobDTO.getId(), selecBotJobDTO.getName(), "block");
        if (errorMessage == null) {
            performDataBase.loadBlocks(selecBotJobDTO.getHomeBankingId(), selecBotJobDTO.getName(), "component_block");
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        BotJobLoadDTO botJobLoad = performLists.getQuickBotJobById(selecBotJobDTO.getId());

        if (botJobLoad != null && botJobLoad.getBlockLoadDTOList() == null) {
            botJobLoad.setBlockLoadDTOList(performLists.getListBlock());
        }

        if (performLists.getListBlock().isEmpty()) {
            errorMessage = performDataBase.initiateNewBlock(
                    "block", selecBotJobDTO.getId(), "Default Block", "Default Block", 1, false);

            if (errorMessage == null) {
                log.info(String.format("A new Block was created for bot job Id %d", selecBotJobDTO.getId()));
            } else {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }
    }

    private void handleDelete(BotJobLoadDTO item) {
        int result = JOptionPane.showConfirmDialog(
                panelPane,
                "Delete Bot Job: " + item.getName() + "?",
                "Confirm Delete",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (result == JOptionPane.OK_OPTION) {
            performDataBase.deleteBotJobData(item.getId());
            botJobListModel.removeElement(item);
        }
    }

    private boolean isInDeleteColumn(MouseEvent e) {
        int listWidth = viewBotJobListView.getWidth();
        int x = e.getX();
        int margin = 10; // same as header side padding
        int deleteWidth = BotJobListCell.ColumnWidths.ACTION;

        // last (ACTION + margin) pixels on the right are treated as delete column
        return x >= listWidth - deleteWidth - margin;
    }


}
