package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;

public class ABRViewBotJobListPane extends ABRPane {

    // UI components
    GridPane header = new GridPane();

    ListView<BotJobDTO> uiBotJobList = new ListView<>();

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(header, uiBotJobList);
    }

    @Override
    public void initUIComponents() {
        ObservableList<BotJobDTO> botJobList = ABRSharedResources.getInstance().getEntityList(BotJobDTO.class);
        uiBotJobList = new ListView<>(botJobList);
        uiBotJobList.setCellFactory(new ABRCellFactory<>(BotJobListCell.class)::call);
        AnchorPane.setTopAnchor(uiBotJobList, ABRConstants.SPACE_M + ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(uiBotJobList, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(uiBotJobList, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(uiBotJobList, ABRConstants.SPACE_M);

        header.setMaxHeight(ABRConstants.SPACE_M);
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
        AnchorPane.setTopAnchor(header, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(header, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(header, ABRConstants.SPACE_M);
        header.add(new Label("Name"), 0, 0);
        header.add(new Label("Description"), 1, 0);
        header.add(new Label("Environment"), 2, 0);
        header.add(new Label("Actions"), 3, 0);
    }

    @Override
    public void initUIBehaviour() {}
}
