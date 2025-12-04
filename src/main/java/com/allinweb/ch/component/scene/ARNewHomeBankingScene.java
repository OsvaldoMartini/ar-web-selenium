package com.allinweb.ch.component.scene;

// import com.allinweb.ch.component.pane.ARNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARNewHomeBankingScene extends ARScene {

    private static final PerformLists performLists = PerformLists.getInstance();
    //    private static final ARNewHomeBankingPane arNewHomeBankingPane = ARNewHomeBankingPane.getInstance();
    private static final int SCENE_HEIGHT = 750;
    private static final int SCENE_WIDTH = 1200;
    private static final String TITLE = "New Organization";

    protected static volatile ARNewHomeBankingScene instance;

    private static HomeBankingLoadDTO homeBank;

    private JDialog modalDialog;

    // Private constructor to prevent instantiation
    private ARNewHomeBankingScene() {
        super();
    }

    public static ARNewHomeBankingScene getInstance() {
        if (instance == null) {
            synchronized (ARNewHomeBankingScene.class) {
                if (instance == null) {
                    instance = new ARNewHomeBankingScene();
                }
            }
        }
        return instance;
    }

    public void initialize(HomeBankingLoadDTO homeBank) {
        ARNewHomeBankingScene.homeBank = homeBank;

        //        if (!isNullOrEmpty(performLists.getListHomeBanking())) {
        //            arNewHomeBankingPane.updateTableBankingView();
        //        }
    }

    private boolean isNullOrEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * Swing modal dialog equivalent of the old JavaFX showModal(Stage parentStage).
     *
     * @param parentWindow parent window (JFrame/JDialog) that owns this modal dialog
     */
    public void showModal(Window parentWindow) {

        //        arNewHomeBankingPane.initialize(homeBank);

        if (modalDialog == null) {
            // Fallback owner if null
            Window owner = parentWindow != null ? parentWindow : JOptionPane.getRootFrame();

            modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
            modalDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            IARPane pane = buildPane();
            if (pane != null) {
                JComponent content = pane.createPane();
                modalDialog.setContentPane(content);

                // Size equivalent to scene width/height
                modalDialog.setSize(getSceneWidth(), getSceneHeight());
                modalDialog.setLocationRelativeTo(owner);

                // Set icon if ARScene provides one (icon is usually a java.awt.Image in the Swing version)
                if (icon != null) {
                    modalDialog.setIconImage(icon);
                }

                // Mimic "always on top briefly" behavior
                modalDialog.setAlwaysOnTop(true);
                // Reset alwaysOnTop after shown
                SwingUtilities.invokeLater(() -> modalDialog.setAlwaysOnTop(false));
            } else {
                log.error("Failed to build pane for modal.");
                return;
            }
        }

        modalDialog.setTitle(getTitle()); // Update title if needed

        if (!modalDialog.isVisible()) {
            modalDialog.setVisible(true); // blocks until dialog is closed (modal)
        } else {
            modalDialog.toFront();
            modalDialog.requestFocus();
        }
    }

    @Override
    public IARPane buildPane() {
        return arNewHomeBankingPane;
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

    public void closeModal() {
        try {
            if (modalDialog != null) {
                modalDialog.dispose();
            }
            modalDialog = null;
        } catch (Exception error) {
            log.error("Error closing NewHomeBanking dialog. Error: " + error.getMessage());
        }
    }
}
