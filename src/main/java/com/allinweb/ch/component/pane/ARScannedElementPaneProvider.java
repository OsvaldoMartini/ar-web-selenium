package com.allinweb.ch.component.pane;

import java.util.function.Supplier;

/** Compatibility wrapper for callers not yet moved to {@link ScannerRuntimeProvider}. */
public final class ARScannedElementPaneProvider {
    private static final ARScannedElementPaneProvider INSTANCE = new ARScannedElementPaneProvider();

    private ARScannedElementPaneProvider() {}

    public static ARScannedElementPaneProvider getInstance() {
        return INSTANCE;
    }

    public synchronized ARScannedElementPanePort currentPane() {
        return (ARScannedElementPanePort) ScannerRuntimeProvider.getInstance().currentRuntime();
    }

    synchronized void installPaneSupplier(Supplier<ARScannedElementPanePort> paneSupplier) {
        ScannerRuntimeProvider.getInstance().installRuntimeSupplier(paneSupplier::get);
    }

    synchronized void reset() {
        ScannerRuntimeProvider.getInstance().reset();
    }
}
