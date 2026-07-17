package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerActionDefaultsService;
import com.allinweb.ch.facade.UiThreadDispatcher;
import com.allinweb.ch.util.ARConstants;
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
        UiThreadDispatcher.getInstance().execute(() -> {
            click.setSelected(decision.click());
            input.setSelected(decision.input());
            output.setSelected(decision.output());
        });
    }
}
