package com.allinweb.ch.facade;

import com.allinweb.ch.facade.scanner.ScannerRuntimePort;
import com.allinweb.ch.model.TargetElement;
import java.util.Objects;

/**
 * Adapter that keeps {@link ScannerTargetContext} behavior routed to the
 * scanner runtime while ownership moves to backend services/React.
 */
public final class ScannerPaneTargetContext implements ScannerTargetContext {

    private final ScannerRuntimePort scannerRuntime;

    public ScannerPaneTargetContext(ScannerRuntimePort scannerRuntime) {
        this.scannerRuntime = Objects.requireNonNull(scannerRuntime, "scannerRuntime");
    }

    @Override
    public void rememberPreviousXPath(String xpath) {
        scannerRuntime.rememberPreviousXPath(xpath);
    }

    @Override
    public void applyActionDefaults(TargetElement targetElement) {
        scannerRuntime.applyActionDefaults(targetElement);
    }
}
