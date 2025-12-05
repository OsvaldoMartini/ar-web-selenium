package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Properties;
import javax.swing.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARMainPane extends ARPane {

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final ARInfoScene arInfoScene;
    private static final ARPropertyManager arPropertyManager;
    private static final PerformLists performLists;
    private static final PerformDBEngine performDBEngine;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final ARConfigurationScene arConfigurationScene;
    private static final ARViewBotJobScene arViewBotJobScene;
    private static final ARSaveCloneScene arSaveCloneScene;
    private static final ARNewBotJobScene arNewBotJobScene;
    private static final ARWebDriver arWebDriver;
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARMainPane instance;

    static {
        arInfoScene = ARInfoScene.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
        arNewBotJobScene = ARNewBotJobScene.getInstance();
        performLists = PerformLists.getInstance();
        performDBEngine = PerformDBEngine.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        arConfigurationScene = ARConfigurationScene.getInstance();
        arViewBotJobScene = ARViewBotJobScene.getInstance();
        arSaveCloneScene = ARSaveCloneScene.getInstance();
        arWebDriver = ARWebDriver.getInstance();
    }

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:";

    // UI components
    JButton newBotJobButton;
    JButton cloneBotJobButton;
    JButton configureButton;
    JButton infoButton;
    JButton editBotJobButton;
    JButton launchBotJobButton;
    JButton exitButton;
    JButton aiButton;
    JTextArea aiTextArea;
    JPanel buttonPane;
    JPanel panelPane;
    JPanel header = new JPanel();
    JList<BotJobLoadDTO> viewBotJobListView = new JList<>();
    DefaultListModel<BotJobLoadDTO> botJobListModel = new DefaultListModel<>();
    private boolean isEnabledLicence;

    @Getter
    private List<WebDriver> webDriverList;

    private Properties properties = new Properties();
    private BotJobLoadDTO selecBotJobDTO;

    private ARMainPane() {
        super();
    }

    public static ARMainPane getInstance() {
        if (instance == null) {
            synchronized (ARMainPane.class) {
                if (instance == null) {
                    instance = new ARMainPane();
                }
            }
        }
        return instance;
    }

    private static int getMajorJavaVersion(String version) {
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        } else {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]);
        }
    }

    public void initialize(List<WebDriver> webDriverList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.webDriverList = webDriverList;

        botJobListModel.clear();
        ErrorMessage errorMessage = performDataBase.loadQuickBotJobs();
        if (!performDataBase.dbFailed && errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
        for (BotJobLoadDTO dto : performLists.getQuickBotJobs()) {
            botJobListModel.addElement(dto);
        }
        viewBotJobListView.setModel(botJobListModel);
    }

    @Override
    public void initUIComponents() {
        int buttonWidth = 100;

        newBotJobButton = builder.buildButton(
                "New", ARConstants.SPACE_S, ARConstants.ICON_NEW, ARConstants.SPACE_S, new Insets(4, 6, 4, 6));
        cloneBotJobButton = builder.buildButton(
                "Clone Job", ARConstants.SPACE_S, ARConstants.ICON_SAVE, ARConstants.SPACE_S, new Insets(4, 6, 4, 6));
        configureButton = builder.buildButton(
                "Config", ARConstants.SPACE_S, ARConstants.ICON_CONFIG, ARConstants.SPACE_S, new Insets(4, 6, 4, 6));
        infoButton = builder.buildButton(
                "Info", ARConstants.SPACE_S, ARConstants.ICON_INFO, ARConstants.SPACE_S, new Insets(4, 6, 4, 6));
        editBotJobButton = builder.buildButton(
                "Open Job", ARConstants.SPACE_S, ARConstants.ICON_EDIT, ARConstants.SPACE_S, new Insets(4, 6, 4, 6));
        launchBotJobButton = builder.buildButton(
                "Launch", ARConstants.SPACE_S, ARConstants.ICON_PLAY, ARConstants.SPACE_S, new Insets(4, 6, 4, 6));
        exitButton = builder.buildButton(
                "Exit", ARConstants.SPACE_S, ARConstants.ICON_CROSS, ARConstants.SPACE_S, new Insets(4, 6, 4, 6));

        aiButton = builder.buildButton(
                "AI", ARConstants.SPACE_L, ARConstants.ICON_AI, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        aiButton.setVisible(false);

        aiTextArea = new JTextArea();
        aiTextArea.setText("AI Tool: Upgrade your version to access this premium feature.");
        aiTextArea.setEditable(false);
        aiTextArea.setLineWrap(true);
        aiTextArea.setVisible(false);

        Dimension dim = new Dimension(buttonWidth, 25);
        for (JButton btn : new JButton[] {
            newBotJobButton,
            cloneBotJobButton,
            configureButton,
            infoButton,
            editBotJobButton,
            launchBotJobButton,
            exitButton,
            aiButton
        }) {
            btn.setPreferredSize(dim);
        }

        buttonPane = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        buttonPane.add(newBotJobButton);
        buttonPane.add(cloneBotJobButton);
        buttonPane.add(configureButton);
        buttonPane.add(infoButton);
        buttonPane.add(launchBotJobButton);
        buttonPane.add(editBotJobButton);
        buttonPane.add(exitButton);

        initHeader();

        viewBotJobListView.setCellRenderer(new BotJobListCell());

        panelPane = new JPanel();
        panelPane.setLayout(new BorderLayout());
        panelPane.add(buttonPane, BorderLayout.NORTH);
        panelPane.add(aiTextArea, BorderLayout.CENTER);
        panelPane.add(header, BorderLayout.SOUTH);
    }

    @Override
    public void initUIBehaviour() {
        aiButton.addActionListener(e -> aiTextArea.setVisible(!aiTextArea.isVisible()));

        exitButton.addActionListener(e -> closeWebDrivers());
    }

    private void closeWebDrivers() {
        for (WebDriver driver : arWebDriver.getWebDriverList()) {
            try {
                driver.quit();
                log.info("WebDriver closed.");
            } catch (Exception e) {
                log.warn("Error closing WebDriver: " + e.getMessage());
            }
        }
        arWebDriver.getWebDriverList().clear();
        System.exit(0);
    }

    private void initHeader() {
        header.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel nameLabel = new JLabel("Name");
        JLabel descriptionLabel = new JLabel("Description");
        JLabel environmentLabel = new JLabel("Organization");
        JLabel statusLabel = new JLabel("Status");
        JLabel actionsLabel = new JLabel("Actions");
        header.add(nameLabel);
        header.add(descriptionLabel);
        header.add(environmentLabel);
        header.add(statusLabel);
        header.add(actionsLabel);
    }

    public void setProperty(String propertyName, String value) {
        this.properties.setProperty(propertyName, value);
    }

    @Override
    public JPanel getPaneReference() {
        return panelPane;
    }
}
