package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARSaveComponentPane extends ARPane {

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    private static final int SECONDS = 3; // Total seconds for the countdown
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final PerformActions performAction = PerformActions.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    protected static volatile ARSaveComponentPane instance;

    @SuppressWarnings("unused")
    private static Map<String, Session> activeSessions;

    JTextField nameTextField;
    JTextArea descriptionTextField;
    JLabel warningLabel;
    JButton closeButton;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private BlockDetailsDTO blockDetailsDTO;
    private List<BlockLoadDTO> savedBlockLoadList = new ArrayList<>();
    private JButton saveNewComponentButton;
    private JPanel mainPanel;

    // Private constructor to prevent instantiation
    private ARSaveComponentPane() {
        super();
    }

    public static ARSaveComponentPane getInstance() {
        if (instance == null) {
            synchronized (ARSaveComponentPane.class) {
                if (instance == null) {
                    instance = new ARSaveComponentPane();
                }
            }
        }
        return instance;
    }

    public void initialize(BlockDetailsDTO blockDetailsDTO) {
        this.blockDetailsDTO = blockDetailsDTO;
    }

    /**
     * Helper to convert JavaFX-style double spacing to Swing int pixels.
     */
    private int px(double value) {
        return (int) Math.round(value);
    }

    /**
     * Swing equivalent of getPaneReference.
     * Make sure ARPane is adapted to use JComponent instead of JavaFX Pane.
     */
    @Override
    public JPanel getPaneReference() {
        return mainPanel;
    }

    @Override
    public void initUIComponents() {

        // Main container similar to AnchorPane + VBox
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(
                px(ARConstants.SPACE_M), px(ARConstants.SPACE_M), px(ARConstants.SPACE_M), px(ARConstants.SPACE_M)));

        // Buttons
        saveNewComponentButton = builder.buildButton("Save New Component", px(ARConstants.SPACE_L));
        closeButton = builder.buildButton(" Close ", px(ARConstants.SPACE_L));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, px(ARConstants.SPACE_SM), 0));
        actionPanel.add(saveNewComponentButton);
        actionPanel.add(closeButton);

        // Name
        JLabel nameLabel = new JLabel("Name :         ");
        nameTextField = new JTextField(blockDetailsDTO.getBlockName());
        nameTextField.setPreferredSize(new Dimension(300, px(ARConstants.SPACE_XL)));

        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.setBorder(new EmptyBorder(
                px(ARConstants.SPACE_XS),
                px(ARConstants.SPACE_XS),
                px(ARConstants.SPACE_XS),
                px(ARConstants.SPACE_XS)));
        namePanel.add(nameLabel, BorderLayout.WEST);
        namePanel.add(nameTextField, BorderLayout.CENTER);

        // Description
        JLabel descriptionLabel = new JLabel("Description : ");
        descriptionTextField = new JTextArea(blockDetailsDTO.getBlockDescription());
        descriptionTextField.setLineWrap(true);
        descriptionTextField.setWrapStyleWord(true);
        descriptionTextField.setRows(4);

        JScrollPane descriptionScrollPane = new JScrollPane(descriptionTextField);
        descriptionScrollPane.setPreferredSize(new Dimension(300, 100));

        JPanel descriptionPanel = new JPanel(new BorderLayout());
        descriptionPanel.setBorder(new EmptyBorder(
                px(ARConstants.SPACE_XS),
                px(ARConstants.SPACE_XS),
                px(ARConstants.SPACE_XS),
                px(ARConstants.SPACE_XS)));
        descriptionPanel.add(descriptionLabel, BorderLayout.WEST);
        descriptionPanel.add(descriptionScrollPane, BorderLayout.CENTER);

        // Warning label
        warningLabel = new JLabel();
        warningLabel.setHorizontalAlignment(SwingConstants.CENTER);
        warningLabel.setForeground(Color.RED);

        // Vertical layout similar to VBox
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(namePanel);
        centerPanel.add(Box.createVerticalStrut(px(ARConstants.SPACE_SM)));
        centerPanel.add(descriptionPanel);
        centerPanel.add(Box.createVerticalStrut(px(ARConstants.SPACE_SM)));
        centerPanel.add(warningLabel);
        centerPanel.add(Box.createVerticalStrut(px(ARConstants.SPACE_SM)));
        centerPanel.add(actionPanel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
    }

    @Override
    public void initUIBehaviour() {

        saveNewComponentButton.addActionListener(e -> {
            if (nameTextField.getText() != null
                    && !nameTextField.getText().trim().isEmpty()
                    && descriptionTextField.getText() != null
                    && !descriptionTextField.getText().trim().isEmpty()) {
                try {
                    warningMSG("");

                    blockDetailsDTO.setBlockName(nameTextField.getText().trim());
                    blockDetailsDTO.setBlockDescription(
                            descriptionTextField.getText().trim());

                    this.savedBlockLoadList = performDataBase.loadSavedBlocksForBotJob(
                            blockDetailsDTO.getHomeBankingId(),
                            blockDetailsDTO.getBotJobId(),
                            blockDetailsDTO.getBotJobName());

                    boolean existName = savedBlockLoadList.stream().anyMatch(block -> block.getName()
                            .equalsIgnoreCase(nameTextField.getText().trim()));

                    if (existName) {
                        performMessage.showCustomModalDialogDragWin11(
                                "Name Already Taken!",
                                "<span style='font-weight: bold;'>Change the Component Name.</span>",
                                "<span style='font-weight: bold; color: #e854c8;'>The Name: \""
                                        + nameTextField.getText().trim()
                                        + "\" Has been take!</span>, and after  will jump back to <span style='font-weight: bold;'>first block (Use Case).</span>",
                                null,
                                null,
                                false,
                                "OK",
                                null,
                                0);

                        return;
                    }

                    log.info("Saving New Component Block: " + blockDetailsDTO.getBlockName());

                    try (Connection conn = performDataBase.getConnection()) {

                        ErrorMessage errorMessage = performDataBase.createCompBlock(blockDetailsDTO);
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createCompInstructions(blockDetailsDTO);
                        }
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createCompVariables(blockDetailsDTO);
                        }
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createUpdateCompInstruction(blockDetailsDTO);
                        }
                        if (errorMessage == null) {
                            errorMessage = performDataBase.createCompReferences(blockDetailsDTO);
                        }

                        if (errorMessage == null) {
                            errorMessage = performDataBase.loadBlocks(
                                    blockDetailsDTO.getHomeBankingId(), "", "component_block");
                            if (errorMessage == null) {
                                errorMessage = performDataBase.updateBlockOrderNumber(
                                        "component_block", blockDetailsDTO.getHomeBankingId(), true);
                            }
                        }

                        if (errorMessage == null) {

                            errorMessage = performDataBase.loadComponentsComplete(
                                    blockDetailsDTO.getHomeBankingId(),
                                    blockDetailsDTO.getBotJobId(),
                                    blockDetailsDTO.getBotJobName());

                            if (errorMessage != null) {
                                performMessage.errorMessageOperationFailed(errorMessage);
                            }

                            String jsonData = "[]";
                            if (!performLists.getListBotJobComp().isEmpty()) {
                                List<InstructionLoad> blockLoopInstructions =
                                        performLists.buildJsonViewData(performLists.getListBotJobComp());
                                jsonData = gson.toJson(blockLoopInstructions);
                            }

                            webSocketSessionManager.sendMessageJson(
                                    blockDetailsDTO.getHomeBankingId(), "componentTasks", jsonData, "componentsUpdate");
                        } else {
                            log.error(
                                    "Database problem : {} Title: {} Message: {}",
                                    errorMessage.getErrorHeader(),
                                    errorMessage.getErrorTitle(),
                                    errorMessage.getErrorMessage());
                        }

                        log.info("ARSaveComponentPane Close()");
                        Close();

                    } catch (SQLException error) {
                        log.info(error.getMessage());
                    }

                } catch (Exception error) {

                    log.error("Error: Unable to save the block. Please try again.\nError: " + error.getMessage());

                    showAlertTimer(
                            JOptionPane.ERROR_MESSAGE,
                            "Error Component",
                            "Unable to create new Component.",
                            "Error creating a new component",
                            "Please try again.",
                            null,
                            null);

                    warningMSG("Error creating a new component! Please try again.");
                }

            } else {
                warningMSG("Warning: give the correct name and description");
            }
        });

        closeButton.addActionListener(e -> Close());
    }

    private void Close() {
        log.info("ARSaveClonePane Close()");
        SwingUtilities.invokeLater(() -> {
            Window window = SwingUtilities.getWindowAncestor(mainPanel);
            if (window != null) {
                window.dispose();
            }
        });
    }

    private void warningMSG(String msg) {
        SwingUtilities.invokeLater(() -> warningLabel.setText(msg));
    }

    /**
     * Swing replacement for JavaFX Alert + Timeline countdown.
     */
    private void showAlertTimer(
            int messageType, String title, String header, String msg1, String msg2, String msg3, String msg4) {

        int remainingSeconds = SECONDS;

        JLabel headerLabel = new JLabel(header);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));

        JPanel messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (msg1 != null) {
            messagesPanel.add(new JLabel(msg1));
        }
        if (msg2 != null) {
            messagesPanel.add(new JLabel(msg2));
        }
        if (msg3 != null) {
            messagesPanel.add(new JLabel(msg3));
        }
        if (msg4 != null) {
            messagesPanel.add(new JLabel(msg4));
        }

        JLabel countdownLabel = new JLabel("Closing in " + remainingSeconds + " seconds...", SwingConstants.CENTER);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.add(headerLabel, BorderLayout.NORTH);
        contentPanel.add(messagesPanel, BorderLayout.CENTER);
        contentPanel.add(countdownLabel, BorderLayout.SOUTH);

        JOptionPane optionPane = new JOptionPane(contentPanel, messageType, JOptionPane.DEFAULT_OPTION);

        JDialog dialog = optionPane.createDialog(mainPanel, title);
        dialog.setModal(true);

        // mutable holder for the remaining seconds
        final int[] secsHolder = {remainingSeconds};
        Timer realTimer = new Timer(1000, e -> {
            secsHolder[0]--;
            countdownLabel.setText("Closing in " + secsHolder[0] + " seconds...");
            if (secsHolder[0] <= 0) {
                ((Timer) e.getSource()).stop();
                dialog.dispose();
            }
        });
        realTimer.start();

        dialog.setVisible(true);
    }
}
