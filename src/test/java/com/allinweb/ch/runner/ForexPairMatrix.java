package com.allinweb.ch.runner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Deterministic matrix operations shared by the live forex Playwright integration test. */
final class ForexPairMatrix {

    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    private ForexPairMatrix() {}

    /**
     * Converts the options observed for every sell currency into a stable, duplicate-free list of
     * directed pairs. Same-currency entries are ignored because they are not exchanges.
     */
    static List<Pair> directedPairs(Map<String, ? extends Collection<String>> buysBySell) {
        Objects.requireNonNull(buysBySell, "buysBySell");
        List<Pair> pairs = new ArrayList<>();
        Set<Pair> seen = new LinkedHashSet<>();

        Map<String, Collection<String>> normalizedAvailability = new LinkedHashMap<>();
        buysBySell.forEach((rawSell, rawBuys) -> {
            String sell = normalize(rawSell);
            Collection<String> target = normalizedAvailability.computeIfAbsent(sell, ignored -> new ArrayList<>());
            target.addAll(Objects.requireNonNull(rawBuys, "Missing buy options for sell currency " + sell));
        });
        for (String sell : new TreeSet<>(normalizedAvailability.keySet())) {
            Collection<String> rawBuys = normalizedAvailability.get(sell);
            TreeSet<String> sortedBuys = new TreeSet<>();
            rawBuys.forEach(currency -> sortedBuys.add(normalize(currency)));
            for (String buy : sortedBuys) {
                if (!sell.equals(buy)) {
                    Pair pair = new Pair(sell, buy);
                    if (seen.add(pair)) {
                        pairs.add(pair);
                    }
                }
            }
        }
        return List.copyOf(pairs);
    }

    /** Key used to exercise swap once for each unordered pair while testing both directions. */
    static String swapCoverageKey(Pair pair) {
        Objects.requireNonNull(pair, "pair");
        return pair.sell().compareTo(pair.buy()) < 0
                ? pair.sell() + "<->" + pair.buy()
                : pair.buy() + "<->" + pair.sell();
    }

    static String normalize(String currency) {
        String normalized = Objects.requireNonNull(currency, "currency")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!ISO_CURRENCY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Expected a three-letter ISO currency, got: " + currency);
        }
        return normalized;
    }

    record Pair(String sell, String buy) {
        Pair {
            sell = normalize(sell);
            buy = normalize(buy);
            if (sell.equals(buy)) {
                throw new IllegalArgumentException("A forex pair must contain distinct currencies: " + sell);
            }
        }

        @Override
        public String toString() {
            return sell + "->" + buy;
        }
    }
}
