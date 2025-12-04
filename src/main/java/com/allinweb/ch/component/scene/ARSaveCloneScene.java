package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARSaveClonePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.model.BotJobLoadDTO;
import java.awt.Dialog;
import java.awt.Window;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARSaveCloneScene extends ARScene {

    private static final ARSaveClonePane arSaveClonePane;
    private static final Double SCENE_HEIGHT = 450D;
    private static final Double SCENE_WIDTH = 800D;
    private static final String TITLE = "Clone Job";
    protected static volatile ARSaveCloneScene instance;

    static {
        arSaveClonePane = ARSaveClonePane.getInstance();
    }

    private JDialog modalDialog;
    private boolean isEnabledLicence;
    private BotJobLoadDTO selecBotJobDTO;
    private List<BotJobLoadDTO> botJobList;

    // Private constructor to prevent instantiation
    private ARSaveCloneScene() {
        super();
    }

    public static ARSaveCloneScene getInstance() {
        if (instance == null) {
            synchronized (ARSaveCloneScene.class) {
                if (instance == null) {
                    instance = new ARSaveCloneScene();
                }
            }
        }
        return instance;
    }

    public void initialize(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobList = botJobList;
    }

    @Override
    public IARPane buildPane() {
        // arSaveClonePane.initialize(selecBotJobDTO, botJobList, isEnabledLicence);
        return arSaveClonePane;
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
     * Swing replacement for the old JavaFX showModal(Stage primaryStage).
     * Pass the owning Swing window as "owner".
     */
    public void showModal(Window owner) {

        arSaveClonePane.initialize(selecBotJobDTO, botJobList, isEnabledLicence);

        SwingUtilities.invokeLater(() -> {
            if (modalDialog == null) {
                modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
                modalDialog.setSize(getSceneWidth().intValue(), getSceneHeight().intValue());
                modalDialog.setLocationRelativeTo(owner);

                if (icon != null) {
                    modalDialog.setIconImage(icon);
                }

                IARPane pane = buildPane();
                if (pane != null) {
                    JComponent content = (JComponent) pane.createPane();
                    modalDialog.setContentPane(content);
                } else {
                    // Handle the case where pane creation failed
                    log.error("Failed to build pane for modal.");
                    return;
                }
            }

            modalDialog.setTitle(getTitle()); // Update title if it might have changed

            if (!modalDialog.isVisible()) {
                modalDialog.setVisible(true); // blocks until closed
            }
        });
    }
}
