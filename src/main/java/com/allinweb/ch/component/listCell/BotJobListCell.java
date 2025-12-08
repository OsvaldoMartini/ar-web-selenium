package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ErrorMessage;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class BotJobListCell extends JPanel implements ListCellRenderer<BotJobLoadDTO> {

    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();

    private final ARViewBotJobScene arViewBotJobScene;
    private final ARWebDriver arWebDriver;
    private final boolean isEnabledLicence;

    // index of row where mouse is hovering over delete column (-1 = none)
    private int hoverDeleteIndex = -1;

    public BotJobListCell(ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, boolean isEnabledLicence) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.isEnabledLicence = isEnabledLicence;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(new EmptyBorder(5, 5, 5, 5));
        setOpaque(true); // needed so background color (striping) is visible
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

        JLabel botJobName        = buildColumnLabel(value.getName(), ColumnWidths.NAME);
        JLabel botJobDescription = buildColumnLabel(value.getDescription(), ColumnWidths.DESCRIPTION);
        JLabel homeBankingName   =
                buildColumnLabel(value.getHomeBankingLoadDTO().getName(), ColumnWidths.ORGANIZATION);
        JLabel statusLabel       =
                buildColumnLabel(value.isActive() ? "Active" : "Inactive", ColumnWidths.STATUS);
        JButton deleteButton     = buildColumnButton("X", ColumnWidths.ACTION);

        // inactive jobs in gray
        if (!value.isActive()) {
            statusLabel.setForeground(Color.GRAY);
        }

        boolean deleteHover = (index == hoverDeleteIndex);
        styleDeleteButton(deleteButton, isSelected, deleteHover);

        // layout with gaps and delete button on the right
        add(botJobName);
        add(Box.createHorizontalStrut(ColumnWidths.GAP));

        add(botJobDescription);
        add(Box.createHorizontalStrut(ColumnWidths.GAP));

        add(homeBankingName);
        add(Box.createHorizontalStrut(ColumnWidths.GAP));

        add(statusLabel);

        // glue pushes delete button to the right edge
        add(Box.createHorizontalGlue());
        add(deleteButton);

        // --- selection + striping ---
        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            // simple zebra striping (tweak to your brownish color if you want)
            Color bg = (index % 2 == 0)
                    ? new Color(245, 236, 220)  // light brown / beige
                    : Color.WHITE;
            setBackground(bg);
            setForeground(list.getForeground());
        }

        // make children inherit row background if they are opaque
        for (Component c : getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setOpaque(false); // keep labels/buttons transparent
            }
        }

        return this;
    }

    // ---------- Public API for ARMainPane ----------

    /** Called from ARMainPane to update which row is hovered over the delete column. */
    public void setHoverDeleteIndex(int index) {
        this.hoverDeleteIndex = index;
    }

    /** Called from ARMainPane when user clicked in delete column. */
    public void performDelete(BotJobLoadDTO item, JList<? extends BotJobLoadDTO> list) {
        handleDelete(item, list);
    }

    // ---------- Delete handling ----------

    private void handleDelete(BotJobLoadDTO item, JList<? extends BotJobLoadDTO> list) {
        int response = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete the bot job selected?\nBot Job: " + item.getName(),
                "Bot Job Deletion",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == JOptionPane.OK_OPTION) {
            deleteBotJob(item);

            @SuppressWarnings("unchecked")
            DefaultListModel<BotJobLoadDTO> model =
                    (DefaultListModel<BotJobLoadDTO>) list.getModel();

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

    // ---------- Column helpers ----------

    private JLabel buildColumnLabel(String text, int width) {
        JLabel label = new JLabel();
        int height = ColumnWidths.ROW_HEIGHT;

        Dimension d = new Dimension(width, height);
        label.setPreferredSize(d);
        label.setMinimumSize(d);
        label.setMaximumSize(d);

        if (text == null) {
            text = "";
        }

        // ellipsis + tooltip on overflow
        String displayed = ellipsize(text, width - 6, label); // small padding
        label.setText(displayed);

        if (!displayed.equals(text)) {
            label.setToolTipText(text);
        } else {
            label.setToolTipText(null);
        }

        return label;
    }

    private JButton buildColumnButton(String text, int width) {
        JButton button = new JButton(text);
        int height = ColumnWidths.ROW_HEIGHT;

        Dimension d = new Dimension(width, height);
        button.setPreferredSize(d);
        button.setMinimumSize(d);
        button.setMaximumSize(d);

        button.setToolTipText("Delete this Bot Job");
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusPainted(false);

        // IMPORTANT: no ActionListener here – renderer is non-interactive
        return button;
    }

    private void styleDeleteButton(JButton button, boolean isSelected, boolean hover) {
        if (hover || isSelected) {
            button.setBorderPainted(true);
            button.setContentAreaFilled(true);
        } else {
            button.setBorderPainted(true);
            button.setContentAreaFilled(false);
        }
    }

    // ---------- Ellipsis helper ----------

    private String ellipsize(String text, int maxWidth, JComponent comp) {
        if (text == null) return "";
        FontMetrics fm = comp.getFontMetrics(comp.getFont());
        int fullWidth = fm.stringWidth(text);
        if (fullWidth <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        int available = maxWidth - ellipsisWidth;
        if (available <= 0) {
            return ellipsis;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (fm.stringWidth(sb.toString() + c) > available) {
                break;
            }
            sb.append(c);
        }
        return sb.toString() + ellipsis;
    }

    // ---------- Column widths / layout constants ----------

    public static final class ColumnWidths {

        public static final int NAME         = 150;
        public static final int DESCRIPTION  = 150;
        public static final int ORGANIZATION = 100;
        public static final int STATUS       = 50;
        public static final int ACTION       = 50;

        public static final int ROW_HEIGHT   = 20;
        public static final int GAP          = 10; // horizontal gap between columns

        private ColumnWidths() {
            // utility class – no instances
        }
    }
}
