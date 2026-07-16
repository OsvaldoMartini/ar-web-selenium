package com.allinweb.ch.component.pane;

import javafx.scene.control.CheckBox;

final class ScannerTestActionCheckboxesAdapter {

    Checkboxes build() {
        return new Checkboxes(
                new CheckBox("For Click"),
                new CheckBox("For Input"),
                new CheckBox("For Output (Excel Export)"));
    }

    record Checkboxes(CheckBox click, CheckBox input, CheckBox output) {}
}
