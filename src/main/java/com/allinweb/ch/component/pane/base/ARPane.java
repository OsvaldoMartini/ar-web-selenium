package com.allinweb.ch.component.pane.base;

import javax.swing.JComponent;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ARPane implements IARPane {

    /** Root Swing container for this pane */
    protected JPanel pane;

    /**
     * In the old JavaFX version this returned a Scene.
     * In Swing we expose the root JPanel instead.
     */
    public JPanel getScene() {
        return pane;
    }

    /**
     * In JavaFX this created a Scene from the Pane.
     * In Swing we just initialize the UI and set a preferred size,
     * callers put the JPanel into a JFrame/JDialog as they wish.
     */
    public void createScene(double width, double height) {
        pane = getPaneReference();
        if (pane != null) {
            pane.setPreferredSize(new java.awt.Dimension((int) width, (int) height));
        }
    }

    @Override
    public JPanel createPane() {
        initUI();
        return pane;
    }

    @Override
    public abstract JPanel getPaneReference();

    private void initUI() {
        initUIComponents();
        initUIBehaviour();
        pane = getPaneReference();
        if (pane == null) {
            // Ensure pane is non-null to avoid NPEs
            pane = new JPanel();
        }
    }

    @Override
    public final void addNodesToPane(JPanel panel, JComponent... toAdd) {
        if (panel == null || toAdd == null) {
            return;
        }
        for (JComponent comp : toAdd) {
            if (comp != null && comp.getParent() != panel) {
                panel.add(comp);
            }
        }
        panel.revalidate();
        panel.repaint();
    }

    @Override
    public void clearPane(JPanel panel) {
        try {
            if (panel != null) {
                panel.removeAll();
                panel.revalidate();
                panel.repaint();
            }
        } catch (Exception ignore) {
            // swallow
        }
    }

    @Override
    public void clear() {
        if (pane != null) {
            clearPane(pane);
            pane = null;
        }
    }

    @Override
    public final void removeNodesFromPane(JPanel panel, JComponent... toRemove) {
        if (panel == null || toRemove == null) {
            return;
        }
        for (JComponent comp : toRemove) {
            if (comp != null) {
                panel.remove(comp);
            }
        }
        panel.revalidate();
        panel.repaint();
    }

    @Override
    public void removeNodesFromPane(JPanel bottomPane) {
        if (bottomPane != null && bottomPane.getComponentCount() > 0) {
            int last = bottomPane.getComponentCount() - 1;
            bottomPane.remove(last);
            bottomPane.revalidate();
            bottomPane.repaint();
        }
    }
}
