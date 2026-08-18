package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ForexPairMatrixTest {

    @Test
    void buildsAllTwentyDirectedPairsAndTenSwapCasesForFiveLinkedCurrencies() {
        List<String> currencies = List.of("CAD", "CHF", "EUR", "GBP", "USD");
        Map<String, List<String>> observedOptions = new LinkedHashMap<>();
        for (String sell : currencies) {
            observedOptions.put(
                    sell,
                    currencies.stream().filter(buy -> !buy.equals(sell)).toList());
        }

        List<ForexPairMatrix.Pair> pairs = ForexPairMatrix.directedPairs(observedOptions);
        Set<String> swapCases = pairs.stream()
                .map(ForexPairMatrix::swapCoverageKey)
                .collect(Collectors.toSet());

        assertEquals(20, pairs.size());
        assertEquals(10, swapCases.size());
        assertEquals(new ForexPairMatrix.Pair("CAD", "EUR"), pairs.get(1));
        assertEquals(new ForexPairMatrix.Pair("USD", "GBP"), pairs.get(19));
    }

    @Test
    void normalizesAndDeduplicatesObservedOptionsAndOmitsSameCurrency() {
        Map<String, List<String>> observedOptions = Map.of(
                " eur ", List.of("usd", "USD", "eur"),
                "USD", List.of("EUR", "USD", "eur"));

        assertEquals(
                List.of(
                        new ForexPairMatrix.Pair("EUR", "USD"),
                        new ForexPairMatrix.Pair("USD", "EUR")),
                ForexPairMatrix.directedPairs(observedOptions));
    }

    @Test
    void rejectsValuesThatCannotBeUnambiguouslyTreatedAsIsoCurrencies() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ForexPairMatrix.directedPairs(Map.of("EURO", List.of("USD"))));
        assertThrows(IllegalArgumentException.class, () -> new ForexPairMatrix.Pair("CHF", "CHF"));
    }
}
