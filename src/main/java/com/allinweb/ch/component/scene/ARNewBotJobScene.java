package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARNewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import java.awt.Dialog;
import java.awt.Window;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARNewBotJobScene extends ARScene {

    private static final int SCENE_HEIGHT = 430;
    private static final int SCENE_WIDTH = 450;
    private static final String TITLE = "New Bot Job";

    protected static volatile ARNewBotJobScene instance;
    private static final ARNewBotJobPane arNewBotJobPane;

    static {
        arNewBotJobPane = ARNewBotJobPane.getInstance();
    }

    private JDialog modalDialog;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private List<WebDriver> webDriverList; // kept for compatibility, even if not used here
    private boolean isEnabledLicence;

    // Private constructor to prevent instantiation
    private ARNewBotJobScene() {
        super();
    }

    public static ARNewBotJobScene getInstance() {
        if (instance == null) {
            synchronized (ARNewBotJobScene.class) {
                if (instance == null) {
                    instance = new ARNewBotJobScene();
                }
            }
        }
        return instance;
    }

    public void initialize(
            ARViewBotJobScene arViewBotJobScene,
            ARWebDriver arWebDriver,
            List<WebDriver> webDriverList,
            boolean isEnabledLicence) {

        this.isEnabledLicence = isEnabledLicence;
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.webDriverList = webDriverList; // currently not used, kept for future use / compatibility
    }

    @Override
    public IARPane buildPane() {
        return arNewBotJobPane;
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
     * Swing version of showModal.
     *
     * @param parentFrame The parent JFrame for modality/centering (can be null).
     */
    public void showModal(JFrame parentFrame) {

        // Initialize pane with dependencies
        arNewBotJobPane.initialize(arViewBotJobScene, arWebDriver, isEnabledLicence);

        if (modalDialog == null) {
            // Determine owner window (if parentFrame is null, we'll let Swing decide)
            Window owner = parentFrame != null ? parentFrame : SwingUtilities.getWindowAncestor(parentFrame);

            modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);

            // If ARScene exposes an icon (java.awt.Image), use it
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
                // Handle the case where pane creation failed
                log.error("Failed to build pane for modal.");
                return;
            }
        }

        // Update title if it might have changed
        modalDialog.setTitle(getTitle());

        // Show dialog (modal); if already visible, just bring to front
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
        } catch (Exception error) {
            log.error("Dialog closed with error. Error: {}", error.getMessage());
        }
    }
}
