package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARLicensePane;
import com.allinweb.ch.component.pane.base.IARPane;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARLicenseScene {

    private static final ARLicensePane arLicensePane;
    private static final int SCENE_HEIGHT = 550;
    private static final int SCENE_WIDTH = 800;
    private static final String TITLE = "Activation Software Required";
    protected static volatile ARLicenseScene instance;

    static {
        arLicensePane = ARLicensePane.getInstance();
    }

    private JDialog modalDialog;

    // Private constructor to prevent instantiation
    private ARLicenseScene() {
        arLicensePane.initialize();
    }

    public static ARLicenseScene getInstance() {
        if (instance == null) {
            synchronized (ARLicenseScene.class) {
                if (instance == null) {
                    instance = new ARLicenseScene();
                }
            }
        }
        return instance;
    }

    public IARPane buildPane() {
        return arLicensePane;
    }

    public int getSceneHeight() {
        return SCENE_HEIGHT;
    }

    public int getSceneWidth() {
        return SCENE_WIDTH;
    }

    public String getTitle() {
        return TITLE;
    }

    private void cleanupAndClose() {
        log.info("Cleanup and Close: Exiting Threads from License Modal");
        // TODO: if you had a thread list in ARScene, move that cleanup here
        if (modalDialog != null) {
            modalDialog.dispose();
        }
    }

    public void showModal() {
        // Make sure UI work happens on the Swing EDT
        SwingUtilities.invokeLater(() -> {
            arLicensePane.initialize();

            if (modalDialog == null) {
                // Try to find a reasonable owner window
                Window owner = null;
                for (Frame f : Frame.getFrames()) {
                    if (f.isVisible()) {
                        owner = f;
                        break;
                    }
                }

                modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
                modalDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                modalDialog.setSize(getSceneWidth(), getSceneHeight());
                modalDialog.setLocationRelativeTo(owner);

                IARPane pane = buildPane();
                if (pane != null) {
                    // IMPORTANT:
                    // make sure IARPane#createPane() now returns a Swing JComponent
                    JComponent content = (JComponent) pane.createPane();
                    modalDialog.setContentPane(content);
                } else {
                    log.error("Failed to build pane for modal.");
                    return;
                }

                modalDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        log.info("Handle Close (Modal Dialog): Exiting Threads from Modal");
                        cleanupAndClose();
                    }
                });
            }

            modalDialog.setTitle(getTitle());
            if (!modalDialog.isVisible()) {
                modalDialog.setVisible(true);
            }
        });
    }
}
