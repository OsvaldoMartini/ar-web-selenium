package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.ScannedElement;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the immutable, owner-scoped registry input used by safe Playwright runtime healing.
 *
 * <p>The active page URL is converted to its one-way page key and is never retained in the plan or
 * written to diagnostics. The Bot Job's organization is always reloaded from the database; a
 * caller-supplied organization can only narrow authority, never grant it.
 */
@Slf4j
public final class RuntimeElementHealingService {

    private static final int MAX_ATTRIBUTE_JSON_LENGTH = 65_536;
    private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 512;
    private static final Gson JSON = new Gson();
    private static final RuntimeElementHealingService INSTANCE =
            new RuntimeElementHealingService(PerformDataBase.getInstance());

    private final PerformDataBase database;

    RuntimeElementHealingService(PerformDataBase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public static RuntimeElementHealingService getInstance() {
        return INSTANCE;
    }

    /**
     * Resolve the server-owned execution scope and select only registry rows that can relate to the
     * instruction by exact locator identity, canonical name, or client alias.
     */
    public Preparation prepare(
            Integer assertedHomeBankingId,
            Integer botJobId,
            String activePageUrl,
            InstructionLoad instruction) {
        if (botJobId == null || botJobId <= 0 || instruction == null) {
            return Preparation.failed(Status.INVALID_REQUEST);
        }
        if (instruction.getBotJobId() != null
                && instruction.getBotJobId() > 0
                && !botJobId.equals(instruction.getBotJobId())) {
            return Preparation.failed(Status.BOT_JOB_MISMATCH);
        }

        final ScannedPageIdentity page;
        try {
            page = ScannedPageIdentity.fromLiveUrl(activePageUrl);
        } catch (RuntimeException invalidPage) {
            return Preparation.failed(Status.INVALID_REQUEST);
        }

        try (Connection connection = database.getConnection()) {
            int authoritativeHomeBankingId = loadAuthoritativeOwner(connection, botJobId);
            if (authoritativeHomeBankingId <= 0) {
                return Preparation.failed(Status.OWNER_NOT_FOUND);
            }
            if (assertedHomeBankingId != null
                    && assertedHomeBankingId > 0
                    && assertedHomeBankingId != authoritativeHomeBankingId) {
                return Preparation.failed(Status.OWNER_MISMATCH);
            }

            List<ScannedElement> registry = ScannedElementRepository.loadByOwnerAndPage(
                    connection, authoritativeHomeBankingId, botJobId, activePageUrl);

            Map<Long, RegistryCandidate> locatorMatches = new LinkedHashMap<>();
            Map<Long, RegistryCandidate> canonicalMatches = new LinkedHashMap<>();
            Map<Long, RegistryCandidate> aliasMatches = new LinkedHashMap<>();
            int strongestLocatorMatch = 0;
            for (ScannedElement row : registry) {
                if (row == null || row.getId() == null || row.getId() <= 0) continue;
                RegistryCandidate candidate = candidate(row);
                int locatorMatch = locatorMatchStrength(instruction, row, candidate.attributes());
                if (locatorMatch > strongestLocatorMatch) {
                    locatorMatches.clear();
                    strongestLocatorMatch = locatorMatch;
                }
                if (locatorMatch > 0 && locatorMatch == strongestLocatorMatch) {
                    locatorMatches.putIfAbsent(row.getId(), candidate);
                }
                if (sameName(instruction.getName(), row.getDefinedName())) {
                    canonicalMatches.putIfAbsent(row.getId(), candidate);
                }
                if (sameName(instruction.getClientNamed(), row.getClientNamed())) {
                    aliasMatches.putIfAbsent(row.getId(), candidate);
                }
            }

            Preparation prepared = new Preparation(
                    Status.READY,
                    authoritativeHomeBankingId,
                    botJobId,
                    page.pageKey(),
                    List.copyOf(locatorMatches.values()),
                    List.copyOf(canonicalMatches.values()),
                    List.copyOf(aliasMatches.values()));
            log.debug(
                    "runtime-healing registry prepared hb={} bot={} locator={} canonical={} alias={}",
                    authoritativeHomeBankingId,
                    botJobId,
                    prepared.locatorCandidates().size(),
                    prepared.canonicalCandidates().size(),
                    prepared.aliasCandidates().size());
            return prepared;
        } catch (RuntimeException | java.sql.SQLException unavailable) {
            log.warn(
                    "runtime-healing registry unavailable bot={} failureType={}",
                    botJobId,
                    unavailable.getClass().getSimpleName());
            return Preparation.unavailable(botJobId, page.pageKey());
        }
    }

    private static int loadAuthoritativeOwner(Connection connection, int botJobId)
            throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT home_banking_id FROM bot_job WHERE id = ?")) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return 0;
                int homeBankingId = rows.getInt("home_banking_id");
                if (homeBankingId <= 0 || rows.next()) return 0;
                return homeBankingId;
            }
        }
    }

    static int locatorMatchStrength(
            InstructionLoad instruction,
            ScannedElement row,
            Map<String, String> stableAttributes) {
        if (sameLocator(instruction.getXpath(), row.getCustomXPath())
                || sameLocator(instruction.getXpath(), row.getXPath())) {
            return 3;
        }

        Map<String, String> references = instructionReferences(instruction);
        if (sameLocator(references.get("id"), row.getAttribId())
                || sameLocator(references.get("name"), row.getAttribName())
                || sameLocator(references.get("data-testid"), stableAttributes.get("data-testid"))
                || sameLocator(references.get("data-test-id"), stableAttributes.get("data-test-id"))
                || sameLocator(references.get("test-id"), stableAttributes.get("test-id"))
                || sameLocator(references.get("data-cy"), stableAttributes.get("data-cy"))
                || sameLocator(references.get("data-qa"), stableAttributes.get("data-qa"))) {
            return 2;
        }
        return sameLocator(instruction.getCssSelector(), row.getCssSelector()) ? 1 : 0;
    }

    private static Map<String, String> instructionReferences(InstructionLoad instruction) {
        if (instruction.getReferenceLoadDTOList() == null) return Map.of();
        Map<String, String> references = new LinkedHashMap<>();
        for (ReferenceLoadDTO reference : instruction.getReferenceLoadDTOList()) {
            if (reference == null
                    || reference.getReferenceType() == null
                    || reference.getValue() == null
                    || reference.getValue().isBlank()) {
                continue;
            }
            String type = reference.getReferenceType().trim().toLowerCase(Locale.ROOT);
            String key = switch (type) {
                case "locator.best.byid", "attrdata:id" -> "id";
                case "locator.best.byname", "attrdata:name" -> "name";
                case "data-testid", "attrdata:data-testid" -> "data-testid";
                case "data-test-id", "attrdata:data-test-id" -> "data-test-id";
                case "test-id", "attrdata:test-id" -> "test-id";
                case "data-cy", "attrdata:data-cy" -> "data-cy";
                case "data-qa", "attrdata:data-qa" -> "data-qa";
                default -> "";
            };
            if (!key.isEmpty()) references.putIfAbsent(key, reference.getValue().trim());
        }
        return references;
    }

    private static RegistryCandidate candidate(ScannedElement row) {
        return new RegistryCandidate(
                row.getId(),
                value(row.getTagName()),
                value(row.getTypeElement()),
                value(row.getXPath()),
                value(row.getCustomXPath()),
                value(row.getCssSelector()),
                value(row.getAttribId()),
                value(row.getAttribName()),
                value(row.getCoordinates()),
                value(row.getIFrameXPath()),
                value(row.getShadowHost()),
                value(row.getShadowRoot()),
                attributes(row));
    }

    private static Map<String, String> attributes(ScannedElement row) {
        String raw = row.getAttributeData();
        if (raw == null || raw.isBlank() || raw.length() > MAX_ATTRIBUTE_JSON_LENGTH) {
            return Map.of();
        }
        try {
            AttributeData[] values = JSON.fromJson(raw, AttributeData[].class);
            if (values == null) return Map.of();
            Map<String, String> attributes = new LinkedHashMap<>();
            for (AttributeData attribute : values) {
                if (attribute == null || attribute.getName() == null || attribute.getValue() == null) {
                    continue;
                }
                String name = attribute.getName().trim().toLowerCase(Locale.ROOT);
                if (!isStableAttribute(name)) continue;
                String value = attribute.getValue().trim();
                if (value.isEmpty() || value.length() > MAX_ATTRIBUTE_VALUE_LENGTH) continue;
                attributes.putIfAbsent(name, value);
            }
            return Collections.unmodifiableMap(attributes);
        } catch (RuntimeException invalidJson) {
            return Map.of();
        }
    }

    private static boolean isStableAttribute(String name) {
        return switch (name) {
            case "id", "name", "data-testid", "data-test-id", "test-id", "data-cy", "data-qa",
                    "aria-label", "role", "type", "original-tag" -> true;
            default -> false;
        };
    }

    private static boolean sameLocator(String left, String right) {
        return left != null
                && right != null
                && !left.isBlank()
                && !right.isBlank()
                && left.trim().equals(right.trim());
    }

    private static boolean sameName(String left, String right) {
        String normalizedLeft = normalizeName(left);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizeName(right));
    }

    private static String normalizeName(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public enum Status {
        READY,
        INVALID_REQUEST,
        BOT_JOB_MISMATCH,
        OWNER_NOT_FOUND,
        OWNER_MISMATCH,
        REGISTRY_UNAVAILABLE
    }

    public record Preparation(
            Status status,
            int homeBankingId,
            int botJobId,
            String pageKey,
            List<RegistryCandidate> locatorCandidates,
            List<RegistryCandidate> canonicalCandidates,
            List<RegistryCandidate> aliasCandidates) {
        public Preparation {
            status = Objects.requireNonNull(status, "status");
            pageKey = pageKey == null ? "" : pageKey;
            locatorCandidates = immutable(locatorCandidates);
            canonicalCandidates = immutable(canonicalCandidates);
            aliasCandidates = immutable(aliasCandidates);
        }

        public boolean ready() {
            return status == Status.READY
                    && homeBankingId > 0
                    && botJobId > 0
                    && !pageKey.isBlank();
        }

        public int registryCandidateCount() {
            return locatorCandidates.size() + canonicalCandidates.size() + aliasCandidates.size();
        }

        private static Preparation failed(Status status) {
            return new Preparation(status, 0, 0, "", List.of(), List.of(), List.of());
        }

        private static Preparation unavailable(int botJobId, String pageKey) {
            return new Preparation(
                    Status.REGISTRY_UNAVAILABLE,
                    0,
                    botJobId,
                    pageKey,
                    List.of(),
                    List.of(),
                    List.of());
        }

        private static List<RegistryCandidate> immutable(List<RegistryCandidate> candidates) {
            return Collections.unmodifiableList(
                    new ArrayList<>(candidates == null ? List.of() : candidates));
        }
    }

    public record RegistryCandidate(
            long scannedElementId,
            String tagName,
            String typeElement,
            String xpath,
            String customXPath,
            String cssSelector,
            String attribId,
            String attribName,
            String coordinates,
            String iframeXpath,
            String shadowHost,
            String shadowRoot,
            Map<String, String> attributes) {
        public RegistryCandidate {
            attributes = Collections.unmodifiableMap(
                    new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
        }
    }
}
