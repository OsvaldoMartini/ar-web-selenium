package com.allinweb.ch.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The two link sets for one requirement — used by the Requirements tab's
 * right-column checkbox lists.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequirementLinksDTO {
    private Integer requirementId;
    private List<Integer> useCaseIds = new ArrayList<>();
    private List<Integer> flowIds = new ArrayList<>();
}
