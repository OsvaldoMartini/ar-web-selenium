package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARLicensePane extends ARPane {

    private static final ARPropertyManager arPropertyManager;
    private static final PerformMessage performMessage;
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARLicensePane instance;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    private JPanel mainPanel;
    private JCheckBox cbAgree;
    private JButton btnProceed;
    private JButton btnClose;
    private JRadioButton rbRequestLicense;
    private JRadioButton rbActivateLicense;
    private JRadioButton rbUseExistentLicense;
    private JTextField tfLicenseOwner;
    private JButton uploadButton;
    private JTextField filePathField;
    private String fileFolder;

    // Private constructor to prevent instantiation
    private ARLicensePane() {
        super();
    }

    public static ARLicensePane getInstance() {
        if (instance == null) {
            synchronized (ARLicensePane.class) {
                if (instance == null) {
                    instance = new ARLicensePane();
                }
            }
        }
        return instance;
    }

    public void initialize() {
        // Set initial directory to Desktop
        defineDesktopFolder();
    }

    /**
     * Swing equivalent of your FX getPaneReference.
     * Adapt the signature in ARPane to JComponent if needed.
     */
    @Override
    public JPanel getPaneReference() {
        return mainPanel;
    }

    @Override
    public void initUIComponents() {
        // Main panel, like your AnchorPane+VBox, with padding
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(
                new EmptyBorder(ARConstants.SPACE_M, ARConstants.SPACE_M, ARConstants.SPACE_M, ARConstants.SPACE_M));

        // Header
        JLabel headerLabel = new JLabel("AR Web Activation software required");
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));

        JPanel headerContainer = new JPanel(new BorderLayout());
        headerContainer.setBackground(new Color(0x00, 0x78, 0xD7));
        headerContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        headerContainer.add(headerLabel, BorderLayout.WEST);

        // ToggleGroup -> ButtonGroup
        ButtonGroup toggleGroup = new ButtonGroup();

        rbRequestLicense = new JRadioButton("Request New License");
        rbRequestLicense.setSelected(true);

        rbActivateLicense = new JRadioButton("Activate Response File");
        rbUseExistentLicense = new JRadioButton("Use Existing License");

        toggleGroup.add(rbRequestLicense);
        toggleGroup.add(rbActivateLicense);
        toggleGroup.add(rbUseExistentLicense);

        JPanel radioButtonsPanel = new JPanel();
        radioButtonsPanel.setLayout(new BoxLayout(radioButtonsPanel, BoxLayout.Y_AXIS));
        radioButtonsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        radioButtonsPanel.add(rbRequestLicense);
        radioButtonsPanel.add(Box.createVerticalStrut(5));
        radioButtonsPanel.add(rbActivateLicense);
        radioButtonsPanel.add(Box.createVerticalStrut(5));
        radioButtonsPanel.add(rbUseExistentLicense);

        // File path field
        filePathField = new JTextField();
        filePathField.setColumns(30);

        JPanel filePathPanel = new JPanel(new BorderLayout(10, 0));
        filePathPanel.add(filePathField, BorderLayout.CENTER);

        // Upload / Directory button
        uploadButton = new JButton("Request target Directory");
        uploadButton.setPreferredSize(new Dimension(300, 30));

        JPanel uploadButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        uploadButtonPanel.add(uploadButton);

        JPanel verticalButtonPanel = new JPanel();
        verticalButtonPanel.setLayout(new BoxLayout(verticalButtonPanel, BoxLayout.Y_AXIS));
        verticalButtonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        verticalButtonPanel.add(uploadButtonPanel);
        verticalButtonPanel.add(Box.createVerticalStrut(5));
        verticalButtonPanel.add(filePathPanel);

        JPanel radioAndUploadPanel = new JPanel();
        radioAndUploadPanel.setLayout(new BoxLayout(radioAndUploadPanel, BoxLayout.X_AXIS));
        radioAndUploadPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        radioAndUploadPanel.add(radioButtonsPanel);
        radioAndUploadPanel.add(Box.createHorizontalStrut(30));
        radioAndUploadPanel.add(verticalButtonPanel);

        // License agreement text area
        JTextArea taLicenseAgreement = new JTextArea("SOFTWARE LICENSE AGREEMENT\n\n"
                + "Important - Read Carefully: This License Agreement (\"Agreement\") is a legal contract between you\n"
                + "(an individual or a legal entity) and Allinweb SA. (\"Licensor\") for the software that accompanies\n"
                + "this agreement, which includes associated software and media material, whether printed, electronic, or online (\"Software\").\n\n"
                + "1. License Grant:\n"
                + "   Subject to the terms of this Agreement, the Licensor grants you a non-exclusive,\n"
                + "   non-transferable license to use the Software for internal purposes according to the following limitations\n"
                + "   and in compliance with the provided documentation.\n\n"
                + "2. Restrictions:\n"
                + "   You are not authorized to:\n"
                + "   - Modify, translate, adapt, or create derivative works from the Software.\n"
                + "   - Reverse engineer, decompile, disassemble, or otherwise attempt to discover the Software's source code.\n"
                + "   - Resell, rent, sublicense, distribute, or otherwise transfer the Software without prior written consent from the Licensor.\n"
                + "   - Remove any copyright notices, trademarks, or other proprietary notices included in the Software.\n\n"
                + "3. Ownership of the Software:\n"
                + "   The Software is protected by copyright laws and international treaties, as well as other intellectual\n"
                + "   property laws and treaties. The Software is licensed, not sold.\n\n"
                + "4. Limited Warranty:\n"
                + "   The Licensor warrants that the Software will operate substantially in accordance with the documentation\n"
                + "   for a period of ninety (90) days from the date of your purchase. Any replacement Software will be\n"
                + "   warranted for the remainder of the original warranty period or for thirty (30) days, whichever is longer.\n\n"
                + "5. Limitation of Liability:\n"
                + "   In no event shall the Licensor be liable for special, incidental, indirect, or consequential damages\n"
                + "   resulting from the use or inability to use the Software, even if the Licensor has been advised of\n"
                + "   the possibility of such damages. In no event shall the Licensor's liability for damages exceed\n"
                + "   the amount paid to purchase the Software.\n\n"
                + "6. Termination:\n"
                + "   This Agreement remains in effect until terminated. This Agreement will automatically terminate without notice\n"
                + "   from the Licensor if you fail to comply with any term or condition of this Agreement.\n\n"
                + "7. Miscellaneous:\n"
                + "   This Agreement constitutes the entire agreement between you and the Licensor and supersedes all prior\n"
                + "   communications, proposals, or agreements, whether verbal or written, regarding the Software.\n");
        taLicenseAgreement.setLineWrap(true);
        taLicenseAgreement.setWrapStyleWord(true);
        taLicenseAgreement.setEditable(false);

        JScrollPane licenseScrollPane = new JScrollPane(taLicenseAgreement);
        licenseScrollPane.setBorder(new EmptyBorder(10, 10, 10, 10));

        // License owner field
        tfLicenseOwner = new JTextField();
        tfLicenseOwner.setColumns(30);
        tfLicenseOwner.setToolTipText("Licensed to (Owner of the license, min 6 chars)");

        JPanel ownerPanel = new JPanel(new BorderLayout());
        ownerPanel.setBorder(new EmptyBorder(0, 10, 0, 10));
        ownerPanel.add(tfLicenseOwner, BorderLayout.CENTER);

        // Agree checkbox
        cbAgree = new JCheckBox("Agree");
        cbAgree.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Buttons (use your builder if it returns Swing JButton)
        btnProceed = builder.buildButton("Proceed");
        btnProceed.setEnabled(false);

        btnClose = builder.buildButton("Close");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        buttonPanel.add(btnProceed);
        buttonPanel.add(btnClose);

        // Center panel to mimic VBox content
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(radioAndUploadPanel);
        centerPanel.add(licenseScrollPane);
        centerPanel.add(ownerPanel);
        centerPanel.add(cbAgree);
        centerPanel.add(buttonPanel);

        mainPanel.add(headerContainer, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
    }

    @Override
    public void initUIBehaviour() {
        rbRequestLicense.addActionListener(event -> {
            if (rbRequestLicense.isSelected()) {
                uploadButton.setText("Change Destination Folder (Desktop)");
                filePathField.setText("");
                tfLicenseOwner.setEnabled(true);
                defineDesktopFolder();
            }
        });

        rbActivateLicense.addActionListener(event -> {
            if (rbActivateLicense.isSelected()) {
                uploadButton.setText("Locate Response File (Desktop)");
                filePathField.setText("");
                tfLicenseOwner.setEnabled(true);
                defineDesktopFolder();
            }
        });

        rbUseExistentLicense.addActionListener(event -> {
            if (rbUseExistentLicense.isSelected()) {
                uploadButton.setText("Locate Existing License");
                filePathField.setText("");
                tfLicenseOwner.setEnabled(false);
                defineDesktopFolder();
            }
        });

        uploadButton.addActionListener(e -> {
            File startingPoint = new File(fileFolder);
            if (rbRequestLicense.isSelected()) {
                String chosenPath = openDirectoryChooserFor(startingPoint, mainPanel);
                filePathField.setText(chosenPath != null ? chosenPath : "");
            } else {
                JFileChooser fileChooser =
                        new JFileChooser(startingPoint.exists() && startingPoint.isDirectory() ? startingPoint : null);
                fileChooser.setDialogTitle("Open Request AR Web File");
                // Allow all files; we enforce .response manually when needed

                int result = fileChooser.showOpenDialog(mainPanel);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    if (file != null) {
                        if (rbActivateLicense.isSelected()) {
                            if (file.getName().endsWith(".response")) {
                                filePathField.setText(file.getAbsolutePath());
                            } else {
                                performMessage.errorMessage(
                                        "Invalid file selected!",
                                        "Must have a '.response' extension.",
                                        "File selected:",
                                        file.getName(),
                                        null,
                                        0);
                            }
                        } else {
                            filePathField.setText(file.getAbsolutePath());
                        }
                    }
                }
            }
        });

        // Enable the proceed button only if the checkbox is checked
        cbAgree.addActionListener(event -> btnProceed.setEnabled(cbAgree.isSelected()));

        btnClose.addActionListener(event -> {
            log.info("ARLicensePane close()");
            SwingUtilities.invokeLater(() -> {
                Window window = SwingUtilities.getWindowAncestor(mainPanel);
                if (window != null) {
                    window.dispose();
                }
            });
        });

        // Actions for Proceed button
        btnProceed.addActionListener(event -> {
            if (!cbAgree.isSelected()) {
                performMessage.errorMessage(
                        "License Aggreement!",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please read and agrred with our license terms!</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Acknowledge and accept the license agreement to proceed with the installation.</span>",
                        "<span style='font-style: italic;'>This software is governed by legal terms and conditions. Your use constitutes acceptance of these terms.</span>",
                        null,
                        0);

            } else {
                try {
                    if (tfLicenseOwner.getText().isEmpty() && (!rbUseExistentLicense.isSelected())) {
                        performMessage.errorMessage(
                                "Mandatory field is missing!",
                                "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please provide the \"User License\" field information!</span>",
                                null,
                                null,
                                null,
                                0);
                    } else {
                        if (rbRequestLicense.isSelected()) {
                            if (!Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                fileFolder = filePathField.getText().trim();
                            }
                            LicenseManager.generateRequestFile(
                                    fileFolder, tfLicenseOwner.getText().trim());
                            performMessage.showCustomModalDialogDragWin11(
                                    "Request File Generated Successfully!",
                                    "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>The request file for license generation has been successfully created.</span>",
                                    "<span style='color: #0277BD; font-weight: bold;'>Please send this request file to your provider to receive the User License.</span>",
                                    "<span style='font-style: italic;'>This request file contains encrypted system information required for license activation.</span>",
                                    "<span style='color: #E65100; font-weight: bold;'>Request file path:</span> <span style='font-weight: bold;'>"
                                            + fileFolder + "</span>",
                                    false,
                                    "OK",
                                    null,
                                    0);

                        } else if (rbActivateLicense.isSelected()) {

                            if (!Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                fileFolder = filePathField.getText().trim();
                            } else {
                                fileFolder += "\\ARWeb 1.1.0.response";
                            }

                            if (LicenseManager.importResponseFile(fileFolder)) {

                                String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
                                if (Strings.isNullOrEmpty(licensePath)) {
                                    licensePath = System.getProperty("user.dir");
                                }

                                if (checkLicense(licensePath)) {
                                    performMessage.showCustomModalDialogDragWin11(
                                            "License Activated!",
                                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Your license has been successfully activated.</span>",
                                            "<span style='color: #0277BD; font-weight: bold;'>You may now use the application without restrictions.</span>",
                                            "<span style='font-style: italic;'>You can close this message and continue.</span>",
                                            "<span style='color: #E65100; font-weight: bold;'>License path:</span> <span style='font-weight: bold;'>"
                                                    + licensePath + "</span>",
                                            false,
                                            "OK",
                                            null,
                                            0);
                                }

                            } else {

                                performMessage.errorMessage(
                                        "License Activation Failed!",
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Response file not found or could not be processed.</span>",
                                        "<span style='color: #0277BD; font-weight: bold;'>Please make sure the response file is available and try again.</span>",
                                        "<span style='font-style: italic;'>Ensure the file was received from your provider and has not been modified.</span>",
                                        "<span style='color: #E65100; font-weight: bold;'>Expected license path:</span> <span style='font-weight: bold;'>"
                                                + fileFolder + "</span>",
                                        0);
                            }
                        } else if (rbUseExistentLicense.isSelected()) {
                            if (Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                performMessage.errorMessage(
                                        "Mandatory field is missing!",
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please provide the \"License Location path\" field information!</span>",
                                        null,
                                        null,
                                        null,
                                        0);
                            } else {
                                String licensePath = filePathField.getText().trim();
                                licensePath = licensePath.substring(0, licensePath.lastIndexOf("\\"));

                                if (checkLicense(licensePath)) {
                                    performMessage.showCustomModalDialogDragWin11(
                                            "The License has been located!",
                                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Your license has been successfully located.</span>",
                                            "<span style='color: #0277BD; font-weight: bold;'>You may now use the application without restrictions.</span>",
                                            "<span style='font-style: italic;'>You can close this message and continue.</span>",
                                            "<span style='color: #E65100; font-weight: bold;'>License path:</span> <span style='font-weight: bold;'>"
                                                    + filePathField.getText().trim() + "</span>",
                                            false,
                                            "OK",
                                            null,
                                            0);

                                    arPropertyManager.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), licensePath);
                                }
                            }
                        }
                    }
                } catch (Exception error) {
                    log.error("License Activation Error: {} ->  {}", fileFolder, error.getMessage());
                    performMessage.errorMessage(
                            "License Activation Error",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the license activation or verification process.</span>",
                            "<span style='font-weight: bold;'>" + fileFolder + "</span>.",
                            "<span style='color: #E65100; font-weight: bold;'>Please ensure the response file is valid and accessible, and that the application has the required permissions.</span>",
                            "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                            0);
                }
            }
        });
    }

    private boolean checkLicense(String licensePath) throws Exception {
        LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

        String msgValid = "The license file is valid and the application is authorized for use.";
        String msgNextStep = "You can now proceed with normal application usage.";

        String msgColor = "#0277BD";
        if (!licenseStatus.equals(LicenceVal.VALID)) {
            msgValid = "The license file is not valid and the application is not authorized for use.";
            msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
            msgColor = "#C62828"; // Soft, elegant red tone

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
    }

    private String openDirectoryChooserFor(File startingDirectory, Component owner) {
        JFileChooser chooser = new JFileChooser(startingDirectory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(owner);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            return selected != null ? selected.getAbsolutePath() : null;
        }
        return null;
    }

    private void defineDesktopFolder() {
        try {
            // 1) Try user home Desktop
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                File desktop = new File(userHome, "Desktop");
                if (desktop.exists() && desktop.isDirectory()) {
                    fileFolder = desktop.getAbsolutePath();
                    return;
                }
            }

            // 2) Fallback: OS home / default folder
            File home = FileSystemView.getFileSystemView().getHomeDirectory();
            if (home != null && home.exists() && home.isDirectory()) {
                fileFolder = home.getAbsolutePath();
                return;
            }

            // 3) Final fallback: PATH_LICENSE or working dir
            if (Strings.isNullOrEmpty(fileFolder)) {
                fileFolder = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
                if (Strings.isNullOrEmpty(fileFolder)) {
                    fileFolder = System.getProperty("user.dir");
                }
            }
        } catch (Exception ex) {
            if (Strings.isNullOrEmpty(fileFolder)) {
                fileFolder = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
                if (Strings.isNullOrEmpty(fileFolder)) {
                    fileFolder = System.getProperty("user.dir");
                }
            }
            log.warn("Error determining Desktop folder, using: " + fileFolder, ex);
        }
    }
}
