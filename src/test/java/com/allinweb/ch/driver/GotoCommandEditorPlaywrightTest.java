package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Browser-level regression for the Command Editor GOTO workflow.
 *
 * <p>The fixture mirrors the required-field contract in {@code InstructionCommandPanel}: GOTO
 * requires a destination block and a positive count. Apply must remain disabled until the block is
 * selected, then emit the same draft fields sent by {@code commandEditor.apply}.
 */
class GotoCommandEditorPlaywrightTest {

    @BeforeEach
    void requirePlaywrightBrowser() {
        PlaywrightTestSupport.assumeBrowserLaunchAvailable();
    }

    @Test
    void selectsGotoDestinationAndEmitsApplyPayload() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent(commandEditorFixture());

            assertTrue(driver.fill(byId("selectedInstruction"), new FieldData("selectedInstruction", "1639")));
            select(driver, "action", "GOTO");

            assertEquals("GOTO count", text(driver, "countLabel"));
            assertTrue(disabled(driver, "apply"), "Apply must wait for a destination block");

            select(driver, "destinationBlock", "218");
            assertTrue(driver.fill(byId("count"), new FieldData("count", "1")));
            assertFalse(disabled(driver, "apply"), "Apply should enable after all GOTO fields are valid");
            assertTrue(driver.click(byId("apply")));

            Object raw = driver.evaluate("() => window.appliedDraft");
            assertTrue(raw instanceof Map<?, ?>, "Apply should publish a command draft");
            Map<?, ?> payload = (Map<?, ?>) raw;
            assertEquals("commandEditor.apply", payload.get("type"));
            assertEquals("after", payload.get("mode"));
            assertEquals("GOTO", payload.get("action"));
            assertEquals(1639, ((Number) payload.get("instructionId")).intValue());
            assertEquals(214, ((Number) payload.get("blockId")).intValue());
            assertEquals(218, ((Number) payload.get("parentBlockId")).intValue());
            assertEquals(1, ((Number) payload.get("count")).intValue());
        } finally {
            driver.close();
        }
    }

    private static InstructionLoad byId(String id) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setName(id);
        instruction.setCssSelector("#" + id);
        return instruction;
    }

    private static void select(ARPlaywrightDriver driver, String id, String value) {
        driver.evaluate(
                "([id,value]) => {"
                        + " const field=document.getElementById(id);"
                        + " field.value=value;"
                        + " field.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "}",
                List.of(id, value));
    }

    private static boolean disabled(ARPlaywrightDriver driver, String id) {
        return Boolean.TRUE.equals(driver.evaluate("id => document.getElementById(id).disabled", id));
    }

    private static String text(ARPlaywrightDriver driver, String id) {
        return String.valueOf(driver.evaluate("id => document.getElementById(id).textContent", id));
    }

    private static String commandEditorFixture() {
        return """
                <!doctype html>
                <html>
                  <body>
                    <label>Selected instruction
                      <input id="selectedInstruction" type="number" />
                    </label>
                    <label>Command
                      <select id="action">
                        <option value="">Select command</option>
                        <option value="GOTO">GOTO</option>
                      </select>
                    </label>
                    <label>Destination Block
                      <select id="destinationBlock">
                        <option value="">Select destination</option>
                        <option value="214">1 - Apertura Conto</option>
                        <option value="218">2 - Apertura Conto</option>
                      </select>
                    </label>
                    <label id="countLabel" hidden>GOTO count</label>
                    <input id="count" type="number" min="1" max="9999" value="1" hidden />
                    <button id="apply" type="button" disabled>Apply</button>
                    <script>
                      const instruction = document.getElementById('selectedInstruction');
                      const action = document.getElementById('action');
                      const destination = document.getElementById('destinationBlock');
                      const count = document.getElementById('count');
                      const countLabel = document.getElementById('countLabel');
                      const apply = document.getElementById('apply');

                      function refresh() {
                        const gotoSelected = action.value === 'GOTO';
                        count.hidden = !gotoSelected;
                        countLabel.hidden = !gotoSelected;
                        apply.disabled = !instruction.value || !gotoSelected || !destination.value
                          || Number(count.value) < 1;
                      }
                      [instruction, action, destination, count].forEach(field => {
                        field.addEventListener('input', refresh);
                        field.addEventListener('change', refresh);
                      });
                      apply.addEventListener('click', () => {
                        window.appliedDraft = {
                          type: 'commandEditor.apply',
                          mode: 'after',
                          action: action.value,
                          instructionId: Number(instruction.value),
                          blockId: 214,
                          parentBlockId: Number(destination.value),
                          count: Number(count.value)
                        };
                      });
                      refresh();
                    </script>
                  </body>
                </html>
                """;
    }
}
