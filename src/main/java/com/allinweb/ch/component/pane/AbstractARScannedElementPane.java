package com.allinweb.ch.component.pane;

import javax.swing.*;

public abstract class AbstractARScannedElementPane {

    protected JFrame frame;

    /**
     * Abstract method to start the Swing UI.
     * Subclasses must implement how the frame/panel is initialized and shown.
     */
    public abstract void start() throws Exception;

    /**
     * Abstract method to stop/cleanup resources.
     * Subclasses must implement how to properly close/dispose the frame or other resources.
     */
    public abstract void stop() throws Exception;

    /**
     * Helper method to initialize a basic JFrame.
     */
    protected void initFrame(String title, int width, int height) {
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null); // center on screen
    }

    /**
     * Show the frame
     */
    protected void showFrame() {
        if (frame != null) {
            SwingUtilities.invokeLater(() -> frame.setVisible(true));
        }
    }

    /**
     * Close the frame
     */
    protected void closeFrame() {
        if (frame != null) {
            SwingUtilities.invokeLater(() -> frame.dispose());
        }
    }
}
