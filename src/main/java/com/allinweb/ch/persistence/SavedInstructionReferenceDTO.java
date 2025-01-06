package com.allinweb.ch.persistence;

import com.allinweb.ch.core.ABRSharedResources;
import java.util.*;
import javax.persistence.*;

@Entity
@Table(name = "saved_instruction_reference")
// @SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "savedInstructionReferenceSeq", allocationSize =
// 1)
public class SavedInstructionReferenceDTO extends BaseDTO {

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "value", length = 1000)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "bot_job_id")
    private BotJobDTO botJobDTO;

    public BotJobDTO getBotJobDTO() {
        return botJobDTO;
    }

    public void setBotJobDTO(BotJobDTO botJobDTO) {
        this.botJobDTO = botJobDTO;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_block_loop_instruction_id")
    private SavedBlockLoopInstructionDTO savedBlockLoopInstructionDTO;

    public SavedInstructionReferenceDTO() {
        super();
    }

    public SavedInstructionReferenceDTO(int id) {
        super(id);
    }

    public SavedInstructionReferenceDTO(SavedBlockLoopInstructionDTO instruction) {
        savedBlockLoopInstructionDTO = instruction;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public SavedBlockLoopInstructionDTO getSavedBlockLoopInstructionDTO() {
        return savedBlockLoopInstructionDTO;
    }

    public void setSavedBlockLoopInstructionDTO(SavedBlockLoopInstructionDTO savedBlockLoopInstructionDTO) {
        this.savedBlockLoopInstructionDTO = savedBlockLoopInstructionDTO;
    }

    public static List<SavedInstructionReferenceDTO> createSavedReferencesFromInstructionForSavedInstruction(
            BlockLoopInstructionDTO instructionDTO, SavedBlockLoopInstructionDTO savedBlockLoopInstructionDTO) {
        List<InstructionReferenceDTO> referenceList = ABRSharedResources.getInstance()
                .getEntityList(
                        InstructionReferenceDTO.class,
                        reference -> reference.getBlockLoopInstructionDTO().getId() == instructionDTO.getId());
        List<SavedInstructionReferenceDTO> list = new ArrayList<>();
        referenceList.forEach(
                reference -> list.add(copyFromReferenceForSavedInstruction(reference, savedBlockLoopInstructionDTO)));
        return list;
    }

    private static SavedInstructionReferenceDTO copyFromReferenceForSavedInstruction(
            InstructionReferenceDTO reference, SavedBlockLoopInstructionDTO savedInstruction) {
        SavedInstructionReferenceDTO saved = new SavedInstructionReferenceDTO();
        saved.setValue(reference.getValue());
        saved.setReferenceType(reference.getReferenceType());
        saved.setSavedBlockLoopInstructionDTO(savedInstruction);
        return saved;
    }
}
