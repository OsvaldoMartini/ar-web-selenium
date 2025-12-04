package com.allinweb.ch.control;

import com.allinweb.ch.util.ARConstants;
import java.awt.*;
import java.net.URL;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARComponentBuilder {

    // Static final variable to hold the singleton instance
    protected static volatile ARComponentBuilder instance;

    // Private constructor to prevent instantiation
    private ARComponentBuilder() {}

    // Public method to access the singleton instance
    public static ARComponentBuilder getInstance() {
        if (instance == null) {
            synchronized (ARComponentBuilder.class) {
                if (instance == null) {
                    instance = new ARComponentBuilder();
                }
            }
        }
        return instance;
    }

    /**
     * Create a "top panel" with a preferred height and outer margin.
     * This replaces the old JavaFX HBox + AnchorPane anchoring.
     */
    public JPanel createTopPanel(int topPanelHeight, int edgeSpace) {
        JPanel topPane = new JPanel();
        topPane.setLayout(new FlowLayout(FlowLayout.LEFT));
        topPane.setBorder(BorderFactory.createEmptyBorder(edgeSpace, edgeSpace, 0, edgeSpace));
        topPane.setPreferredSize(new Dimension(0, topPanelHeight));
        return topPane;
    }

    /**
     * Create a "bottom panel" with a preferred height and outer margin.
     */
    public JPanel createBottomPanel(int bottomPanelHeight, int edgeSpace) {
        JPanel bottomPane = new JPanel();
        bottomPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
        bottomPane.setBorder(BorderFactory.createEmptyBorder(0, edgeSpace, edgeSpace, edgeSpace));
        bottomPane.setPreferredSize(new Dimension(0, bottomPanelHeight));
        return bottomPane;
    }

    /**
     * Create a central content panel with margins.
     * In JavaFX you used AnchorPane constraints; here we just use border / BoxLayout.
     */
    public JPanel createContentPanel(int topPanelHeight, int bottomPanelHeight, int edgeSpace) {
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BorderLayout());
        contentPane.setBorder(BorderFactory.createEmptyBorder(edgeSpace, edgeSpace, edgeSpace, edgeSpace));
        return contentPane;
    }

    /**
     * Anchor helpers: in Swing there is no AnchorPane, so these are no-ops that simply return the same component.
     */
    public <T extends JComponent> T setAnchorPaneAnchors(T component, Double edge) {
        return component;
    }

    public <T extends JComponent> T setAnchorPaneAnchors(T component, Double vertical, Double horizontal) {
        return component;
    }

    public <T extends JComponent> T setAnchorPaneAnchors(
            T component, Double top, Double bottom, Double left, Double right) {
        return component;
    }

    /**
     * Load an image as Swing ImageIcon and scale it.
     */
    public ImageIcon buildImageIcon(String source, Integer size) {
        try {
            URL url = getClass().getResource(source);
            if (url == null) {
                throw new IllegalArgumentException("Resource not found: " + source);
            }
            ImageIcon icon = new ImageIcon(url);
            if (size != null && size > 0) {
                Image scaled = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
            return icon;
        } catch (Exception e) {
            log.error(String.format("buildImageIcon source: %s size %s \n%s", source, size, e.getMessage()));
            return null;
        }
    }

    /* ------------------------------------------------------------------
     * Button builders (Swing JButtons)
     * ------------------------------------------------------------------ */

    public JButton buildButton(String text) {
        return buildButton(text, ARConstants.SPACE_L);
    }

    public JButton buildButton(String text, int height) {
        return buildButton(
                text,
                height,
                new Insets(
                        ARConstants.SPACE_XS, // top
                        ARConstants.SPACE_XS, // left
                        ARConstants.SPACE_XS, // bottom
                        ARConstants.SPACE_XS // right
                        ));
    }

    public JButton buildButton(String text, Integer height, Insets padding) {
        JButton button = new JButton(text);

        if (height > 0) { // assuming 0 means "use default height"
            Dimension d = button.getPreferredSize();
            d.height = height;
            button.setPreferredSize(d);
        }

        if (padding != null) {
            button.setMargin(padding);
        }

        return button;
    }

    public JButton buildButton(String text, Integer height, String iconSource, Integer iconSize, Insets padding) {
        return buildButton(text, height, iconSource, iconSize, padding, null);
    }

    public JButton buildButton(
            String text, Integer height, String iconSource, Integer iconSize, Insets padding, Color background) {
        JButton button = buildButton(text, height, padding);
        ImageIcon icon = buildImageIcon(iconSource, iconSize);
        if (icon != null) {
            button.setIcon(icon);
            button.setHorizontalTextPosition(SwingConstants.RIGHT);
            button.setVerticalTextPosition(SwingConstants.CENTER);
        }
        if (background != null) {
            button.setOpaque(true);
            button.setBackground(background);
        }
        return button;
    }

    public JButton buildButton(
            String text,
            Integer height,
            String iconSource,
            Integer iconSize,
            Insets padding,
            Color background,
            double maxTextWidth) {

        ImageIcon icon = buildImageIcon(iconSource, iconSize);
        JLabel label = new JLabel(text, icon, SwingConstants.CENTER);
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        if (maxTextWidth > 0) {
            label.setPreferredSize(new Dimension((int) maxTextWidth, label.getPreferredSize().height));
        }

        JPanel graphicPanel = new JPanel();
        graphicPanel.setOpaque(false);
        graphicPanel.setLayout(new BoxLayout(graphicPanel, BoxLayout.Y_AXIS));
        graphicPanel.add(label);

        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.add(graphicPanel, BorderLayout.CENTER);

        if (height != null) {
            Dimension d = button.getPreferredSize();
            d.height = height;
            button.setPreferredSize(d);
        }
        if (padding != null) {
            button.setMargin(padding);
        }
        if (background != null) {
            button.setOpaque(true);
            button.setBackground(background);
        }
        // No text outside graphic; we only use the icon+label inside the panel
        return button;
    }
}
