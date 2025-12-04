package com.allinweb.ch.license;

import com.allinweb.ch.facade.PerformMessage;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.ptr.PointerByReference;
import java.awt.*;
import java.io.IOException;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LicenseActivationApp {

    private static final PerformMessage performMessage;

    static {
        performMessage = PerformMessage.getInstance();
    }

    private JFrame frame;

    public static void main(String[] args) throws Exception {
        String licensePath = System.getProperty("user.dir");
        if (args.length > 0) {
            licensePath = args[0];
        }

        if (!LicenseManager.checkLicenseFile(licensePath).isActive()) {
            String finalLicensePath = licensePath;
            SwingUtilities.invokeLater(() -> {
                try {
                    new LicenseActivationApp().start(finalLicensePath);
                } catch (Exception e) {
                    log.error("Error starting LicenseActivationApp: {}", e.getMessage(), e);
                }
            });
        } else {
            log.info("AR Web agree licence terms are active.\n\nPress OK to proceed.");
            // Previously they might show a dialog and continue the application
        }
    }

    /**
     * Windows Desktop directory via JNA (unchanged logic, only used in Swing now).
     */
    private static String getDesktopDir() throws IOException {
        PointerByReference ppszPath = new PointerByReference();
        if (Shell32.INSTANCE.SHGetKnownFolderPath(KnownFolders.FOLDERID_Desktop, 0, null, ppszPath) != 0) {

            log.warn("Error reading/writing to the file! -> Desktop Folder");
            return null;
        }

        String desktopPath = ppszPath.getValue().getWideString(0);
        Native.free(Pointer.nativeValue(ppszPath.getValue()));
        return desktopPath;
    }

    /**
     * Entry point for building the Swing UI (replacement for JavaFX start()).
     */
    public void start(String licensePath) throws Exception {
        LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

        String msgValid = "The license file is valid and the application is authorized for use.";
        String msgNextStep = "You can now proceed with normal application usage.";

        String msgColor = "#0277BD";
        if (!licenseStatus.equals(LicenceVal.VALID)) {
            msgValid = "The license file is not valid and the application is not authorized for use.";
            msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
            msgColor = "#C62828"; // Soft, elegant red tone
        }

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

        buildSwingUI(licensePath);
    }

    private void buildSwingUI(String licensePath) {
        frame = new JFrame("Activation Software Required");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(600, 450);
        frame.setLocationRelativeTo(null);

        // === Header label ===
        JLabel headerLabel = new JLabel("AR Web Activation software required", SwingConstants.LEFT);
        headerLabel.setOpaque(true);
        headerLabel.setBackground(new Color(0x0078d7));
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === Radio buttons (Request / Activate) ===
        JRadioButton rbRequestLicense = new JRadioButton("Request License");
        JRadioButton rbActivateLicense = new JRadioButton("Activate with License");

        ButtonGroup toggleGroup = new ButtonGroup();
        toggleGroup.add(rbRequestLicense);
        toggleGroup.add(rbActivateLicense);

        rbRequestLicense.setSelected(true);

        JPanel radioButtonsBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        radioButtonsBox.add(rbRequestLicense);
        radioButtonsBox.add(rbActivateLicense);

        // === License agreement text area inside scroll ===
        JTextArea taLicenseAgreement = new JTextArea(
                "SOFTWARE LICENSE AGREEMENT\n\n"
                        + "Important - Read Carefully: This License Agreement (\"Agreement\") is a legal contract between you (an individual or a legal entity) and Allinweb SA. (\"Licensor\") for the software that accompanies this agreement, which includes associated software and media material, whether printed, electronic, or online (\"Software\").\n\n"
                        + "1. License Grant: Subject to the terms of this Agreement, the Licensor grants you a non-exclusive, non-transferable license to use the Software for internal purposes according to the following limitations and in compliance with the provided documentation.\n\n"
                        + "2. Restrictions: You are not authorized to:\n"
                        + "   - Modify, translate, adapt, or create derivative works from the Software.\n"
                        + "   - Reverse engineer, decompile, disassemble, or otherwise attempt to discover the Software’s source code.\n"
                        + "   - Resell, rent, sublicense, distribute, or otherwise transfer the Software without prior written consent from the Licensor.\n"
                        + "   - Remove any copyright notices, trademarks, or other proprietary notices included in the Software.\n\n"
                        + "3. Ownership of the Software: The Software is protected by copyright laws and international treaties, as well as other intellectual property laws and treaties. The Software is licensed, not sold.\n\n"
                        + "4. Limited Warranty: The Licensor warrants that the Software will operate substantially in accordance with the documentation for a period of ninety (90) days from the date of your purchase. Any replacement Software will be warranted for the remainder of the original warranty period or for thirty (30) days, whichever is longer.\n\n"
                        + "5. Limitation of Liability: In no event shall the Licensor be liable for special, incidental, indirect, or consequential damages resulting from the use or inability to use the Software, even if the Licensor has been advised of the possibility of such damages. In no event shall the Licensor’s liability for damages exceed the amount paid to purchase the Software.\n\n"
                        + "6. Termination: This Agreement remains in effect until terminated. This Agreement will automatically terminate without notice from the Licensor if you fail to comply with any term or condition of this Agreement.\n\n"
                        + "7. Miscellaneous: This Agreement constitutes the entire agreement between you and the Licensor and supersedes all prior communications, proposals, or agreements, whether verbal or written, regarding the Software.\n");
        taLicenseAgreement.setWrapStyleWord(true);
        taLicenseAgreement.setLineWrap(true);
        taLicenseAgreement.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(taLicenseAgreement);
        scrollPane.setPreferredSize(new Dimension(580, 250));

        // === License owner text field ===
        JTextField tfLicenseOwner = new JTextField();
        tfLicenseOwner.setToolTipText("Licensed to (Owner of the license, min 6 chars)");

        // === Agree checkbox ===
        JCheckBox cbAgree = new JCheckBox("Agree");

        // === Buttons ===
        JButton btnProceed = new JButton("Proceed");
        btnProceed.setEnabled(false);

        JButton btnClose = new JButton("Close");

        // === Wire checkbox to enable/disable Proceed ===
        cbAgree.addActionListener(e -> btnProceed.setEnabled(cbAgree.isSelected()));

        // === Proceed button logic ===
        btnProceed.addActionListener(e -> {
            if (!cbAgree.isSelected()) {
                performMessage.errorMessage(
                        "License Aggreement!",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please read and agrred with our license terms!</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Acknowledge and accept the license agreement to proceed with the installation.</span>",
                        "<span style='font-style: italic;'>This software is governed by legal terms and conditions. Your use constitutes acceptance of these terms.</span>",
                        null,
                        0);
                return;
            }

            try {
                if (tfLicenseOwner.getText().isEmpty() && !LicenseManager.importResponseFile(licensePath)) {
                    performMessage.errorMessage(
                            "Mandatory field is missing!",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please provide the \"User License\" field information!</span>",
                            "<span style='color: #E65100; font-weight: bold;'>You must accept the User License Agreement to continue with the installation.</span>",
                            "<span style='font-style: italic;'>By proceeding, you confirm that you understand and agree to the terms of the User License.</span>",
                            null,
                            0);
                } else {
                    try {
                        if (rbRequestLicense.isSelected()) {
                            String desktopDir = getDesktopDir();

                            LicenseManager.generateRequestFile(
                                    desktopDir, tfLicenseOwner.getText().trim());
                            performMessage.showCustomModalDialogDragWin11(
                                    "Request File Generated Successfully!",
                                    "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>The request file for license generation has been successfully created.</span>",
                                    "<span style='color: #0277BD; font-weight: bold;'>Please send this request file to your provider to receive the User License.</span>",
                                    "<span style='font-style: italic;'>This request file contains encrypted system information required for license activation.</span>",
                                    "<span style='color: #E65100; font-weight: bold;'>License path:</span> <span style='font-weight: bold;'>"
                                            + licensePath + "</span>",
                                    false,
                                    "OK",
                                    null,
                                    0);
                        } else if (rbActivateLicense.isSelected() && LicenseManager.importResponseFile(licensePath)) {
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
                        } else {
                            performMessage.errorMessage(
                                    "License Activation Failed!",
                                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Response file not found or could not be processed.</span>",
                                    "<span style='color: #0277BD; font-weight: bold;'>Please make sure the response file is available and try again.</span>",
                                    "<span style='font-style: italic;'>Ensure the file was received from your provider and has not been modified.</span>",
                                    "<span style='color: #E65100; font-weight: bold;'>Expected license path:</span> <span style='font-weight: bold;'>"
                                            + licensePath + "</span>",
                                    0);
                        }
                    } catch (Exception error) {
                        performMessage.errorMessage(
                                "License Activation Error",
                                "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the license activation or verification process.</span>",
                                "<span style='font-weight: bold;'>" + licensePath + "</span>.",
                                "<span style='color: #E65100; font-weight: bold;'>Please ensure the response file is valid and accessible, and that the application has the required permissions.</span>",
                                "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                                0);
                    }
                }
            } catch (Exception ex) {
                log.error("Error in Proceed button action: {}", ex.getMessage(), ex);
            }
        });

        // === Close button logic ===
        btnClose.addActionListener(e -> frame.dispose());

        JPanel actionButtonsBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        actionButtonsBox.add(btnProceed);
        actionButtonsBox.add(btnClose);

        // === Main layout (vertical) ===
        JPanel mainLayout = new JPanel();
        mainLayout.setLayout(new BoxLayout(mainLayout, BoxLayout.Y_AXIS));
        mainLayout.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mainLayout.add(headerLabel);
        mainLayout.add(Box.createVerticalStrut(10));
        mainLayout.add(radioButtonsBox);
        mainLayout.add(scrollPane);
        mainLayout.add(Box.createVerticalStrut(10));
        mainLayout.add(tfLicenseOwner);
        mainLayout.add(Box.createVerticalStrut(5));
        mainLayout.add(cbAgree);
        mainLayout.add(Box.createVerticalStrut(10));
        mainLayout.add(actionButtonsBox);

        frame.setContentPane(mainLayout);
        frame.setVisible(true);
    }
}
