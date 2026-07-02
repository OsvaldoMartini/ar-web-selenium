package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaywrightAutomationSmokeTest {

    @Test
    void scansMockBankingPageAndRunsActions() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent(mockBankingPage());

            List<ElementDTO> elements =
                    driver.scanElements(new String[] {"input", "button", "select", "label", "div"}, false);

            assertFalse(elements.isEmpty(), "Expected scanner to return mock page elements");
            assertTrue(elements.stream().anyMatch(e -> "clientName".equals(e.getAttribId())));
            assertTrue(elements.stream().anyMatch(e -> "createClient".equals(e.getAttribId())));
            assertTrue(elements.stream().anyMatch(e -> "balanceStatus".equals(e.getAttribId())));

            assertTrue(driver.fill(byId("clientName"), new FieldData("clientName", "Maria Silva")));
            assertTrue(driver.click(byId("createClient")));

            String status = driver.text(byId("balanceStatus"));
            assertEquals("Client Maria Silva created", status);
        } finally {
            driver.close();
        }
    }

    private static InstructionLoad byId(String id) {
        ReferenceLoadDTO ref = new ReferenceLoadDTO();
        ref.setReferenceType("locator.best.byId");
        ref.setValue(id);

        InstructionLoad instruction = new InstructionLoad();
        instruction.setName(id);
        instruction.setXpath("//*[@id='" + id + "']");
        instruction.setCssSelector("#" + id);
        instruction.setReferenceLoadDTOList(List.of(ref));
        return instruction;
    }

    private static String mockBankingPage() {
        return """
                <!doctype html>
                <html>
                  <head>
                    <title>Mock Banking Automation</title>
                  </head>
                  <body>
                    <main>
                      <label for="clientName">Client name</label>
                      <input id="clientName" name="clientName" placeholder="Client name" />

                      <label for="accountType">Account type</label>
                      <select id="accountType" name="accountType">
                        <option value="checking">Checking</option>
                        <option value="savings">Savings</option>
                      </select>

                      <button id="createClient" type="button">Create client</button>
                      <div id="balanceStatus">Waiting</div>
                    </main>
                    <script>
                      document.getElementById('createClient').addEventListener('click', () => {
                        const name = document.getElementById('clientName').value;
                        document.getElementById('balanceStatus').innerText = 'Client ' + name + ' created';
                      });
                    </script>
                  </body>
                </html>
                """;
    }
}
