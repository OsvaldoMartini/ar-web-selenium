package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARLicenseScene;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.awt.Color;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARInfoPane extends ARPane {

    private static final ARPropertyManager arPropertyManager;
    private static final ARLicenseScene arLicenseScene;
    protected static volatile ARInfoPane instance;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        arLicenseScene = ARLicenseScene.getInstance();
    }

    private boolean isEnabledLicence;

    // Swing components
    private JLabel applicationNameLabel;
    private JLabel compileDateLabel;
    private JLabel expirationDateLabel;
    private JLabel copyrightLabel;
    private JLabel rightsReservedLabel;
    private JButton btnLicense;
    private JPanel mainPane;

    // Private constructor to prevent instantiation
    private ARInfoPane() {
        super();
    }

    public static ARInfoPane getInstance() {
        if (instance == null) {
            synchronized (ARInfoPane.class) {
                if (instance == null) {
                    instance = new ARInfoPane();
                }
            }
        }
        return instance;
    }

    public void initialize(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }

    @Override
    public JPanel getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // --- Create labels from properties ---
        applicationNameLabel = new JLabel(arPropertyManager.getProperty(ARPropertyEnum.VERSION));
        compileDateLabel = new JLabel("Build: " + arPropertyManager.getProperty(ARPropertyEnum.BUILD));

        String expirationStr = arPropertyManager.getProperty(ARPropertyEnum.EXPIRATION);
        expirationDateLabel = new JLabel("Expiration: " + expirationStr);

        copyrightLabel = new JLabel("© Allinweb AG");
        rightsReservedLabel = new JLabel("All rights reserved");

        // --- Styles (Swing equivalents of the old -fx styles) ---
        Font baseFont = new Font("SansSerif", Font.PLAIN, 13);
        Color baseColor = new Color(0x2d3436);
        Color footerColor = new Color(0x636e72);

        applicationNameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        applicationNameLabel.setForeground(new Color(0x0984e3)); // blue

        compileDateLabel.setFont(baseFont);
        compileDateLabel.setForeground(baseColor);

        copyrightLabel.setFont(baseFont);
        rightsReservedLabel.setFont(baseFont);
        // base + footer tone
        copyrightLabel.setForeground(footerColor);
        rightsReservedLabel.setForeground(footerColor);

        // --- Expiration label dynamic color/logic ---
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate expirationDate = LocalDate.parse(expirationStr, formatter);
            LocalDate today = LocalDate.now();
            long daysLeft = ChronoUnit.DAYS.between(today, expirationDate);

            Color expirationColor = daysLeft > 30 ? new Color(0x218c52) : new Color(0xc0392b); // green or red
            expirationDateLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            expirationDateLabel.setForeground(expirationColor);

        } catch (Exception e) {
            // Fallback in case of invalid date format
            expirationDateLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            if (isEnabledLicence) {
                expirationDateLabel.setForeground(new Color(0xd63031)); // red
                expirationDateLabel.setText("⚠ Unlicensed Version – Features May Be Limited");
            } else {
                expirationDateLabel.setForeground(new Color(0x3498db)); // blue
                expirationDateLabel.setText("⚠ Unlicensed Version – Demo Version");
            }
        }

        // --- License button ---
        btnLicense = new JButton("License");
        btnLicense.setFocusPainted(false);
        btnLicense.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btnLicense.setBackground(new Color(0x007bff));
        btnLicense.setForeground(Color.WHITE);
        btnLicense.setBorder(new EmptyBorder(6, 12, 6, 12));

        // Hover effect (approximation of old CSS hover)
        btnLicense.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnLicense.setBackground(new Color(0x0056b3));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnLicense.setBackground(new Color(0x007bff));
            }
        });

        // --- Layout: vertical group equivalent of VBox ---
        JPanel infoGroup = new JPanel();
        infoGroup.setLayout(new javax.swing.BoxLayout(infoGroup, javax.swing.BoxLayout.Y_AXIS));

        int pad = (int) ARConstants.SPACE_M;
        infoGroup.setBorder(new EmptyBorder(pad, pad, pad, pad));
        infoGroup.setBackground(new Color(0xf1f2f6)); // light background

        // This border mimics subtle card style
        infoGroup.setOpaque(true);

        infoGroup.add(applicationNameLabel);
        infoGroup.add(javax.swing.Box.createVerticalStrut(5));
        infoGroup.add(compileDateLabel);
        infoGroup.add(javax.swing.Box.createVerticalStrut(5));
        infoGroup.add(expirationDateLabel);
        infoGroup.add(javax.swing.Box.createVerticalStrut(5));
        infoGroup.add(copyrightLabel);
        infoGroup.add(javax.swing.Box.createVerticalStrut(5));
        infoGroup.add(rightsReservedLabel);
        infoGroup.add(javax.swing.Box.createVerticalStrut(10));
        infoGroup.add(btnLicense);

        // --- Root pane ---
        mainPane = new JPanel(new java.awt.BorderLayout());
        mainPane.setBackground(new Color(0xf5f5f5));
        mainPane.add(infoGroup, java.awt.BorderLayout.CENTER);
    }

    @Override
    public void initUIBehaviour() {
        // Wire button to license scene (same logical behavior as JavaFX version)
        btnLicense.addActionListener(e -> arLicenseScene.showModal());
    }
}
