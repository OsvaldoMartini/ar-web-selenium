package com.allinweb.ch.component.scene;

import java.awt.*;
import javax.swing.*;

public class WebViewExample {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(WebViewExample::new);
    }

    public WebViewExample() {
        // Main frame
        JFrame frame = new JFrame("Three Panels Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 300);
        frame.setLayout(null); // Absolute positioning to mimic AnchorPane

        // Create three panels with labels
        JPanel pane1 = createPanel("Pane 1", 50, 50);
        JPanel pane2 = createPanel("Pane 2", 200, 100);
        JPanel pane3 = createPanel("Pane 3", 350, 150);

        // Add panels to frame
        frame.add(pane1);
        frame.add(pane2);
        frame.add(pane3);

        // Show the frame
        frame.setVisible(true);

        // Second example: ListView
        SwingUtilities.invokeLater(() -> {
            JFrame listFrame = new JFrame("List Example");
            listFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            listFrame.setSize(200, 200);
            listFrame.setLayout(new BorderLayout());

            // Sample data
            String[] data = {"Item 1", "Item 2", "Item 3", "Item 4", "Item 5"};

            // Create a JList
            JList<String> listView = new JList<>(data);
            JScrollPane scrollPane = new JScrollPane(listView);

            // Add to frame
            listFrame.add(scrollPane, BorderLayout.CENTER);

            listFrame.setLocationRelativeTo(null);
            listFrame.setVisible(true);
        });
    }

    // Helper method to create a JPanel with a label
    private JPanel createPanel(String text, int x, int y) {
        JPanel panel = new JPanel(null); // null layout for absolute positioning
        panel.setBounds(x, y, 100, 100);
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        JLabel label = new JLabel(text);
        label.setBounds(10, 10, 80, 20);
        panel.add(label);

        return panel;
    }
}
