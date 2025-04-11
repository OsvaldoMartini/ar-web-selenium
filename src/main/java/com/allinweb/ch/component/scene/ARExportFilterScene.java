// package com.allinweb.ch.component.scene;
//
// import com.allinweb.ch.component.model.BotJobLoadDTO;
// import com.allinweb.ch.component.pane.ARExportFilterPane;
// import com.allinweb.ch.component.pane.base.IARPane;
// import com.allinweb.ch.component.scene.base.ARScene;
//
// import lombok.extern.slf4j.Slf4j;   @Slf4j public class ARExportFilterScene extends ARScene {
//
//    private static final Double SCENE_HEIGHT = 600D;
//    private static final Double SCENE_WIDTH = 1000D;
//    private static final String TITLE = "Excel Export Field Filters";
//
//    private BotJobLoadDTO botJobLoad;
//
//    public ARExportFilterScene(BotJobLoadDTO botJobLoad) {
//        this.botJobLoad = botJobLoad;
//    }
//
//    @Override
//    public IARPane buildPane() {
//        return new ARExportFilterPane(botJobLoad);
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
