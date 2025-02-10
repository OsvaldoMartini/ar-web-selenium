package com.allinweb.ch.tests;

import com.allinweb.ch.util.ARConstants;
import com.google.common.base.Strings;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class CustomModalDialog2 {

    public static void showCustomModalDialog(
            String title, String message, String message2, String message3, String message4, boolean redMsg) {

        // Create a JDialog as a custom modal message dialog
        JDialog dialog = new JDialog((Frame) null, title, true); // true makes it modal
        if (message3 == null && message4 == null) {
            dialog.setSize(300, 210);
        } else if (message3 != null && message4 == null) {
            dialog.setSize(300, 230);
        } else if (message3 != null && message4 != null) {
            dialog.setSize(300, 300);
        } else {
            dialog.setSize(300, 300);
        }

        dialog.setLocationRelativeTo(null); // Center on screen
        dialog.setUndecorated(true); // Remove the default border  IT REMOVE TEH ORIGINAL TITLE

        // Style the dialog's main panel
        JPanel panel = new JPanel();
        panel.setBackground(new Color(255, 218, 51)); // Light orange background
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setLayout(new BorderLayout());

        // Build the message
        String titleMessage = "<html><br><span style='color: blue;'>";
        titleMessage += "<span style='font-size: 14px; font-weight: bold;'>" + title
                + "</span><br>---------------------------<br>";

        String concatenateMsg = "<span style='color: blue;'>" + message
                + "</span><br>---------------------------<br><span style='color: blue;'>" + message2 + "</span>";

        if (message3 != null && message4 == null) {
            concatenateMsg +=
                    "<br>---------------------------<br><span style='color: blue;'>" + message3 + "</span></html>";
        } else if (message3 != null && message4 != null) {
            concatenateMsg += "<br>---------------------------<br><span style='color: blue;'>"
                    + message3 + "</span><br>---------------------------<br><span style='color: blue;'>"
                    + message4 + "</span><br><br></html>";
        } else {
            concatenateMsg += "</html>";
        }

        // Apply red color to message if redMsg is true
        if (redMsg) {
            concatenateMsg = concatenateMsg.replaceAll("blue", "red");
        }
        concatenateMsg = titleMessage + concatenateMsg;

        // Create a JLabel to display the formatted message
        JLabel messageLabel = new JLabel(concatenateMsg, SwingConstants.CENTER);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(messageLabel, BorderLayout.CENTER);

        // OK button to close the dialog
        JButton okButton = new JButton("OK");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        panel.add(okButton, BorderLayout.SOUTH);

        // Add panel to dialog and set properties
        dialog.getContentPane().add(panel);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true); // This will block other input until the dialog is closed
    }

    public static ARConstants.DialogModal showCustomModalDialog(
            String title,
            String message,
            String message2,
            String message3,
            String message4,
            boolean redMsg,
            String secondButton) {
        // Create a JDialog as a custom modal message dialog
        JDialog dialog = new JDialog((Frame) null, title, true); // true makes it modal
        if (message2 != null && message3 == null && message4 == null) {
            dialog.setSize(380, 210);
        } else if (message2 != null && message3 != null && message4 == null) {
            dialog.setSize(380, 250);
        } else if (message2 != null && message3 != null && message4 != null) {
            dialog.setSize(380, 260);
        } else {
            dialog.setSize(380, 150);
        }

        dialog.setLocationRelativeTo(null); // Center on screen
        dialog.setUndecorated(true); // Remove the default border

        // Style the dialog's main panel
        JPanel panel = new JPanel();
        panel.setBackground(new Color(255, 218, 51)); // Light orange background
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setLayout(new BorderLayout());

        // Build the message
        String titleMessage = "<html><br><span style='color: blue;'>";
        titleMessage += "<span style='font-size: 14px; font-weight: bold;'>" + title
                + "</span><br>------------------------------<br>";

        String concatenateMsg = "<span style='color: blue;'>" + message;
        if (message2 != null) {
            concatenateMsg +=
                    "</span><br>------------------------------<br><span style='color: blue;'>" + message2 + "</span>";
        } else {
            concatenateMsg += "</span><br>------------------------------<br><br>                            <br>";
        }

        if (message3 != null && message4 == null) {
            concatenateMsg +=
                    "<br>------------------------------<br><span style='color: blue;'>" + message3 + "</span></html>";
        } else if (message3 != null && message4 != null) {
            concatenateMsg += "<br>------------------------------<br><span style='color: blue;'>"
                    + message3 + "</span><br>------------------------------<br><span style='color: blue;'>"
                    + message4 + "</span><br><br></html>";
        } else {
            concatenateMsg += "</html>";
        }

        // Apply red color to message if redMsg is true
        if (redMsg) {
            concatenateMsg = concatenateMsg.replaceAll("blue", "red");
        }
        concatenateMsg = titleMessage + concatenateMsg;

        // Create a JLabel to display the formatted message
        JLabel messageLabel = new JLabel(concatenateMsg, SwingConstants.CENTER);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(messageLabel, BorderLayout.CENTER);

        final ARConstants.DialogModal[] status = {ARConstants.DialogModal.NONE};

        if (!Strings.isNullOrEmpty(secondButton)) {

            // Create a JPanel for the buttons with horizontal layout
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0)); // Reduced horizontal gap to 5
            buttonPanel.setBackground(new Color(255, 218, 51)); // Light orange background
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Reduced padding to 10

            Dimension buttonSize = new Dimension(150, 20); // Set button width to 120 and height to 20

            // OK button with custom gradient background
            JButton okButton = new JButton("OK") {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isOpaque()) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        GradientPaint gradient =
                                new GradientPaint(0, 0, Color.LIGHT_GRAY, getWidth(), getHeight(), Color.WHITE);
                        g2.setPaint(gradient);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                    }
                    super.paintComponent(g);
                }
            };
            okButton.setPreferredSize(buttonSize);
            okButton.setFocusPainted(false);
            buttonPanel.add(okButton);

            // Stop button with custom gradient background
            JButton stopButton = new JButton(secondButton) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isOpaque()) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        GradientPaint gradient =
                                new GradientPaint(0, 0, Color.LIGHT_GRAY, getWidth(), getHeight(), Color.WHITE);
                        g2.setPaint(gradient);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                    }
                    super.paintComponent(g);
                }
            };
            stopButton.setPreferredSize(buttonSize);
            stopButton.setFocusPainted(false);
            buttonPanel.add(stopButton);

            // OK button action listener
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dialog.dispose();
                    status[0] = ARConstants.DialogModal.OK;
                }
            });

            // Stop button action listener
            stopButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    System.out.println("Stop button clicked!");
                    dialog.dispose();
                    status[0] = ARConstants.DialogModal.STOP;
                }
            });

            panel.add(buttonPanel, BorderLayout.SOUTH);
        } else {

            Dimension buttonSize = new Dimension(150, 20); // Set button width to 120 and height to 20

            // OK button with custom gradient background
            JButton okButton = new JButton("OK") {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isOpaque()) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        GradientPaint gradient =
                                new GradientPaint(0, 0, Color.LIGHT_GRAY, getWidth(), getHeight(), Color.WHITE);
                        g2.setPaint(gradient);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                    }
                    super.paintComponent(g);
                }
            };
            okButton.setPreferredSize(buttonSize);
            okButton.setFocusPainted(false);

            // OK button action listener
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dialog.dispose();
                    status[0] = ARConstants.DialogModal.OK;
                }
            });

            panel.add(okButton, BorderLayout.SOUTH);
        }

        // Add panel to dialog and set properties
        dialog.getContentPane().add(panel);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true); // This will block other input until the dialog is closed

        return status[0];
    }

    public static void main(String[] args) {
        // Test the dialog method

        //        showCustomModalDialog("Test Title First", "This is the first message", null, null, null, true, null);
        //
        //        showCustomModalDialog(
        //                "Test Title Second", "This is the first message", "This is the second message", null, null,
        // true, null);
        //
        //        showCustomModalDialog(
        //                "Test Title Third",
        //                "This is the first message",
        //                "This is the second message",
        //                "This is the third message",
        //                null,
        //                true,
        //                null);
        //
        //        showCustomModalDialog(
        //                "Test Title Fourth",
        //                "This is the first message",
        //                "This is the second message",
        //                "This is the third message",
        //                "This is the fourth message",
        //                true,
        //                null);

        ARConstants.DialogModal status = showCustomModalDialog(
                "Test Title Fifth",
                "This is the first message",
                "This is the second message",
                "This is the third message",
                "This is the fourth message",
                true,
                null);

        System.out.println(status);
    }
}
