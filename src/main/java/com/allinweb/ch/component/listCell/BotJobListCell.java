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

        removeAll();

        if (value == null) return this;

        JLabel botJobName        = buildColumnLabel(value.getName(), 150);
        JLabel botJobDescription = buildColumnLabel(value.getDescription(), 150);
        JLabel homeBankingName   = buildColumnLabel(value.getHomeBankingLoadDTO().getName(), 100);
        JLabel statusLabel       = buildColumnLabel(value.isActive() ? "Active" : "Inactive", 50);
        JButton deleteButton     = buildColumnButton("X", 50);

        deleteButton.addActionListener(e -> handleDelete(value, list));

        // Add components WITH gaps
        add(botJobName);
        add(Box.createHorizontalStrut(20));

        add(botJobDescription);
        add(Box.createHorizontalStrut(20));

        add(homeBankingName);
        add(Box.createHorizontalStrut(20));

        add(statusLabel);

        // Push delete button to the RIGHT
        add(Box.createHorizontalGlue());

        add(deleteButton);

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

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

    private JLabel buildColumnLabel(String text, int width) {
        JLabel label = new JLabel(text);
        int height = 20;                     // fixed row height

        Dimension d = new Dimension(width, height);
        label.setPreferredSize(d);
        label.setMinimumSize(d);
        label.setMaximumSize(d);

        return label;
    }

    private JButton buildColumnButton(String text, int width) {
        JButton button = new JButton(text);
        int height = 20;

        Dimension d = new Dimension(width, height);
        button.setPreferredSize(d);
        button.setMinimumSize(d);
        button.setMaximumSize(d);

        return button;
    }

}
