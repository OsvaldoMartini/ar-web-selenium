package com.allinweb.ch.component.pane;

import javafx.scene.control.CheckBox;

final class ScannerHiddenCloneCheckboxAdapter {

    CheckBox build() {
        CheckBox checkBox = new CheckBox("HOVER PICK ");
        checkBox.setVisible(false);
        checkBox.setManaged(false);
        checkBox.setDisable(true);
        return checkBox;
    }

    boolean isSelected(CheckBox checkBox) {
        return checkBox != null && checkBox.isSelected();
    }

    void clear(CheckBox checkBox) {
        if (checkBox != null) {
            checkBox.setSelected(false);
        }
    }
}
