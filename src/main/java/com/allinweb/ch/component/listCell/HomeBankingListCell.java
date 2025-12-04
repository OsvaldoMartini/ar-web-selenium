package com.allinweb.ch.component.listCell;

import com.allinweb.ch.model.HomeBankingLoadDTO;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class HomeBankingListCell extends JPanel implements ListCellRenderer<HomeBankingLoadDTO> {

    public HomeBankingListCell() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(new EmptyBorder(5, 5, 5, 5));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends HomeBankingLoadDTO> list,
            HomeBankingLoadDTO item,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        removeAll(); // clear previous components

        if (item != null && item.getUrl() != null) {
            JLabel nameLabel = new JLabel(item.getName());
            nameLabel.setPreferredSize(new Dimension(100, 20));
            nameLabel.setMinimumSize(new Dimension(100, 20));
            nameLabel.setMaximumSize(new Dimension(100, 20));

            JLabel urlLabel = new JLabel(item.getUrl());
            urlLabel.setPreferredSize(new Dimension(200, 20)); // initial size
            urlLabel.setMinimumSize(new Dimension(50, 20));
            urlLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

            // Add spacing between name and URL
            add(nameLabel);
            add(Box.createHorizontalStrut(10));
            add(urlLabel);
        }

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

        return this;
    }
}
