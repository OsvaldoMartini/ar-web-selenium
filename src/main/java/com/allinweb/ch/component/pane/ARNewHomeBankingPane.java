package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BankingDTO;
import com.allinweb.ch.model.DatabaseUserDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ARExecution;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARNewHomeBankingPane extends ARPane {

    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();

    // Regular expression for a basic URL validation (improved)
    private static final String URL_REGEX =
            "^((https?|ftp|file)://)?([\\da-z.-]+)\\.([a-z.]{2,6})(:\\d+)?(/\\w.*-*)?/?$";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE);

    protected static volatile ARNewHomeBankingPane instance;
    private static HomeBankingLoadDTO homeBank;

    // Buttons
    private JButton insertORGButton;
    private JButton updateORGButton;
    private JButton deleteORGButton;
    private JButton templateORGButton;
    private JButton insertURLButton;
    private JButton updateURLButton;
    private JButton deleteURLButton;

    // Labels
    private JLabel idLabel;
    private JLabel nameLabel;
    private JLabel urlLabel;
    private JLabel priorityLabel;
    private JLabel jobsLabel;
    private JLabel searchConfigLabel;
    private JLabel optionsConfigLabel;
    private JLabel organizationsLabel;
    private JLabel urlEnviromentLabel;

    // Fields
    private JTextField idField;
    private JTextField nameField;
    private JTextField urlField;
    private JTextArea priorityField;
    private JTextField jobsField;
    private JTextArea scanConfigField;
    private JTextArea optionsConfigField;
    private JTextField homeUrlIdField;
    private JTextField homeUrlValueField;

    private Connection conn = null;

    // Tables + models
    private JTable tableViewOrg;
    private DefaultTableModel orgTableModel;

    private JTable tableViewHomeUrl;
    private DefaultTableModel homeUrlTableModel;
    private List<HomeUrlDTO> currentHomeUrls = new ArrayList<>();

    private List<BankingDTO> dtoList;
    private JPanel mainPane;

    // Private constructor to prevent instantiation
    private ARNewHomeBankingPane() {
        super();
    }

    public static ARNewHomeBankingPane getInstance() {
        if (instance == null) {
            synchronized (ARNewHomeBankingPane.class) {
                if (instance == null) {
                    instance = new ARNewHomeBankingPane();
                }
            }
        }
        return instance;
    }

    /**
     * Validates a URL using a regular expression and checks for a valid protocol.
     *
     * @param urlStr The URL string to validate.
     * @return true if the URL is valid, false otherwise.
     */
    public static boolean isValidUrl(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            return false;
        }

        String trimmedUrl = urlStr.trim();
        // Check for basic syntax using regex
        Matcher matcher = URL_PATTERN.matcher(trimmedUrl);
        if (!matcher.matches()) {
            return false;
        }

        // Further check using java.net.URL for protocol and general validity
        try {
            URL url = new URL(trimmedUrl);
            // Check if the protocol is valid.
            String protocol = url.getProtocol();
            if (protocol == null
                    || (!protocol.equals("http")
                            && !protocol.equals("https")
                            && !protocol.equals("ftp")
                            && !protocol.equals("file"))) {
                return false;
            }
            return true;
        } catch (MalformedURLException e) {
            return false; // Invalid URL
        }
    }

    public void initialize(HomeBankingLoadDTO homeBank) {
        ARNewHomeBankingPane.homeBank = homeBank;
    }

    @Override
    public JPanel getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Load initial data
        ErrorMessage errorMessage = performDataBase.loadAllDataUsers();
        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        // --- 1. Initialize Labels ---
        idLabel = new JLabel("ID:");
        nameLabel = new JLabel("Organization:");
        urlLabel = new JLabel("Url Baseline:");
        priorityLabel = new JLabel("Priority:");
        jobsLabel = new JLabel("Active Jobs");
        searchConfigLabel = new JLabel("Scan Config:");
        optionsConfigLabel = new JLabel("WebDriver Options:");

        organizationsLabel = new JLabel("Organizations", SwingConstants.CENTER);
        urlEnviromentLabel = new JLabel("Environments", SwingConstants.CENTER);

        // --- 2. Initialize Fields ---

        // ID Field (read-only)
        idField = new JTextField();
        idField.setEditable(false);
        idField.setBackground(new Color(0xD3, 0xD3, 0xD3));
        idField.setPreferredSize(new Dimension(50, 28));
        idField.setMaximumSize(new Dimension(50, 28));

        // Name Field (yellow background)
        nameField = new JTextField();
        nameField.setBackground(new Color(0xFF, 0xDA, 0x33));
        nameField.setPreferredSize(new Dimension(150, 28));
        nameField.setMaximumSize(new Dimension(150, 28));

        // URL Field
        urlField = new JTextField();
        urlField.setBackground(new Color(0xFF, 0xDA, 0x33));
        urlField.setPreferredSize(new Dimension(200, 28));

        // Priority field
        priorityField = new JTextArea(4, 20);
        priorityField.setLineWrap(true);
        priorityField.setWrapStyleWord(true);
        priorityField.setBackground(new Color(0xFF, 0xDA, 0x33));

        // Jobs (read-only)
        jobsField = new JTextField();
        jobsField.setEditable(false);
        jobsField.setBackground(new Color(0xD3, 0xD3, 0xD3));
        jobsField.setPreferredSize(new Dimension(70, 28));
        jobsField.setMaximumSize(new Dimension(70, 28));

        // Scan config
        scanConfigField = new JTextArea(4, 20);
        scanConfigField.setLineWrap(true);
        scanConfigField.setWrapStyleWord(true);
        scanConfigField.setBackground(new Color(0xFF, 0xDA, 0x33));

        // Options config
        optionsConfigField = new JTextArea(4, 20);
        optionsConfigField.setLineWrap(true);
        optionsConfigField.setWrapStyleWord(true);
        optionsConfigField.setBackground(new Color(0xFF, 0xDA, 0x33));

        // Environment fields
        homeUrlIdField = new JTextField();
        homeUrlIdField.setEditable(false);
        homeUrlIdField.setBackground(new Color(0xD3, 0xD3, 0xD3));
        homeUrlIdField.setPreferredSize(new Dimension(50, 28));
        homeUrlIdField.setMaximumSize(new Dimension(50, 28));

        homeUrlValueField = new JTextField();
        homeUrlValueField.setBackground(new Color(0xFF, 0xDA, 0x33));
        homeUrlValueField.setPreferredSize(new Dimension(200, 28));

        // --- 3. Initialize Buttons ---
        insertORGButton = new JButton("Insert");
        updateORGButton = new JButton("Update");
        deleteORGButton = new JButton("Delete");
        templateORGButton = new JButton("Template");

        insertURLButton = new JButton("Insert URL");
        updateURLButton = new JButton("Update URL");
        deleteURLButton = new JButton("Delete URL");

        // --- 4. Organization Details Layout ---

        // Small vertical groups (label + field)
        JPanel idGroup = new JPanel();
        idGroup.setLayout(new BoxLayout(idGroup, BoxLayout.Y_AXIS));
        idGroup.add(idLabel);
        idGroup.add(idField);

        JPanel nameGroup = new JPanel();
        nameGroup.setLayout(new BoxLayout(nameGroup, BoxLayout.Y_AXIS));
        nameGroup.add(nameLabel);
        nameGroup.add(nameField);

        JPanel jobsGroup = new JPanel();
        jobsGroup.setLayout(new BoxLayout(jobsGroup, BoxLayout.Y_AXIS));
        jobsGroup.add(jobsLabel);
        jobsGroup.add(jobsField);

        JPanel urlGroup = new JPanel();
        urlGroup.setLayout(new BoxLayout(urlGroup, BoxLayout.Y_AXIS));
        urlGroup.add(urlLabel);
        urlGroup.add(urlField);

        JPanel priorityGroup = new JPanel();
        priorityGroup.setLayout(new BoxLayout(priorityGroup, BoxLayout.Y_AXIS));
        priorityGroup.add(priorityLabel);
        priorityGroup.add(new JScrollPane(priorityField));

        JPanel searchConfigGroup = new JPanel();
        searchConfigGroup.setLayout(new BoxLayout(searchConfigGroup, BoxLayout.Y_AXIS));
        searchConfigGroup.add(searchConfigLabel);
        searchConfigGroup.add(new JScrollPane(scanConfigField));

        JPanel optionsConfigGroup = new JPanel();
        optionsConfigGroup.setLayout(new BoxLayout(optionsConfigGroup, BoxLayout.Y_AXIS));
        optionsConfigGroup.add(optionsConfigLabel);
        optionsConfigGroup.add(new JScrollPane(optionsConfigField));

        // First row: ID, Name, Jobs, URL
        JPanel topFieldsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        topFieldsRow.add(idGroup);
        topFieldsRow.add(nameGroup);
        topFieldsRow.add(jobsGroup);
        topFieldsRow.add(urlGroup);

        // Second row: Priority, Scan Config, Options
        JPanel middleFieldsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        middleFieldsRow.add(priorityGroup);
        middleFieldsRow.add(searchConfigGroup);
        middleFieldsRow.add(optionsConfigGroup);

        // Org buttons row
        JPanel orgButtonsBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        orgButtonsBox.add(insertORGButton);
        orgButtonsBox.add(updateORGButton);
        orgButtonsBox.add(deleteORGButton);
        orgButtonsBox.add(templateORGButton);

        // Organizations table
        orgTableModel = new DefaultTableModel(new Object[] {"ID", "Active Jobs", "Organization", "Url Baseline"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableViewOrg = new JTable(orgTableModel);
        tableViewOrg.setFillsViewportHeight(true);
        JScrollPane orgScrollPane = new JScrollPane(tableViewOrg);

        // Container for org section
        JPanel orgDetailsContainer = new JPanel();
        orgDetailsContainer.setLayout(new BoxLayout(orgDetailsContainer, BoxLayout.Y_AXIS));
        orgDetailsContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY), BorderFactory.createEmptyBorder(10, 10, 5, 10)));
        orgDetailsContainer.setBackground(new Color(0xE8, 0xF5, 0xE9));

        organizationsLabel.setFont(organizationsLabel.getFont().deriveFont(Font.BOLD, 15f));
        organizationsLabel.setForeground(new Color(0x15, 0x65, 0xC0));

        orgDetailsContainer.add(organizationsLabel);
        orgDetailsContainer.add(Box.createVerticalStrut(10));
        orgDetailsContainer.add(topFieldsRow);
        orgDetailsContainer.add(middleFieldsRow);
        orgDetailsContainer.add(orgButtonsBox);
        orgDetailsContainer.add(Box.createVerticalStrut(5));
        orgDetailsContainer.add(orgScrollPane);

        // --- 5. Environment (HomeUrl) section ---

        JPanel envFieldsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        envFieldsRow.add(homeUrlIdField);
        envFieldsRow.add(homeUrlValueField);

        JPanel envButtonsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        envButtonsRow.add(insertURLButton);
        envButtonsRow.add(updateURLButton);
        envButtonsRow.add(deleteURLButton);

        homeUrlTableModel = new DefaultTableModel(new Object[] {"ID", "Organization", "Url Environment"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableViewHomeUrl = new JTable(homeUrlTableModel);
        tableViewHomeUrl.setFillsViewportHeight(true);
        JScrollPane envScrollPane = new JScrollPane(tableViewHomeUrl);

        JPanel homeUrlDetailsContainer = new JPanel();
        homeUrlDetailsContainer.setLayout(new BoxLayout(homeUrlDetailsContainer, BoxLayout.Y_AXIS));
        homeUrlDetailsContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY), BorderFactory.createEmptyBorder(10, 10, 5, 10)));
        homeUrlDetailsContainer.setBackground(new Color(0xE8, 0xF5, 0xE9));

        urlEnviromentLabel.setFont(urlEnviromentLabel.getFont().deriveFont(Font.BOLD, 15f));
        urlEnviromentLabel.setForeground(new Color(0x15, 0x65, 0xC0));

        homeUrlDetailsContainer.add(urlEnviromentLabel);
        homeUrlDetailsContainer.add(Box.createVerticalStrut(10));
        homeUrlDetailsContainer.add(envFieldsRow);
        homeUrlDetailsContainer.add(envButtonsRow);
        homeUrlDetailsContainer.add(envScrollPane);

        // --- 6. Root layout ---

        JPanel rootVBox = new JPanel();
        rootVBox.setLayout(new BoxLayout(rootVBox, BoxLayout.Y_AXIS));
        rootVBox.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        rootVBox.setBackground(new Color(0xF5, 0xF5, 0xF5));

        rootVBox.add(orgDetailsContainer);
        rootVBox.add(Box.createVerticalStrut(20));
        rootVBox.add(homeUrlDetailsContainer);

        mainPane = new JPanel(new BorderLayout());
        mainPane.add(rootVBox, BorderLayout.CENTER);

        // Populate tables
        reloadOrgTable();
        if (homeBank != null) {
            reloadHomeUrlTableForBank(homeBank.getId());
        }
    }

    private void reloadOrgTable() {
        orgTableModel.setRowCount(0);
        for (HomeBankingLoadDTO dto : performLists.getListHomeBanking()) {
            orgTableModel.addRow(new Object[] {dto.getId(), dto.getJobs(), dto.getName(), dto.getUrl()});
        }
    }

    private void reloadHomeUrlTableForBank(int homeBankId) {
        currentHomeUrls = performLists.getHomeUrlsByBankId(homeBankId);
        homeUrlTableModel.setRowCount(0);
        for (HomeUrlDTO dto : currentHomeUrls) {
            homeUrlTableModel.addRow(new Object[] {dto.getId(), dto.getOrgName(), dto.getUrl()});
        }
    }

    public void updateTableBankingView() {
        SwingUtilities.invokeLater(() -> {
            ErrorMessage errorMessage = performDataBase.loadAllDataUsers();
            if (errorMessage == null) {
                performDBEngine.loadHomeBanking(null);
            }

            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }

            reloadOrgTable();
        });
    }

    @Override
    public void initUIBehaviour() {
        // ORG INSERT
        insertORGButton.addActionListener(event -> {
            if (nameField.getText() == null
                    || urlField.getText() == null
                    || nameField.getText().trim().isEmpty()
                    || urlField.getText().trim().isEmpty()) {
                performMessage.errorMessage(
                        "Validation Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Name and URL cannot be empty.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Both fields are required to proceed.</span>",
                        "<span style='font-style: italic;'>Please enter a valid name and URL, then try again.</span>",
                        null,
                        0);
                return;
            }

            DatabaseUserDTO user = new DatabaseUserDTO(
                    null,
                    nameField.getText().trim(),
                    urlField.getText().trim(),
                    priorityField.getText(),
                    scanConfigField.getText(),
                    optionsConfigField.getText());

            if (Strings.isNullOrEmpty(priorityField.getText().trim())) {
                user.setPriority(fillUpTemplatePriority());
            }

            if (Strings.isNullOrEmpty(scanConfigField.getText().trim())) {
                user.setSearchConfig(fillUpTemplateScanConfig());
            }

            if (Strings.isNullOrEmpty(optionsConfigField.getText().trim())) {
                user.setOptionsConfig(fillUpTemplateWebDriver());
            }

            if (nameExists(nameField.getText().trim())) {
                performMessage.errorMessage(
                        "Environment Creation Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Environment name already exists.</span>",
                        "<span style='font-weight: bold;'>" + nameField.getText()
                                + "</span> cannot be inserted with the same name.",
                        "<span style='color: #E65100; font-weight: bold;'>Please choose a different environment name.</span>",
                        "<span style='font-style: italic;'>Tip: Use descriptive and unique names for easier management.</span>",
                        0);

                return;
            }

            ErrorMessage errorMessage = performDataBase.createNewHomeBanking(user);
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }

            int newHomeBankId = performDataBase.getNewHomeBankId();

            if (errorMessage == null) {
                errorMessage = performDataBase.createHomeUrlChild(newHomeBankId, user.getUrl());
            }

            if (errorMessage == null) {
                performMessage.showCustomModalDialogDragWin11(
                        "New Environment Created Successfully",
                        "<span style='color: #388E3C; font-weight: bold; font-size: 1.1em;'>The test environment has been successfully created for the organization.</span>",
                        "<span style='font-weight: bold; color: #1976D2;'>Organization: " + user.getName() + "</span>",
                        "<span style='color: #0288D1; font-weight: bold;'>This environment is now ready for testing and further configuration.</span>",
                        "<span style='font-style: italic; color: #1976D2;'>Environment URL: " + user.getUrl()
                                + "</span>",
                        false,
                        "OK",
                        null,
                        0);

            } else {
                log.error(
                        "Insert New Organization Failed. Error: {} ->  {}",
                        errorMessage.getErrorTitle(),
                        errorMessage.getErrorMessage());
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
            }

            errorMessage = performDataBase.loadAllDataUsers();
            if (errorMessage == null) {
                performDBEngine.loadHomeBanking(null);
            }
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
            reloadOrgTable();
        });

        // ORG UPDATE
        updateORGButton.addActionListener(event -> {
            if (nameField.getText() == null
                    || urlField.getText() == null
                    || nameField.getText().trim().isEmpty()
                    || urlField.getText().trim().isEmpty()) {
                performMessage.errorMessage(
                        "Validation Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Name and URL cannot be empty.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Both fields are required to proceed.</span>",
                        "<span style='font-style: italic;'>Please enter a valid name and URL, then try again.</span>",
                        null,
                        0);
                return;
            }

            String id = idField.getText();

            DatabaseUserDTO user = new DatabaseUserDTO(
                    id,
                    nameField.getText(),
                    urlField.getText(),
                    priorityField.getText(),
                    scanConfigField.getText(),
                    optionsConfigField.getText());
            ErrorMessage errorMessage = performDataBase.updateUserData(id, user);
            if (errorMessage == null) {
                errorMessage = performDataBase.loadAllDataUsers();
            }
            if (errorMessage == null) {
                performDBEngine.loadHomeBanking(null);
            }
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
            reloadOrgTable();
        });

        // ORG DELETE
        deleteORGButton.addActionListener(event -> {
            String id = idField.getText();
            if (!jobsField.getText().trim().isEmpty() && Integer.parseInt(jobsField.getText()) > 0) {
                performMessage.errorMessage(
                        "Attempt to Delete",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The organization cannot be deleted:</span>",
                        "<span style='font-weight: bold;'>" + nameField.getText() + "</span>.",
                        "<span style='color: #E65100; font-weight: bold;'>Please delete the bot job(s) attached to it first.</span>",
                        "<span style='font-style: italic;'>Details: Total bot jobs attached: " + jobsField.getText()
                                + "</span>",
                        0);
                return;
            }

            ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                    "Delete Confirmation",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Are you sure you want to delete this organization?</span>",
                    "<span style='font-weight: bold;'>" + nameField.getText() + "</span>",
                    null,
                    null,
                    false,
                    "Continue",
                    "Cancel",
                    0);

            if (respModal.equals(ARExecution.DialogModal.OK)) {
                ErrorMessage errorMessage = performDataBase.deleteUserData(id);
                if (errorMessage == null) {
                    errorMessage = performDataBase.loadAllDataUsers();
                }
                if (errorMessage == null) {
                    performDBEngine.loadHomeBanking(null);
                }
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
                reloadOrgTable();
            }
        });

        // ORG TEMPLATE
        templateORGButton.addActionListener(event -> {
            StringBuilder priorities = new StringBuilder();
            priorities.append("#numero priorità, categoria, identificativo").append(System.lineSeparator());
            priorities.append("1,xpath,currentXPath").append(System.lineSeparator());
            priorities.append("2,attributeID,attributeID").append(System.lineSeparator());
            priorities.append("3,attributeName,attributeName").append(System.lineSeparator());
            priorities.append("4,searchAttribute,searchAttribute").append(System.lineSeparator());
            priorities.append("5,coordinates,coordinates").append(System.lineSeparator());
            priorities.append("6,attribute,test-id").append(System.lineSeparator());
            priorityField.setText(priorities.toString());

            StringBuilder searchCriteria = new StringBuilder();
            searchCriteria.append("1,ByAttribute,test-id").append(System.lineSeparator());
            scanConfigField.setText(searchCriteria.toString());

            String argument1 = "arg:-disable-web-security";
            String argument2 = "arg:-disable-site-isolation-trials";
            String argument3 = "arg:-allow-running-insecure-content";
            String argument4 = "arg:-disable-features=IsolateOrigins,site-per-process";
            String argument5 = "arg:-disable-infobars";
            String argument6 = "#arg:-disable-dev-shm-usage";
            String proxyAddress = "#proxy:proxy_address:proxy_port";
            StringBuilder optionsConfig = new StringBuilder();
            optionsConfig.append(argument1).append(System.lineSeparator());
            optionsConfig.append(argument2).append(System.lineSeparator());
            optionsConfig.append(argument3).append(System.lineSeparator());
            optionsConfig.append(argument4).append(System.lineSeparator());
            optionsConfig.append(argument5).append(System.lineSeparator());
            optionsConfig.append(argument6).append(System.lineSeparator());
            optionsConfig.append(proxyAddress).append(System.lineSeparator());

            optionsConfigField.setText(optionsConfig.toString());
        });

        // URL INSERT
        insertURLButton.addActionListener(event -> {
            if (homeUrlValueField.getText() == null
                    || homeUrlValueField.getText().trim().isEmpty()) {
                performMessage.errorMessage(
                        "Validation Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Environment cannot be empty.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>This field is required to proceed.</span>",
                        "<span style='font-style: italic;'>Please enter a valid Environment, then try again.</span>",
                        null,
                        0);
                return;
            }

            String homeBankIdStr = idField.getText().trim();
            String homeUrl = homeUrlValueField.getText().trim();

            if (homeBankIdStr.isEmpty() || homeUrl.isEmpty()) {
                performMessage.errorMessage(
                        "Insert Environment Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Missing required fields.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>You must select an Organization and fill the Environment field.</span>",
                        "<span style='font-style: italic;'>Please complete all required fields before proceeding.</span>",
                        null,
                        0);

                return;
            }

            int homeBankId = Integer.parseInt(homeBankIdStr);

            ErrorMessage errorMessage = performDataBase.createNewHomeUrl(homeBankId, homeUrl);
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            } else {
                errorMessage = performDBEngine.loadHomeUrls(null);
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }
                reloadHomeUrlTableForBank(homeBankId);

                homeUrlIdField.setText("");
                homeUrlValueField.setText("");
            }
        });

        // URL UPDATE
        updateURLButton.addActionListener(event -> {
            if (homeUrlIdField.getText() == null
                    || homeUrlValueField.getText() == null
                    || homeUrlIdField.getText().trim().isEmpty()
                    || homeUrlValueField.getText().trim().isEmpty()) {
                performMessage.errorMessage(
                        "Validation Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Environment URL cannot be empty.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Please select an Organization and an Environment row.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Also, make sure to fill in a valid URL.</span>",
                        "<span style='font-style: italic;'>All these fields are mandatory to successfully update the environment.</span>",
                        0);
                return;
            }

            String homeBankIdStr = idField.getText().trim();
            String homeUrlIdStr = homeUrlIdField.getText().trim();
            String homeUrl = homeUrlValueField.getText().trim();

            if (homeBankIdStr.isEmpty() || homeUrl.isEmpty() || homeUrlIdStr.isEmpty()) {
                performMessage.errorMessage(
                        "Update Environment Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Missing Required Fields</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Please select an Organization and an Environment row.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Also, make sure to fill in a valid URL.</span>",
                        "<span style='font-style: italic;'>All these fields are mandatory to successfully update the environment.</span>",
                        0);

                return;
            }

            try {
                int homeBankId = Integer.parseInt(homeBankIdStr);
                int homeUrlId = Integer.parseInt(homeUrlIdStr);

                ErrorMessage errorMessage = performDataBase.updateHomeUrl(homeUrlId, homeBankId, homeUrl);
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                } else {
                    errorMessage = performDBEngine.loadHomeUrls(null);
                    if (errorMessage != null) {
                        performMessage.errorMessageOperationFailed(errorMessage);
                    }

                    reloadHomeUrlTableForBank(homeBankId);
                    tableViewHomeUrl.clearSelection();

                    homeUrlIdField.setText("");
                    homeUrlValueField.setText("");
                }

            } catch (SQLException | NumberFormatException e) {
                log.error("Update Environment Failed. Error: {}", e.getMessage());
                performMessage.errorMessage(
                        "Update Environment Failed",
                        "Database Error",
                        "Verify [INSERT] or [UPDATE] or [SELECT]",
                        e.getMessage(),
                        null,
                        0);
            }
        });

        // URL DELETE
        deleteURLButton.addActionListener(event -> {
            String homeBankIdStr = idField.getText().trim();
            String homeUrlIdStr = homeUrlIdField.getText().trim();
            String homeUrl = homeUrlValueField.getText().trim();

            if (homeBankIdStr.isEmpty() || homeUrlIdStr.isEmpty() || homeUrl.isEmpty()) {
                performMessage.errorMessage(
                        "Delete Environment Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Missing Fields or Row Selection</span>",
                        "<span style='color: #E65100; font-weight: bold;'>You must select an Environment row to proceed.</span>",
                        "<span style='font-style: italic;'>Please ensure all required selections are made before deleting.</span>",
                        null,
                        0);

                return;
            }

            performDBEngine.loadHomeUrls(null);
            List<HomeUrlDTO> filteredHomeUrl = performLists.getHomeUrlsByBankId(Integer.parseInt(homeBankIdStr));
            if (filteredHomeUrl.size() == 1) {

                performMessage.showCustomModalDialogDragWin11(
                        "Only One Environment",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>This organization must have at least one Environment.</span>",
                        "<span style='font-style: italic;'>"
                                + nameField.getText().trim() + "</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Use Update to change the Environment URL.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Or delete the entire Organization if that is your intention.</span>",
                        false,
                        "OK",
                        null,
                        0);

                return;
            }

            ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                    "Delete Confirmation",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Are you sure you want to delete this URL?</span>",
                    "<span style='font-weight: bold;'>" + homeUrl + "</span>",
                    null,
                    null,
                    false,
                    "Continue",
                    "Cancel",
                    0);

            if (!respModal.equals(ARExecution.DialogModal.OK)) {
                return;
            }

            try {
                int homeBankId = Integer.parseInt(homeBankIdStr);
                int homeUrlId = Integer.parseInt(homeUrlIdStr);

                ErrorMessage errorMessage = performDataBase.deleteHomeUrl(homeUrlId);
                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                } else {
                    performDBEngine.loadHomeUrls(null);
                    reloadHomeUrlTableForBank(homeBankId);

                    homeUrlIdField.setText("");
                    homeUrlValueField.setText("");
                }

            } catch (SQLException | NumberFormatException e) {
                log.error("Delete Environment Failed> Error: {}", e.getMessage());
                performMessage.errorMessage(
                        "Delete Environment Failed",
                        "Database Error",
                        e.getMessage(),
                        "Verify [DELETE] operation",
                        null,
                        0);
            }
        });

        // ORG table selection
        tableViewOrg.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tableViewOrg.getSelectedRow();
                if (row >= 0 && row < performLists.getListHomeBanking().size()) {
                    HomeBankingLoadDTO selectedUser =
                            performLists.getListHomeBanking().get(row);

                    idField.setText(String.valueOf(selectedUser.getId()));
                    nameField.setText(selectedUser.getName());
                    urlField.setText(selectedUser.getUrl());
                    priorityField.setText(selectedUser.getPriority());
                    jobsField.setText(String.valueOf(selectedUser.getJobs()));
                    scanConfigField.setText(selectedUser.getSearchConfig());
                    optionsConfigField.setText(selectedUser.getOptionsConfig());

                    // Load URLs for this bank
                    performDBEngine.loadHomeUrls(null);
                    reloadHomeUrlTableForBank(selectedUser.getId());
                } else {
                    idField.setText("");
                    nameField.setText("");
                    urlField.setText("");
                    priorityField.setText("");
                    jobsField.setText("");
                    scanConfigField.setText("");
                    optionsConfigField.setText("");
                    homeUrlTableModel.setRowCount(0);
                }
            }
        });

        // HOME URL table selection
        tableViewHomeUrl.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tableViewHomeUrl.getSelectedRow();
                if (row >= 0 && row < currentHomeUrls.size()) {
                    HomeUrlDTO dto = currentHomeUrls.get(row);
                    homeUrlIdField.setText(String.valueOf(dto.getId()));
                    homeUrlValueField.setText(dto.getUrl());
                    nameField.setText(dto.getOrgName());
                    idField.setText(String.valueOf(dto.getHomeBankingId()));
                } else {
                    homeUrlIdField.setText("");
                    homeUrlValueField.setText("");
                }
            }
        });
    }

    private String fillUpTemplatePriority() {
        StringBuilder priorities = new StringBuilder();
        priorities.append("#numero priorità, categoria, identificativo").append(System.lineSeparator());
        priorities.append("1,xpath,currentXPath").append(System.lineSeparator());
        priorities.append("2,attributeID,attributeID").append(System.lineSeparator());
        priorities.append("3,attributeName,attributeName").append(System.lineSeparator());
        priorities.append("4,searchAttribute,searchAttribute").append(System.lineSeparator());
        priorities.append("5,coordinates,coordinates").append(System.lineSeparator());
        priorities.append("6,attribute,test-id").append(System.lineSeparator());

        SwingUtilities.invokeLater(() -> priorityField.setText(priorities.toString()));
        return priorities.toString();
    }

    private String fillUpTemplateScanConfig() {
        StringBuilder searchCriteria = new StringBuilder();
        searchCriteria.append("1,ByAttribute,test-id").append(System.lineSeparator());
        SwingUtilities.invokeLater(() -> scanConfigField.setText(searchCriteria.toString()));
        return searchCriteria.toString();
    }

    private String fillUpTemplateWebDriver() {
        String argument1 = "arg:-disable-web-security";
        String argument2 = "arg:-disable-site-isolation-trials";
        String argument3 = "arg:-allow-running-insecure-content";
        String argument4 = "arg:-disable-features=IsolateOrigins,site-per-process";
        String argument5 = "arg:-disable-infobars";
        String argument6 = "#arg:-disable-dev-shm-usage";
        String proxyAddress = "#proxy:proxy_address:proxy_port";

        StringBuilder optionsConfig = new StringBuilder();
        optionsConfig.append(argument1).append(System.lineSeparator());
        optionsConfig.append(argument2).append(System.lineSeparator());
        optionsConfig.append(argument3).append(System.lineSeparator());
        optionsConfig.append(argument4).append(System.lineSeparator());
        optionsConfig.append(argument5).append(System.lineSeparator());
        optionsConfig.append(argument6).append(System.lineSeparator());
        optionsConfig.append(proxyAddress).append(System.lineSeparator());

        SwingUtilities.invokeLater(() -> optionsConfigField.setText(optionsConfig.toString()));
        return optionsConfig.toString();
    }

    private boolean nameExists(String name) {
        for (DatabaseUserDTO dto : performLists.getListDatabaseUsers()) {
            if (dto.getName().trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    private List<BankingDTO> createSampleData() {
        List<BankingDTO> dataList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dataList.add(new BankingDTO(i + 1, "Name " + i, "URL " + i, "Priority " + i, 5, new ArrayList<>()));
        }
        return dataList;
    }

    @Override
    public void clearPane(JPanel panel) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.info(e.getMessage());
            }
        }
        if (panel != null) {
            panel.removeAll();
            panel.revalidate();
            panel.repaint();
        }
    }
}
