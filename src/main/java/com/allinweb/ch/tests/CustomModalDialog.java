package com.allinweb.ch.tests;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class CustomModalDialog {

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

        String concatenaMsg = "<span style='color: blue;'>" + message
                + "</span><br>---------------------------<br><span style='color: blue;'>" + message2 + "</span>";

        if (message3 != null && message4 == null) {
            concatenaMsg +=
                    "<br>---------------------------<br><span style='color: blue;'>" + message3 + "</span></html>";
        } else if (message3 != null && message4 != null) {
            concatenaMsg += "<br>---------------------------<br><span style='color: blue;'>"
                    + message3 + "</span><br>---------------------------<br><span style='color: blue;'>"
                    + message4 + "</span><br><br></html>";
        } else {
            concatenaMsg += "</html>";
        }

        // Apply red color to message if redMsg is true
        if (redMsg) {
            concatenaMsg = concatenaMsg.replaceAll("blue", "red");
        }
        concatenaMsg = titleMessage + concatenaMsg;

        // Create a JLabel to display the formatted message
        JLabel messageLabel = new JLabel(concatenaMsg, SwingConstants.CENTER);
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

    public static void main(String[] args) {
        // Test the dialog method

        showCustomModalDialog(
                "Test Title", "This is the first message", "This is the second message", null, null, true);

        showCustomModalDialog(
                "Test Title",
                "This is the first message",
                "This is the second message",
                "This is the third message",
                null,
                true);

        showCustomModalDialog(
                "Test Title",
                "This is the first message",
                "This is the second message",
                "This is the third message",
                "This is the fourth message",
                true);
    }
}
