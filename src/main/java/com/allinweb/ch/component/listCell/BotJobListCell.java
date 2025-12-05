package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ErrorMessage;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class BotJobListCell extends JPanel implements ListCellRenderer<BotJobLoadDTO> {

    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private boolean isEnabledLicence;

    public BotJobListCell(ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, boolean isEnabledLicence) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.isEnabledLicence = isEnabledLicence;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(new EmptyBorder(5, 5, 5, 5));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends BotJobLoadDTO> list,
            BotJobLoadDTO value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        removeAll(); // clear previous components

        if (value == null) {
            return this;
        }

        // Labels
        JLabel botJobName = new JLabel(value.getName());
        botJobName.setPreferredSize(new Dimension(150, 20));

        JLabel botJobDescription = new JLabel(value.getDescription());
        botJobDescription.setPreferredSize(new Dimension(150, 20));

        JLabel homeBankingName = new JLabel(value.getHomeBankingLoadDTO().getName());
        homeBankingName.setPreferredSize(new Dimension(100, 20));

        JLabel statusLabel = new JLabel(value.isActive() ? "Active" : "Inactive");
        statusLabel.setForeground(value.isActive() ? Color.BLACK : Color.GRAY);
        statusLabel.setPreferredSize(new Dimension(50, 20));

        // Delete button
        JButton deleteBotJobButton = new JButton("X");
        deleteBotJobButton.setPreferredSize(new Dimension(20, 20));
        deleteBotJobButton.addActionListener(e -> handleDelete(value, list));

        // Add components with spacing
        add(botJobName);
        add(Box.createHorizontalStrut(ARConstants.SPACE_SM));
        add(botJobDescription);
        add(Box.createHorizontalStrut(ARConstants.SPACE_SM));
        add(homeBankingName);
        add(Box.createHorizontalStrut(ARConstants.SPACE_SM));
        add(statusLabel);
        add(Box.createHorizontalStrut(ARConstants.SPACE_SM));
        add(deleteBotJobButton);

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

        // ⚠️ Double-click handling is intentionally NOT here anymore.
        // Add a MouseListener on the JList itself where you create it.

        return this;
    }

    private void handleDelete(BotJobLoadDTO item, JList<? extends BotJobLoadDTO> list) {
        int response = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete the bot job selected?\nBot Job: " + item.getName(),
                "Bot Job Deletion",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == JOptionPane.OK_OPTION) {
            deleteBotJob(item);
            DefaultListModel model = (DefaultListModel) list.getModel();
            model.removeElement(item);
        }
    }

    private void deleteBotJob(BotJobLoadDTO botJob) {
        ErrorMessage errorMessage = performDataBase.deleteBotJobData(botJob.getId());
        if (errorMessage == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Bot Job \"" + botJob.getName() + "\" deleted successfully!",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
    }
}
