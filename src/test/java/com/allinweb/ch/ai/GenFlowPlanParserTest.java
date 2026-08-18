package com.allinweb.ch.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure parser/validator tests — no network, no database. */
class GenFlowPlanParserTest {

    private static final String PLAN_JSON =
            """
            {
              "blocks": [
                {
                  "name": "Visit Investor Relations",
                  "steps": [
                    {"action": "CLICK", "elementName": "investor_relations",
                     "xpath": "/html[1]/body[1]/a[1]", "cssSelector": "a.link"},
                    {"action": "BACK"}
                  ]
                }
              ]
            }""";

    private static InstructionLoad element(String name, String xpath) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setName(name);
        instruction.setXpath(xpath);
        instruction.setActions("C");
        return instruction;
    }

    private static final List<InstructionLoad> INVENTORY = List.of(
            element("investor_relations", "/html[1]/body[1]/a[1]"),
            element("search_input", "/input[1]"),
            element("page_back_link", "/html[1]/body[1]/nav[1]/a[1]"));

    @Test
    void parsesBareJson() throws Exception {
        GenFlowPlan plan = GenFlowPlanParser.parse(PLAN_JSON);
        assertEquals(1, plan.blocks.size());
        assertEquals(2, plan.blocks.get(0).steps.size());
    }

    @Test
    void parsesJsonWrappedInMarkdownFencesAndProse() throws Exception {
        String wrapped = "Here is the plan you asked for:\n```json\n" + PLAN_JSON + "\n```\nLet me know!";
        GenFlowPlan plan = GenFlowPlanParser.parse(wrapped);
        assertEquals(1, plan.blocks.size());
    }

    @Test
    void malformedJsonThrows() {
        assertThrows(GenFlowException.class, () -> GenFlowPlanParser.parse("{\"blocks\": [ {name: broken"));
        assertThrows(GenFlowException.class, () -> GenFlowPlanParser.parse("no json here at all"));
        assertThrows(GenFlowException.class, () -> GenFlowPlanParser.parse(""));
        assertThrows(GenFlowException.class, () -> GenFlowPlanParser.parse("{\"blocks\": []}"));
    }

    @Test
    void validateMatchesByXpathAndDropsBrowserBack() throws Exception {
        GenFlowPlan plan = GenFlowPlanParser.parse(PLAN_JSON);
        GenFlowPlanParser.ValidatedPlan validated = GenFlowPlanParser.validate(plan, INVENTORY, 30);

        assertEquals(1, validated.blocks().size());
        assertEquals(1, validated.droppedSteps());
        List<GenFlowPlanParser.ValidatedStep> steps = validated.blocks().get(0).steps();
        assertEquals(1, steps.size());
        assertEquals("CLICK", steps.get(0).action());
        assertEquals("investor_relations", steps.get(0).source().getName());
    }

    @Test
    void validateAllowsPageBackOnlyAsScannedClickElement() throws Exception {
        String pageBackPlan =
                """
                {"blocks": [
                  {"name": "Return with page link", "steps": [
                    {"action": "CLICK", "elementName": "page_back_link", "xpath": "/html[1]/body[1]/nav[1]/a[1]"}
                  ]}
                ]}""";
        GenFlowPlan plan = GenFlowPlanParser.parse(pageBackPlan);
        GenFlowPlanParser.ValidatedPlan validated = GenFlowPlanParser.validate(plan, INVENTORY, 30);

        assertEquals(1, validated.blocks().size());
        assertEquals(0, validated.droppedSteps());
        assertEquals("CLICK", validated.blocks().get(0).steps().get(0).action());
        assertEquals(
                "page_back_link",
                validated.blocks().get(0).steps().get(0).source().getName());
    }

    @Test
    void validateDropsHallucinatedElementsAndEmptyBlocks() throws Exception {
        String hallucinated =
                """
                {"blocks": [
                  {"name": "Ghost", "steps": [
                    {"action": "CLICK", "elementName": "does_not_exist", "xpath": "/html/fake[9]"},
                    {"action": "BACK"}
                  ]},
                  {"name": "Real", "steps": [
                    {"action": "CLICK", "elementName": "INVESTOR_RELATIONS", "xpath": "/wrong/xpath"}
                  ]}
                ]}""";
        GenFlowPlan plan = GenFlowPlanParser.parse(hallucinated);
        GenFlowPlanParser.ValidatedPlan validated = GenFlowPlanParser.validate(plan, INVENTORY, 30);

        // Ghost block: CLICK dropped (hallucinated) then BACK-only -> whole block dropped.
        // Real block: xpath wrong but name matches case-insensitively -> kept.
        assertEquals(1, validated.blocks().size());
        assertEquals("Real", validated.blocks().get(0).name());
        assertTrue(validated.droppedSteps() >= 2);
    }

    @Test
    void insertWithoutValueGetsDefaultSyntheticValue() throws Exception {
        String insertPlan =
                """
                {"blocks": [
                  {"name": "Type in search", "steps": [
                    {"action": "INSERT", "elementName": "search_input", "xpath": "/input[1]"}
                  ]}
                ]}""";
        GenFlowPlan plan = GenFlowPlanParser.parse(insertPlan);
        GenFlowPlanParser.ValidatedPlan validated = GenFlowPlanParser.validate(plan, INVENTORY, 30);

        assertEquals(
                GenFlowPlanParser.DEFAULT_SYNTHETIC_VALUE,
                validated.blocks().get(0).steps().get(0).syntheticValue());
    }

    @Test
    void blockCountIsCappedAtMaxBlocks() throws Exception {
        StringBuilder many = new StringBuilder("{\"blocks\": [");
        for (int i = 0; i < 10; i++) {
            if (i > 0) many.append(',');
            many.append("{\"name\": \"B")
                    .append(i)
                    .append("\", \"steps\": [")
                    .append("{\"action\": \"CLICK\", \"elementName\": \"investor_relations\",")
                    .append("\"xpath\": \"/html[1]/body[1]/a[1]\"}]}");
        }
        many.append("]}");
        GenFlowPlan plan = GenFlowPlanParser.parse(many.toString());
        GenFlowPlanParser.ValidatedPlan validated = GenFlowPlanParser.validate(plan, INVENTORY, 3);
        assertEquals(3, validated.blocks().size());
    }

    @Test
    void truncatedResponseSalvagesCompleteBlocks() throws Exception {
        // Simulates the model hitting max_tokens mid-way through block 3.
        String truncated =
                """
                {"blocks": [
                  {"name": "First", "steps": [
                    {"action": "CLICK", "elementName": "investor_relations", "xpath": "/html[1]/body[1]/a[1]"},
                    {"action": "BACK"}
                  ]},
                  {"name": "Second", "steps": [
                    {"action": "CLICK", "elementName": "investor_relations", "xpath": "/html[1]/body[1]/a[1]"}
                  ]},
                  {"name": "Third (cut off)", "steps": [
                    {"action": "CLICK", "elementName": "investor_rel""";
        GenFlowPlan plan = GenFlowPlanParser.parse(truncated);
        assertEquals(2, plan.blocks.size());
        assertEquals("First", plan.blocks.get(0).name);
        assertEquals("Second", plan.blocks.get(1).name);
    }

    @Test
    void longBlockNamesAreTruncatedTo40Chars() throws Exception {
        String longName = "X".repeat(80);
        String json = "{\"blocks\": [{\"name\": \"" + longName + "\", \"steps\": ["
                + "{\"action\": \"CLICK\", \"elementName\": \"investor_relations\","
                + "\"xpath\": \"/html[1]/body[1]/a[1]\"}]}]}";
        GenFlowPlan plan = GenFlowPlanParser.parse(json);
        GenFlowPlanParser.ValidatedPlan validated = GenFlowPlanParser.validate(plan, INVENTORY, 30);
        assertEquals(40, validated.blocks().get(0).name().length());
    }
}
