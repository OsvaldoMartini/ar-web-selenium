package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;
import org.openqa.selenium.WebDriver;

public class ARViewBotJobListPane extends ARPane {

    protected static volatile ARViewBotJobListPane instance;

    // Private constructor to prevent instantiation
    private ARViewBotJobListPane() {
        // Initialize if necessary
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

    // UI components
    private final GridPane header = new GridPane();
    private ListView<BotJobLoadDTO> uiBotJobList;

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private ObservableList<WebDriver> webDriverList;

    private static final PerformDataBase performDataBase;
    private static final PerformActions performActions;
    private static final PerformMessage performMessage;

    static {
        performDataBase = PerformDataBase.getInstance();
        performActions = PerformActions.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    // Constructor for Dependency Injection
    public void initialize(
            ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, ObservableList<WebDriver> webDriverList) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.webDriverList = webDriverList;
        initUIComponents();
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(header, uiBotJobList);
    }

    @Override
    public void initUIComponents() {
        ObservableList<BotJobLoadDTO> botJobList = FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
        uiBotJobList = new ListView<>(botJobList);

        // Setting the cell factory correctly
        uiBotJobList.setCellFactory(new ARCellFactory<>(
                BotJobListCell.class,
                arViewBotJobScene,
                arWebDriver,
                performDataBase,
                performActions,
                performMessage,
                (ObservableList<BotJobLoadDTO>) botJobList,
                webDriverList)::call);

        // Anchor positioning
        AnchorPane.setTopAnchor(uiBotJobList, ARConstants.SPACE_M * 2);
        AnchorPane.setBottomAnchor(uiBotJobList, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(uiBotJobList, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(uiBotJobList, ARConstants.SPACE_M);

        // Header setup
        header.setMaxHeight(ARConstants.SPACE_M);
        ColumnConstraints con = new ColumnConstraints();
        con.setPercentWidth(25);
        con.setHgrow(Priority.ALWAYS);
        con.setHalignment(HPos.LEFT);
        header.getColumnConstraints().addAll(con, con, con);

        ColumnConstraints con2 = new ColumnConstraints();
        con2.setPercentWidth(25);
        con2.setHgrow(Priority.ALWAYS);
        con2.setHalignment(HPos.CENTER);
        header.getColumnConstraints().add(con2);

        AnchorPane.setTopAnchor(header, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(header, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(header, ARConstants.SPACE_M);

        header.add(new Label("Name"), 0, 0);
        header.add(new Label("Description"), 1, 0);
        header.add(new Label("Environment"), 2, 0);
        header.add(new Label("Actions"), 3, 0);
    }

    @Override
    public void initUIBehaviour() {}
}
