package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementLocatorEntity;
import com.allinweb.ch.util.TextSimilarity;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Roadmap 3 step 3 — one-shot cleanup of orphan locator rows accumulated before the
 * DOM-First filters were active. Pure scan-and-delete; no migration, never runs
 * automatically.
 *
 * <p>Heuristics, in order of confidence (most-certain-orphan first):
 * <ul>
 *   <li>{@code SHORT_NAME} — definedName length &lt; 3. Bare anchors / tag-only fallbacks.</li>
 *   <li>{@code LABEL_TAG} — original tag is {@code label}. Their text already lives on the
 *       associated input via aria/for-id, so the label row is structurally redundant.</li>
 *   <li>{@code RANDOM_TOKEN} — definedName matches the CSRF/random-token pattern (no vowels,
 *       all lowercase alnum, ≥ 16 chars). Catches the "oos9fqtw6diffmbr3jnpfw" case.</li>
 *   <li>{@code FUZZY_DUPLICATE} — definedName is a fuzzy variant of another row in the same
 *       scope (Levenshtein OR token-set ratio ≥ 0.65) AND has a strictly lower
 *       {@code pick_count}. The lower-count one is treated as the orphan; ties are kept.</li>
 * </ul>
 *
 * <p>Each candidate carries a human-readable {@code detail} string for the confirm dialog.
 */
@Slf4j
public final class LocatorCleanupService {

    /** Tokens that look like CSRF / random hex IDs: ≥ 16 chars, alphanumeric only, almost no vowels. */
    private static final Pattern RANDOM_TOKEN = Pattern.compile("^[a-z0-9]{16,}$");

    /** Below this similarity ratio we treat a pair as unrelated. */
    private static final double FUZZY_THRESHOLD = 0.65;

    private static final org.slf4j.Logger logScanner = org.slf4j.LoggerFactory.getLogger("com.allinweb.scanner");

    private static volatile LocatorCleanupService instance;

    public static LocatorCleanupService getInstance() {
        if (instance == null) {
            synchronized (LocatorCleanupService.class) {
                if (instance == null) instance = new LocatorCleanupService();
            }
        }
        return instance;
    }

    private LocatorCleanupService() {}

    public static final class OrphanCandidate {
        public final ElementLocatorEntity locator;
        public final String reason;
        public final String detail;

        public OrphanCandidate(ElementLocatorEntity locator, String reason, String detail) {
            this.locator = locator;
            this.reason = reason;
            this.detail = detail;
        }
    }

    public static final class ScanReport {
        public final int totalRows;
        public final List<OrphanCandidate> candidates;

        public ScanReport(int totalRows, List<OrphanCandidate> candidates) {
            this.totalRows = totalRows;
            this.candidates = candidates;
        }
    }

    /** Scan the locator table for the given scope (null parts mean "any") and report orphan candidates. */
    public ScanReport scan(Integer homebankingId, Integer homeUrlId) {
        ElementLocatorRepository repo = ElementLocatorRepository.getInstance();
        List<ElementLocatorEntity> all = repo.listForScope(homebankingId, homeUrlId);

        // Use a LinkedHashMap keyed on locator.id so the same row never appears twice
        // even when several heuristics fire on it. Order of insertion preserved.
        Map<Long, OrphanCandidate> hits = new LinkedHashMap<>();

        // 1. SHORT_NAME
        for (ElementLocatorEntity loc : all) {
            String name = loc.getDefinedName();
            if (name == null || name.length() >= 3) continue;
            hits.putIfAbsent(loc.getId(), new OrphanCandidate(
                    loc, "SHORT_NAME", "definedName length " + name.length() + " < 3 (bare tag fallback)"));
        }

        // 2. LABEL_TAG
        for (ElementLocatorEntity loc : all) {
            if ("label".equalsIgnoreCase(loc.getTagNameOriginal())) {
                hits.putIfAbsent(
                        loc.getId(),
                        new OrphanCandidate(
                                loc, "LABEL_TAG", "<label> tag — duplicates its associated <input>'s text"));
            }
        }

        // 3. RANDOM_TOKEN
        for (ElementLocatorEntity loc : all) {
            String name = loc.getDefinedName();
            if (name == null) continue;
            if (RANDOM_TOKEN.matcher(name).matches() && hasFewVowels(name)) {
                hits.putIfAbsent(
                        loc.getId(),
                        new OrphanCandidate(
                                loc,
                                "RANDOM_TOKEN",
                                "definedName looks like a CSRF / random token (no spaces, alphanumeric, ≥ 16 chars)"));
            }
        }

        // 4. FUZZY_DUPLICATE — pair-wise; keep the one with the higher pick_count, drop the other.
        for (int i = 0; i < all.size(); i++) {
            ElementLocatorEntity a = all.get(i);
            String an = a.getDefinedName();
            if (an == null || an.isBlank()) continue;
            for (int j = i + 1; j < all.size(); j++) {
                ElementLocatorEntity b = all.get(j);
                String bn = b.getDefinedName();
                if (bn == null || bn.isBlank()) continue;
                if (an.equalsIgnoreCase(bn)) continue;

                double ratio =
                        Math.max(TextSimilarity.levenshteinRatio(an, bn), TextSimilarity.tokenSetRatio(an, bn));
                if (ratio < FUZZY_THRESHOLD || ratio >= 1.0) continue;

                int aCount = a.getPickCount() == null ? 0 : a.getPickCount();
                int bCount = b.getPickCount() == null ? 0 : b.getPickCount();
                if (aCount == bCount) continue; // can't decide which is the orphan

                ElementLocatorEntity loser = aCount < bCount ? a : b;
                ElementLocatorEntity keeper = aCount < bCount ? b : a;
                hits.putIfAbsent(
                        loser.getId(),
                        new OrphanCandidate(
                                loser,
                                "FUZZY_DUPLICATE",
                                "fuzzy match of '" + keeper.getDefinedName() + "' (ratio "
                                        + String.format("%.2f", ratio) + "); lower pick_count ("
                                        + (loser.getPickCount() == null ? 0 : loser.getPickCount()) + " < "
                                        + (keeper.getPickCount() == null ? 0 : keeper.getPickCount()) + ")"));
            }
        }

        return new ScanReport(all.size(), new ArrayList<>(hits.values()));
    }

    /** Delete the candidates. Returns the count actually deleted. */
    public int deleteCandidates(List<OrphanCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return 0;
        ElementLocatorRepository repo = ElementLocatorRepository.getInstance();
        int deleted = 0;
        for (OrphanCandidate c : candidates) {
            if (c == null || c.locator == null || c.locator.getId() == null) continue;
            try {
                repo.deleteLocator(c.locator.getId());
                logScanner.info(
                        "LOCATOR CLEANUP — deleted id={} defined='{}' reason={} ({})",
                        c.locator.getId(),
                        c.locator.getDefinedName(),
                        c.reason,
                        c.detail);
                deleted++;
            } catch (SQLException e) {
                log.warn("Could not delete locator id={}: {}", c.locator.getId(), e.getMessage());
            }
        }
        logScanner.info("LOCATOR CLEANUP — deleted {} of {} candidates", deleted, candidates.size());
        return deleted;
    }

    private static boolean hasFewVowels(String s) {
        if (s == null) return false;
        int vowels = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("aeiou".indexOf(c) >= 0) vowels++;
        }
        // Random tokens typically have < 25% vowels; English words ~38–40%. 0.30 is a safe cutoff.
        return ((double) vowels / s.length()) < 0.30;
    }
}
