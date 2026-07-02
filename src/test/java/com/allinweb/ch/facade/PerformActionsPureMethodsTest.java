package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/**
 * Characterization tests pinning the behavior of PerformActions' pure methods
 * before they are extracted into the facade.actions package. These tests call
 * the facade only, so they must stay green through every extraction increment.
 */
class PerformActionsPureMethodsTest {

    private final PerformActions performActions = PerformActions.getInstance();

    @Test
    void removeTrailingSlashStripsOnlyTrailingSlash() {
        assertEquals("//div[1]", PerformActions.removeTrailingSlash("//div[1]/"));
        assertEquals("//div[1]", PerformActions.removeTrailingSlash("//div[1]"));
        assertNull(PerformActions.removeTrailingSlash(null));
    }

    @Test
    void extractTagNameReturnsLastSegmentWithoutIndex() {
        assertEquals("input", PerformActions.extractTagName("//form/div/input[2]"));
        assertEquals("button", PerformActions.extractTagName("//div/button"));
        assertEquals("span", PerformActions.extractTagName("span"));
    }

    @Test
    void convertToCssSelectorMapsPriorityAliasesToAttributes() {
        assertEquals(
                "input[id='user']", PerformActions.convertToCssSelector("input", List.of("attributeID"), " user "));
        assertEquals(
                "input[name='pwd']", PerformActions.convertToCssSelector("input", List.of("attributeName"), "pwd"));
        assertEquals("div[test-id='x1']", PerformActions.convertToCssSelector("div", List.of("test-id"), "x1"));
        assertNull(PerformActions.convertToCssSelector("div", List.of(), "x1"));
    }

    @Test
    void convertToCriteriaListBuildsOneCssCriterionPerPriority() {
        List<By> criteria =
                PerformActions.convertToCriteriaList("input", List.of("attributeID", "attributeName", "data-qa"), "v");
        assertEquals(3, criteria.size());
        assertEquals(By.cssSelector("input[id='v']"), criteria.get(0));
        assertEquals(By.cssSelector("input[name='v']"), criteria.get(1));
        assertEquals(By.cssSelector("input[data-qa='v']"), criteria.get(2));
    }

    @Test
    void sanitizeValueNormalizesUnicodeSpacesAndCollapsesWhitespace() {
        assertEquals("", PerformActions.sanitizeValue(null));
        assertEquals("1 000.50", PerformActions.sanitizeValue("1 000.50"));
        assertEquals("a b c", PerformActions.sanitizeValue("  a\t b   c  "));
    }

    @Test
    void truncateAndNormalizeCurrentlyReturnsInputUnchanged() {
        assertEquals("  some   text  ", PerformActions.truncateAndNormalize("  some   text  ", 5));
        assertNull(PerformActions.truncateAndNormalize(null, 5));
    }

    @Test
    void extractFileExtensionHandlesPathsAndNonFiles() {
        assertEquals("pdf", PerformActions.extractFileExtension("dir/report.pdf"));
        assertEquals("png", PerformActions.extractFileExtension("image.png"));
        assertEquals("", PerformActions.extractFileExtension("no-extension"));
        assertEquals("", PerformActions.extractFileExtension("trailing.dot."));
        assertEquals("", PerformActions.extractFileExtension(""));
        assertEquals("", PerformActions.extractFileExtension(null));
    }

    @Test
    void generateRandomNameRespectsLengthAndAlphabet() {
        for (int i = 0; i < 50; i++) {
            String name = PerformActions.generateRandomName();
            assertTrue(name.length() >= 3 && name.length() <= 30, "length in [3,30]: " + name);
            assertTrue(name.matches("[A-Za-z]+"), "letters only: " + name);
        }
    }

    @Test
    void formatLocalNumberGroupsIntegerAndKeepsDecimals() {
        assertEquals("1,234,567.89", performActions.formatLocalNumber("1234567.89", "US"));
        assertEquals("1.234.567,89", performActions.formatLocalNumber("1234567,89", "EU"));
        assertEquals("1,234", performActions.formatLocalNumber("1234", "anything"));
    }

    @Test
    void removeAllCurrencySymbolsKeepsDigitsAndSeparators() {
        assertEquals("1'234.50".replace("'", ""), performActions.removeAllCurrencySymbols("CHF 1'234.50"));
        assertEquals("1,000.99", performActions.removeAllCurrencySymbols("$1,000.99"));
    }

    @Test
    void removeCurrencySymbolsCleansEveryMapValuePreservingOrder() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("a", "€ 12,50");
        input.put("b", "CHF 7.00");
        Map<String, String> cleaned = performActions.removeCurrencySymbols(input);
        assertEquals(List.of("a", "b"), new ArrayList<>(cleaned.keySet()));
        assertEquals("12,50", cleaned.get("a"));
        assertEquals("7.00", cleaned.get("b"));
    }

    @Test
    void getConditionIndexMapByParentIdIndexesConditionalInstructions() {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setInstructionLoad(List.of(
                instruction("CLICK", 1),
                instruction("IF", 1),
                instruction("ELSE", 1),
                instruction("ENDIF", 1),
                instruction("IF", 2)));

        Map<String, List<Integer>> map = performActions.getConditionIndexMapByParentId(block);

        assertEquals(List.of(1), map.get("1-IF"));
        assertEquals(List.of(2), map.get("1-ELSE"));
        assertEquals(List.of(3), map.get("1-ENDIF"));
        assertEquals(List.of(4), map.get("2-IF"));
        assertEquals(4, map.size());
    }

    @Test
    void searchMapConditionalReturnsFirstIndexAtOrAfterCurrent() {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put("1-ENDIF", List.of(3, 9));

        assertEquals(3, performActions.searchMapConditional(map, 1, ARExecution.ConditionStatus.ENDIF, 0, false));
        assertEquals(9, performActions.searchMapConditional(map, 1, ARExecution.ConditionStatus.ENDIF, 4, false));
        assertEquals(-1, performActions.searchMapConditional(map, 1, ARExecution.ConditionStatus.ENDIF, 10, false));
        assertEquals(-1, performActions.searchMapConditional(map, 2, ARExecution.ConditionStatus.ENDIF, 0, false));
    }

    @Test
    void checkActionToJumpJumpsToEndifForElseBranchesAndZeroOtherwise() {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put("1-ENDIF", List.of(5));

        assertEquals(5, performActions.checkActionToJump("ELSEIF", ARExecution.ConditionStatus.IF_PASSED, map, 1, 2));
        assertEquals(5, performActions.checkActionToJump("ELSE", ARExecution.ConditionStatus.IF_PASSED, map, 1, 2));
        assertEquals(0, performActions.checkActionToJump("CLICK", ARExecution.ConditionStatus.NONE, map, 1, 2));
    }

    private static InstructionLoad instruction(String actions, int parentId) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setActions(actions);
        instruction.setParentId(parentId);
        return instruction;
    }
}
