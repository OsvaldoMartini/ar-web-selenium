package com.allinweb.ch.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SplitDTO {

    private static final String ATTR_PREFIX = "AttrData:";

    private String type;
    private String sessionId;
    private String operationId;

    private Integer homeBankingId;
    private Integer botJobId;
    private String botJobName = "";

    private Integer blockId;
    private String blockName;
    private Integer blockOrderNumber;
    private Boolean blockActive;

    private Integer instructionId;
    private String instructionName;
    private Integer instructionOrderNumber;
    private Boolean instructionActive;

    private String actions;
    private String operation;

    private Integer variableId;
    private Integer parentId;
    private Integer parentBlockId;

    private String exportFile;
    private String appQueryApp;
    private String appQueryPackage;

    private String projectType;

    // Optional fields for SplitDTO
    private ElementDTO[] elementDetails;

    // Optional fields for BlockSplitDTO
    private DetailsDTO details;

    // Optional fields for UpdtateRows
    private List<UpdatedRow> updatedRows;
    // Optional fields for UpdtateRows
    private List<UpdatedRow> instructions;

    // Optional fields for BlockOrderDetail
    private List<BlockOrderDetailDTO> updatedBlocks;

    public static InstructionLoad mapSplitToInstruction(SplitDTO split) {
        if (split == null) return null;

        InstructionLoad instruction = new InstructionLoad();

        // Map all relevant fields from SplitDTO to InstructionLoad
        instruction.setId(split.getInstructionId());
        instruction.setBotJobId(split.getBotJobId());
        instruction.setHomeBankingId(split.getHomeBankingId());
        instruction.setBlockId(split.getBlockId());
        instruction.setParentBlockId(split.getParentBlockId());
        instruction.setParentId(split.getParentId());
        instruction.setActions(split.getActions());
        instruction.setSessionId(split.getSessionId());
        instruction.setOperation(split.getOperationId());
        instruction.setInstructionOrderNumber(split.getInstructionOrderNumber());
        instruction.setBlockOrderNumber(split.getBlockOrderNumber());
        instruction.setName(split.getInstructionName());
        instruction.setBlockName(split.getBlockName() != null ? split.getBlockName() : split.getBotJobName());

        // Optional element details mapping (if needed)
        if (split.getElementDetails() != null) {
            // map or store element details in a list, depending on your business logic
        }

        // Flags or defaults
        instruction.setInstructionActive(true);

        return instruction;
    }

    public static void applyAttrDataFromReferences(SplitDTO splitDTO, InstructionLoad instruction) {
        if (splitDTO == null || instruction == null) return;

        List<ReferenceLoadDTO> refs = instruction.getReferenceLoadDTOList();
        if (refs == null || refs.isEmpty()) return;

        List<AttributeData> attributes = new ArrayList<>();
        String attribId = null;
        String attribName = null;

        // Collect attributes and identify "id"/"name" special cases
        for (ReferenceLoadDTO ref : refs) {
            if (ref == null || ref.getReferenceType() == null || ref.getValue() == null) continue;
            String refType = ref.getReferenceType().trim();

            if (refType.startsWith(ATTR_PREFIX)) {
                String name = refType.substring(ATTR_PREFIX.length()).trim();
                String value = ref.getValue();

                // Capture special identifiers
                if (name.equalsIgnoreCase("id")) {
                    attribId = value;
                } else if (name.equalsIgnoreCase("name")) {
                    attribName = value;
                }

                attributes.add(new AttributeData(name, value));
            }
        }

        if (attributes.isEmpty() && attribId == null && attribName == null) return;

        // Ensure elementDetails exists
        ElementDTO[] elements = splitDTO.getElementDetails();
        if (elements == null || elements.length == 0) {
            splitDTO.setElementDetails(new ElementDTO[] {new ElementDTO()});
            elements = splitDTO.getElementDetails();
        }

        // Assign data
        for (ElementDTO el : elements) {
            if (el == null) continue;

            // Append or replace attributeData[]
            List<AttributeData> current = new ArrayList<>();
            if (el.getAttributeData() != null) {
                for (AttributeData a : el.getAttributeData()) {
                    if (a != null) current.add(a);
                }
            }
            current.addAll(attributes);
            el.setAttributeData(current.toArray(new AttributeData[0]));

            // Set special fields
            if (attribId != null) el.setAttribId(attribId);
            if (attribName != null) el.setAttribName(attribName);
        }
    }
}
