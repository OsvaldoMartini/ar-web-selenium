package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARSaveClonePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.model.BotJobLoadDTO;
import java.awt.Frame;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARSaveCloneScene extends ARScene {

    private static final ARSaveClonePane arSaveClonePane;
    private static final int SCENE_HEIGHT = 450;
    private static final int SCENE_WIDTH = 800;
    private static final String TITLE = "Clone Job";
    protected static volatile ARSaveCloneScene instance;

    static {
        arSaveClonePane = ARSaveClonePane.getInstance();
    }

    private JDialog modalDialog;
    private boolean isEnabledLicence;
    private BotJobLoadDTO selecBotJobDTO;
    private DefaultListModel<BotJobLoadDTO> botJobListModel;

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

    /**
     * Swing version: receives DefaultListModel instead of List<BotJobLoadDTO>
     */
    public void initialize(
            BotJobLoadDTO selecBotJobDTO, DefaultListModel<BotJobLoadDTO> botJobListModel, boolean isEnabledLicence) {

        this.isEnabledLicence = isEnabledLicence;
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobListModel = botJobListModel;
    }

    @Override
    public IARPane buildPane() {
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
     * Swing modal dialog instead of JavaFX Stage.
     */
    public void showModal(Frame parent) {

        // Initialize pane with current context (note: ARSaveClonePane must be updated accordingly)
        arSaveClonePane.initialize(selecBotJobDTO, botJobListModel, isEnabledLicence);

        if (modalDialog == null) {
            modalDialog = new JDialog(parent, getTitle(), true);
            modalDialog.setSize(getSceneWidth(), getSceneHeight());
            modalDialog.setAlwaysOnTop(true);
            modalDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            IARPane pane = buildPane();
            if (pane != null) {
                modalDialog.setContentPane(pane.createPane());
            } else {
                log.error("Failed to build pane for modal.");
                return;
            }
        }

        modalDialog.setTitle(getTitle());

        SwingUtilities.invokeLater(() -> modalDialog.setVisible(true));
    }
}
