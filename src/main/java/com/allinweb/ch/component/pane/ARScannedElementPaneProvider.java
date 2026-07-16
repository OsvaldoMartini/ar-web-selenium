package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.IARPane;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.stage.Stage;

/** Central provider for the legacy scanner pane while scene ownership is being extracted. */
public final class ARScannedElementPaneProvider {
    private static final ARScannedElementPaneProvider INSTANCE = new ARScannedElementPaneProvider();

    private Supplier<ARScannedElementPanePort> paneSupplier = ARScannedElementPane::getInstance;

    private ARScannedElementPaneProvider() {}

    public static ARScannedElementPaneProvider getInstance() {
        return INSTANCE;
    }

    public synchronized ARScannedElementPanePort currentPane() {
        return paneSupplier.get();
    }

    public synchronized IARPane currentPaneView() {
        ARScannedElementPanePort pane = currentPane();
        return pane instanceof IARPane paneView ? paneView : null;
    }

    public synchronized void setStage(Stage stage) {
        ARScannedElementPanePort pane = currentPane();
        if (pane instanceof ARScannedElementPane scannerPane) {
            scannerPane.setStage(stage);
        }
    }

    synchronized void installPaneSupplier(Supplier<ARScannedElementPanePort> paneSupplier) {
        this.paneSupplier = Objects.requireNonNull(paneSupplier, "paneSupplier");
    }

    synchronized void reset() {
        paneSupplier = ARScannedElementPane::getInstance;
    }
}
