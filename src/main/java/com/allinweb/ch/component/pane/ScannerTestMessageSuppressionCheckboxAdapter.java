package com.allinweb.ch.component.pane;

import javafx.scene.control.CheckBox;

final class ScannerTestMessageSuppressionCheckboxAdapter {

    CheckBox build() {
        CheckBox checkBox = new CheckBox("Not Show Test Message");
        checkBox.setSelected(true);
        return checkBox;
    }

    boolean shouldShowSuccessMessage(CheckBox checkBox) {
        return checkBox == null || !checkBox.isSelected();
    }
}
