package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class ScannerGridBootstrapServiceTest {

    private final ScannerGridBootstrapService service = new ScannerGridBootstrapService();

    @Test
    void buildsScannerGridBootstrapScript() {
        String script = service.bootstrapScript(
                new ScannerGridBootstrapService.Request("[{\"id\":1}]", 54525, "scannerGrid", 7, 11, "Job A"),
                new Gson());

        assertTrue(script.contains("window.receiveDataFromJava"));
        assertTrue(script.contains("JSON.stringify([{\"id\":1}])"));
        assertTrue(script.contains("54525"));
        assertTrue(script.contains("\"scannerGrid\""));
        assertTrue(script.contains("7"));
        assertTrue(script.contains("11"));
        assertTrue(script.contains("\"Job A\""));
    }

    @Test
    void rejectsMissingRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.bootstrapScript(null, new Gson()));
    }
}
