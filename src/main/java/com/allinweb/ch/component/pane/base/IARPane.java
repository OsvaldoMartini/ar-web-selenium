package com.allinweb.ch.component.pane.base;

import javax.swing.JComponent;
import javax.swing.JPanel;

public interface IARPane {

    /**
     * Create and return the Swing panel representing this UI.
     */
    JPanel createPane();

    /**
     * Return a reference to the main panel.
     */
    JPanel getPaneReference();

    /**
     * Initialize UI components (labels, buttons, fields, etc.).
     */
    void initUIComponents();

    /**
     * Initialize event handlers / behavior.
     */
    void initUIBehaviour();

    /**
     * Add components to panel.
     */
    default void addNodesToPane(JPanel panel, JComponent... toAdd) {
        if (panel != null && toAdd != null) {
            for (JComponent c : toAdd) {
                panel.add(c);
            }
        }
    }

    /**
     * Clear panel content.
     */
    default void clearPane(JPanel panel) {
        if (panel != null) {
            panel.removeAll();
            panel.revalidate();
            panel.repaint();
        }
    }

    /**
     * Custom clear logic.
     */
    void clear();

    /**
     * Remove specific components.
     */
    default void removeNodesFromPane(JPanel panel, JComponent... toRemove) {
        if (panel != null && toRemove != null) {
            for (JComponent c : toRemove) {
                panel.remove(c);
            }
            panel.revalidate();
            panel.repaint();
        }
    }

    /**
     * Remove all components.
     */
    default void removeNodesFromPane(JPanel panel) {
        clearPane(panel);
    }
}
