package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARNewBotJobPane extends ARPane {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final ARNewHomeBankingScene arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();

    protected static volatile ARNewBotJobPane instance;

    private JLabel labelBotJobName;
    private JLabel descriptionLabel;
    private JLabel labelHomeBanking;

    private JTextField botJobName;
    private JTextField botJobDescription;

    private JButton createBotJobButton;
    private JButton refreshEnvsButton;
    private JButton insertSitesdButton;
    private JComboBox<HomeUrlDTO> homeURLChoiceBox;

    private ButtonGroup appTypeGroup;
    private JRadioButton rbWeb;
    private JRadioButton rbAndroid;
    private JRadioButton rbIos;

    private JPanel mainPane;
    private boolean isEnabledLicence;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;

    private ARNewBotJobPane() {
        super();
        initUIComponents();
        initUIBehaviour();
    }

    public static ARNewBotJobPane getInstance() {
        if (instance == null) {
            synchronized (ARNewBotJobPane.class) {
                if (instance == null) {
                    instance = new ARNewBotJobPane();
                }
            }
        }
        return instance;
    }

    @Override
    public JPanel getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        mainPane = new JPanel(new BorderLayout());
        mainPane.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Title
        JLabel paneTitleLabel = new JLabel("Create New Bot Job", SwingConstants.CENTER);
        paneTitleLabel.setFont(paneTitleLabel.getFont().deriveFont(Font.BOLD, 16f));
        paneTitleLabel.setForeground(Color.BLUE);

        // Name / Description labels
        labelBotJobName = new JLabel("Name:");
        labelBotJobName.setForeground(Color.BLUE);
        labelBotJobName.setFont(labelBotJobName.getFont().deriveFont(Font.BOLD, 14f));

        botJobName = new JTextField();
        botJobName.setToolTipText("Enter Bot Job Name");

        descriptionLabel = new JLabel("Description:");
        descriptionLabel.setForeground(Color.BLUE);
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(Font.BOLD, 14f));

        botJobDescription = new JTextField();
        botJobDescription.setToolTipText("Enter Description (optional)");

        // Home banking label
        labelHomeBanking = new JLabel("Select the URL / Environment:", SwingConstants.CENTER);
        labelHomeBanking.setForeground(new Color(0x15, 0x65, 0xC0));
        labelHomeBanking.setFont(labelHomeBanking.getFont().deriveFont(Font.BOLD, 14f));

        // JComboBox for HomeUrl
        homeURLChoiceBox = new JComboBox<>();
        homeURLChoiceBox.setPrototypeDisplayValue(new HomeUrlDTO(-99, null, -99, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        homeURLChoiceBox.setToolTipText("Select the target URL / environment for the Bot Job");
        homeURLChoiceBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof HomeUrlDTO dto) {
                    if (dto.getUrl() != null) {
                        setText(dto.getOrgName() + " | " + dto.getUrl());
                    } else {
                        setText(dto.getOrgName());
                    }
                } else {
                    setText("");
                }
                return this;
            }
        });

        populateHomeUrlChoiceBox();

        refreshEnvsButton = new JButton("Refresh");
        insertSitesdButton = new JButton("Orgs / Environments");

        // Make labels labelFor fields (semantic only)
        labelBotJobName.setLabelFor(botJobName);
        descriptionLabel.setLabelFor(botJobDescription);
        labelHomeBanking.setLabelFor(homeURLChoiceBox);

        // App type radio buttons
        appTypeGroup = new ButtonGroup();
        rbWeb = new JRadioButton("Web Apps", true);
        rbAndroid = new JRadioButton("Android Apps");
        rbIos = new JRadioButton("iOS Apps");

        appTypeGroup.add(rbWeb);
        appTypeGroup.add(rbAndroid);
        appTypeGroup.add(rbIos);

        JPanel appTypePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        appTypePanel.add(rbWeb);
        appTypePanel.add(rbAndroid);
        appTypePanel.add(rbIos);

        // URL & buttons container
        JPanel choiceAndRefreshPanel = new JPanel(new BorderLayout(5, 5));
        choiceAndRefreshPanel.add(homeURLChoiceBox, BorderLayout.CENTER);
        choiceAndRefreshPanel.add(refreshEnvsButton, BorderLayout.EAST);

        JPanel buttonsBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        createBotJobButton = new JButton("Create Bot Job");
        createBotJobButton.setForeground(Color.WHITE);
        createBotJobButton.setBackground(new Color(0x4C, 0xAF, 0x50));
        createBotJobButton.setOpaque(true);
        createBotJobButton.setBorderPainted(false);

        buttonsBox.add(createBotJobButton);
        buttonsBox.add(insertSitesdButton);

        JPanel homeUrlDetailsContainer = new JPanel();
        homeUrlDetailsContainer.setLayout(new BoxLayout(homeUrlDetailsContainer, BoxLayout.Y_AXIS));
        homeUrlDetailsContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY), new EmptyBorder(10, 10, 10, 10)));
        homeUrlDetailsContainer.setBackground(new Color(0xE8, 0xF5, 0xE9));

        labelHomeBanking.setAlignmentX(Component.CENTER_ALIGNMENT);
        choiceAndRefreshPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonsBox.setAlignmentX(Component.CENTER_ALIGNMENT);

        homeUrlDetailsContainer.add(labelHomeBanking);
        homeUrlDetailsContainer.add(Box.createVerticalStrut(5));
        homeUrlDetailsContainer.add(choiceAndRefreshPanel);
        homeUrlDetailsContainer.add(Box.createVerticalStrut(10));
        homeUrlDetailsContainer.add(buttonsBox);

        // Form layout: vertical box
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        paneTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        appTypePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        labelBotJobName.setAlignmentX(Component.LEFT_ALIGNMENT);
        botJobName.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        botJobDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        homeUrlDetailsContainer.setAlignmentX(Component.CENTER_ALIGNMENT);

        formPanel.add(paneTitleLabel);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(appTypePanel);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(labelBotJobName);
        formPanel.add(botJobName);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(descriptionLabel);
        formPanel.add(botJobDescription);
        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(homeUrlDetailsContainer);

        mainPane.add(formPanel, BorderLayout.CENTER);
    }

    private void populateHomeUrlChoiceBox() {
        homeURLChoiceBox.removeAllItems();

        // "Select Environment"
        HomeUrlDTO selectEnv = new HomeUrlDTO(-2, null, -2, "Select the Environment");
        homeURLChoiceBox.addItem(selectEnv);

        // All real envs
        for (HomeUrlDTO dto : performLists.getListHomeUrl()) {
            homeURLChoiceBox.addItem(dto);
        }

        // No real envs
        if (performLists.getListHomeUrl().isEmpty()) {
            HomeUrlDTO noEnv = new HomeUrlDTO(-1, null, -1, "No Environment Defined");
            homeURLChoiceBox.addItem(noEnv);
            homeURLChoiceBox.setEnabled(false);
        } else {
            homeURLChoiceBox.setEnabled(true);
        }

        homeURLChoiceBox.setSelectedIndex(0);
    }

    @Override
    public void initUIBehaviour() {
        insertSitesdButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
            arNewHomeBankingScene.initialize(homeBank);

            Window parent = SwingUtilities.getWindowAncestor(mainPane);
            arNewHomeBankingScene.showModal(parent);

            if (homeURLChoiceBox != null) {
                populateHomeUrlChoiceBox();
            }
        });

        createBotJobButton.addActionListener(e -> launchBotJobCreation());

        refreshEnvsButton.addActionListener(e -> {
            performDBEngine.loadHomeUrls(null);
            if (homeURLChoiceBox != null) {
                populateHomeUrlChoiceBox();
            }
        });
    }

    // Initialize references for Scene and WebDriver
    public void initialize(ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;

        ErrorMessage errorMessage = performDBEngine.loadHomeBanking(null);
        if (errorMessage == null) {
            errorMessage = performDBEngine.loadHomeUrls(null);
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
    }

    private void launchBotJobCreation() {
        // In JavaFX you used Task + Platform.runLater; here we just run on EDT
        createBotJob();
    }

    private void createBotJob() {
        // Resolve selected app type
        String appType = getSelectedAppType(); // "Web", "Android", or "iOS"

        String rawName =
                botJobName.getText() == null ? "" : botJobName.getText().trim();
        String projectType = "Web App";
        if ("Android".equals(appType)) projectType = "Android";
        else if ("iOS".equals(appType)) projectType = "iOS";

        boolean isMeaningfulEmpty = Strings.isNullOrEmpty(rawName);

        if (isMeaningfulEmpty) {
            performMessage.errorMessage(
                    "Missing Bot Job Name",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Bot Job Name cannot be empty.</span>",
                    "<span style='color: #000080; font-weight: bold;'>Please enter a name for the Bot Job to proceed.</span>",
                    null,
                    null,
                    0);
            return;
        }

        if (Strings.isNullOrEmpty(rawName)) {
            performMessage.errorMessage(
                    "Missing Bot Job Name",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Bot Job Name cannot be empty.</span>",
                    "<span style='color: #000080; font-weight: bold;'>Please enter a name for the Bot Job to proceed.</span>",
                    null,
                    null,
                    0);
            return;
        }

        final String nameToCheck = rawName;
        boolean existName =
                performLists.getListBotJob().stream().anyMatch(f -> f.getName().equalsIgnoreCase(nameToCheck));

        if (existName) {
            performMessage.errorMessage(
                    "Bot Job Name Already Exists",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The name you have entered is already in use.</span>",
                    "<span style='color: #000080; font-weight: bold;'>" + rawName + "</span>",
                    null,
                    null,
                    0);
            return;
        }

        HomeUrlDTO selected = (HomeUrlDTO) homeURLChoiceBox.getSelectedItem();
        if (selected == null
                || Strings.isNullOrEmpty(selected.getOrgName())
                || selected.getId() < 0
                || selected.getId() == -2) {

            performMessage.errorMessage(
                    "Missing Website",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Website cannot be empty or undefined.</span>",
                    "<span style='color: #000080; font-weight: bold;'>Please select a valid Website for the Bot Job to proceed.</span>",
                    null,
                    null,
                    0);
            return;
        }

        HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(selected.getHomeBankingId(), selected.getId());
        if (homeUrlDTO == null) {
            performMessage.errorMessage(
                    "Missing or Removed Environment",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Environment Not Found ❌</span>",
                    "<span style='color: #E65100; font-weight: bold;'>Possible cause:</span> It may have been deleted or is no longer available.",
                    "<span style='font-style: italic;'>Action:</span> Select a valid Website / Environment.",
                    "<span style='font-style: italic;'>Tip:</span> Click 'Refresh' to reload the list.",
                    0);
            return;
        }

        rawName = nameFileOnWindows(rawName);

        BotJobLoadDTO createdBotJob = new BotJobLoadDTO();
        createdBotJob.setName(rawName);
        createdBotJob.setPriority(projectType);
        createdBotJob.setDescription(
                botJobDescription.getText() == null
                        ? ""
                        : botJobDescription.getText().trim());
        createdBotJob.setHomeBankingId(selected.getHomeBankingId());
        createdBotJob.setHomeUrlId(selected.getId());

        ErrorMessage errorMessage = performDataBase.createNewBotJob(createdBotJob);

        int newBotJobId = performDataBase.getNewBotJobId();
        if (errorMessage == null && newBotJobId > -1) {
            createdBotJob.setId(newBotJobId);

            if (performDataBase.isConnDBWorks()) {
                performDataBase.loadQuickBotJobs();
            }

            arViewBotJobScene.initialize(arWebDriver, createdBotJob, isEnabledLicence);

            Window parentWindow = SwingUtilities.getWindowAncestor(mainPane);
            Frame parentFrame = (parentWindow instanceof Frame) ? (Frame) parentWindow : null;
            arViewBotJobScene.showModal(parentFrame);

            log.info("Success creating new Bot Job ID: {}", newBotJobId);

            if (parentWindow != null) {
                parentWindow.dispose();
            }
        } else {
            log.error("Error creating BotJobDTO. Check the Block Creation!");
            if (errorMessage == null) {
                performMessage.errorMessage(
                        "Create New Bot Job Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Unknown Error</span>",
                        "<span style='color: #E65100; font-weight: bold;'>No error details were returned.</span>",
                        "Verify  [INSERT] or [UPDATE] or [SELECT]",
                        "<span style='font-style: italic;'>Please check logs for more details.</span>",
                        0);
            } else {
                performMessage.errorMessage(
                        "Create New Bot Job Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>"
                                + errorMessage.getErrorTitle() + "</span>",
                        "<span style='color: #E65100; font-weight: bold;'>" + errorMessage.getErrorHeader() + "</span>",
                        "Verify  [INSERT] or [UPDATE] or [SELECT]",
                        "<span style='font-style: italic;'>" + errorMessage.getErrorMessage() + "</span>",
                        0);
            }
        }
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
            if (!LicenceVal.VALID.equals(licenseStatus)) {
                msgValid = "The license file is not valid and the application is not authorized for use.";
                msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
                msgColor = "#C62828"; // Soft, elegant red tone

                performMessage.showCustomModalDialogDragWin11(
                        "License Status Verification",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>License status has been successfully verified.</span>",
                        "<span style='color: " + msgColor + "; font-weight: bold;'>" + msgValid + "</span>",
                        "<span style='font-style: italic;'>" + msgNextStep + "</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Current license status:</span> "
                                + "<span style='font-weight: bold;'>" + licenseStatus.getStaus() + "</span>",
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

    private String getSelectedAppType() {
        if (rbAndroid.isSelected()) return "Android";
        if (rbIos.isSelected()) return "iOS";
        return "Web";
    }

    @Override
    public void clearPane(JPanel panel) {
        // No resources to explicitly clear here; kept for symmetry with other panes.
    }
}
