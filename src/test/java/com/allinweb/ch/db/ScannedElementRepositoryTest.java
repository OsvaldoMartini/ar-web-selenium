package com.allinweb.ch.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260704_ScannedElement;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannedElement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Repository behavior over in-memory SQLite: hashing, upsert semantics, and load. */
class ScannedElementRepositoryTest {

    private static Connection freshDb() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        new M20260704_ScannedElement().apply(conn, "TEXT");
        return conn;
    }

    private static ElementDTO el(String xpath, String id, String someText) {
        ElementDTO e = new ElementDTO();
        e.setTagName("button");
        e.setTypeElement("button");
        e.setXPath(xpath);
        e.setAttribId(id);
        e.setCssSelector(id == null ? null : "button#" + id);
        e.setSomeText(someText);
        e.setDefinedName(someText == null ? null : someText.toLowerCase().replace(' ', '_'));
        return e;
    }

    @Test
    void hashDistinguishesSameNameDifferentXpath() {
        String h1 = ScannedElementRepository.hashOf(el("//*[@id='a']", "a", "Rifiuta tutti"));
        String h2 = ScannedElementRepository.hashOf(el("//*[@id='b']", "b", "Rifiuta tutti"));
        assertNotEquals(h1, h2, "same name but different xpath/id must hash differently");
        // Stable: same identity -> same hash.
        assertEquals(h1, ScannedElementRepository.hashOf(el("//*[@id='a']", "a", "Rifiuta tutti")));
    }

    @Test
    void upsertInsertsThenUpdatesAndKeepsDistinctElements() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO reject =
                    el("//*[@id='onetrust-reject-all-handler']", "onetrust-reject-all-handler", "Rifiuta tutti");
            ElementDTO accept = el(
                    "//*[@id='onetrust-accept-btn-handler']", "onetrust-accept-btn-handler", "Accetta tutti i cookie");

            // First scan: two distinct inserts.
            ScannedElementRepository.UpsertResult r1 =
                    ScannedElementRepository.upsert(conn, 2, 5, 3, "https://bank/x", List.of(reject, accept));
            assertEquals(2, r1.inserted());
            assertEquals(0, r1.updated());

            // Re-scan same page: both match by hash -> updates, no new rows.
            ScannedElementRepository.UpsertResult r2 =
                    ScannedElementRepository.upsert(conn, 2, 5, 3, "https://bank/x", List.of(reject, accept));
            assertEquals(0, r2.inserted());
            assertEquals(2, r2.updated());

            List<ScannedElement> rows = ScannedElementRepository.load(conn, 2, 5);
            assertEquals(2, rows.size(), "still two distinct rows after re-scan");
            for (ScannedElement s : rows) {
                assertEquals(2, s.getScanCount(), "scan_count should bump to 2 after re-scan");
                assertTrue(s.getElementHash() != null && !s.getElementHash().isBlank());
            }

            // A third element sharing the SAME name as reject but a different xpath -> new row.
            ElementDTO rejectDup = el("//footer//button[1]", "footer-reject", "Rifiuta tutti");
            ScannedElementRepository.upsert(conn, 2, 5, 3, "https://bank/x", List.of(rejectDup));
            assertEquals(3, ScannedElementRepository.load(conn, 2, 5).size());
        }
    }

    @Test
    void scopeIsolation() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO e = el("//*[@id='x']", "x", "X");
            ScannedElementRepository.upsert(conn, 2, 5, 3, "u", List.of(e));
            ScannedElementRepository.upsert(conn, 2, 6, 3, "u", List.of(e)); // different bot job
            assertEquals(1, ScannedElementRepository.load(conn, 2, 5).size());
            assertEquals(1, ScannedElementRepository.load(conn, 2, 6).size());
        }
    }
}
