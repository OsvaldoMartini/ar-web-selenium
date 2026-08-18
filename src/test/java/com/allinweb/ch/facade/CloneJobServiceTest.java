package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.*;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloneJobServiceTest {
    private final PerformLists lists = PerformLists.getInstance();
    private List<BotJobLoadDTO> original;

    @BeforeEach void snapshot() {
        original = new ArrayList<>(lists.getQuickBotJobs());
        lists.getQuickBotJobs().clear();
        BotJobLoadDTO existing = new BotJobLoadDTO();
        existing.setId(41);
        existing.setName("Existing Job");
        lists.getQuickBotJobs().add(existing);
    }

    @AfterEach void restore() {
        lists.getQuickBotJobs().clear();
        lists.getQuickBotJobs().addAll(original);
    }

    @Test void rejectsBlankName() {
        JsonObject body = new JsonObject();
        body.addProperty("name", "   ");
        Map<String, Object> response = CloneJobService.getInstance().validateName(body);
        assertEquals(false, response.get("ok"));
        assertEquals("New Bot Job name is required", response.get("message"));
    }

    @Test void rejectsDuplicateNameIgnoringCase() {
        JsonObject body = new JsonObject();
        body.addProperty("name", "existing job");
        Map<String, Object> response = CloneJobService.getInstance().validateName(body);
        assertEquals(false, response.get("ok"));
        assertEquals("Bot Job name already exists", response.get("message"));
    }

    @Test void acceptsAvailableName() {
        JsonObject body = new JsonObject();
        body.addProperty("name", "New Job");
        Map<String, Object> response = CloneJobService.getInstance().validateName(body);
        assertEquals(true, response.get("ok"));
    }
}
