package com.allinweb.ch.tests;

import java.util.List;
import java.util.Optional;

public class FindNamesInArray_2 {
    public static void main(String[] args) {
        // Sample data
        List<InstructionReferenceDTO> instructionReferences = List.of(
                new InstructionReferenceDTO("ref1", "value1"),
                new InstructionReferenceDTO("ref2", "value2"),
                new InstructionReferenceDTO("REF3", "value3"));

        List<Priority> priorityList =
                List.of(new Priority(List.of("ref1"), "type1", 1), new Priority(List.of("ref2", "REF3"), "type2", 2));

        // Check if all instruction references have corresponding priorities
        boolean allMatch = instructionReferences.stream()
                .allMatch(reference -> priorityList.stream().anyMatch(priority -> priority.getName().stream()
                        .anyMatch(name -> name.equalsIgnoreCase(reference.getReferenceType()))));

        System.out.println("All instruction references have corresponding priorities: " + allMatch);

        // Find the first matching instruction reference
        Optional<InstructionReferenceDTO> firstMatchingInstructionReference = instructionReferences.stream()
                .filter(reference -> priorityList.stream().anyMatch(priority -> priority.getName().stream()
                        .anyMatch(name -> name.equalsIgnoreCase(reference.getReferenceType()))))
                .findFirst();

        // Print or process the first matching instruction reference
        firstMatchingInstructionReference.ifPresent(System.out::println);
    }
}
