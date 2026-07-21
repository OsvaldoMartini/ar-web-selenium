package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260721_PageScannerProfile;
import com.allinweb.ch.model.ScannerSearchProfile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PageScannerProfileRepositoryTest {

    private Connection anchor;
    private String databaseUrl;
    private PageScannerProfileRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseUrl = "jdbc:sqlite:file:page-scanner-profile-repository-"
                + UUID.randomUUID() + "?mode=memory&cache=shared";
        anchor = DriverManager.getConnection(databaseUrl);
        new M20260721_PageScannerProfile().apply(anchor, "TEXT");
        repository = new PageScannerProfileRepository(() -> DriverManager.getConnection(databaseUrl));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (anchor != null) {
            anchor.close();
        }
    }

    @Test
    void createsUpdatesOrdersFindsAndDeletesProfilesThroughInjectedConnections() throws Exception {
        int createdId = repository.insert(
                new ScannerSearchProfile(0, "custom", "Custom", "button, attr:qa-hook", 5, false));
        assertTrue(createdId > 0);

        List<ScannerSearchProfile> ordered = repository.list();
        assertEquals("custom", ordered.get(0).key());
        assertEquals(8, ordered.size());
        assertEquals(createdId, repository.findByKey("custom").orElseThrow().id());

        ScannerSearchProfile updated = new ScannerSearchProfile(
                createdId, "custom", "Custom updated", "input, attr:data-hook", 80, false);
        assertTrue(repository.update(updated));
        assertEquals("Custom updated", repository.findById(createdId).orElseThrow().label());
        assertEquals("custom", repository.list().get(7).key());

        assertTrue(repository.delete(createdId));
        assertFalse(repository.findById(createdId).isPresent());
        assertEquals(7, repository.list().size());
    }
}
