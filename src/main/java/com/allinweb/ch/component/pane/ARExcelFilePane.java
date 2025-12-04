package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.FormatOption;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARExcelFilePane extends ARPane {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARExcelFilePane instance;
    private final Gson gson = new Gson();

    // Swing Components
    private JLabel titleLabel;
    private JLabel blockNameLabel;
    private JLabel pathExportLabel;
    private JLabel fileExportLabel;
    private JLabel fileTypeLabel;
    private JLabel delimeterCSVLabel;
    private JTextField pathExport;
    private JTextField fileExport;
    private JComboBox<String> fileTypeChoiceBox;
    private JComboBox<FormatOption> comboBoxCSVColumns;
    private JButton pathExportButton;
    private JButton pathDeleteButton;
    private JButton saveButton;
    private JButton cancelButton;
    private JPanel mainPane;
    private double buttonWidth = 200;
    private String excelPath;
    private String directory;
    private String fileName;
    private String delimiter;
    private SplitDTO splitDTO;
    private String sessionId;

    private ARExcelFilePane() {
        super();
    }

    public static ARExcelFilePane getInstance() {
        if (instance == null) {
            synchronized (ARExcelFilePane.class) {
                if (instance == null) {
                    instance = new ARExcelFilePane();
                }
            }
        }
        return instance;
    }

    public void initialize(String sessionId, SplitDTO splitDTO) {
        this.sessionId = sessionId;
        this.splitDTO = splitDTO;

        extractPathAndFileName();
    }

    @Override
    public JPanel getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        mainPane = new JPanel();
        mainPane.setLayout(new BorderLayout());
        mainPane.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Title Panel
        blockNameLabel = new JLabel("Block Name:");
        blockNameLabel.setForeground(Color.BLUE);
        blockNameLabel.setFont(blockNameLabel.getFont().deriveFont(18f));

        titleLabel = new JLabel("#" + splitDTO.getBlockOrderNumber() + "-" + splitDTO.getBlockName());
        titleLabel.setForeground(new Color(0, 100, 0));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        titlePanel.add(blockNameLabel);
        titlePanel.add(titleLabel);

        // Path and File Panel
        pathExportLabel = new JLabel("Export Path:");
        fileExportLabel = new JLabel("File Name:");
        fileTypeLabel = new JLabel("File Type:");
        delimeterCSVLabel = new JLabel("Delimiter:");

        pathExport = new JTextField(directory, 20);
        fileExport = new JTextField(fileName, 15);

        fileTypeChoiceBox = new JComboBox<>(new String[] {ARConstants.FILE_FORMAT_EXCEL, ARConstants.FILE_FORMAT_CSV});
        if (!fileName.toLowerCase().endsWith(".csv")) {
            fileTypeChoiceBox.setSelectedIndex(0);
        } else {
            fileTypeChoiceBox.setSelectedIndex(1);
        }

        comboBoxCSVColumns = new JComboBox<>();
        comboBoxCSVColumns.addItem(new FormatOption("Comma: \",\"", ","));
        comboBoxCSVColumns.addItem(new FormatOption("Pipe \"|\"", "|"));
        comboBoxCSVColumns.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FormatOption fo) {
                    setText(fo.getText());
                    setForeground(Color.BLACK);
                }
                return this;
            }
        });

        pathExportButton = new JButton("...");
        pathDeleteButton = new JButton("X");
        saveButton = new JButton("OK");
        cancelButton = new JButton("Close");

        JPanel gridPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gridPanel.add(pathExportLabel, gbc);
        gbc.gridx = 1;
        gridPanel.add(fileExportLabel, gbc);
        gbc.gridx = 2;
        gridPanel.add(fileTypeLabel, gbc);
        gbc.gridx = 3;
        gridPanel.add(delimeterCSVLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gridPanel.add(pathExport, gbc);
        gbc.gridx = 1;
        gridPanel.add(fileExport, gbc);
        gbc.gridx = 2;
        gridPanel.add(fileTypeChoiceBox, gbc);
        gbc.gridx = 3;
        gridPanel.add(comboBoxCSVColumns, gbc);
        gbc.gridx = 4;
        gridPanel.add(pathExportButton, gbc);
        gbc.gridx = 5;
        gridPanel.add(pathDeleteButton, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        mainPane.add(titlePanel, BorderLayout.NORTH);
        mainPane.add(gridPanel, BorderLayout.CENTER);
        mainPane.add(buttonPanel, BorderLayout.SOUTH);
    }

    @Override
    public void initUIBehaviour() {
        pathExportButton.addActionListener(e -> openChooserFor(pathExport, true));
        pathDeleteButton.addActionListener(e -> {
            pathExport.setText("");
            fileExport.setText("");
            saveConfigurations();
        });

        saveButton.addActionListener(e -> saveConfigurations());
        cancelButton.addActionListener(
                e -> SwingUtilities.getWindowAncestor(mainPane).dispose());
    }

    private void extractPathAndFileName() {
        directory = "";
        fileName = "";
        excelPath = splitDTO.getExportFile() != null ? splitDTO.getExportFile() : "";
        delimiter = ",";

        String[] fileParts = excelPath.split(":");
        if (fileParts.length > 2) {
            delimiter = fileParts[2];
            excelPath = excelPath.replace(":,", "").replace(":|", "");
        }

        if (!excelPath.isEmpty()) {
            try {
                Path path = Paths.get(excelPath);
                directory = path.getParent() != null ? path.getParent().toString() : "";
                fileName = path.getFileName() != null ? path.getFileName().toString() : "";

                if (fileName.equalsIgnoreCase("No Excel Export File")) {
                    fileName = "";
                }

                log.info("Identified Directory: " + directory);
                log.info("Identified File Name: " + fileName);
            } catch (Exception ex) {
                log.error("Excel Path Error: " + ex.getMessage());
            }
        } else {
            log.info("No export file path provided.");
        }
    }

    private void openChooserFor(JTextField field, boolean isDirectory) {
        JFileChooser chooser = new JFileChooser(directory != null ? directory : System.getProperty("user.dir"));
        chooser.setFileSelectionMode(isDirectory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        int option = chooser.showOpenDialog(mainPane);
        if (option == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            field.setText(selected.getAbsolutePath());
        }
    }

    private void saveConfigurations() {
        String exportFile = "";
        String delimiter = "|";
        FormatOption selected = (FormatOption) comboBoxCSVColumns.getSelectedItem();
        if (selected != null) delimiter = selected.getValue();

        String filePath = fileExport.getText().trim();
        if (Strings.isNullOrEmpty(filePath)) {
            performMessage.errorMessage("File Name Is Empty!", "Type the File Name!", null, null, null, 0);
            return;
        }

        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0) filePath = filePath.substring(0, lastDotIndex);
        filePath += fileTypeChoiceBox.getSelectedItem();
        fileExport.setText(filePath);

        exportFile = pathExport.getText() + "/" + fileExport.getText();
        exportFile = exportFile.replace("\\", "/") + ":" + delimiter;

        String blockTable = "block";
        int whereId = splitDTO.getBotJobId();
        String updateAction = "updateInstructions";

        if (sessionId != null && sessionId.matches(".*componentTasks.*")) {
            blockTable = "component_block";
            whereId = splitDTO.getHomeBankingId();
            updateAction = "componentsUpdate";
        }

        ErrorMessage errorMessage =
                performDataBase.updateBlockExportFile(blockTable, whereId, splitDTO.getBlockId(), exportFile);
        if (errorMessage != null) performMessage.errorMessageOperationFailed(errorMessage);

        if (errorMessage == null) {
            performLists.updateMemoryBlockExcelExport(blockTable, whereId, splitDTO.getBlockId(), exportFile);
            List<BotJobLoadDTO> listToSend =
                    blockTable.equals("block") ? performLists.getListBotJob() : performLists.getListBotJobComp();
            String jsonData =
                    gson.toJson(listToSend.isEmpty() ? List.of() : performLists.buildJsonViewData(listToSend));
            webSocketSessionManager.sendMessageJson(splitDTO.getHomeBankingId(), sessionId, jsonData, updateAction);
        }

        JOptionPane.showMessageDialog(
                mainPane,
                "Export File: " + exportFile + "\n"
                        + (errorMessage == null ? "Bot-Job Updated successfully!" : "Bot-Job NOT Updated!"),
                "Export Result",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
