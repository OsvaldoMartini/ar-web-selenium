package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.allinweb.ch.driver.PlaywrightTestSupport;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Exhaustive, non-submitting Playwright coverage for the authenticated InLinea forex entry page.
 *
 * <p>The suite is disabled unless explicitly opted in. It never selects an account and never clicks
 * Avanti, confirmation, or submit controls. Export an authenticated Playwright storage state first,
 * then run:
 *
 * <pre>
 * mvn -Dtest=InlineaForexPlaywrightIT -DinlineaForexIT=true \
 *     -DinlineaForexStorageState=C:\\secure\\inlinea-storage-state.json test
 * </pre>
 *
 * <p>Use {@code -DinlineaForexHeadless=false} for a visible browser. The exchange amount is always
 * {@value #DEFAULT_AMOUNT}; currencies and valid directed pairs are discovered from the live
 * Angular Material overlays. The known production universe defaults to CAD, CHF, EUR, GBP, and USD;
 * intentionally override it with {@code -DinlineaForexExpectedCurrencies=CAD,EUR,...} if the bank
 * changes the product.
 */
@EnabledIfSystemProperty(named = "inlineaForexIT", matches = "(?i)true")
@Isolated("Uses a live authenticated banking session")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InlineaForexPlaywrightIT {

    private static final String TARGET_URL = "https://www.inlinea.ch/bscch/wb/ui/trading/forex/new";
    private static final String TARGET_HOST = "www.inlinea.ch";
    private static final String TARGET_PATH = "/bscch/wb/ui/trading/forex/new";
    private static final String STORAGE_STATE_PROPERTY = "inlineaForexStorageState";
    private static final String EXPECTED_CURRENCIES_PROPERTY = "inlineaForexExpectedCurrencies";
    private static final String DEFAULT_EXPECTED_CURRENCIES = "CAD,CHF,EUR,GBP,USD";
    private static final String DEFAULT_AMOUNT = "1";
    private static final double ACTION_TIMEOUT_MS = 20_000;
    private static final double NAVIGATION_TIMEOUT_MS = 45_000;

    private static final String DETAILS_TEST_ID_PREFIX =
            "web-banking-trading-forex-entry-banklet.trading-forex-entry.forex-trade-details.";
    private static final String DEAL_RATE_SELECTOR =
            "div[test-id='web-banking-trading-forex-entry-banklet.forex-deal-rate.deal-rate'] p";
    private static final String DEAL_RATE_TIMESTAMP_SELECTOR =
            "div[test-id='web-banking-trading-forex-entry-banklet.forex-deal-rate.deal-rate'] section > span";
    private static final String SWAP_SELECTOR =
            "button[test-id='web-banking-trading-forex-entry-banklet.trading-forex-entry.forex-trade-details.swipe-currency']";
    private static final String VISIBLE_OPTION_OVERLAY_SELECTOR =
            "div.cdk-overlay-pane:visible:has(mat-option[role='option']:visible)";
    private static final Pattern OPTION_TEST_ID_CURRENCY =
            Pattern.compile("(?:^|[-_.])([A-Z]{3})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_TOKEN = Pattern.compile("\\b([A-Z]{3})\\b");
    private static final Pattern QUOTE_RESPONSE_URL =
            Pattern.compile("(?i)(forex|exchange|fx[-_/]?rate|deal[-_/]?rate|quote|price|trading)");

    private final AtomicReference<String> unsafeNavigation = new AtomicReference<>();
    private final AtomicLong quoteResponseSequence = new AtomicLong();
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void openAuthenticatedForexEntryPage() {
        Path storageState = requireStorageState();
        try {
            playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(Boolean.parseBoolean(System.getProperty("inlineaForexHeadless", "true")));
            PlaywrightTestSupport.locateBrowserExecutable().ifPresent(launchOptions::setExecutablePath);
            browser = playwright.chromium().launch(launchOptions);
            context = browser.newContext(new Browser.NewContextOptions()
                    .setStorageStatePath(storageState)
                    .setLocale("it-CH")
                    .setViewportSize(1440, 1000));
            page = context.newPage();
            page.setDefaultTimeout(ACTION_TIMEOUT_MS);
            page.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
            page.navigate(
                    TARGET_URL,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(NAVIGATION_TIMEOUT_MS));

            try {
                currencyControl(Side.SELL)
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(NAVIGATION_TIMEOUT_MS));
                currencyControl(Side.BUY)
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(NAVIGATION_TIMEOUT_MS));
            } catch (PlaywrightException error) {
                throw new AssertionError(
                        "Authenticated forex controls were not visible. Current URL is "
                                + page.url()
                                + ". The storage state supplied by -D"
                                + STORAGE_STATE_PROPERTY
                                + " may be expired or belong to a user without forex access.",
                        error);
            }
            assertTrue(isEntryUrl(page.url()), "Authentication redirected away from the forex entry page: " + page.url());
            installFinalActionBlocker();
            installQuoteMutationMarker();
            page.onResponse(this::observeQuoteResponse);
            armNavigationGuard();
            assertSafeEntryState("initial page");
        } catch (RuntimeException | AssertionError error) {
            closeResources();
            throw error;
        }
    }

    @AfterAll
    void closeBrowser() {
        closeResources();
    }

    @Test
    void exercisesEveryLiveDirectedPairLinkedSelectionsAndRepresentativeSwaps() {
        LinkResult sellLink = verifySelectingOppositeCurrencyIsLinked(Side.SELL);
        LinkResult buyLink = verifySelectingOppositeCurrencyIsLinked(Side.BUY);
        assertTrue(sellLink != LinkResult.NOT_OBSERVED, "Sell-side linked behavior was not observed");
        assertTrue(buyLink != LinkResult.NOT_OBSERVED, "Buy-side linked behavior was not observed");

        Set<String> discoveredCurrencies = discoverCurrencyUniverse();
        Set<String> expectedCurrencies = expectedCurrencies();
        assertEquals(
                expectedCurrencies,
                discoveredCurrencies,
                "The dynamically discovered currency universe is incomplete or the product changed. "
                        + "If the product change is intentional, override -D"
                        + EXPECTED_CURRENCIES_PROPERTY);

        Deque<String> sellsToVisit = new ArrayDeque<>(discoveredCurrencies);
        Set<String> visitedSells = new LinkedHashSet<>();
        Map<String, Collection<String>> buysBySell = new LinkedHashMap<>();
        Set<ForexPairMatrix.Pair> exercisedPairs = new LinkedHashSet<>();
        Set<String> exercisedSwapCases = new LinkedHashSet<>();

        while (!sellsToVisit.isEmpty()) {
            String sell = sellsToVisit.removeFirst();
            if (!visitedSells.add(sell)) {
                continue;
            }

            ensureCurrency(Side.SELL, sell);
            assertEquals(sell, selectedCurrency(Side.SELL));

            OptionSnapshot buySnapshot = openCurrencyOptions(Side.BUY);
            LinkedHashSet<String> buys = new LinkedHashSet<>(buySnapshot.enabledCurrencies());
            String currentlySelectedBuy = selectedCurrency(Side.BUY);
            buys.add(currentlySelectedBuy);
            closeCurrencyOverlay();
            buys.remove(sell);
            assertFalse(buys.isEmpty(), "No enabled buy currencies were offered while selling " + sell);
            buysBySell.put(sell, List.copyOf(buys));

            addNewCurrencies(discoveredCurrencies, sellsToVisit, buySnapshot.allCurrencies());
            addNewCurrencies(discoveredCurrencies, sellsToVisit, buys);

            for (String buy : buys) {
                ensureCurrency(Side.BUY, buy);
                assertCurrentPair(sell, buy, "after choosing " + sell + "->" + buy);
                setDefaultSellAmountAndAwaitQuote(sell, buy);

                ForexPairMatrix.Pair pair = new ForexPairMatrix.Pair(sell, buy);
                exercisedPairs.add(pair);
                String swapCase = ForexPairMatrix.swapCoverageKey(pair);
                if (exercisedSwapCases.add(swapCase)) {
                    exerciseSwapAndRestore(pair);
                }
                assertSafeEntryState("after pair " + pair);
            }
        }

        List<ForexPairMatrix.Pair> expectedPairs = ForexPairMatrix.directedPairs(buysBySell);
        int completeDirectedPairCount = expectedCurrencies.size() * (expectedCurrencies.size() - 1);
        assertEquals(
                completeDirectedPairCount,
                expectedPairs.size(),
                "Expected the complete directed currency matrix for " + expectedCurrencies
                        + ", but the linked overlays exposed " + buysBySell);
        for (String sell : expectedCurrencies) {
            LinkedHashSet<String> expectedBuys = new LinkedHashSet<>(expectedCurrencies);
            expectedBuys.remove(sell);
            assertEquals(
                    expectedBuys,
                    new LinkedHashSet<>(buysBySell.getOrDefault(sell, List.of())),
                    "Incomplete buy options while selling " + sell);
        }
        assertEquals(
                new LinkedHashSet<>(expectedPairs),
                exercisedPairs,
                "Every directed pair exposed by the linked currency controls must be exercised");
        assertEquals(
                expectedPairs.stream()
                        .map(ForexPairMatrix::swapCoverageKey)
                        .collect(java.util.stream.Collectors.toSet()),
                exercisedSwapCases,
                "Swap must be exercised once for every unordered live pair");
        assertSafeEntryState("completed matrix");

        System.out.printf(
                Locale.ROOT,
                "InLinea forex matrix complete: currencies=%s, directedPairs=%d, swapCases=%d, sellLink=%s, buyLink=%s%n",
                discoveredCurrencies,
                exercisedPairs.size(),
                exercisedSwapCases.size(),
                sellLink,
                buyLink);
    }

    private LinkResult verifySelectingOppositeCurrencyIsLinked(Side side) {
        String oppositeBefore = selectedCurrency(side.opposite());
        OptionSnapshot snapshot = openCurrencyOptions(side);
        CurrencyOption oppositeOption = snapshot.options().get(oppositeBefore);
        if (oppositeOption == null || !oppositeOption.enabled()) {
            closeCurrencyOverlay();
            assertNotEquals(
                    selectedCurrency(side),
                    oppositeBefore,
                    "The controls already contained the same currency before linked-selection verification");
            assertSafeEntryState(side + " linked-option constraint");
            return LinkResult.CONSTRAINED;
        }

        oppositeOption.locator().click();
        waitForOverlayToClose();
        await(
                side + " to select " + oppositeBefore + " and update the other side",
                () -> oppositeBefore.equals(selectedCurrencyOrEmpty(side))
                        && !oppositeBefore.equals(selectedCurrencyOrEmpty(side.opposite())));
        assertEquals(oppositeBefore, selectedCurrency(side));
        assertNotEquals(
                oppositeBefore,
                selectedCurrency(side.opposite()),
                "Selecting the opposite currency must update the linked control so currencies stay distinct");
        assertSafeEntryState(side + " linked-option update");
        return LinkResult.UPDATED;
    }

    private Set<String> discoverCurrencyUniverse() {
        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        currencies.add(selectedCurrency(Side.SELL));
        currencies.add(selectedCurrency(Side.BUY));
        for (Side side : Side.values()) {
            OptionSnapshot snapshot = openCurrencyOptions(side);
            currencies.addAll(snapshot.allCurrencies());
            closeCurrencyOverlay();
        }
        return currencies;
    }

    private void ensureCurrency(Side side, String requestedCurrency) {
        String target = ForexPairMatrix.normalize(requestedCurrency);
        if (target.equals(selectedCurrency(side))) {
            return;
        }

        String oppositeBefore = selectedCurrency(side.opposite());
        OptionSnapshot snapshot = openCurrencyOptions(side);
        CurrencyOption option = snapshot.options().get(target);
        if (option == null || !option.enabled()) {
            closeCurrencyOverlay();
            if (target.equals(oppositeBefore)) {
                swapCurrencies();
                assertEquals(target, selectedCurrency(side));
                return;
            }
            fail("Currency " + target + " was discovered but is not enabled in the " + side
                    + " overlay. Enabled options: " + snapshot.enabledCurrencies());
        }

        option.locator().click();
        waitForOverlayToClose();
        await(
                side + " currency " + target,
                () -> target.equals(selectedCurrencyOrEmpty(side))
                        && !target.equals(selectedCurrencyOrEmpty(side.opposite())));
        assertEquals(target, selectedCurrency(side));
        assertNotEquals(target, selectedCurrency(side.opposite()), "Linked currencies must remain distinct");
        if (target.equals(oppositeBefore)) {
            assertNotEquals(
                    oppositeBefore,
                    selectedCurrency(side.opposite()),
                    "Selecting the other side's currency must update that linked side");
        }
        assertSafeEntryState("select " + target + " on " + side);
    }

    private QuoteSnapshot setDefaultSellAmountAndAwaitQuote(String sell, String buy) {
        Locator sellAmount = amountControl(Side.SELL);
        QuoteSnapshot beforeFill = quoteSnapshot();
        sellAmount.fill(DEFAULT_AMOUNT);
        sellAmount.press("Tab");
        await(
                "a post-fill quote calculation for " + sell + "->" + buy,
                () -> isOne(inputNumber(amountControl(Side.SELL)))
                        && isPositive(inputNumber(amountControl(Side.BUY)))
                        && dealRateContains(sell, buy)
                        && quoteAdvancedSince(beforeFill));

        assertTrue(isOne(inputNumber(sellAmount)), "Sell amount must use the requested default of 1");
        assertTrue(isPositive(inputNumber(amountControl(Side.BUY))), "Computed buy amount must be positive");
        assertDealRateContains(sell, buy);
        QuoteSnapshot afterFill = quoteSnapshot();
        assertTrue(
                quoteAdvancedSince(beforeFill, afterFill),
                "Quote did not advance after filling amount 1 for " + sell + "->" + buy
                        + ". Before=" + beforeFill.describe() + ", after=" + afterFill.describe());
        return afterFill;
    }

    private void exerciseSwapAndRestore(ForexPairMatrix.Pair pair) {
        QuoteSnapshot beforeSwap = quoteSnapshot();
        swapCurrencies();
        assertCurrentPair(pair.buy(), pair.sell(), "after swapping " + pair);
        QuoteSnapshot afterSwappedFill = setDefaultSellAmountAndAwaitQuote(pair.buy(), pair.sell());
        assertTrue(
                quoteAdvancedSince(beforeSwap, afterSwappedFill),
                "Swap did not produce a fresh reversed quote for " + pair
                        + ". Before=" + beforeSwap.describe() + ", after=" + afterSwappedFill.describe());
        assertTrue(
                isOne(inputNumber(amountControl(Side.SELL))),
                "The swapped direction must also use the default sell amount 1");
        assertSafeEntryState("after swapping " + pair);

        QuoteSnapshot beforeRestore = quoteSnapshot();
        swapCurrencies();
        assertCurrentPair(pair.sell(), pair.buy(), "after restoring " + pair);
        QuoteSnapshot afterRestore = setDefaultSellAmountAndAwaitQuote(pair.sell(), pair.buy());
        assertTrue(
                quoteAdvancedSince(beforeRestore, afterRestore),
                "Restoring the pair did not produce a fresh quote for " + pair);
    }

    private void swapCurrencies() {
        String sellBefore = selectedCurrency(Side.SELL);
        String buyBefore = selectedCurrency(Side.BUY);
        page.locator(SWAP_SELECTOR).click();
        await(
                "swap " + sellBefore + " and " + buyBefore,
                () -> buyBefore.equals(selectedCurrencyOrEmpty(Side.SELL))
                        && sellBefore.equals(selectedCurrencyOrEmpty(Side.BUY)));
        assertEquals(buyBefore, selectedCurrency(Side.SELL));
        assertEquals(sellBefore, selectedCurrency(Side.BUY));
        assertSafeEntryState("currency swap");
    }

    private OptionSnapshot openCurrencyOptions(Side side) {
        closeCurrencyOverlay();
        currencyControl(side).click();
        Locator overlays = page.locator(VISIBLE_OPTION_OVERLAY_SELECTOR);
        try {
            overlays.first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(ACTION_TIMEOUT_MS));
        } catch (PlaywrightException error) {
            throw new AssertionError("No visible CDK option overlay opened for " + side, error);
        }
        assertEquals(1, overlays.count(), "Exactly one visible CDK option overlay must be active");

        Locator options = overlays.locator("mat-option[role='option']:visible");
        assertTrue(options.count() > 0, "The visible " + side + " overlay did not contain currency options");
        LinkedHashMap<String, CurrencyOption> currencies = new LinkedHashMap<>();
        for (int index = 0; index < options.count(); index++) {
            Locator option = options.nth(index);
            String currency = optionCurrency(option);
            boolean enabled = option.isEnabled() && !"true".equalsIgnoreCase(option.getAttribute("aria-disabled"));
            CurrencyOption duplicate = currencies.putIfAbsent(currency, new CurrencyOption(currency, enabled, option));
            assertNull(duplicate, "Duplicate currency " + currency + " in the active " + side + " overlay");
        }
        return new OptionSnapshot(currencies);
    }

    private String optionCurrency(Locator option) {
        String testId = option.getAttribute("test-id");
        if (testId != null) {
            Matcher suffix = OPTION_TEST_ID_CURRENCY.matcher(testId.trim());
            if (suffix.find()) {
                return ForexPairMatrix.normalize(suffix.group(1));
            }
        }

        String text = option.innerText().trim().toUpperCase(Locale.ROOT);
        Matcher token = ISO_TOKEN.matcher(text);
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        while (token.find()) {
            matches.add(token.group(1));
        }
        if (matches.size() != 1) {
            throw new AssertionError("Could not resolve one ISO currency from option test-id='"
                    + testId + "', text='" + text + "'");
        }
        return matches.iterator().next();
    }

    private void closeCurrencyOverlay() {
        if (page == null || page.isClosed()) {
            return;
        }
        if (page.locator(VISIBLE_OPTION_OVERLAY_SELECTOR).count() > 0) {
            page.keyboard().press("Escape");
            waitForOverlayToClose();
        }
    }

    private void waitForOverlayToClose() {
        await(
                "currency overlay to close",
                () -> page.locator(VISIBLE_OPTION_OVERLAY_SELECTOR).count() == 0);
    }

    private void assertCurrentPair(String sell, String buy, String context) {
        assertEquals(sell, selectedCurrency(Side.SELL), context + " (sell)");
        assertEquals(buy, selectedCurrency(Side.BUY), context + " (buy)");
        assertNotEquals(sell, buy, context + " must keep currencies distinct");
    }

    private void assertDealRateContains(String sell, String buy) {
        String rate = page.locator(DEAL_RATE_SELECTOR).innerText();
        assertTrue(
                containsIso(rate, sell) && containsIso(rate, buy),
                "Deal rate must contain both " + sell + " and " + buy + ", but was: " + rate);
    }

    private boolean dealRateContains(String sell, String buy) {
        Locator rate = page.locator(DEAL_RATE_SELECTOR);
        if (rate.count() != 1 || !rate.isVisible()) {
            return false;
        }
        String text = rate.innerText();
        return containsIso(text, sell) && containsIso(text, buy);
    }

    private QuoteSnapshot quoteSnapshot() {
        Locator rate = page.locator(DEAL_RATE_SELECTOR);
        Locator timestamp = page.locator(DEAL_RATE_TIMESTAMP_SELECTOR);
        Object rawDomVersion = page.evaluate("() => window.__inlineaForexQuoteMarker?.version || 0");
        long domVersion = rawDomVersion instanceof Number number ? number.longValue() : 0L;
        return new QuoteSnapshot(
                rate.count() == 1 ? rate.innerText().trim() : "",
                timestamp.count() == 1 ? timestamp.innerText().trim() : "",
                amountControl(Side.SELL).inputValue(),
                amountControl(Side.BUY).inputValue(),
                domVersion,
                quoteResponseSequence.get());
    }

    private boolean quoteAdvancedSince(QuoteSnapshot before) {
        return quoteAdvancedSince(before, quoteSnapshot());
    }

    private static boolean quoteAdvancedSince(QuoteSnapshot before, QuoteSnapshot after) {
        // The source value is deliberately excluded: our own fill must not count as quote progress.
        boolean computedValueChanged = !Objects.equals(before.buyAmount(), after.buyAmount());
        boolean renderedQuoteChanged = !Objects.equals(before.rateText(), after.rateText())
                || !Objects.equals(before.timestampText(), after.timestampText());
        boolean quoteMarkerAdvanced = after.domVersion() > before.domVersion()
                || after.responseSequence() > before.responseSequence();
        return computedValueChanged || renderedQuoteChanged || quoteMarkerAdvanced;
    }

    private void observeQuoteResponse(Response response) {
        String resourceType = response.request().resourceType();
        boolean asyncData = "xhr".equalsIgnoreCase(resourceType) || "fetch".equalsIgnoreCase(resourceType);
        if (asyncData && response.ok() && QUOTE_RESPONSE_URL.matcher(response.url()).find()) {
            quoteResponseSequence.incrementAndGet();
        }
    }

    private static boolean containsIso(String text, String iso) {
        return Pattern.compile("(?i)(?<![A-Z])" + Pattern.quote(iso) + "(?![A-Z])")
                .matcher(Objects.toString(text, ""))
                .find();
    }

    private String selectedCurrency(Side side) {
        String selected = selectedCurrencyOrEmpty(side);
        if (selected.isEmpty()) {
            throw new AssertionError("No selected ISO currency was visible in the " + side + " control");
        }
        return ForexPairMatrix.normalize(selected);
    }

    private String selectedCurrencyOrEmpty(Side side) {
        Locator value = currencyControl(side).locator(".mat-mdc-select-value-text .mat-mdc-select-min-line");
        if (value.count() != 1 || !value.isVisible()) {
            return "";
        }
        String text = value.innerText().trim().toUpperCase(Locale.ROOT);
        Matcher matcher = ISO_TOKEN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private BigDecimal inputNumber(Locator input) {
        try {
            return parseLocalizedNumber(input.inputValue());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static BigDecimal parseLocalizedNumber(String rawValue) {
        String value = Objects.requireNonNull(rawValue, "amount")
                .replace("\u00a0", "")
                .replace("'", "")
                .replace(" ", "")
                .trim();
        int lastComma = value.lastIndexOf(',');
        int lastDot = value.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            char decimal = lastComma > lastDot ? ',' : '.';
            char grouping = decimal == ',' ? '.' : ',';
            value = value.replace(String.valueOf(grouping), "");
            if (decimal == ',') {
                value = value.replace(',', '.');
            }
        } else if (lastComma >= 0) {
            value = value.replace(',', '.');
        }
        value = value.replaceAll("[^0-9.+-]", "");
        if (value.isBlank() || ".".equals(value)) {
            throw new NumberFormatException("Blank numeric value: " + rawValue);
        }
        return new BigDecimal(value);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean isOne(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ONE) == 0;
    }

    private Locator currencyControl(Side side) {
        return formFieldControl(side.currencyTestIdSuffix, "mat-select", side.currencyFormControl);
    }

    private Locator amountControl(Side side) {
        return formFieldControl(side.amountTestIdSuffix, "input", side.amountFormControl);
    }

    private Locator accountControl(Side side) {
        return formFieldControl(side.accountTestIdSuffix, "mat-select", side.accountFormControl);
    }

    private Locator formFieldControl(String testIdSuffix, String childTag, String formControlName) {
        String wrapper = "mat-form-field[test-id='" + DETAILS_TEST_ID_PREFIX + testIdSuffix + "']";
        return page.locator(wrapper + " " + childTag + "[formcontrolname='" + formControlName + "']");
    }

    private void assertSafeEntryState(String checkpoint) {
        String escaped = unsafeNavigation.get();
        assertNull(escaped, checkpoint + ": navigation/final-step guard observed " + escaped);
        assertTrue(isEntryUrl(page.url()), checkpoint + ": left the forex entry URL: " + page.url());
        assertTrue(currencyControl(Side.SELL).isVisible(), checkpoint + ": sell control is no longer visible");
        assertTrue(currencyControl(Side.BUY).isVisible(), checkpoint + ": buy control is no longer visible");
        for (Side side : Side.values()) {
            Locator account = accountControl(side);
            assertEquals(1, account.count(), checkpoint + ": expected the exact " + side + " account control");
            String accountClass = Objects.toString(account.getAttribute("class"), "");
            assertTrue(
                    accountClass.contains("mat-mdc-select-empty"),
                    checkpoint + ": the test must never select a " + side + " money account");
        }
        Object blocked = page.evaluate("() => window.__inlineaForexBlockedFinalActions || 0");
        assertEquals(0, ((Number) blocked).intValue(), checkpoint + ": a final/submit control was targeted");
        Object blockedAccounts = page.evaluate("() => window.__inlineaForexBlockedAccountActions || 0");
        assertEquals(
                0,
                ((Number) blockedAccounts).intValue(),
                checkpoint + ": a debitAccount/creditAccount interaction was blocked");
    }

    private void installFinalActionBlocker() {
        page.evaluate(
                """
                () => {
                  window.__inlineaForexBlockedFinalActions = 0;
                  window.__inlineaForexBlockedAccountActions = 0;
                  const accountSelector = [
                    "mat-form-field[test-id='web-banking-trading-forex-entry-banklet.trading-forex-entry.forex-trade-details.debit-money-account'] mat-select[formcontrolname='debitAccount']",
                    "mat-form-field[test-id='web-banking-trading-forex-entry-banklet.trading-forex-entry.forex-trade-details.credit-money-account'] mat-select[formcontrolname='creditAccount']"
                  ].join(', ');
                  const originElement = event => event.target instanceof Element ? event.target : null;
                  const blockAccountInteraction = event => {
                    const origin = originElement(event);
                    if (!origin || !origin.closest(accountSelector)) return;
                    window.__inlineaForexBlockedAccountActions += 1;
                    event.preventDefault();
                    event.stopImmediatePropagation();
                  };
                  for (const type of ['pointerdown', 'mousedown', 'click', 'keydown']) {
                    window.addEventListener(type, blockAccountInteraction, true);
                  }
                  window.addEventListener('click', event => {
                    const origin = originElement(event);
                    const control = origin && origin.closest(
                      'button, input[type="submit"], a, [role="button"]');
                    if (!control) return;
                    const text = (control.textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();
                    const finalLabel = /^(avanti|continua|conferma|esegui|firma|next|confirm)$/.test(text);
                    if (control.matches('button[type="submit"], input[type="submit"]') || finalLabel) {
                      window.__inlineaForexBlockedFinalActions += 1;
                      event.preventDefault();
                      event.stopImmediatePropagation();
                    }
                  }, true);
                  window.addEventListener('submit', event => {
                    window.__inlineaForexBlockedFinalActions += 1;
                    event.preventDefault();
                    event.stopImmediatePropagation();
                  }, true);
                }
                """);
    }

    private void installQuoteMutationMarker() {
        page.evaluate(
                """
                () => {
                  const rootSelector =
                    "div[test-id='web-banking-trading-forex-entry-banklet.forex-deal-rate.deal-rate']";
                  const root = document.querySelector(rootSelector);
                  if (!root) throw new Error('Forex deal-rate root was not found');
                  const readSignature = () => {
                    const current = document.querySelector(rootSelector);
                    const rate = current?.querySelector('p')?.textContent?.trim() || '';
                    const timestamp = current?.querySelector('section > span')?.textContent?.trim() || '';
                    return `${rate}|${timestamp}`;
                  };
                  const marker = {
                    version: 0,
                    signature: readSignature(),
                    observer: null
                  };
                  marker.observer = new MutationObserver(() => {
                    const next = readSignature();
                    if (next !== marker.signature) {
                      marker.signature = next;
                      marker.version += 1;
                    }
                  });
                  marker.observer.observe(root.closest('form') || document.body, {
                    childList: true,
                    characterData: true,
                    subtree: true
                  });
                  window.__inlineaForexQuoteMarker = marker;
                }
                """);
    }

    private void armNavigationGuard() {
        page.onFrameNavigated(this::observeNavigation);
        page.onPopup(popup -> {
            unsafeNavigation.compareAndSet(null, "unexpected popup " + popup.url());
            popup.close();
        });
    }

    private void observeNavigation(Frame frame) {
        if (page != null && frame == page.mainFrame() && !isEntryUrl(frame.url())) {
            unsafeNavigation.compareAndSet(null, frame.url());
        }
    }

    private static boolean isEntryUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            return TARGET_HOST.equalsIgnoreCase(uri.getHost()) && TARGET_PATH.equals(uri.getPath());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void await(String description, BooleanSupplier condition) {
        try {
            page.waitForCondition(
                    condition,
                    new Page.WaitForConditionOptions().setTimeout(ACTION_TIMEOUT_MS));
        } catch (PlaywrightException error) {
            throw new AssertionError(
                    "Timed out waiting for " + description + ". Current pair="
                            + selectedCurrencyOrEmpty(Side.SELL) + "->" + selectedCurrencyOrEmpty(Side.BUY)
                            + ", URL=" + page.url(),
                    error);
        }
    }

    private static void addNewCurrencies(
            Set<String> discovered, Deque<String> pending, Collection<String> candidates) {
        for (String candidate : candidates) {
            String normalized = ForexPairMatrix.normalize(candidate);
            if (discovered.add(normalized)) {
                pending.addLast(normalized);
            }
        }
    }

    private static Path requireStorageState() {
        String configured = System.getProperty(STORAGE_STATE_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            throw new IllegalStateException("The live forex suite is enabled, but -D"
                    + STORAGE_STATE_PROPERTY
                    + "=<path-to-authenticated-playwright-storage-state.json> was not supplied. "
                    + "A storage state is required because this test will not automate credentials or MFA.");
        }
        Path storageState = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(storageState)) {
            throw new IllegalStateException("The Playwright storage state does not exist or is not a file: "
                    + storageState);
        }
        return storageState;
    }

    private static Set<String> expectedCurrencies() {
        String configured = System.getProperty(EXPECTED_CURRENCIES_PROPERTY, DEFAULT_EXPECTED_CURRENCIES);
        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        for (String token : configured.split("[,;\\s]+")) {
            if (!token.isBlank()) {
                currencies.add(ForexPairMatrix.normalize(token));
            }
        }
        if (currencies.size() < 2) {
            throw new IllegalArgumentException("-D" + EXPECTED_CURRENCIES_PROPERTY
                    + " must contain at least two distinct ISO currencies, but was: " + configured);
        }
        return currencies;
    }

    private void closeResources() {
        try {
            closeCurrencyOverlay();
        } catch (RuntimeException | AssertionError ignored) {
            // Overlay cleanup is best effort; it must never prevent context/browser shutdown.
        }
        if (context != null) {
            try {
                context.close();
            } catch (RuntimeException ignored) {
                // Best-effort cleanup after a live-test assertion.
            }
            context = null;
        }
        if (browser != null) {
            try {
                browser.close();
            } catch (RuntimeException ignored) {
                // Best-effort cleanup after a live-test assertion.
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (RuntimeException ignored) {
                // Best-effort cleanup after a live-test assertion.
            }
            playwright = null;
        }
    }

    private enum LinkResult {
        UPDATED,
        CONSTRAINED,
        NOT_OBSERVED
    }

    private enum Side {
        SELL("debit-currency", "debitCurrency", "debit-amount", "debitAmount", "debit-money-account", "debitAccount"),
        BUY("credit-currency", "creditCurrency", "credit-amount", "creditAmount", "credit-money-account", "creditAccount");

        private final String currencyTestIdSuffix;
        private final String currencyFormControl;
        private final String amountTestIdSuffix;
        private final String amountFormControl;
        private final String accountTestIdSuffix;
        private final String accountFormControl;

        Side(
                String currencyTestIdSuffix,
                String currencyFormControl,
                String amountTestIdSuffix,
                String amountFormControl,
                String accountTestIdSuffix,
                String accountFormControl) {
            this.currencyTestIdSuffix = currencyTestIdSuffix;
            this.currencyFormControl = currencyFormControl;
            this.amountTestIdSuffix = amountTestIdSuffix;
            this.amountFormControl = amountFormControl;
            this.accountTestIdSuffix = accountTestIdSuffix;
            this.accountFormControl = accountFormControl;
        }

        Side opposite() {
            return this == SELL ? BUY : SELL;
        }
    }

    private record CurrencyOption(String currency, boolean enabled, Locator locator) {}

    private record OptionSnapshot(Map<String, CurrencyOption> options) {
        OptionSnapshot {
            options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        }

        List<String> enabledCurrencies() {
            return options.values().stream()
                    .filter(CurrencyOption::enabled)
                    .map(CurrencyOption::currency)
                    .toList();
        }

        Set<String> allCurrencies() {
            return options.keySet();
        }
    }

    private record QuoteSnapshot(
            String rateText,
            String timestampText,
            String sellAmount,
            String buyAmount,
            long domVersion,
            long responseSequence) {

        String describe() {
            return "rate='" + rateText + "', timestamp='" + timestampText + "', sell='" + sellAmount
                    + "', buy='" + buyAmount + "', domVersion=" + domVersion
                    + ", responseSequence=" + responseSequence;
        }
    }
}
