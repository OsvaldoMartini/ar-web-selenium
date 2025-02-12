package com.allinweb.ch.persistence;

import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.core.ARSharedResources;
import java.util.*;
import javax.persistence.*;

@Entity
@Table(name = "component_reference")
// @SequenceGenerator(initialValue = 1, name = "idgen", sequenceName = "savedInstructionReferenceSeq", allocationSize =
// 1)
public class ComponentReferenceDTO extends BaseDTO {

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
    @JoinColumn(name = "component_instruction_id")
    private ComponentInstructionDTO componentInstructionDTO;

    public ComponentReferenceDTO() {
        super();
    }

    public ComponentReferenceDTO(int id) {
        super(id);
    }

    public ComponentReferenceDTO(ComponentInstructionDTO instruction) {
        componentInstructionDTO = instruction;
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

    public ComponentInstructionDTO getSavedBlockLoopInstructionDTO() {
        return componentInstructionDTO;
    }

    public void setSavedBlockLoopInstructionDTO(ComponentInstructionDTO componentInstructionDTO) {
        this.componentInstructionDTO = componentInstructionDTO;
    }

    public static List<ComponentReferenceDTO> createSavedReferencesFromInstructionForSavedInstruction(
            InstructionDTO instructionDTO, ComponentInstructionDTO componentInstructionDTO) {
        List<ReferenceDTO> referenceList = ARSharedResources.getInstance()
                .getEntityList(
                        ReferenceDTO.class,
                        reference -> reference.getBlockLoopInstructionDTO().getId() == instructionDTO.getId());
        List<ComponentReferenceDTO> list = new ArrayList<>();
        referenceList.forEach(
                reference -> list.add(copyFromReferenceForSavedInstruction(reference, componentInstructionDTO)));
        return list;
    }

    private static ComponentReferenceDTO copyFromReferenceForSavedInstruction(
            ReferenceDTO reference, ComponentInstructionDTO savedInstruction) {
        ComponentReferenceDTO saved = new ComponentReferenceDTO();
        saved.setValue(reference.getValue());
        saved.setReferenceType(reference.getReferenceType());
        saved.setSavedBlockLoopInstructionDTO(savedInstruction);
        return saved;
    }
}
