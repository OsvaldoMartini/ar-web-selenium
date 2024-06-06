package com.allinweb.ch.tests;

import java.util.ArrayList;
import java.util.List;

public class FindNamesInArray {
    public static void main(String[] args) {
        // Sample data
        List<InstructionReferenceDTO> instructionReferences = new ArrayList<>();
        instructionReferences.add(new InstructionReferenceDTO("ref1", "value1"));
        instructionReferences.add(new InstructionReferenceDTO("ref2", "value2"));
        instructionReferences.add(new InstructionReferenceDTO("REF3", "value3"));

        List<Priority> priorityList = new ArrayList<>();
        priorityList.add(new Priority(List.of("ref1"), "type1", 1));
        priorityList.add(new Priority(List.of("ref2", "REF3"), "type2", 2));

        // Find matches
        List<Priority> matchingPriorities = findMatchingPriorities(instructionReferences, priorityList);

        // Print or process matching priorities
        for (Priority priority : matchingPriorities) {
            System.out.println("Match found: " + priority.getPriorityType());
        }
    }

    private static List<Priority> findMatchingPriorities(
            List<InstructionReferenceDTO> instructionReferences, List<Priority> priorityList) {
        List<Priority> matchingPriorities = new ArrayList<>();
        for (InstructionReferenceDTO instructionReference : instructionReferences) {
            String referenceType = instructionReference.getReferenceType();
            for (Priority priority : priorityList) {
                for (String name : priority.getName()) {
                    if (name.equalsIgnoreCase(referenceType)) {
                        matchingPriorities.add(priority);
                        break;
                    }
                }
            }
        }
        return matchingPriorities;
    }
}
