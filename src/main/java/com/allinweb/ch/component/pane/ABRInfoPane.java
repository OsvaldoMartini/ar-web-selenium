package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.util.ABRConstants;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class ABRInfoPane extends ABRPane {

    private Label applicationNameLabel;
    private Label compileDateLabel;
    private Label copyrightLabel;
    private Label rightsReservedLabel;

    private Pane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        applicationNameLabel = new Label("ABR Web Scan v0.5f Beta Test");
        compileDateLabel = new Label("Build: 02-10-2024");
        copyrightLabel = new Label("Copyright Allinweb AG");
        rightsReservedLabel = new Label("All rights reserved");

        /*

        ObservableList<Node> secondLayerList1 = FXCollections.observableArrayList(
                new Label("second1text1"),new Label("second1text2")
        );
        ObservableList<Node> secondLayerList2 = FXCollections.observableArrayList(
                new Label("second2text1"),new Label("second2text2")
        );

        ListView<Node> secondLayer1 = new ListView<>(secondLayerList1);
        secondLayer1.setPrefHeight(secondLayerList1.size() * ABRConstants.SPACE_L);
        ListView<Node> secondLayer2 = new ListView<>(secondLayerList2);
        secondLayer2.setMaxHeight(secondLayerList2.size() * ABRConstants.SPACE_L);



        ObservableList<Node> firstLayerList = FXCollections.observableArrayList(
                new Label("text1"),secondLayer1,new Label("text2"),secondLayer2
        );

        ListView<Node> infoGroup = new ListView<>(firstLayerList);
        String css = getClass().getResource("/listView.css").toExternalForm();
        secondLayerList1.addListener(
                (ListChangeListener<? super Node>) (change) -> {
                    while(change.next()){
                        if (change.wasAdded()){
                            secondLayer1.setPrefHeight(secondLayer1.getPrefHeight() + (change.getAddedSize() * ABRConstants.SPACE_L));
                        }
                    }
                }
        );
        infoGroup.setCellFactory(
                (val) -> new ListCell<>(){
                    @Override
                    protected void updateItem(Node item, boolean empty) {
                        super.updateItem(item, empty);
                        Node graphic = null;
                        if (!empty && item != null){
                            graphic = item;
                        }
                        setGraphic(graphic);
                        setBackground(null);
                        setTextFill(Color.BLACK);
                        getStylesheets().add(css);
                    }
                }
        );
        infoGroup.setBackground(null);
        infoGroup.setBorder(null);
        Button addLabel = new Button("add label");
        addLabel.setOnMouseClicked(
                (e) -> {
                    Task<Void> adding = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            for (int i = 0; i < 5; i++) {
                                Platform.runLater(
                                        () -> secondLayerList1.add(new Label("Generated"))
                                );
                                Thread.sleep(1000);
                            }
                            return null;
                        }
                    };
                    new Thread(adding).start();
                }
        );
        addLabel.setMaxHeight(ABRConstants.SPACE_M);
        AnchorPane.setTopAnchor(addLabel, ABRConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(addLabel, ABRConstants.SPACE_M);
        AnchorPane.setTopAnchor(infoGroup, ABRConstants.SPACE_M + addLabel.getMaxHeight());

        */
        VBox infoGroup = new VBox(applicationNameLabel, compileDateLabel, copyrightLabel, rightsReservedLabel);
        AnchorPane.setTopAnchor(infoGroup, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(infoGroup, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(infoGroup, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(infoGroup, ABRConstants.SPACE_M);

        mainPane = new AnchorPane(infoGroup);
    }

    @Override
    public void initUIBehaviour() {}
}
