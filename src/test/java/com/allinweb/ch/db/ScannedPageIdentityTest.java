package com.allinweb.ch.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannedPageIdentityTest {

    @Test
    void normalizesSchemeAndHostCaseAndRemovesDefaultPorts() {
        ScannedPageIdentity http =
                ScannedPageIdentity.fromLiveUrl("HTTP://BANK.EXAMPLE:80/accounts");
        ScannedPageIdentity https =
                ScannedPageIdentity.fromLiveUrl("HTTPS://BANK.EXAMPLE:443/accounts");

        assertEquals("http://bank.example/accounts", http.normalizedUrl());
        assertEquals(
                ScannedPageIdentity.fromLiveUrl("http://bank.example/accounts").pageKey(),
                http.pageKey());
        assertEquals("https://bank.example/accounts", https.normalizedUrl());
        assertEquals(
                ScannedPageIdentity.fromLiveUrl("https://bank.example/accounts").pageKey(),
                https.pageKey());
        assertTrue(http.pageKey().matches("url-v1:[0-9a-f]{64}"));
        assertTrue(https.pageKey().matches("url-v1:[0-9a-f]{64}"));
    }

    @Test
    void preservesQueryOrderAndKeepsDifferentlyOrderedQueriesDistinct() {
        ScannedPageIdentity first =
                ScannedPageIdentity.fromLiveUrl(
                        "https://bank.example/payments?currency=CHF&account=42");
        ScannedPageIdentity reordered =
                ScannedPageIdentity.fromLiveUrl(
                        "https://bank.example/payments?account=42&currency=CHF");

        assertEquals(
                "https://bank.example/payments?currency=CHF&account=42",
                first.normalizedUrl());
        assertEquals(
                "https://bank.example/payments?account=42&currency=CHF",
                reordered.normalizedUrl());
        assertNotEquals(first.pageKey(), reordered.pageKey());
    }

    @Test
    void preservesDuplicateQueryParametersAndDifferentValues() {
        ScannedPageIdentity first = ScannedPageIdentity.fromLiveUrl(
                "https://bank.example/accounts?id=1&id=2&empty=");
        ScannedPageIdentity reordered = ScannedPageIdentity.fromLiveUrl(
                "https://bank.example/accounts?id=2&id=1&empty=");
        ScannedPageIdentity differentValue = ScannedPageIdentity.fromLiveUrl(
                "https://bank.example/accounts?id=1&id=3&empty=");

        assertEquals(
                "https://bank.example/accounts?id=1&id=2&empty=",
                first.normalizedUrl());
        assertNotEquals(first.pageKey(), reordered.pageKey());
        assertNotEquals(first.pageKey(), differentValue.pageKey());
    }

    @Test
    void removesTrailingSlashesWithoutChangingTheRootPage() {
        assertEquals(
                ScannedPageIdentity.fromLiveUrl("https://bank.example/accounts").pageKey(),
                ScannedPageIdentity.fromLiveUrl("https://bank.example/accounts/")
                        .pageKey());
        assertEquals(
                "https://bank.example/",
                ScannedPageIdentity.fromLiveUrl("https://bank.example/").normalizedUrl());
        assertNotEquals(
                ScannedPageIdentity.fromLiveUrl("https://bank.example/accounts").pageKey(),
                ScannedPageIdentity.fromLiveUrl("https://bank.example/accounts//")
                        .pageKey());
    }

    @Test
    void removesDotSegmentsWithoutCollapsingRepeatedSeparators() {
        assertEquals(
                "https://bank.example/payments//confirm",
                ScannedPageIdentity.fromLiveUrl(
                                "https://bank.example/accounts/../payments//./confirm")
                        .normalizedUrl());
    }

    @Test
    void preservesSpaFragmentsAsDistinctPageIdentities() {
        ScannedPageIdentity accounts =
                ScannedPageIdentity.fromLiveUrl("https://bank.example/app#/accounts");
        ScannedPageIdentity payments =
                ScannedPageIdentity.fromLiveUrl("https://bank.example/app#/payments");

        assertEquals("https://bank.example/app#/accounts", accounts.normalizedUrl());
        assertEquals("https://bank.example/app#/payments", payments.normalizedUrl());
        assertNotEquals(accounts.pageKey(), payments.pageKey());
    }

    @Test
    void keepsDifferentPathsAsDifferentPages() {
        ScannedPageIdentity login =
                ScannedPageIdentity.fromLiveUrl(
                        "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password");
        ScannedPageIdentity dashboard =
                ScannedPageIdentity.fromLiveUrl(
                        "https://www.inlinea.ch/bscch/wb/ui/trading/forex/new");

        assertNotEquals(login.normalizedUrl(), dashboard.normalizedUrl());
        assertNotEquals(login.pageKey(), dashboard.pageKey());
    }

    @Test
    void storedLegacyNullAndBlankUrlsUseTheExplicitUnknownPage() {
        ScannedPageIdentity missing = ScannedPageIdentity.fromStoredUrl(null);
        ScannedPageIdentity blank = ScannedPageIdentity.fromStoredUrl("   ");

        assertEquals("arweb://unknown-page", missing.normalizedUrl());
        assertEquals(missing, blank);
        assertTrue(missing.pageKey().matches("url-v1:[0-9a-f]{64}"));
    }

    @Test
    void storedLegacyMalformedValuesRemainDistinct() {
        ScannedPageIdentity first =
                ScannedPageIdentity.fromStoredUrl("https://bank.example/account one");
        ScannedPageIdentity second =
                ScannedPageIdentity.fromStoredUrl("https://bank.example/account two");

        assertEquals("https://bank.example/account one", first.normalizedUrl());
        assertEquals("https://bank.example/account two", second.normalizedUrl());
        assertNotEquals(first.pageKey(), second.pageKey());
    }

    @Test
    void storedValidUrlUsesTheSameKeyAsALiveUrl() {
        String url = "HTTPS://WWW.INLINEA.CH:443/bscch/wb/ui/trading/forex/new/";

        ScannedPageIdentity stored = ScannedPageIdentity.fromStoredUrl(url);
        ScannedPageIdentity live = ScannedPageIdentity.fromLiveUrl(url);

        assertEquals(live.normalizedUrl(), stored.normalizedUrl());
        assertEquals(live.pageKey(), stored.pageKey());
    }

    @Test
    void liveUrlRejectsBlankNonHttpAndMalformedValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScannedPageIdentity.fromLiveUrl(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScannedPageIdentity.fromLiveUrl("   "));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScannedPageIdentity.fromLiveUrl("file:///tmp/page.html"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScannedPageIdentity.fromLiveUrl("https://bank.example/account one"));
    }
}
