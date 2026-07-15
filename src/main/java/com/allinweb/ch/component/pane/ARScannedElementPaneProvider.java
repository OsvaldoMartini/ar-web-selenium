package com.allinweb.ch.component.pane;

import java.util.Objects;
import java.util.function.Supplier;

/** Central provider for the legacy scanner pane while scene ownership is being extracted. */
public final class ARScannedElementPaneProvider {
    private static final ARScannedElementPaneProvider INSTANCE = new ARScannedElementPaneProvider();

    private Supplier<ARScannedElementPane> paneSupplier = ARScannedElementPane::getInstance;

    private ARScannedElementPaneProvider() {}

    public static ARScannedElementPaneProvider getInstance() {
        return INSTANCE;
    }

    public synchronized ARScannedElementPane currentPane() {
        return paneSupplier.get();
    }

    synchronized void installPaneSupplier(Supplier<ARScannedElementPane> paneSupplier) {
        this.paneSupplier = Objects.requireNonNull(paneSupplier, "paneSupplier");
    }

    synchronized void reset() {
        paneSupplier = ARScannedElementPane::getInstance;
    }
}
