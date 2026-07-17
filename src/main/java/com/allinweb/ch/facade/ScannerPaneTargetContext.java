package com.allinweb.ch.facade;

import com.allinweb.ch.component.pane.ARScannedElementPanePort;
import com.allinweb.ch.model.TargetElement;
import java.util.Objects;

/**
 * Adapter that keeps {@link ScannerTargetContext} behavior routed to the
 * scanner runtime while ownership moves to backend services/React.
 */
public final class ScannerPaneTargetContext implements ScannerTargetContext {

    private final ARScannedElementPanePort pane;

    public ScannerPaneTargetContext(ARScannedElementPanePort pane) {
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
