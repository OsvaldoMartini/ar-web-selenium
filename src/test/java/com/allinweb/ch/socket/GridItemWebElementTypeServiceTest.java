package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateTransaction.UpdateResult;
import com.allinweb.ch.model.GridItemWebElementTypeContracts.WebElementType;
import org.junit.jupiter.api.Test;

class GridItemWebElementTypeServiceTest {

    @Test
    void freshChangedMutationPreparesSnapshotAndNotifiesVariables() {
        GridItemWebElementTypeService.PublicationPlan plan =
                GridItemWebElementTypeService.publicationPlan(result(true, false));

        assertTrue(plan.prepareSnapshot());
        assertTrue(plan.notifyVariables());
    }

    @Test
    void duplicateReplayPreparesSnapshotWithoutSecondVariablesNotification() {
        GridItemWebElementTypeService.PublicationPlan plan =
                GridItemWebElementTypeService.publicationPlan(result(true, true));

        assertTrue(plan.prepareSnapshot());
        assertFalse(plan.notifyVariables());
    }

    @Test
    void unchangedMutationDoesNotPublishOrNotify() {
        GridItemWebElementTypeService.PublicationPlan plan =
                GridItemWebElementTypeService.publicationPlan(result(false, false));

        assertFalse(plan.prepareSnapshot());
        assertFalse(plan.notifyVariables());
    }

    private static UpdateResult result(boolean changed, boolean duplicate) {
        return new UpdateResult(
                OwnerKey.botJob(2, 32),
                7L,
                "type-request",
                1728,
                WebElementType.INPUT,
                WebElementType.OUTPUT,
                "O:Account",
                10L,
                changed ? 11L : 10L,
                "a".repeat(64),
                changed,
                duplicate);
    }
}
