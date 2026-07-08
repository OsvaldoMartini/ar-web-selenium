package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Playwright-only Page Scanner diagnostic for the BancaStato contact form.
 *
 * <p>Disabled by default. Enable explicitly with:
 *
 * <pre>
 * mvn -Dtest=BancaStatoPageScannerPlaywrightIT -DbancastatoScannerIT=true test
 * </pre>
 */
class BancaStatoPageScannerPlaywrightIT {

    private static final String DEFAULT_WEB_CONFIG_FILE_PATH =
            "D:\\Projects\\ARWebBancaStato\\Config-4.2\\ARWeb.config";
    private static final String DEFAULT_OUTPUT =
            "D:\\Projects\\ARWebBancaStato\\ARWeb\\page_diagnostics\\elementDTO-PS-playwright-it.json";
    private static final String ENDPOINT =
            "https://www.bancastato.ch/supporto-e-contatti/formulario-di-contatto";

    private final ARPropertyManager properties = ARPropertyManager.getInstance();

    @BeforeEach
    void loadConfig() throws Exception {
        String configPath = System.getProperty("arweb.config", DEFAULT_WEB_CONFIG_FILE_PATH);
        File configFile = new File(configPath);
        properties.setConfigurationFileName(configPath);
        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.loadProperties(fis);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "bancastatoScannerIT", matches = "true")
    void scansBancaStatoContactFormAndWritesElementDtoJson() throws Exception {
        ARPlaywrightDriver driver = ARWebDriver.getInstance().getPlaywrightDriver();
        String browserType = properties.getProperty(ARPropertyEnum.BROWSER);
        String optionsConfig = "";
        driver.openOrNavigate(browserType, ENDPOINT, optionsConfig);

        List<ElementDTO> elements = driver.scanElements(new String[] {"input"}, true);
        Path output = Path.of(System.getProperty("bancastatoScannerOutput", DEFAULT_OUTPUT));
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                new GsonBuilder().setPrettyPrinting().create().toJson(elements),
                StandardCharsets.UTF_8);

        long nativeInputs = elements.stream()
                .filter(e -> "input".equalsIgnoreCase(e.getTagName()))
                .count();
        long badInputButtons = elements.stream()
                .filter(e -> "input".equalsIgnoreCase(e.getTagName()))
                .filter(e -> "button".equalsIgnoreCase(e.getTypeElement()))
                .count();

        System.out.printf(
                "BancaStato scanner wrote %d DTOs to %s; nativeInputs=%d; badInputButtons=%d%n",
                elements.size(), output, nativeInputs, badInputButtons);

        assertTrue(nativeInputs > 0, "Expected scanner to find native input elements");
        assertTrue(badInputButtons == 0, "Native input elements must not be classified as button");
    }
}
