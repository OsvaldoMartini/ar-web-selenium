// package com.allinweb.ch.component.scene;
//
// import com.allinweb.ch.component.pane.ARComponentDetailsPane;
// import com.allinweb.ch.component.pane.base.IARPane;
// import com.allinweb.ch.component.scene.base.ARScene;
// import com.allinweb.ch.persistence.ComponentBlockDTO;
//
// import lombok.extern.slf4j.Slf4j;   @Slf4j public class ARComponentDetailsScene extends ARScene {
//
//    private static final Double SCENE_HEIGHT = 400D;
//    private static final Double SCENE_WIDTH = 650D;
//    private static String TITLE = "";
//
//    private ComponentBlockDTO componentBlockDTO;
//
//    public ARComponentDetailsScene(ComponentBlockDTO componentBlockDTO) {
//        this.componentBlockDTO = componentBlockDTO;
//        TITLE = "Details - " + componentBlockDTO.getName();
//    }
//
//    @Override
//    public IARPane buildPane() {
//        return new ARComponentDetailsPane(componentBlockDTO);
//    }
//
//    @Override
//    public Double getSceneHeight() {
//        return SCENE_HEIGHT;
//    }
//
//    @Override
//    public Double getSceneWidth() {
//        return SCENE_WIDTH;
//    }
//
//    @Override
//    public String getTitle() {
//        return TITLE;
//    }
// }
