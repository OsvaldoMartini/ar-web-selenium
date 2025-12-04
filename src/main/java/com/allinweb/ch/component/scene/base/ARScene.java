package com.allinweb.ch.component.scene.base;

import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.util.ARConstants;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ARScene implements IARScene, IconLoader {

    protected final List<Thread> threadList = new ArrayList<>();

    @Setter
    protected Image icon;

    protected JFrame frame; // Swing frame instead of JavaFX Stage
    protected boolean isCloseHandlerSet = false;
    private JComponent content; // Swing content instead of JavaFX Scene

    public ARScene() {
        setupFrame();
        loadIcon();
    }

    public void loadIcon() {
        // IconLoader should now load a java.awt.Image and call setIcon(...)
        loadAndSetIcon(ARConstants.ICON_APPLICATION);
        if (frame != null && icon != null) {
            frame.setIconImage(icon);
        }
    }

    public abstract IARPane buildPane();

    public abstract int getSceneHeight();

    public abstract int getSceneWidth();

    /**
     * Hook for subclasses to configure the frame (size, resizability, etc.)
     */
    public void setFrameBehaviour(JFrame frame) {
        // By default, do nothing. Subclasses may override.
    }

    private void setupFrame() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setFrameBehaviour(frame);
    }

    public void createScene() {
        if (content == null) {
            IARPane mainPane = buildPane();
            if (mainPane != null) {
                // IMPORTANT: IARPane#createPane() must now return a Swing JComponent
                content = (JComponent) mainPane.createPane();
            }
        }
    }

    @Override
    public void show() {
        createScene();
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.setTitle(getTitle());
                if (icon != null) {
                    frame.setIconImage(icon);
                }
                if (content != null) {
                    frame.setContentPane(content);
                }
                frame.setSize(getSceneWidth().intValue(), getSceneHeight().intValue());
                frame.setLocationRelativeTo(null);

                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
                frame.toFront();
                frame.setAlwaysOnTop(false);

                if ("AR Web Scanner".equalsIgnoreCase(frame.getTitle())) {
                    handleCloseApp();
                } else {
                    handleCloseThreads();
                }
            }
        });
    }

    private void handleCloseApp() {
        if (frame == null) return;

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleCloseThreads();
                handleWindowClose();
            }
        });
    }

    /**
     * Install a close handler that interrupts all registered threads.
     * Only installed once.
     */
    protected void handleCloseThreads() {
        if (frame == null) return;

        if (!isCloseHandlerSet) {
            log.info("Setting close handler for the first time");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    threadList.forEach(ARScene.this::interruptThread);
                }
            });
            isCloseHandlerSet = true;
        } else {
            log.info("Close handler already set, skipping...");
        }
    }

    protected void interruptThread(Thread thread) {
        if (thread != null && thread.isAlive()) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cleanUpFinishedThreads() {
        threadList.removeIf(thread -> !thread.isAlive());
    }

    public void startNewThread(String threadName, Runnable task) {
        cleanUpFinishedThreads();
        for (Thread existingThread : threadList) {
            if (existingThread.getName().equals(threadName) && existingThread.isAlive()) {
                log.info("Thread with name '{}' is already running.", threadName);
                return;
            }
        }

        Thread thread = new Thread(task);
        thread.setName(threadName);
        threadList.add(thread);
        thread.start();
    }

    private void handleWindowClose() {
        log.info("X button clicked. Window is closing.");
        System.exit(0);
    }
}
