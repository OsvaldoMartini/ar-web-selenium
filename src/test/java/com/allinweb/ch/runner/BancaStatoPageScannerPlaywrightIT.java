package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.ElementTextResolver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Playwright-only Page Scanner diagnostic for the BancaStato contact form.
 *
 * <p>Disabled by default. Enable explicitly with:
 *
 * <pre>
 * mvn -Dtest=BancaStatoPageScannerPlaywrightIT -DbancastatoScannerIT=true test
 * </pre>
 */
@Isolated("Mutates ARPropertyManager and ARWebDriver singletons")
class BancaStatoPageScannerPlaywrightIT {

    private static final String ENDPOINT = "https://www.bancastato.ch/supporto-e-contatti/formulario-di-contatto";
    private static final String TEXTAREA_TEST_VALUE = "dddsddfff";

    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private BancaStatoIsolatedFixture fixture;

    @TempDir
    Path tempDirectory;

    @AfterEach
    void closePlaywright() {
        try {
            ARWebDriver.getInstance().closeBrowser();
        } catch (Exception ignored) {
            // Best-effort diagnostic cleanup.
        } finally {
            if (fixture != null) {
                fixture.close();
            }
        }
    }

    @BeforeEach
    void loadConfig() throws Exception {
        fixture = BancaStatoIsolatedFixture.create(tempDirectory);
        fixture.activate(properties);
        assertTrue(Files.exists(fixture.databaseFile()), "Expected isolated BancaStato database snapshot");
    }

    @Test
    @EnabledIfSystemProperty(named = "bancastatoScannerIT", matches = "true")
    void scansBancaStatoContactFormAndWritesElementDtoJson() throws Exception {
        ARPlaywrightDriver driver = ARWebDriver.getInstance().getPlaywrightDriver();
        String browserType = properties.getProperty(ARPropertyEnum.BROWSER);
        driver.openReadOnlyDiagnostic(browserType, ENDPOINT, false);

        driver.evaluate(
                """
                (value) => {
                  const textarea = document.querySelector('#richiesta textarea, textarea[data-slot="textarea"]');
                  if (!textarea) return false;
                  textarea.value = value;
                  textarea.textContent = value;
                  textarea.dispatchEvent(new Event('input', { bubbles: true }));
                  textarea.dispatchEvent(new Event('change', { bubbles: true }));
                  return true;
                }
                """,
                TEXTAREA_TEST_VALUE);

        List<ElementDTO> elements = driver.scanElements(new String[] {"textarea"}, true);
        Path output = Path.of(System.getProperty(
                "bancastatoScannerOutput", fixture.diagnosticsFolder().resolve("elementDTO-PS.json").toString()));
        fixture.requireIsolatedOutput(output);
        ElementDTO[] asArray = elements.toArray(new ElementDTO[0]);
        ElementTextResolver.resolveAll(asArray, null, null);
        Files.createDirectories(output.getParent());
        Files.writeString(
                output, new GsonBuilder().setPrettyPrinting().create().toJson(asArray), StandardCharsets.UTF_8);

        long textareas = elements.stream()
                .filter(e -> "textarea".equalsIgnoreCase(e.getTagName()))
                .count();
        long badTextareaButtons = elements.stream()
                .filter(e -> "textarea".equalsIgnoreCase(e.getTagName()))
                .filter(e -> "button".equalsIgnoreCase(e.getTypeElement()))
                .count();
        boolean foundRichiesta = elements.stream().anyMatch(BancaStatoPageScannerPlaywrightIT::isRichiestaTextarea);

        System.out.printf(
                "BancaStato scanner wrote %d DTOs to %s; textareas=%d; badTextareaButtons=%d; foundRichiesta=%s%n",
                elements.size(), output, textareas, badTextareaButtons, foundRichiesta);

        assertTrue(textareas > 0, "Expected scanner to find native textarea elements");
        assertTrue(badTextareaButtons == 0, "Native textarea elements must not be classified as button");
        assertTrue(foundRichiesta, "Expected scanner to find the Richiesta textarea");
    }

    private static boolean isRichiestaTextarea(ElementDTO element) {
        if (element == null || !"textarea".equalsIgnoreCase(element.getTagName())) {
            return false;
        }
        String text = lower(element.getSomeText());
        String name = lower(element.getDefinedName());
        String xpath = lower(element.getXPath());
        return text.contains("richiesta") || name.contains("richiesta") || xpath.contains("richiesta");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

}
