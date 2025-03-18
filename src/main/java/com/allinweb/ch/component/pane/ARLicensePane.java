package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.licence.LicenseManager;
import com.allinweb.ch.util.ARConstants;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ARLicensePane extends ARPane {

    private static final ARComponentBuilder builder = new ARComponentBuilder();

    private Button btnLicense; // Declare the License button

    private Pane mainPane;
    private CheckBox cbAgree;
    private Button btnProceed;
    private Button btnClose;

    private RadioButton rbRequestLicense;
    private RadioButton rbActivateLicense;
    private TextField tfLicenseOwner;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Initialize the License button
        //        LicenseManager.showAlert(
        //                Alert.AlertType.INFORMATION, LicenseManager.checkLicenseFile().getStaus() + "\n\nPress OK to
        // proceed.");

        // Header label for the application
        // TextArea for the License Agreement
        TextArea taLicenseAgreement = new TextArea(
                """
                LICENZA D'USO DEL SOFTWARE

                Importante - leggere con attenzione: Questo Accordo di Licenza ("Accordo") è un contratto legale tra te
                (un individuo o un'entità legale) e [Nome della Tua Azienda] ("Licenziante") per il software che accompagna
                questo accordo, che include software associato e materiale media, stampato, elettronico o online ("Software").

                1. Concessione della licenza:
                   Soggetto ai termini di questo Accordo, il Licenziante concede a te una licenza non esclusiva,
                   non trasferibile per utilizzare il Software per scopi interni secondo le seguenti limitazioni
                   e in conformità con la documentazione fornita.

                2. Restrizioni:
                   Non sei autorizzato a:
                   - Modificare, tradurre, adattare o derivare opere dal Software.
                   - Ingegnerizzare all'indietro, decompilare, disassemblare o altrimenti tentare di scoprire il codice sorgente del Software.
                   - Rivendere, noleggiare, sub-licenziare, distribuire o altrimenti trasferire il Software senza il previo consenso scritto del Licenziante.
                   - Rimuovere qualsiasi avviso di diritto d'autore, marchio o altro avviso di proprietà inclusi nel Software.

                3. Proprietà del Software:
                   Il Software è protetto da leggi sul diritto d'autore e trattati internazionali, così come altre leggi e trattati
                   sulla proprietà intellettuale. Il Software è licenziato, non venduto.

                4. Garanzia Limitata:
                   Il Licenziante garantisce che il Software funzionerà sostanzialmente in conformità con la documentazione
                   per un periodo di novanta (90) giorni dalla data del tuo acquisto. Qualsiasi sostituzione del Software sarà
                   garantita per il resto del periodo di garanzia originale o per trenta (30) giorni, a seconda di quale sia più lungo.

                5. Limitazione di Responsabilità:
                   In nessun caso il Licenziante sarà responsabile per danni speciali, incidentali, indiretti o consequenziali
                   risultanti dall'uso o dall'impossibilità di usare il Software, anche se il Licenziante è stato informato
                   della possibilità di tali danni. In nessun caso la responsabilità del Licenziante per danni supererà
                   l'importo pagato per acquistare il Software.

                6. Terminazione:
                   Questo Accordo è in vigore fino alla sua terminazione. Questo Accordo terminerà automaticamente senza preavviso
                   dal Licenziante se non rispetti qualsiasi termine o condizione di questo Accordo.

                7. Varie:
                   Questo Accordo costituisce l'intero accordo tra te e il Licenziante e sostituisce tutte le precedenti
                   comunicazioni, proposte o accordi, verbali o scritti, riguardo al Software.
                """);

        Label headerLabel = new Label("AR Web Activation software required");
        headerLabel.setStyle("-fx-text-fill: white; " + // Keep text color white
                "-fx-font-size: 14px; "
                + "-fx-padding: 10;");

        HBox headerContainer = new HBox(headerLabel);
        headerContainer.setStyle("-fx-background-color: #0078d7;"); // Blue background
        headerContainer.setPadding(new Insets(10));
        headerContainer.setAlignment(Pos.CENTER_LEFT); // Align text to the left

        HBox.setHgrow(headerLabel, Priority.ALWAYS);
        HBox.setHgrow(headerContainer, Priority.ALWAYS);

        // ToggleGroup for exclusive RadioButton selection
        ToggleGroup toggleGroup = new ToggleGroup();

        rbRequestLicense = new RadioButton("Request License");
        rbRequestLicense.setToggleGroup(toggleGroup);
        rbRequestLicense.setSelected(true);

        rbActivateLicense = new RadioButton("Activate with License");
        rbActivateLicense.setToggleGroup(toggleGroup);

        HBox radioButtonsBox = new HBox(10, rbRequestLicense, rbActivateLicense);
        radioButtonsBox.setPadding(new Insets(10));

        taLicenseAgreement.setWrapText(true);
        taLicenseAgreement.setEditable(false);

        tfLicenseOwner = new TextField();
        tfLicenseOwner.setPromptText("Licensed to (Owner of the license, min 6 chars)");

        // Checkbox to agree
        cbAgree = new CheckBox("Agree");
        cbAgree.setPadding(new Insets(10));

        // Button to proceed
        btnProceed = builder.buildButton("Procedere");
        btnProceed.setDisable(true);

        btnClose = builder.buildButton("Close");

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        HBox actionButtonsBox = new HBox(10, btnProceed, btnClose);
        actionButtonsBox.setPadding(new Insets(10));

        VBox mainLayout = new VBox(
                10, headerContainer, radioButtonsBox, taLicenseAgreement, tfLicenseOwner, cbAgree, actionButtonsBox);
        mainLayout.setPadding(new Insets(10));
        mainLayout.setFillWidth(true); // Ensure components stretch horizontally

        VBox.setVgrow(taLicenseAgreement, Priority.ALWAYS);

        AnchorPane.setTopAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(mainLayout, ARConstants.SPACE_M);

        mainPane = new AnchorPane(mainLayout);
    }

    @Override
    public void initUIBehaviour() {
        // Additional behavior for the button can be added here if needed
        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        btnClose.setOnAction(event -> {
            Stage stage = (Stage) btnClose.getScene().getWindow();
            stage.close();
        });

        // Actions for Proceed button
        btnProceed.setOnAction(event -> {
            if (!cbAgree.isSelected()) {
                LicenseManager.showAlert(Alert.AlertType.ERROR, "Please agree to the terms to proceed.");
            } else
                try {
                    if (tfLicenseOwner.getText().isEmpty()) {
                        LicenseManager.showAlert(Alert.AlertType.ERROR, "The 'Licensed to' field is required.");
                    } else {
                        if (rbRequestLicense.isSelected()) {
                            LicenseManager.generateRequestFile(tfLicenseOwner.getText());
                            LicenseManager.showAlert(
                                    Alert.AlertType.INFORMATION, "Request file generated successfully.");
                        } else if (rbActivateLicense.isSelected() && LicenseManager.importResponseFile()) {
                            LicenseManager.showAlert(
                                    Alert.AlertType.INFORMATION, "Licence activated! You can close this Message!");
                        } else {
                            LicenseManager.showAlert(
                                    Alert.AlertType.ERROR, "Response file not found or could not be processed.");
                        }
                    }
                } catch (Exception error) {
                    // TODO Auto-generated catch block
                    LicenseManager.showAlert(Alert.AlertType.ERROR, "An error occurred: " + error.getMessage());
                }
        });
    }
}
