package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.awt.*;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
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

    // Swing components
    private JLabel labelBotJobName;
    private JLabel descriptionLabel;
    private JLabel labelHomeBanking;
    private JTextField botJobName;
    private JTextField botJobDescription;
    private JTextField newUrl;
    private JButton cloneBotJobButton;
    private JButton refreshEnvsButton;
    private JButton insertSitesdButton;
    private JComboBox<HomeUrlDTO> homeURLComboBox;
    private JPanel mainPane;

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

    public void initialize(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.selectedBotJob = selecBotJobDTO;

        if (botJobName != null) {
            botJobName.setText(selecBotJobDTO.getName().trim());
            botJobDescription.setText(selecBotJobDTO.getDescription().trim());
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
        mainPane = new JPanel();
        mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.Y_AXIS));
        mainPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Title
        JLabel paneTitleLabel = new JLabel("Clone Bot Jobs", SwingConstants.CENTER);
        paneTitleLabel.setForeground(Color.BLUE);
        paneTitleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        paneTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Labels
        labelBotJobName = new JLabel("Name of new Bot Job:");
        labelBotJobName.setForeground(Color.BLUE);
        labelBotJobName.setFont(new Font("Arial", Font.BOLD, 14));

        descriptionLabel = new JLabel("Description:");
        descriptionLabel.setForeground(Color.BLUE);
        descriptionLabel.setFont(new Font("Arial", Font.BOLD, 14));

        labelHomeBanking = new JLabel("Enter a New URL or Choose from the List Below", SwingConstants.CENTER);
        labelHomeBanking.setFont(new Font("Arial", Font.BOLD, 14));
        labelHomeBanking.setForeground(new Color(21, 101, 192));

        // Text fields
        botJobName = new JTextField(selectedBotJob.getName().trim(), 30);
        botJobDescription = new JTextField("Description", 30);
        newUrl = new JTextField(selectedBotJob.getHomeBankingLoadDTO().getUrl(), 20);

        // ComboBox
        homeURLComboBox = new JComboBox<>();
        populateHomeUrlComboBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());

        refreshEnvsButton = new JButton("⟳"); // simple refresh symbol

        insertSitesdButton = new JButton("Orgs / Environments");
        cloneBotJobButton = new JButton("Clone Bot Job");
        cloneBotJobButton.setBackground(new Color(76, 175, 80));
        cloneBotJobButton.setForeground(Color.WHITE);
        cloneBotJobButton.setFont(new Font("Arial", Font.BOLD, 12));

        // Layout for URL + refresh
        JPanel urlPanel = new JPanel();
        urlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        urlPanel.add(homeURLComboBox);
        urlPanel.add(refreshEnvsButton);

        // Layout for buttons
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 5));
        buttonsPanel.add(cloneBotJobButton);
        buttonsPanel.add(insertSitesdButton);

        // Organization labels
        JLabel organizationLabel = new JLabel("Organization:");
        organizationLabel.setFont(new Font("Arial", Font.BOLD, 12));
        JLabel orgNameLabel = new JLabel(selectedBotJob.getHomeBankingLoadDTO().getName());
        orgNameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        orgNameLabel.setForeground(new Color(30, 144, 255));

        JPanel orgPanel = new JPanel();
        orgPanel.setLayout(new BoxLayout(orgPanel, BoxLayout.Y_AXIS));
        JPanel orgLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        orgLabelPanel.add(organizationLabel);
        orgLabelPanel.add(orgNameLabel);
        orgPanel.add(orgLabelPanel);
        orgPanel.add(newUrl);

        // URL details container
        JPanel urlDetailsContainer = new JPanel();
        urlDetailsContainer.setLayout(new BoxLayout(urlDetailsContainer, BoxLayout.Y_AXIS));
        urlDetailsContainer.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
        urlDetailsContainer.setBackground(new Color(232, 245, 233));
        urlDetailsContainer.add(labelHomeBanking);
        urlDetailsContainer.add(orgPanel);
        urlDetailsContainer.add(urlPanel);
        urlDetailsContainer.add(buttonsPanel);

        // Main panel assembly
        mainPane.add(paneTitleLabel);
        mainPane.add(Box.createVerticalStrut(10));
        mainPane.add(labelBotJobName);
        mainPane.add(botJobName);
        mainPane.add(descriptionLabel);
        mainPane.add(botJobDescription);
        mainPane.add(Box.createVerticalStrut(10));
        mainPane.add(urlDetailsContainer);
    }

    @Override
    public void initUIBehaviour() {
        insertSitesdButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) return;

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
            arNewHomeBankingScene.initialize(homeBank);
            arNewHomeBankingScene.showModal(null); // adapt modal showing for Swing

            populateHomeUrlComboBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        });

        refreshEnvsButton.addActionListener(e -> {
            performDBEngine.loadHomeUrls(null);
            populateHomeUrlComboBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
        });

        homeURLComboBox.addActionListener(e -> {
            HomeUrlDTO selected = (HomeUrlDTO) homeURLComboBox.getSelectedItem();
            if (selected != null) {
                newUrl.setText(selected.getUrl());
            }
        });

        cloneBotJobButton.addActionListener(e -> {
            String newBotJobName = botJobName.getText().trim();
            String newDescription = botJobDescription.getText().trim();

            botJobName.setText(newBotJobName);

            if (Strings.isNullOrEmpty(newBotJobName)) {
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
                        "Please choose a different name and try again",
                        null,
                        null,
                        0);
                return;
            }

            ExcelUtils.createExcelDataFile(selectedBotJob, newBotJobName);

            if (!Strings.isNullOrEmpty(newUrl.getText())) {
                List<HomeUrlDTO> filteredHomeUrl = performLists.getHomeUrlsByBankId(
                        selectedBotJob.getHomeBankingLoadDTO().getId());
                Optional<HomeUrlDTO> matchHomeUrl = filteredHomeUrl.stream()
                        .filter(homeUrl -> homeUrl.getId() != null
                                && selectedBotJob.getHomeBankingId().equals(homeUrl.getHomeBankingId())
                                && newUrl.getText().trim().equals(homeUrl.getUrl()))
                        .findFirst();

                if (matchHomeUrl.isPresent()) {
                    cloneBotJobSteps(matchHomeUrl.get(), newBotJobName, newDescription);
                } else {
                    ErrorMessage errorMessage = performDataBase.createNewHomeUrl(
                            selectedBotJob.getHomeBankingId(), newUrl.getText().trim());
                    if (errorMessage == null) {
                        int newHomeUrlId = performDataBase.getNewHomeUrlId();
                        HomeUrlDTO homeUrlDTO = new HomeUrlDTO();
                        homeUrlDTO.setId(newHomeUrlId);
                        homeUrlDTO.setHomeBankingId(selectedBotJob.getHomeBankingId());
                        homeUrlDTO.setUrl(newUrl.getText().trim());

                        cloneBotJobSteps(homeUrlDTO, newBotJobName, newDescription);
                        populateHomeUrlComboBox(selectedBotJob.getHomeBankingId(), selectedBotJob.getHomeUrlId());
                    } else {
                        performMessage.errorMessageOperationFailed(errorMessage);
                    }
                }
            } else {
                performMessage.errorMessage(
                        "URL Field cannot be empty", "There is NOT a URL defined", null, null, null, 0);
            }
        });
    }

    private void cloneBotJobSteps(HomeUrlDTO homeUrlDTO, String newBotJobName, String newDescription) {
        ErrorMessage errorMessage =
                performDataBase.cloneBotJob(homeUrlDTO, selectedBotJob.getId(), newBotJobName, newDescription);

        if (errorMessage == null) errorMessage = performDataBase.cloneBlock(selectedBotJob.getId());
        if (errorMessage == null) errorMessage = performDataBase.cloneInstructions(selectedBotJob.getId());
        if (errorMessage == null) errorMessage = performDataBase.cloneVariables(selectedBotJob.getId());
        if (errorMessage == null) errorMessage = performDataBase.cloneUpdateInstruction(selectedBotJob.getId());
        if (errorMessage == null) errorMessage = performDataBase.cloneReferences(selectedBotJob.getId());

        if (errorMessage == null) {
            Integer newBotJobId = performDataBase.getNewBotBojId(selectedBotJob.getId());
            performMessage.showCustomModalDialogDragWin11(
                    "Success: Bot Job Cloned",
                    "Bot Job Cloned Successful! ✅",
                    "New Bot Job Details:",
                    "ID: " + newBotJobId + "\nName: '" + newBotJobName + "'",
                    "Description: " + newDescription,
                    false,
                    "OK",
                    null,
                    0);
        } else {
            Integer newBotJobId = performDataBase.getNewBotBojId(selectedBotJob.getId());
            if (newBotJobId != null) performDataBase.deleteBotJobData(newBotJobId);
            performMessage.errorMessageOperationFailed(errorMessage);
        }
    }

    private void populateHomeUrlComboBox(int homeBankId, int currentHomeUrlId) {
        homeURLComboBox.removeAllItems();
        List<HomeUrlDTO> homeUrlFiltered = performLists.getHomeUrlsByBankId(homeBankId);
        if (homeUrlFiltered.isEmpty()) {
            homeURLComboBox.addItem(new HomeUrlDTO(-1, null, -1, "No Environment Defined"));
            homeURLComboBox.setEnabled(false);
        } else {
            for (HomeUrlDTO item : homeUrlFiltered) homeURLComboBox.addItem(item);
            homeURLComboBox.setEnabled(true);

            for (HomeUrlDTO item : homeUrlFiltered) {
                if (item.getId() == currentHomeUrlId) {
                    homeURLComboBox.setSelectedItem(item);
                    break;
                }
            }
        }
    }

    private boolean checkLicense() {
        try {
            String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
            if (Strings.isNullOrEmpty(licensePath)) licensePath = System.getProperty("user.dir");

            LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

            if (!licenseStatus.equals(LicenceVal.VALID)) {
                performMessage.showCustomModalDialogDragWin11(
                        "License Status Verification",
                        "License status has been successfully verified.",
                        "License not valid!",
                        "Application access is restricted. Please obtain a valid license to continue.",
                        "Current license status: " + licenseStatus.getStaus(),
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
