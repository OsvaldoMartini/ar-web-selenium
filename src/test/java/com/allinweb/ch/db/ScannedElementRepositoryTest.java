package com.allinweb.ch.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260704_ScannedElement;
import com.allinweb.ch.db.migrations.M20260724_ScannedElementPageScope;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannedElement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Repository behavior over in-memory SQLite: hashing, upsert semantics, and load. */
class ScannedElementRepositoryTest {

    private static final String ACCOUNTS_PAGE = "https://bank.example/accounts";
    private static final String PAYMENTS_PAGE = "https://bank.example/payments";

    private static Connection freshDb() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        new M20260704_ScannedElement().apply(conn, "TEXT");
        new M20260724_ScannedElementPageScope().apply(conn, "TEXT");
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
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(e));
            ScannedElementRepository.upsert(conn, 2, 6, 3, ACCOUNTS_PAGE, List.of(e)); // different bot job
            assertEquals(1, ScannedElementRepository.load(conn, 2, 5).size());
            assertEquals(1, ScannedElementRepository.load(conn, 2, 6).size());
        }
    }

    @Test
    void sameLocatorOnTwoPagesCreatesTwoPageScopedRows() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO continueButton = el("//button[@test-id='continue']", "continue", "Continue");

            ScannedElementRepository.UpsertResult accounts =
                    ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(continueButton));
            ScannedElementRepository.UpsertResult payments =
                    ScannedElementRepository.upsert(conn, 2, 5, 3, PAYMENTS_PAGE, List.of(continueButton));

            assertEquals(1, accounts.inserted());
            assertEquals(1, payments.inserted());
            List<ScannedElement> rows = ScannedElementRepository.load(conn, 2, 5);
            assertEquals(2, rows.size());
            assertNotEquals(rows.get(0).getPageKey(), rows.get(1).getPageKey());
            assertNotEquals(rows.get(0).getElementHash(), rows.get(1).getElementHash());
        }
    }

    @Test
    void rescanUpdatesOnlyTheMatchingPageObservation() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO continueButton = el("//button[@test-id='continue']", "continue", "Continue");
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(continueButton));
            ScannedElementRepository.upsert(conn, 2, 5, 3, PAYMENTS_PAGE, List.of(continueButton));
            ScannedElementRepository.UpsertResult rescan =
                    ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(continueButton));

            assertEquals(0, rescan.inserted());
            assertEquals(1, rescan.updated());
            assertEquals(
                    2,
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, ACCOUNTS_PAGE)
                            .get(0)
                            .getScanCount());
            assertEquals(
                    1,
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, PAYMENTS_PAGE)
                            .get(0)
                            .getScanCount());
        }
    }

    @Test
    void pageScopedLoadReturnsOnlyTheRequestedPage() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO accountsPrimary = el("//main//button[1]", "accounts-primary", "Continue");
            ElementDTO accountsSecondary = el("//main//button[2]", "accounts-secondary", "Cancel");
            ElementDTO paymentsPrimary = el("//main//button[1]", "accounts-primary", "Continue");

            ScannedElementRepository.upsert(
                    conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(accountsPrimary, accountsSecondary));
            ScannedElementRepository.upsert(conn, 2, 5, 3, PAYMENTS_PAGE, List.of(paymentsPrimary));

            List<ScannedElement> accounts =
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, ACCOUNTS_PAGE);
            List<ScannedElement> payments =
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, PAYMENTS_PAGE);
            assertEquals(2, accounts.size());
            assertEquals(1, payments.size());

            String accountsKey = ScannedPageIdentity.fromLiveUrl(ACCOUNTS_PAGE).pageKey();
            String paymentsKey = ScannedPageIdentity.fromLiveUrl(PAYMENTS_PAGE).pageKey();
            assertTrue(accounts.stream().allMatch(row -> accountsKey.equals(row.getPageKey())));
            assertTrue(payments.stream().allMatch(row -> paymentsKey.equals(row.getPageKey())));
        }
    }

    @Test
    void customXpathMutationAndRescanRemainPageLocal() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO continueButton = el("//button[@test-id='continue']", "continue", "Continue");
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(continueButton));
            ScannedElementRepository.upsert(conn, 2, 5, 3, PAYMENTS_PAGE, List.of(continueButton));

            continueButton.setCustomXPath("//accounts//button[@test-id='continue']");
            assertEquals(
                    1,
                    ScannedElementRepository.updateCustomXPath(
                            conn, 2, 5, ACCOUNTS_PAGE, continueButton));

            assertEquals(
                    "//accounts//button[@test-id='continue']",
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, ACCOUNTS_PAGE)
                            .get(0)
                            .getCustomXPath());
            assertNull(
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, PAYMENTS_PAGE)
                            .get(0)
                            .getCustomXPath());

            continueButton.setCustomXPath(null);
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(continueButton));
            assertEquals("//accounts//button[@test-id='continue']", continueButton.getCustomXPath());
            assertNull(
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, PAYMENTS_PAGE)
                            .get(0)
                            .getCustomXPath());
        }
    }

    @Test
    void customXpathUpdatesOnlyTheSelectedSameNameElementAndSurvivesRescan() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO first = el("//main//button[1]", null, "Continue");
            ElementDTO second = el("//aside//button[1]", null, "Continue");
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(first, second));

            first.setCustomXPath("//button[@test-id='primary-next']");
            assertEquals(
                    1,
                    ScannedElementRepository.updateCustomXPath(conn, 2, 5, ACCOUNTS_PAGE, first));

            ElementDTO stale = el("//footer//button[1]", null, "Continue");
            stale.setCustomXPath("//button[@test-id='forged']");
            assertEquals(
                    0,
                    ScannedElementRepository.updateCustomXPath(conn, 2, 5, ACCOUNTS_PAGE, stale),
                    "locator apply must not insert a forged or stale scanner row");

            // A subsequent raw scan carries no client override and must not erase it.
            first.setCustomXPath(null);
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(first, second));
            assertEquals("//button[@test-id='primary-next']", first.getCustomXPath());

            List<ScannedElement> rows = ScannedElementRepository.load(conn, 2, 5);
            ScannedElement selected = rows.stream()
                    .filter(row -> "//main//button[1]".equals(row.getXPath()))
                    .findFirst()
                    .orElseThrow();
            ScannedElement untouched = rows.stream()
                    .filter(row -> "//aside//button[1]".equals(row.getXPath()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("//button[@test-id='primary-next']", selected.getCustomXPath());
            assertNull(untouched.getCustomXPath());
        }
    }

    @Test
    void clientAliasMutationIsExactAndSurvivesRawRescan() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO accounts = el("//main//button[1]", null, "Continue");
            ElementDTO sibling = el("//aside//button[1]", null, "Continue");
            ElementDTO payments = el("//main//button[1]", null, "Continue");
            ElementDTO otherOrganization = el("//main//button[1]", null, "Continue");
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(accounts, sibling));
            ScannedElementRepository.upsert(conn, 2, 5, 3, PAYMENTS_PAGE, List.of(payments));
            ScannedElementRepository.upsert(conn, 3, 5, 3, ACCOUNTS_PAGE, List.of(otherOrganization));

            ScannedElementRepository.ClientNamedMutationResult renamed =
                    ScannedElementRepository.updateClientNamed(
                            conn, 2, 5, ACCOUNTS_PAGE, accounts, " Primary account ");
            assertEquals(1, renamed.affectedRows());
            assertEquals("Primary account", renamed.element().getClientNamed());

            ElementDTO rawRescan = el("//main//button[1]", null, "Continue");
            rawRescan.setClientNamed("stale client value");
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(rawRescan));
            assertEquals("Primary account", rawRescan.getClientNamed(),
                    "the outgoing scan DTO must be rehydrated from the registry alias");

            List<ScannedElement> accountRows =
                    ScannedElementRepository.loadByBotJobAndPage(conn, 5, ACCOUNTS_PAGE);
            assertEquals(
                    "Primary account",
                    accountRows.stream()
                            .filter(row -> accounts.getXPath().equals(row.getXPath()))
                            .findFirst()
                            .orElseThrow()
                            .getClientNamed());
            assertNull(accountRows.stream()
                    .filter(row -> sibling.getXPath().equals(row.getXPath()))
                    .findFirst()
                    .orElseThrow()
                    .getClientNamed());
            assertNull(ScannedElementRepository.loadByBotJobAndPage(conn, 5, PAYMENTS_PAGE)
                    .get(0)
                    .getClientNamed());
            assertNull(ScannedElementRepository.load(conn, 3, 5).get(0).getClientNamed());

            ElementDTO stale = el("//footer//button[1]", null, "Continue");
            assertEquals(
                    0,
                    ScannedElementRepository.updateClientNamed(
                                    conn, 2, 5, ACCOUNTS_PAGE, stale, "Forged")
                            .affectedRows());
        }
    }

    @Test
    void canonicalClientAliasClearsToSqlNull() throws Exception {
        try (Connection conn = freshDb()) {
            ElementDTO target = el("//button[@test-id='continue']", "continue", "Continue");
            ScannedElementRepository.upsert(conn, 2, 5, 3, ACCOUNTS_PAGE, List.of(target));
            assertEquals(
                    1,
                    ScannedElementRepository.updateClientNamed(
                                    conn, 2, 5, ACCOUNTS_PAGE, target, "Custom Continue")
                            .affectedRows());

            ScannedElementRepository.ClientNamedMutationResult cleared =
                    ScannedElementRepository.updateClientNamed(
                            conn, 2, 5, ACCOUNTS_PAGE, target, "Continue");
            assertEquals(1, cleared.affectedRows());
            assertNull(cleared.element().getClientNamed());
            assertNull(ScannedElementRepository.loadByBotJobAndPage(conn, 5, ACCOUNTS_PAGE)
                    .get(0)
                    .getClientNamed());
        }
    }
}
