package com.allinweb.ch.licence;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LicenseActivationApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        LicenseManager.showAlert(
                Alert.AlertType.INFORMATION, LicenseManager.checkLicenseFile().getStaus() + "\n\nPress OK to proceed.");

        // Header label for the application
        Label headerLabel = new Label("AR Web Activation software required");
        headerLabel.setStyle(
                "-fx-background-color: #0078d7; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10;");
        headerLabel.setMinWidth(500);
        headerLabel.setMaxHeight(Double.MAX_VALUE); // Ensure the label stretches across the top

        // ToggleGroup for exclusive RadioButton selection
        ToggleGroup toggleGroup = new ToggleGroup();

        RadioButton rbRequestLicense = new RadioButton("Request License");
        rbRequestLicense.setToggleGroup(toggleGroup);
        rbRequestLicense.setSelected(true);

        RadioButton rbActivateLicense = new RadioButton("Activate with License");
        rbActivateLicense.setToggleGroup(toggleGroup);

        HBox radioButtonsBox = new HBox(10, rbRequestLicense, rbActivateLicense);
        radioButtonsBox.setPadding(new Insets(10));

        // TextArea for the License Agreement
        TextArea taLicenseAgreement = new TextArea(
                "LICENZA D'USO DEL SOFTWARE\n\n"
                        + "Importante - leggere con attenzione: Questo Accordo di Licenza (\"Accordo\") è un contratto legale tra te (un individuo o un'entità legale) e [Nome della Tua Azienda] (\"Licenziante\") per il software che accompagna questo accordo, che include software associato e materiale media, stampato, elettronico o online (\"Software\").\n\n"
                        + "1. Concessione della licenza: Soggetto ai termini di questo Accordo, il Licenziante concede a te una licenza non esclusiva, non trasferibile per utilizzare il Software per scopi interni secondo le seguenti limitazioni e in conformità con la documentazione fornita.\n\n"
                        + "2. Restrizioni: Non sei autorizzato a:\n"
                        + "   - Modificare, tradurre, adattare o derivare opere dal Software.\n"
                        + "   - Ingegnerizzare all'indietro, decompilare, disassemblare o altrimenti tentare di scoprire il codice sorgente del Software.\n"
                        + "   - Rivendere, noleggiare, sub-licenziare, distribuire o altrimenti trasferire il Software senza il previo consenso scritto del Licenziante.\n"
                        + "   - Rimuovere qualsiasi avviso di diritto d'autore, marchio o altro avviso di proprietà inclusi nel Software.\n\n"
                        + "3. Proprietà del Software: Il Software è protetto da leggi sul diritto d'autore e trattati internazionali, così come altre leggi e trattati sulla proprietà intellettuale. Il Software è licenziato, non venduto.\n\n"
                        + "4. Garanzia Limitata: Il Licenziante garantisce che il Software funzionerà sostanzialmente in conformità con la documentazione per un periodo di novanta (90) giorni dalla data del tuo acquisto. Qualsiasi sostituzione del Software sarà garantita per il resto del periodo di garanzia originale o per trenta (30) giorni, a seconda di quale sia più lungo.\n\n"
                        + "5. Limitazione di Responsabilità: In nessun caso il Licenziante sarà responsabile per danni speciali, incidentali, indiretti o consequenziali risultanti dall'uso o dall'impossibilità di usare il Software, anche se il Licenziante è stato informato della possibilità di tali danni. In nessun caso la responsabilità del Licenziante per danni supererà l'importo pagato per acquistare il Software.\n\n"
                        + "6. Terminazione: Questo Accordo è in vigore fino alla sua terminazione. Questo Accordo terminerà automaticamente senza preavviso dal Licenziante se non rispetti qualsiasi termine o condizione di questo Accordo.\n\n"
                        + "7. Varie: Questo Accordo costituisce l'intero accordo tra te e il Licenziante e sostituisce tutte le precedenti comunicazioni, proposte o accordi, verbali o scritti, riguardo al Software.\n");

        taLicenseAgreement.setWrapText(true);
        taLicenseAgreement.setEditable(false);
        ScrollPane scrollPane = new ScrollPane(taLicenseAgreement);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        // TextField for entering the license owner's name
        TextField tfLicenseOwner = new TextField();
        tfLicenseOwner.setPromptText("Licensed to (Owner of the license, min 6 chars)");

        // Checkbox to agree
        CheckBox cbAgree = new CheckBox("Agree");
        cbAgree.setPadding(new Insets(10));

        // Button to proceed
        Button btnProceed = new Button("Procedere");
        btnProceed.setDisable(true); // Initially disabled

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        // Actions for Proceed button
        btnProceed.setOnAction(event -> {
            if (!cbAgree.isSelected()) {
                LicenseManager.showAlert(Alert.AlertType.ERROR, "Please agree to the terms to proceed.");
            } else
                try {
                    if (tfLicenseOwner.getText().isEmpty() && !LicenseManager.importResponseFile()) {
                        LicenseManager.showAlert(Alert.AlertType.ERROR, "The 'Licensed to' field is required.");
                    } else {
                        try {
                            if (rbRequestLicense.isSelected()) {
                                LicenseManager.generateRequestFile(tfLicenseOwner.getText());
                                LicenseManager.showAlert(
                                        Alert.AlertType.INFORMATION, "Request file generated successfully.");
                            } else if (rbActivateLicense.isSelected() && LicenseManager.importResponseFile()) {
                                LicenseManager.showAlert(
                                        Alert.AlertType.INFORMATION,
                                        "Licence activated! You can close this Message!");
                            } else {
                                LicenseManager.showAlert(
                                        Alert.AlertType.ERROR, "Response file not found or could not be processed.");
                            }
                        } catch (Exception erro) {
                            LicenseManager.showAlert(Alert.AlertType.ERROR, "An error occurred: " + erro.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        });

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        // Button to close the application
        Button btnClose = new Button("Close");
        btnClose.setOnAction(event -> primaryStage.close());

        HBox actionButtonsBox = new HBox(10, btnProceed, btnClose);
        actionButtonsBox.setPadding(new Insets(10));

        // Main layout
        VBox mainLayout =
                new VBox(10, headerLabel, radioButtonsBox, scrollPane, tfLicenseOwner, cbAgree, actionButtonsBox);
        mainLayout.setPadding(new Insets(10));

        // Set up the scene
        Scene scene = new Scene(mainLayout, 600, 450); // Adjusted window size for better layout
        primaryStage.setTitle("Activation Software Required");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) throws Exception {
        if (!LicenseManager.checkLicenseFile().isActive()) {
            launch(args);
        } else {
            System.out.println("AR Web agree licence terms are activate.\n\nPress OK to proceed.");
            //        Application.launch(LicenceResponseManagerApp.class, args); // Lancia questa
            // applicazione se la
            // condizione  falsa
        }
    }
}
