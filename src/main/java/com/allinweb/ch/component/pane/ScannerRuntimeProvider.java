package com.allinweb.ch.component.pane;

import java.util.Objects;
import java.util.function.Supplier;

/** Central provider for scanner runtime operations while implementation ownership is extracted. */
public final class ScannerRuntimeProvider {
    private static final ScannerRuntimeProvider INSTANCE = new ScannerRuntimeProvider();

    private Supplier<ScannerRuntimePort> runtimeSupplier = ARScannedElementPane::getInstance;

    private ScannerRuntimeProvider() {}

    public static ScannerRuntimeProvider getInstance() {
        return INSTANCE;
    }

    public synchronized ScannerRuntimePort currentRuntime() {
        return runtimeSupplier.get();
    }

    synchronized void installRuntimeSupplier(Supplier<ScannerRuntimePort> runtimeSupplier) {
        this.runtimeSupplier = Objects.requireNonNull(runtimeSupplier, "runtimeSupplier");
    }

    synchronized void reset() {
        runtimeSupplier = ARScannedElementPane::getInstance;
    }
}
