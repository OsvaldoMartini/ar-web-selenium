package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.ExcelUtils;
import com.google.common.base.Strings;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARSaveClonePane extends ARPane {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final ARNewHomeBankingScene arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();

    protected static volatile ARSaveClonePane instance;

    private boolean isEnabledLicence;
    private BotJobLoadDTO selectedBotJob;

    // UI
    private JPanel mainPane;
    private JLabel labelBotJobName;
    private JLabel descriptionLabel;
    private JLabel labelHomeBanking;

    private JTextField botJobName;
    private JTextField botJobDescription;
    private JTextField newUrl;

    private JButton cloneBotJobButton;
    private JButton refreshEnvsButton;
    private JButton insertSitesdButton;

    private JComboBox<HomeUrlDTO> homeURLChoiceBox;

    private ARSaveClonePane() {
        super();
    }

    public static ARSaveClonePane getInstance() {
        if (instance == null) {
            synchronized (ARSaveClonePane.class) {
                if (instance == null) {
                    instance = new ARSaveClonePane();
                }
            }
        }
        return instance;
    }

    /**
     * Swing version: accepts DefaultListModel instead of List (even if not used inside).
     */
    public void initialize(
            BotJobLoadDTO selecBotJobDTO, DefaultListModel<BotJobLoadDTO> botJobListModel, boolean isEnabledLicence) {

        this.isEnabledLicence = isEnabledLicence;
        this.selectedBotJob = selecBotJobDTO;

        if (botJobName != null) {
            botJobName.setText(selecBotJobDTO.getName().trim());
        }
        if (botJobDescription != null) {
            botJobDescription.setText(selecBotJobDTO.getDescription().trim());
        }
        if (newUrl != null) {
            newUrl.setText(selecBotJobDTO.getHomeBankingLoadDTO().getUrl());
        }

        ErrorMessage errorMessage = null;
        if (performLists.getListHomeBanking().isEmpty()) {
            errorMessage = performDBEngine.loadHomeBanking(null);
        }
        if (errorMessage == null && performLists.getListHomeUrl().isEmpty()) {
            errorMessage = performDBEngine.loadHomeUrls(null);
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
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
        JLabel paneTitleLabel = new JLabel("Clone Bot Jobs", SwingConstants.CENTER);
        paneTitleLabel.setFont(paneTitleLabel.getFont().deriveFont(16f));
        mainPane.add(paneTitleLabel, BorderLayout.NORTH);

        // Labels
        labelBotJobName = new JLabel("Name of new Bot Job:");
        labelBotJobName.setFont(labelBotJobName.getFont().deriveFont(14f));

        descriptionLabel = new JLabel("Description:");
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(14f));

        labelHomeBanking = new JLabel("Enter a New URL or Choose from the List Below", SwingConstants.CENTER);
        labelHomeBanking.setFont(labelHomeBanking.getFont().deriveFont(14f));

        // Text fields
        botJobName = new JTextField(
                selectedBotJob != null && selectedBotJob.getName() != null
                        ? selectedBotJob.getName().trim()
                        : "");
        botJobName.setColumns(30);

        botJobDescription = new JTextField(
                selectedBotJob != null && selectedBotJob.getDescription() != null
                        ? selectedBotJob.getDescription().trim()
                        : "Description");
        botJobDescription.setColumns(30);

        newUrl = new JTextField(
                selectedBotJob != null
                                && selectedBotJob.getHomeBankingLoadDTO() != null
                                && selectedBotJob.getHomeBankingLoadDTO().getUrl() != null
                        ? selectedBotJob.getHomeBankingLoadDTO().getUrl()
                        : "");
        newUrl.setColumns(25);

        // ChoiceBox -> JComboBox
        homeURLChoiceBox = new JComboBox<>();
        homeURLChoiceBox.setPrototypeDisplayValue(
                new HomeUrlDTO(-99, null, -99, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        homeURLChoiceBox.setToolTipText("Select the target URL / environment for the Bot Job");

        homeURLChoiceBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
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

        if (selectedBotJob != null) {
            populateHomeUrlChoiceBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        }

        // Refresh button
        refreshEnvsButton = createPathButton();

        JPanel choiceAndRefreshPanel = new JPanel();
        choiceAndRefreshPanel.setLayout(new BoxLayout(choiceAndRefreshPanel, BoxLayout.X_AXIS));
        choiceAndRefreshPanel.add(homeURLChoiceBox);
        choiceAndRefreshPanel.add(Box.createHorizontalStrut(5));
        choiceAndRefreshPanel.add(refreshEnvsButton);

        // Buttons
        cloneBotJobButton = new JButton("Clone Bot Job");
        cloneBotJobButton.setFont(cloneBotJobButton.getFont().deriveFont(13f));

        insertSitesdButton = new JButton("Orgs / Environments");

        JPanel buttonsBox = new JPanel();
        buttonsBox.setLayout(new BoxLayout(buttonsBox, BoxLayout.X_AXIS));
        buttonsBox.add(cloneBotJobButton);
        buttonsBox.add(Box.createHorizontalStrut(15));
        buttonsBox.add(insertSitesdButton);

        // Organization line
        JLabel organizationLabel = new JLabel("Organization: ");
        organizationLabel.setFont(organizationLabel.getFont().deriveFont(12f));
        JLabel orgNameLabel = new JLabel(
                selectedBotJob != null && selectedBotJob.getHomeBankingLoadDTO() != null
                        ? selectedBotJob.getHomeBankingLoadDTO().getName()
                        : "");
        orgNameLabel.setFont(orgNameLabel.getFont().deriveFont(13f));

        JPanel labelsBox = new JPanel();
        labelsBox.setLayout(new BoxLayout(labelsBox, BoxLayout.X_AXIS));
        labelsBox.add(organizationLabel);
        labelsBox.add(Box.createHorizontalStrut(10));
        labelsBox.add(orgNameLabel);

        JPanel organizationBox = new JPanel();
        organizationBox.setLayout(new BoxLayout(organizationBox, BoxLayout.Y_AXIS));
        organizationBox.add(labelsBox);
        organizationBox.add(Box.createVerticalStrut(5));
        organizationBox.add(newUrl);

        // URL details container
        JPanel homeUrlDetailsContainer = new JPanel();
        homeUrlDetailsContainer.setLayout(new BoxLayout(homeUrlDetailsContainer, BoxLayout.Y_AXIS));
        homeUrlDetailsContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color.LIGHT_GRAY), new EmptyBorder(10, 10, 10, 10)));

        labelHomeBanking.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        organizationBox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        choiceAndRefreshPanel.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        buttonsBox.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        homeUrlDetailsContainer.add(labelHomeBanking);
        homeUrlDetailsContainer.add(Box.createVerticalStrut(8));
        homeUrlDetailsContainer.add(organizationBox);
        homeUrlDetailsContainer.add(Box.createVerticalStrut(8));
        homeUrlDetailsContainer.add(choiceAndRefreshPanel);
        homeUrlDetailsContainer.add(Box.createVerticalStrut(8));
        homeUrlDetailsContainer.add(buttonsBox);

        // Main layout (center)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(Box.createVerticalStrut(10));

        labelBotJobName.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        botJobName.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        descriptionLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        botJobDescription.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        homeUrlDetailsContainer.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        centerPanel.add(labelBotJobName);
        centerPanel.add(botJobName);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(descriptionLabel);
        centerPanel.add(botJobDescription);
        centerPanel.add(Box.createVerticalStrut(15));
        centerPanel.add(homeUrlDetailsContainer);

        mainPane.add(centerPanel, BorderLayout.CENTER);
    }

    @Override
    public void initUIBehaviour() {

        insertSitesdButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            ErrorMessage errorMessage = null;
            if (performLists.getListHomeBanking().isEmpty()) {
                errorMessage = performDBEngine.loadHomeBanking(null);
            }
            if (errorMessage == null && performLists.getListHomeUrl().isEmpty()) {
                errorMessage = performDBEngine.loadHomeUrls(null);
            }

            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }

            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();

            Window parentWindow = SwingUtilities.getWindowAncestor(mainPane);
            java.awt.Frame parentFrame =
                    (parentWindow instanceof java.awt.Frame) ? (java.awt.Frame) parentWindow : null;

            arNewHomeBankingScene.initialize(homeBank);
            arNewHomeBankingScene.showModal(parentFrame);

            if (homeURLChoiceBox != null && selectedBotJob != null) {
                populateHomeUrlChoiceBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
            }
        });

        refreshEnvsButton.addActionListener(e -> {
            performDBEngine.loadHomeUrls(null);
            if (homeURLChoiceBox != null && selectedBotJob != null) {
                populateHomeUrlChoiceBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
            }
        });

        homeURLChoiceBox.addActionListener(e -> {
            HomeUrlDTO newVal = (HomeUrlDTO) homeURLChoiceBox.getSelectedItem();
            if (newVal != null && newVal.getUrl() != null) {
                newUrl.setText(newVal.getUrl().trim());
            }
        });

        cloneBotJobButton.addActionListener(e -> {
            String newBotJobName = botJobName.getText().trim();
            String newDescription = botJobDescription.getText().trim();

            botJobName.setText(newBotJobName);

            if (Strings.isNullOrEmpty(botJobName.getText().trim())) {
                performMessage.errorMessage(
                        "Select a new Bot Job name", "There is NOT a name defined", null, null, null, 0);
                return;
            }

            BotJobLoadDTO existBotJob = performLists.getQuickBotJobs().stream()
                    .filter(botJob -> botJob.getName().equals(newBotJobName))
                    .findFirst()
                    .orElse(null);

            if (existBotJob != null) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists",
                        "The name you have entered is already in use.",
                        "Please choose a different name and try again.",
                        null,
                        null,
                        0);
                return;
            }

            ExcelUtils.createExcelDataFile(selectedBotJob, newBotJobName);

            if (!Strings.isNullOrEmpty(newUrl.getText())) {
                if (performLists.getListHomeUrl().isEmpty()) {
                    performDBEngine.loadHomeUrls(null);
                }

                List<HomeUrlDTO> filteredHomeUrl = performLists.getHomeUrlsByBankId(
                        selectedBotJob.getHomeBankingLoadDTO().getId());

                if (!newUrl.getText()
                        .trim()
                        .equals(selectedBotJob.getHomeBankingLoadDTO().getUrl())) {

                    Optional<HomeUrlDTO> matchHomeUrl = filteredHomeUrl.stream()
                            .filter(homeUrl -> homeUrl.getId() != null
                                    && selectedBotJob.getHomeBankingId().equals(homeUrl.getHomeBankingId())
                                    && newUrl.getText().trim().equals(homeUrl.getUrl()))
                            .findFirst();

                    if (matchHomeUrl.isPresent()) {
                        HomeUrlDTO matchedHomeUrl = matchHomeUrl.get();
                        log.info("Found matching HomeUrlDTO: id="
                                + matchedHomeUrl.getId()
                                + ", url="
                                + matchedHomeUrl.getUrl());

                        cloneBotJobSteps(matchedHomeUrl, newBotJobName, newDescription);

                    } else {
                        log.info("No matching HomeUrlDTO found.");

                        ErrorMessage errorMessage = performDataBase.createNewHomeUrl(
                                selectedBotJob.getHomeBankingId(),
                                newUrl.getText().trim());
                        if (errorMessage == null) {
                            int newHomeUrlId = performDataBase.getNewHomeUrlId();

                            HomeUrlDTO homeUrlDTO = new HomeUrlDTO();
                            homeUrlDTO.setId(newHomeUrlId);
                            homeUrlDTO.setHomeBankingId(selectedBotJob.getHomeBankingId());
                            homeUrlDTO.setUrl(newUrl.getText().trim());

                            cloneBotJobSteps(homeUrlDTO, newBotJobName, newDescription);

                            if (homeURLChoiceBox != null) {
                                populateHomeUrlChoiceBox(
                                        selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
                            }

                        } else {
                            performMessage.errorMessage(
                                    "Insert New Environment Failed ❌",
                                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>"
                                            + errorMessage.getErrorTitle() + "</span>",
                                    "<span style='color: #E65100; font-weight: bold;'>" + errorMessage.getErrorHeader()
                                            + "</span>",
                                    "<span style='font-style: italic;'>" + errorMessage.getErrorMessage() + "</span>",
                                    null,
                                    0);
                        }
                    }

                } else {

                    Optional<HomeUrlDTO> matchHomeUrl = filteredHomeUrl.stream()
                            .filter(homeUrl -> homeUrl.getId() != null
                                    && selectedBotJob.getHomeBankingId().equals(homeUrl.getHomeBankingId())
                                    && newUrl.getText().trim().equals(homeUrl.getUrl()))
                            .findFirst();

                    if (matchHomeUrl.isPresent()) {
                        HomeUrlDTO matchedHomeUrl = matchHomeUrl.get();
                        log.info("Found matching HomeUrlDTO: id="
                                + matchedHomeUrl.getId()
                                + ", url="
                                + matchedHomeUrl.getUrl());

                        cloneBotJobSteps(matchedHomeUrl, newBotJobName, newDescription);
                    }
                }

            } else {
                performMessage.errorMessage(
                        "URL Field cannot be empty",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>There is NOT a URL defined</span>",
                        null,
                        null,
                        null,
                        0);
            }
        });
    }

    private void cloneBotJobSteps(HomeUrlDTO homeUrlDTO, String newBotJobName, String newDescription) {
        ErrorMessage errorMessage =
                performDataBase.cloneBotJob(homeUrlDTO, selectedBotJob.getId(), newBotJobName, newDescription);

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneBlock(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneInstructions(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneVariables(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneUpdateInstruction(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            errorMessage = performDataBase.cloneReferences(selectedBotJob.getId());
        }

        if (errorMessage == null) {
            Integer newBotJobId = performDataBase.getNewBotBojId(selectedBotJob.getId());

            performMessage.showCustomModalDialogDragWin11(
                    "Success: Bot Job Cloned",
                    "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Bot Job Cloned Successful!</span> ✅",
                    "<span style='color: #1976D2;'>New Bot Job Details:</span>",
                    "<span style='font-weight: bold;'>ID:</span> " + newBotJobId + "<br>"
                            + "<span style='font-weight: bold;'>Name:</span> '" + newBotJobName + "'",
                    "<span style='font-style: italic;'>Description: " + newDescription + "</span>",
                    false,
                    "OK",
                    null,
                    0);

        } else {

            Integer newBotJobId = performDataBase.getNewBotBojId(selectedBotJob.getId());
            if (newBotJobId != null) {
                performDataBase.deleteBotJobData(newBotJobId);
            }
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        log.info("ARSaveClonePane Close()");

        Window window = SwingUtilities.getWindowAncestor(mainPane);
        if (window instanceof JDialog dialog) {
            dialog.dispose();
        }
    }

    private void populateHomeUrlChoiceBox(int homeBankId, int currentHomeUrlId) {
        homeURLChoiceBox.removeAllItems();

        List<HomeUrlDTO> homeUrlFiltered = performLists.getHomeUrlsByBankId(homeBankId);

        if (homeUrlFiltered.isEmpty()) {
            HomeUrlDTO noEnv = new HomeUrlDTO(-1, null, -1, "No Environment Defined");
            homeURLChoiceBox.addItem(noEnv);
            homeURLChoiceBox.setEnabled(false);
        } else {
            for (HomeUrlDTO dto : homeUrlFiltered) {
                homeURLChoiceBox.addItem(dto);
            }
            homeURLChoiceBox.setEnabled(true);

            for (HomeUrlDTO item : homeUrlFiltered) {
                if (item.getId() == currentHomeUrlId) {
                    homeURLChoiceBox.setSelectedItem(item);
                    break;
                }
            }
        }
    }

    private JButton createPathButton() {
        // Using builder in Swing as in ARMainPane
        JButton button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3, 3, 3, 3));
        button.setPreferredSize(new java.awt.Dimension((int) ARConstants.SPACE_L, (int) ARConstants.SPACE_L));
        return button;
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
}
