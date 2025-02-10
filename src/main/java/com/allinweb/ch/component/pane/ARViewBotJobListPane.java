package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ARConstants;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;

public class ARViewBotJobListPane extends ARPane {

    // UI components
    GridPane header = new GridPane();

    ListView<BotJobLoadDTO> uiBotJobList = new ListView<>();

    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(header, uiBotJobList);
    }

    @Override
    public void initUIComponents() {
        //        ARSharedResources.getInstance().getEntityList(BotJobDTO.class);
        ObservableList<BotJobLoadDTO> botJobList = FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
        uiBotJobList = new ListView<>(botJobList);
        uiBotJobList.setCellFactory(new ARCellFactory<>(BotJobListCell.class)::call);
        AnchorPane.setTopAnchor(uiBotJobList, ARConstants.SPACE_M + ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(uiBotJobList, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(uiBotJobList, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(uiBotJobList, ARConstants.SPACE_M);

        header.setMaxHeight(ARConstants.SPACE_M);
        ColumnConstraints con = new ColumnConstraints();
        con.setPercentWidth(25);
        con.setHgrow(Priority.ALWAYS);
        con.setHalignment(HPos.LEFT);
        header.getColumnConstraints().add(con);
        header.getColumnConstraints().add(con);
        header.getColumnConstraints().add(con);
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
