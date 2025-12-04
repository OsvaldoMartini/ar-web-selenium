package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARMainPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARPropertyManager;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.websocket.server.ServerContainer;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARMainScene extends ARScene {

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 700D;
    private static final String TITLE = "AR Web Bot Job List";

    private static final ARMainPane arMainPane;
    private static final ARPropertyManager arPropertyManager;
    protected static volatile ARMainScene instance;
    private static Server jettyServer;
    private static ServerContainer wsContainer;

    static {
        arMainPane = ARMainPane.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
        // WebSocket stuff left commented out, as before
        // webSocketSessionManager = WebSocketSessionManager.getInstance();
    }

    private boolean isEnabledLicence;
    private JDialog modalDialog;
    private final List<WebDriver> webDriverList = new ArrayList<>();

    private ARMainScene() {
        super();
    }

    public static ARMainScene getInstance() {
        if (instance == null) {
            synchronized (ARMainScene.class) {
                if (instance == null) {
                    instance = new ARMainScene();
                }
            }
        }
        return instance;
    }

    public void initialize(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }

    @Override
    public IARPane buildPane() {
        // initiateJetty();
        arMainPane.initialize(webDriverList, isEnabledLicence);
        return arMainPane;
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
     * Swing-specific frame behaviour: handle close (X button) for main window.
     */
    @Override
    public void setFrameBehaviour(javax.swing.JFrame frame) {
        super.setFrameBehaviour(frame);

        if (!isCloseHandlerSet) {
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    handleCloseRequest();
                }
            });
            isCloseHandlerSet = true;
        }
    }

    /**
     * Show this scene as a modal dialog (similar to old showModal with JavaFX Stage).
     */
    public void showModal() {
        SwingUtilities.invokeLater(() -> {
            arMainPane.initialize(webDriverList, isEnabledLicence);

            if (modalDialog == null) {
                // Try to find a suitable owner
                Window owner = null;
                for (Frame f : Frame.getFrames()) {
                    if (f.isVisible()) {
                        owner = f;
                        break;
                    }
                }

                modalDialog = new JDialog(owner, getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
                modalDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
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
                modalDialog.setVisible(true); // modal, blocks until closed
            }
        });
    }

    private void cleanupAndClose() {
        log.info("Cleanup and Close: Exiting Threads");
        // Interrupt running threads
        threadList.forEach(this::interruptThread);
        // Close WebDrivers
        closeWebDrivers();
        if (modalDialog != null) {
            modalDialog.dispose();
        }
    }

    private void handleCloseRequest() {
        log.info("Handle Close: Exiting Threads and Quitting WebDriver");
        threadList.forEach(this::interruptThread);
        closeWebDrivers();
    }

    // Method to close all WebDriver instances
    private void closeWebDrivers() {
        for (WebDriver driver : webDriverList) {
            try {
                driver.quit();
                log.info("WebDriver closed.");
            } catch (Exception e) {
                log.warn("Error closing WebDriver: " + e.getMessage());
            }
        }
        webDriverList.clear();
        System.exit(0);
    }

    // WebSocket server control remains commented as before:
    // public void stopWebSocketServer() { ... }
}
