// package com.allinweb.ch.component.scene;
//
// import com.allinweb.ch.component.pane.ARMoveBlockPane;
// import com.allinweb.ch.component.pane.base.IARPane;
// import com.allinweb.ch.component.scene.base.ARScene;
// import com.allinweb.ch.persistence.BlockDTO;
//
// public class ARMoveBlockScene extends ARScene {
//    private static final Double SCENE_HEIGHT = 400D;
//    private static final Double SCENE_WIDTH = 400D;
//    private static String TITLE = "Move Block";
//
//    private BlockDTO block;
//
//    public ARMoveBlockScene(BlockDTO block) {
//        this.block = block;
//        TITLE = "Move - " + block.getName();
//    }
//
//    @Override
//    public IARPane buildPane() {
//        return new ARMoveBlockPane(block);
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
