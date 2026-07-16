package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARScannedElementPaneProvider;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.facade.ScannerModalStageService;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

final class JavaFxScannerModalStageFactory implements ScannerModalStageService.StageFactory {
    private final ARScannedElementPaneProvider scannerPaneProvider;
    private final Supplier<IARPane> paneFactory;
    private final Image icon;
    private final Runnable closeRequest;

    JavaFxScannerModalStageFactory(
            ARScannedElementPaneProvider scannerPaneProvider,
            Supplier<IARPane> paneFactory,
            Image icon,
            Runnable closeRequest) {
        this.scannerPaneProvider = scannerPaneProvider;
        this.paneFactory = paneFactory;
        this.icon = icon;
        this.closeRequest = closeRequest;
    }

    @Override
    public ScannerModalStageService.ModalStage create(ScannerModalStageService.Config config) {
        Stage stage = new Stage();
        scannerPaneProvider.setStage(stage);
        stage.getIcons().add(icon);
        IARPane pane = paneFactory.get();
        if (pane == null) {
            return null;
        }
        Scene scene = new Scene(pane.createPane(), config.width(), config.height());
        stage.setScene(scene);
        stage.setTitle(config.title());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setAlwaysOnTop(true);
        stage.toFront();
        stage.setAlwaysOnTop(false);
        stage.setOnShown(event -> Platform.runLater(() -> stage.setAlwaysOnTop(false)));
        stage.setOnCloseRequest(event -> closeRequest.run());
        return new JavaFxScannerModalStage(stage);
    }

    private static final class JavaFxScannerModalStage implements ScannerModalStageService.ModalStage {
        private final Stage stage;

        private JavaFxScannerModalStage(Stage stage) {
            this.stage = stage;
        }

        @Override
        public void setTitle(String title) {
            stage.setTitle(title);
        }

        @Override
        public boolean isShowing() {
            return stage.isShowing();
        }

        @Override
        public void showAndWait() {
            stage.showAndWait();
        }

        @Override
        public void close() {
            stage.close();
        }
    }
}
