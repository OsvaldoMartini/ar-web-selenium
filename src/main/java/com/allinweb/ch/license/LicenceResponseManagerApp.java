package com.allinweb.ch.license;

import com.allinweb.ch.facade.PerformMessage;
import com.google.common.base.Strings;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.ptr.PointerByReference;
import java.awt.*;
import java.io.File;
import java.text.NumberFormat;
import javax.swing.*;
import javax.swing.text.NumberFormatter;

public class LicenceResponseManagerApp {

    private static final PerformMessage performMessage = PerformMessage.getInstance();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LicenceResponseManagerApp().showUI());
    }

    private void showUI() {
        JFrame frame = new JFrame("Generate AR Web Licence File App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 300);
        frame.setLayout(new BorderLayout());

        // Header label
        JLabel headerLabel = new JLabel("AR Web Licence response file generator");
        headerLabel.setOpaque(true);
        headerLabel.setBackground(new Color(0, 120, 215));
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        frame.add(headerLabel, BorderLayout.NORTH);

        // Grid panel
        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // File upload
        JLabel filePathLabel = new JLabel("Upload AR request file:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        grid.add(filePathLabel, gbc);

        JTextField filePathField = new JTextField();
        filePathField.setEditable(false);
        gbc.gridx = 1;
        gbc.weightx = 1;
        grid.add(filePathField, gbc);

        JButton uploadButton = new JButton("Upload");
        gbc.gridx = 2;
        gbc.weightx = 0;
        grid.add(uploadButton, gbc);

        uploadButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Open Request AR Web File");

            // Set initial directory to Desktop
            try {
                PointerByReference ppszPath = new PointerByReference();
                if (Shell32.INSTANCE.SHGetKnownFolderPath(KnownFolders.FOLDERID_Desktop, 0, null, ppszPath) == 0) {

                    String desktopPath = ppszPath.getValue().getWideString(0);
                    Native.free(Pointer.nativeValue(ppszPath.getValue()));
                    fileChooser.setCurrentDirectory(new File(desktopPath));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (file.getName().endsWith(".request")) {
                    filePathField.setText(file.getAbsolutePath());
                } else {
                    performMessage.errorMessage(
                            "Invalid file selected!",
                            "Must have a '.request' extension.",
                            "File selected:",
                            file.getName(),
                            null,
                            0);
                }
            }
        });

        // Number of days
        JLabel daysLabel = new JLabel("Number of days granted:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        grid.add(daysLabel, gbc);

        NumberFormat format = NumberFormat.getIntegerInstance();
        format.setGroupingUsed(false);
        NumberFormatter numberFormatter = new NumberFormatter(format);
        numberFormatter.setAllowsInvalid(false);
        JFormattedTextField daysField = new JFormattedTextField(numberFormatter);
        daysField.setColumns(10);
        gbc.gridx = 1;
        grid.add(daysField, gbc);

        // Generate button
        JButton generateButton = new JButton("Generate");
        gbc.gridx = 1;
        gbc.gridy = 2;
        grid.add(generateButton, gbc);

        generateButton.addActionListener(e -> {
            try {
                if (Strings.isNullOrEmpty(filePathField.getText().trim())) {
                    performMessage.errorMessage(
                            "Error reading the file!", "You have not selected any file!", null, null, null, 0);
                    return;
                }

                if (Strings.isNullOrEmpty(daysField.getText().trim())) {
                    performMessage.errorMessage(
                            "Field empty!", "You must provide the quantity of the Days!!", null, null, null, 0);
                    return;
                }

                String decryptedContent = LicenseManager.getDecryptedResponseFile(filePathField.getText());
                if ("Invalid file selected".equals(decryptedContent)) return;

                String response = new LicenseManager()
                        .genereteResponseFile(decryptedContent, Integer.parseInt(daysField.getText()));
                if ("File creation success".equals(response)) {
                    performMessage.errorMessage(
                            "File was Generated successfully!",
                            "File Name:",
                            "ARWeb 1.1.0.response",
                            "Generated successfully.",
                            null,
                            0);
                }

            } catch (Exception ex) {
                performMessage.errorMessage(
                        "Error writing to the file!",
                        "File Name:",
                        "ARWeb 1.1.0.response",
                        "Please verify that you have permission to read/write to the Desktop.",
                        null,
                        0);
            }
        });

        // Close button
        JButton closeButton = new JButton("Close");
        gbc.gridy = 3;
        gbc.gridx = 1;
        grid.add(closeButton, gbc);
        closeButton.addActionListener(e -> frame.dispose());

        frame.add(grid, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
