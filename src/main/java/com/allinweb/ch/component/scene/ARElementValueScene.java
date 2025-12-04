package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARElementValuePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.model.SplitDTO;
import javax.swing.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARElementValueScene extends ARScene {

    private static final int SCENE_HEIGHT = 600;
    private static final int SCENE_WIDTH = 600;
    private static final String TITLE = "New Variables";
    protected static volatile ARElementValueScene instance;
    private static ARElementValuePane arElementValuePane = ARElementValuePane.getInstance();
    public boolean closeCalled;

    @Getter
    @Setter
    public SplitDTO splitDTO;

    private JDialog modalDialog;
    private int varId;
    private String varValue;
    private int instructionId;
    private String instructionName;
    private String varName;
    private String instructionType;
    private boolean firstLoad = true;

    // Private constructor to prevent instantiation
    private ARElementValueScene() {
        super();
    }

    public static ARElementValueScene getInstance() {
        if (instance == null) {
            synchronized (ARElementValueScene.class) {
                if (instance == null) {
                    instance = new ARElementValueScene();
                }
            }
        }
        return instance;
    }

    public void initialize(
            SplitDTO splitDTO,
            int varId,
            String varName,
            String varValue,
            int instructionId,
            String instructionName,
            String instructionType) {
        this.splitDTO = splitDTO;
        this.varId = varId;
        this.varName = varName;
        this.varValue = varValue;
        this.instructionId = instructionId;
        this.instructionName = instructionName;
        this.instructionType = instructionType;

        if (!firstLoad) {
            arElementValuePane.initialize(
                    splitDTO, varId, varValue, instructionId, instructionName, varName, instructionType);
        }
    }

    @Override
    public IARPane buildPane() {
        return arElementValuePane;
    }

    @Override
    public int getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public int getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    public void showModal(JFrame parentFrame) {
        firstLoad = false;

        arElementValuePane.initialize(
                splitDTO, varId, varValue, instructionId, instructionName, varName, instructionType);

        if (modalDialog == null) {
            modalDialog = new JDialog(parentFrame, getTitle(), true); // modal dialog
            modalDialog.setSize(SCENE_WIDTH, SCENE_HEIGHT);
            modalDialog.setContentPane(arElementValuePane.createPane());
            modalDialog.setLocationRelativeTo(parentFrame);
        }

        modalDialog.setTitle(getTitle());

        if (!modalDialog.isVisible()) {
            modalDialog.setVisible(true); // Show modal dialog
        }
    }

    public void closeModal() {
        try {
            if (modalDialog != null) {
                modalDialog.dispose();
            }
            modalDialog = null;
            closeCalled = true;
        } catch (Exception error) {
            closeCalled = true;
        }
    }

    public void setTableRowById(Integer varId) {
        //        arElementValuePane.selectRowById(varId);
    }
}
