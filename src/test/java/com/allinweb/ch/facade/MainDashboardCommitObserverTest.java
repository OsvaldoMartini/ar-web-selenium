package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MainDashboardCommitObserverTest {

    @Test
    void committedDeletionPublishesAnImmutableOwnerSet() {
        AtomicReference<List<Integer>> delivered = new AtomicReference<>();

        MainDashboardService.notifyCommittedDeletion(delivered::set, List.of(41, 42));

        assertEquals(List.of(41, 42), delivered.get());
        assertThrows(UnsupportedOperationException.class, () -> delivered.get().add(43));
    }

    @Test
    void observerFailureCannotRewriteAnAlreadyCommittedDeleteOutcome() {
        MainDashboardService.notifyCommittedDeletion(
                ignored -> {
                    throw new IllegalStateException("cleanup failed");
                },
                List.of(42));
    }

    @Test
    void singleDeleteInvalidatesImmediatelyAfterCommitAndBeforeReload() {
        JsonObject request = new JsonObject();
        request.addProperty("botJobId", 42);
        List<String> events = new ArrayList<>();

        var response = MainDashboardService.getInstance().deleteBotJob(
                request,
                ids -> events.add("invalidate-" + ids),
                id -> {
                    events.add("commit-" + id);
                    return null;
                },
                () -> {
                    events.add("reload");
                    return null;
                });

        assertTrue(Boolean.TRUE.equals(response.get("committed")));
        assertEquals(
                List.of("commit-42", "invalidate-[42]", "reload"),
                events);
    }

    @Test
    void bulkDeleteInvalidatesImmediatelyAfterCommitAndBeforeReload() {
        JsonObject request = new JsonObject();
        request.addProperty("contractVersion", 1);
        request.addProperty("requestId", "bulk-delete");
        JsonArray ids = new JsonArray();
        ids.add(41);
        ids.add(42);
        request.add("botJobIds", ids);
        List<String> events = new ArrayList<>();

        var response = MainDashboardService.getInstance().deleteBotJobs(
                request,
                deleted -> events.add("invalidate-" + deleted),
                deleted -> {
                    events.add("commit-" + deleted);
                    return null;
                },
                () -> {
                    events.add("reload");
                    return null;
                });

        assertTrue(Boolean.TRUE.equals(response.get("committed")));
        assertEquals(
                List.of(
                        "commit-[41, 42]",
                        "invalidate-[41, 42]",
                        "reload"),
                events);
    }
}
