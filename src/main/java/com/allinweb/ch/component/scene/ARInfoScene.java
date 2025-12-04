package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARInfoPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import java.awt.Frame;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.WindowConstants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARInfoScene extends ARScene {

    private static final int DIALOG_HEIGHT = 300;
    private static final int DIALOG_WIDTH = 400;
    private static final String TITLE = "About";

    protected static volatile ARInfoScene instance;
    private static final ARInfoPane arInfoPane;

    static {
        arInfoPane = ARInfoPane.getInstance();
    }

    private JDialog dialog;
    private boolean isEnabledLicence;

    // Private constructor to prevent instantiation
    private ARInfoScene() {
        super();
    }

    public static ARInfoScene getInstance() {
        if (instance == null) {
            synchronized (ARInfoScene.class) {
                if (instance == null) {
                    instance = new ARInfoScene();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize license flag (if needed by ARInfoPane).
     */
    public void initialize(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }

    @Override
    public IARPane buildPane() {
        return arInfoPane;
    }

    @Override
    public int getSceneHeight() {
        return DIALOG_HEIGHT;
    }

    @Override
    public int getSceneWidth() {
        return DIALOG_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    /**
     * Show the info dialog (modal, centered, always on top initially).
     * Equivalent to the old JavaFX showModal().
     */
    public void showModal() {

        // Initialize pane with license flag
        arInfoPane.initialize(isEnabledLicence);

        if (dialog == null) {
            IARPane pane = buildPane();
            if (pane == null) {
                log.error("Failed to build pane for modal.");
                return;
            }

            dialog = new JDialog((Frame) null, getTitle(), true); // modal
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            JComponent root = pane.createPane();
            dialog.setContentPane(root);

            dialog.setSize(getSceneWidth(), getSceneHeight());
            dialog.setResizable(false);

            if (icon != null) {
                // 'icon' comes from ARScene Swing base (java.awt.Image)
                dialog.setIconImage(icon);
            }

            // Center and bring to front
            dialog.setLocationRelativeTo(null);
            dialog.setAlwaysOnTop(true);
        }

        dialog.setTitle(getTitle()); // update title if needed
        dialog.setVisible(true); // blocks until dialog is closed
        // After visible returns, Swing has already handled modality.
    }

    /**
     * Optional helper to programmatically close the dialog.
     */
    public void closeModal() {
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }
}
