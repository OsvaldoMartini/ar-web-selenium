package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARConfigurationPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.model.BotJobLoadDTO;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARConfigurationScene extends ARScene {

    private static final int SCENE_HEIGHT = 700;
    private static final int SCENE_WIDTH = 800;
    private static final String TITLE = "Configuration";
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    protected static volatile ARConfigurationScene instance;
    private static ARConfigurationPane arConfigurationPane;

    static {
        arConfigurationPane = ARConfigurationPane.getInstance();
    }

    private boolean isEnabledLicence;
    private JDialog modalDialog;
    private JList<BotJobLoadDTO> viewBotJobListView = new JList<>();

    private ARConfigurationScene() {
        super();
    }

    public static ARConfigurationScene getInstance() {
        if (instance == null) {
            synchronized (ARConfigurationScene.class) {
                if (instance == null) {
                    instance = new ARConfigurationScene();
                }
            }
        }
        return instance;
    }

    @Override
    public IARPane buildPane() {
        // arConfigurationPane.initialize(modalDialog, viewBotJobListView, isEnabledLicence);
        return arConfigurationPane;
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

    private void cleanupAndClose() {
        log.info("Cleanup and Close: Exiting Threads");
        threadList.forEach(this::interruptThread);
        if (modalDialog != null) {
            modalDialog.dispose();
        }
    }

    public void showModal() {
        SwingUtilities.invokeLater(() -> {
            // Initialize pane state with current dialog & list (adapt ARConfigurationPane to Swing types)
            arConfigurationPane.initialize(modalDialog, viewBotJobListView, isEnabledLicence);

            if (modalDialog == null) {
                // Try to find reasonable owner (e.g., main frame)
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
                modalDialog.setVisible(true); // blocks until closed because it's modal
            }
        });
    }

    public void initialize(JList<BotJobLoadDTO> viewBotJobListView, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.viewBotJobListView = viewBotJobListView;
    }

    public void initializeLicense(boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
    }
}
