package com.allinweb.ch.component.pane;

import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.facade.ScannerActionDefaultsService;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;

final class ScannerTestActionCheckboxStateAdapter {

    String selectedAction(CheckBox click, CheckBox input, CheckBox output) {
        return click.isSelected()
                ? ARConstants.CLICK
                : input.isSelected() ? ARConstants.INSERT : output.isSelected() ? ARConstants.OUTPUT : ARConstants.OTHER;
    }

    boolean clickSelected(CheckBox click) {
        return click.isSelected();
    }

    boolean inputSelected(CheckBox input) {
        return input.isSelected();
    }

    boolean outputSelected(CheckBox output) {
        return output.isSelected();
    }

    void apply(ScannerActionDefaultsService.Decision decision, CheckBox click, CheckBox input, CheckBox output) {
        Platform.runLater(() -> {
            click.setSelected(decision.click());
            input.setSelected(decision.input());
            output.setSelected(decision.output());
        });
    }
}
