package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ARConstants;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARViewBotJobListPane extends ARPane {

    private static final PerformLists performLists;
    private static final PerformDataBase performDataBase;
    private static final PerformActions performActions;
    private static final PerformMessage performMessage;
    protected static volatile ARViewBotJobListPane instance;

    static {
        performLists = PerformLists.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performActions = PerformActions.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    // Swing UI components
    private JPanel mainPanel;
    private JPanel headerPanel;
    private JList<BotJobLoadDTO> uiBotJobList;

    // External dependencies
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private List<WebDriver> webDriverList;

    // Private constructor to prevent instantiation
    private ARViewBotJobListPane() {
        super();
    }

    public static ARViewBotJobListPane getInstance() {
        if (instance == null) {
            synchronized (ARViewBotJobListPane.class) {
                if (instance == null) {
                    instance = new ARViewBotJobListPane();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize dependencies (replaces JavaFX-style DI).
     */
    public void initialize(
            ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, List<WebDriver> webDriverList) {

        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.webDriverList = webDriverList;
    }

    @Override
    public JPanel getPaneReference() {
        return mainPanel;
    }

    @Override
    public void initUIComponents() {

        // Root panel
        mainPanel = new JPanel(new BorderLayout());
        int spaceM = (int) ARConstants.SPACE_M;

        // -----------------------------
        // Header (equivalent to JavaFX GridPane with 4 columns)
        // -----------------------------
        headerPanel = new JPanel(new GridLayout(1, 4));
        headerPanel.add(new JLabel("Name"));
        headerPanel.add(new JLabel("Description"));
        headerPanel.add(new JLabel("Environment"));
        headerPanel.add(new JLabel("Actions"));

        headerPanel.setBorder(new EmptyBorder(spaceM, spaceM, spaceM / 2, spaceM));
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // -----------------------------
        // Bot Job list (Swing JList with custom renderer)
        // -----------------------------

        // Load data
        performDataBase.loadQuickBotJobs();
        java.util.List<BotJobLoadDTO> botJobList = performLists.getQuickBotJobs();

        // Create Swing list model
        DefaultListModel<BotJobLoadDTO> listModel = new DefaultListModel<>();
        for (BotJobLoadDTO dto : botJobList) {
            listModel.addElement(dto);
        }

        // Create JList
        uiBotJobList = new JList<>(listModel);

        // Optional: some basic list tuning
        uiBotJobList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        uiBotJobList.setVisibleRowCount(-1);

        // Use ARCellFactory to create the renderer instance via reflection
        @SuppressWarnings("unchecked")
        ARCellFactory<Object, ListCellRenderer<BotJobLoadDTO>> cellFactory = new ARCellFactory<>(
                (Class<ListCellRenderer<BotJobLoadDTO>>) (Class<?>) BotJobListCell.class,
                arViewBotJobScene,
                arWebDriver,
                performDataBase,
                performActions,
                performMessage,
                listModel,
                webDriverList);

        // Apply renderer to the list
        uiBotJobList.setCellRenderer(cellFactory.create(null));

        // Put list into a scroll pane with padding similar to AnchorPane constraints
        JScrollPane scrollPane = new JScrollPane(uiBotJobList);
        scrollPane.setBorder(new EmptyBorder(spaceM * 2, spaceM, spaceM, spaceM));

        mainPanel.add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public void initUIBehaviour() {
        // Add Swing listeners here if needed later
    }
}
