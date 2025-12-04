package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARExcelFilePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.model.SplitDTO;
import java.awt.Dialog;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARExcelFileScene extends ARScene {

    private static final int SCENE_HEIGHT = 300;
    private static final int SCENE_WIDTH = 800;
    private static final String TITLE = "Create or Delete the Export Excel File";

    protected static volatile ARExcelFileScene instance;

    private static final ARExcelFilePane arExcelFilePane = ARExcelFilePane.getInstance();

    private JDialog modalDialog;

    private SplitDTO splitDTO;
    private String sessionId;

    // Private constructor to prevent instantiation
    private ARExcelFileScene() {
        super();
    }

    public static ARExcelFileScene getInstance() {
        if (instance == null) {
            synchronized (ARExcelFileScene.class) {
                if (instance == null) {
                    instance = new ARExcelFileScene();
                }
            }
        }
        return instance;
    }

    public void initialize(String sessionId, SplitDTO splitDTO) {
        this.sessionId = sessionId;
        this.splitDTO = splitDTO;
    }

    @Override
    public IARPane buildPane() {
        return arExcelFilePane;
    }

    @Override
    public int getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public int getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    /**
     * Swing modal dialog version of the old JavaFX showModal().
     */
    public void showModal(JFrame parentFrame) {

        arExcelFilePane.initialize(sessionId, splitDTO);

        if (modalDialog == null) {

            Window owner = parentFrame != null ? parentFrame : SwingUtilities.getWindowAncestor(parentFrame);

            modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);

            if (icon != null) {
                modalDialog.setIconImage(icon);
            }

            IARPane pane = buildPane();
            if (pane != null) {
                JComponent content = pane.createPane();
                modalDialog.setContentPane(content);
                modalDialog.setSize(getSceneWidth(), getSceneHeight());
                modalDialog.setLocationRelativeTo(parentFrame);
            } else {
                log.error("Failed to build pane for modal.");
                return;
            }
        }

        modalDialog.setTitle(getTitle());

        if (!modalDialog.isVisible()) {
            modalDialog.setLocationRelativeTo(parentFrame);
            modalDialog.setVisible(true);
        } else {
            modalDialog.toFront();
        }
    }

    public void closeModal() {
        try {
            if (modalDialog != null) {
                modalDialog.dispose();
            }
            modalDialog = null;
        } catch (Exception e) {
            log.error("Error closing Excel modal: {}", e.getMessage());
        }
    }
}
