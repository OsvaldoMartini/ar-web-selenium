package com.allinweb.ch.ai;

import com.allinweb.ch.ARControlPanel;
import com.allinweb.ch.socket.WebSocketTestClient;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class OpenAIEngine {

    private static final ARPropertyManager arPropertyManager;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
    }

    private static String fileNameJson = "AI-ElementDTO.json";
    private static String urlPage = "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

    private static final String OPENAI_API_KEY =
            "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";

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    public static void main(String[] args) throws IOException, InterruptedException {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("-c")) {
            int configurationValueIndex = arguments.indexOf("-c") + 1;
            String configurationValue = arguments.get(configurationValueIndex);
            try {
                System.setProperty("ARWebConfig", configurationValue);
            } catch (Exception ignore) {

            }
            // Prevention if  System.setProperty(...) has no permission access
            arPropertyManager.setConfigurationFileName(configurationValue);

            File configurationFile = new File(configurationValue);
            try (FileInputStream conf = new FileInputStream(configurationFile)) {
                arPropertyManager.loadProperties(conf);
            } catch (Exception error) {
                error.printStackTrace();
            }

            ARLogger.getInstance(ARControlPanel.class).fine("Configuration file path: " + configurationValue);
        }

        System.out.println("Inserisci cosa vuoi automatizzare:");

        String logPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LOG);

        String elementsJson = new String(Files.readAllBytes(Paths.get(logPath + "\\" + fileNameJson)));

        String fullPrompt = buildFullPrompt(urlPage, elementsJson);

        JSONObject requestBody = new JSONObject()
                .put("model", "gpt-4")
                .put(
                        "messages",
                        new JSONArray().put(new JSONObject().put("role", "user").put("content", fullPrompt)))
                .put("temperature", 0);

        try {
            // Load keystore from resources and copy to a temp file
            String keystorePassword = "Martini!383940";
            File keystoreTempFile = copyResourceToTempFile("keystore.jks", "keystore", ".jks");
            System.setProperty("javax.net.ssl.keyStore", keystoreTempFile.getAbsolutePath());
            System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);

            // Load truststore from resources and copy to a temp file
            String truststorePassword = "Martini!383940";
            File truststoreTempFile = copyResourceToTempFile("truststore.jks", "truststore", ".jks");
            System.setProperty("javax.net.ssl.trustStore", truststoreTempFile.getAbsolutePath());
            System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
        } catch (Exception erroTemp) {

        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)) // Set a 10-second connection timeout
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(30)) // Optional per-request timeout
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
                    .header("OpenAI-Organization", "org-x3K136h2q7mZxMHGt5ICoHD4") // Optional, only if required
                    .header("OpenAI-Project", "proj_vB7dXxKrsbsR3tPpmwK9bTGA") // Proje: ARWebScanner
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject responseJson = new JSONObject(response.body());
            String reply = responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            System.out.println("\n✅ Elementi rilevanti:\n" + reply);

            Files.write(Paths.get("output.json"), reply.getBytes());
            System.out.println("✅ Risposta salvata in 'output.json'.");

        } catch (IOException e) {
            System.err.println("❌ Errore di I/O: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("❌ La richiesta HTTP è stata interrotta: " + e.getMessage());
            Thread.currentThread().interrupt(); // best practice
        } catch (org.json.JSONException e) {
            System.err.println("❌ Errore nel parsing della risposta JSON: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Errore imprevisto: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildFullPrompt(String userPrompt, String elementsJson) {
        return userPrompt.trim() + "\n\n"
                + "Analizza l'elenco degli elementi UI della pagina e restituisci **solo quelli necessari** per completare quanto richiesto (es: login)."
                + "\nIl risultato deve essere un JSON puro con gli elementi da usare, senza testo extra, markdown o spiegazioni.\n\n"
                + "Elementi della pagina:\n"
                + elementsJson;
    }

    private static File copyResourceToTempFile(String resourceName, String prefix, String suffix) throws IOException {
        URL resourceUrl = WebSocketTestClient.class.getClassLoader().getResource(resourceName);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Resource not found: " + resourceName);
        }

        File tempFile = Files.createTempFile(prefix, suffix).toFile();
        tempFile.deleteOnExit();

        try (InputStream in = resourceUrl.openStream();
                OutputStream out = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }
}
