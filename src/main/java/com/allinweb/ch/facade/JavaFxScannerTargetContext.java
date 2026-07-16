package com.allinweb.ch.facade;

import com.allinweb.ch.component.pane.ARScannedElementPanePort;
import com.allinweb.ch.model.TargetElement;
import java.util.Objects;

/**
 * Legacy adapter that keeps {@link ScannerTargetContext} behavior routed to
 * AR Web Factory while scanner ownership is moved to backend services/React.
 */
public final class JavaFxScannerTargetContext implements ScannerTargetContext {

    private final ARScannedElementPanePort pane;

    public JavaFxScannerTargetContext(ARScannedElementPanePort pane) {
        this.pane = Objects.requireNonNull(pane, "pane");
    }

    @Override
    public void rememberPreviousXPath(String xpath) {
        pane.rememberPreviousXPath(xpath);
    }

    @Override
    public void applyActionDefaults(TargetElement targetElement) {
        pane.applyActionDefaults(targetElement);
    }
}
