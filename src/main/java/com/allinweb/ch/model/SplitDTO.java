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

    /**
     * Copies only non-null / non-blank fields from InstructionLoad into SplitDTO and its first ElementDTO.
     */
    public static void applyInstructionToSplit(SplitDTO splitDTO, InstructionLoad src) {
        if (splitDTO == null || src == null) return;

        // --- SplitDTO (top-level) ---
        if (src.getHomeBankingId() != null) splitDTO.setHomeBankingId(src.getHomeBankingId());
        if (src.getBotJobId() != null) splitDTO.setBotJobId(src.getBotJobId());
        if (hasText(src.getBotJobName())) splitDTO.setBotJobName(src.getBotJobName());

        if (src.getBlockId() != null) splitDTO.setBlockId(src.getBlockId());
        if (hasText(src.getBlockName())) splitDTO.setBlockName(src.getBlockName());
        if (src.getBlockOrderNumber() != null) splitDTO.setBlockOrderNumber(src.getBlockOrderNumber());
        if (src.getBlockActive() != null) splitDTO.setBlockActive(src.getBlockActive());

        if (src.getId() != null) splitDTO.setInstructionId(src.getId());
        if (hasText(src.getName())) splitDTO.setInstructionName(src.getName());
        if (src.getInstructionOrderNumber() != null)
            splitDTO.setInstructionOrderNumber(src.getInstructionOrderNumber());
        if (src.getInstructionActive() != null) splitDTO.setInstructionActive(src.getInstructionActive());

        if (hasText(src.getActions())) splitDTO.setActions(src.getActions());
        if (hasText(src.getOperation())) splitDTO.setOperation(src.getOperation());

        if (src.getVariableId() != null) splitDTO.setVariableId(src.getVariableId());
        if (src.getParentId() != null) splitDTO.setParentId(src.getParentId());
        if (src.getParentBlockId() != null) splitDTO.setParentBlockId(src.getParentBlockId());

        if (hasText(src.getExportFile())) splitDTO.setExportFile(src.getExportFile());
        // appQueryApp / appQueryPackage not present on InstructionLoad → not set here.

        // --- ElementDTO (first item in array) ---
        ElementDTO el = ensureFirstElement(splitDTO);

        // Choose which ID to mirror on the element:
        // If your ElementDTO.id represents the variable link, prefer variableId; otherwise use src.getId()
        if (src.getVariableId() != null) el.setId(src.getVariableId());
        else if (src.getId() != null) el.setId(src.getId());

        if (hasText(src.getType())) el.setTypeElement(src.getType());
        if (hasText(src.getTagName())) el.setTagName(src.getTagName());
        if (hasText(src.getXpath())) el.setXPath(src.getXpath()); // Lombok: field `xPath` -> setter `setXPath`
        if (hasText(src.getCoordinates())) el.setCoordinates(src.getCoordinates());
        if (hasText(src.getIFrameXPath())) el.setIFrameXPath(src.getIFrameXPath());
        if (hasText(src.getShadowHost())) el.setShadowHost(src.getShadowHost());
        if (hasText(src.getShadowRoot())) el.setShadowRoot(src.getShadowRoot());
        if (hasText(src.getCssSelector())) el.setCssSelector(src.getCssSelector());

        // Fields without a clear mapping from InstructionLoad are left untouched:
        // someText, attribId, attribName, attributeData, customXPath, nestedShadow,
        // attributeValue, attributeType, searchAttributeValue.
    }

    // ---------- helpers ----------

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static ElementDTO ensureFirstElement(SplitDTO splitDTO) {
        ElementDTO[] arr = splitDTO.getElementDetails();
        if (arr == null || arr.length == 0) {
            ElementDTO e = new ElementDTO();
            splitDTO.setElementDetails(new ElementDTO[] {e});
            return e;
        }
        return arr[0];
    }
}
