package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARSaveComponentPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.model.BlockDetailsDTO;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARSaveComponentScene extends ARScene {

    private static final int SCENE_HEIGHT = 250;
    private static final int SCENE_WIDTH = 600D;
    protected static volatile ARSaveComponentScene instance;
    private static ARSaveComponentPane arSaveComponentPane = ARSaveComponentPane.getInstance();
    private static String TITLE = "Move Block";

    private JDialog modalDialog;
    private BlockDetailsDTO blockDetailsDTO;

    // Private constructor to prevent instantiation
    private ARSaveComponentScene() {
        super();
    }

    public static ARSaveComponentScene getInstance() {
        if (instance == null) {
            synchronized (ARSaveComponentScene.class) {
                if (instance == null) {
                    instance = new ARSaveComponentScene();
                }
            }
        }
        return instance;
    }

    public void initialize(BlockDetailsDTO blockDetailsDTO) {
        this.blockDetailsDTO = blockDetailsDTO;
        TITLE = "Save Block:  Comp - " + blockDetailsDTO.getBlockName();
    }

    public void showModal() {

        arSaveComponentPane.initialize(blockDetailsDTO);

        SwingUtilities.invokeLater(() -> {
            if (modalDialog == null) {
                // Try to find a visible owner frame
                Window owner = null;
                for (Frame f : Frame.getFrames()) {
                    if (f.isVisible()) {
                        owner = f;
                        break;
                    }
                }

                modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
                modalDialog.setSize(getSceneWidth(), getSceneHeight());
                modalDialog.setLocationRelativeTo(owner);

                if (icon != null) {
                    modalDialog.setIconImage(icon);
                }

                IARPane pane = buildPane();
                if (pane != null) {
                    JComponent content = (JComponent) pane.createPane();
                    modalDialog.setContentPane(content);
                } else {
                    log.error("Failed to build pane for modal.");
                    return;
                }
            }

            modalDialog.setTitle(getTitle());

            if (!modalDialog.isVisible()) {
                modalDialog.setVisible(true); // blocks until closed (modal)
            }
        });
    }

    @Override
    public IARPane buildPane() {
        // arSaveComponentPane.initialize(blockDetailsDTO);
        return arSaveComponentPane;
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
}
