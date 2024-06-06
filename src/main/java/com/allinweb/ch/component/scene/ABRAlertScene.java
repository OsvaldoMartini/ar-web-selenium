package com.allinweb.ch.component.scene;

import com.allinweb.ch.util.ABRCallback;
import com.allinweb.ch.util.ABRConstants;
import java.util.Objects;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class ABRAlertScene {

    public ABRAlertScene(Alert.AlertType alertType, String title, String message, ButtonType... buttons) {
        Alert alert = new Alert(alertType, message, buttons);
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(ABRConstants.ICON_APPLICATION)));
        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        alertStage.getIcons().add(icon);
        alert.setTitle(alertType.name());
        alert.setHeaderText(title);
        alertStage.setAlwaysOnTop(true);
        alert.show();
    }

    public ABRAlertScene(
            Alert.AlertType alertType, String title, String message, ABRCallback callback, ButtonType... buttons) {
        Alert alert = new Alert(alertType, message, buttons);
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(ABRConstants.ICON_APPLICATION)));
        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        alertStage.getIcons().add(icon);
        alert.setTitle(alertType.name());
        alert.setHeaderText(title);
        alertStage.setAlwaysOnTop(true);
        alert.showAndWait()
                .filter((response) -> {
                    return response == ButtonType.OK;
                })
                .ifPresent((response) -> {
                    callback.execute();
                });
    }
}
