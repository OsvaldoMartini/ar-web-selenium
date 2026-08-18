package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VariableDefinitionPolicyTest {

    @Test
    void classifiesTheConfirmedProducerAndConsumerActions() {
        assertTrue(VariableDefinitionPolicy.isProducer("GET"));
        assertTrue(VariableDefinitionPolicy.isConsumer("E"));
        assertTrue(VariableDefinitionPolicy.isConsumer("CK"));
        assertTrue(VariableDefinitionPolicy.isConsumer("PDF CHECK"));
        assertTrue(VariableDefinitionPolicy.isConsumer("CSV CHECK"));
        assertTrue(VariableDefinitionPolicy.isVariableCommand("SET"));

        assertFalse(VariableDefinitionPolicy.isConsumer("SET"));
        assertFalse(VariableDefinitionPolicy.isProducer("H"));
        assertFalse(VariableDefinitionPolicy.isConsumer("H"));
        assertFalse(VariableDefinitionPolicy.isConsumer("PAUSE"));
    }

    @Test
    void generatesAStableReadableOwnerName() {
        assertEquals(
                "VAR-44-Order-Number-CHF",
                VariableDefinitionPolicy.generatedName(44, "  Order Number / CHF  "));
        assertEquals(
                "VAR-47-Instruction",
                VariableDefinitionPolicy.generatedName(47, "  "));
    }
}
