package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Scope;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.ScopeKind;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Loads one immutable, owner-scoped execution plan directly from the database.
 *
 * <p>This repository never reads or mutates {@code PerformLists}. All queries run on one local
 * connection and one transaction so a smoke integration run cannot combine facts from different
 * database revisions. The client supplies only scope IDs; actions, locators, references, parent
 * relationships, variable slots and browser environment are authoritative backend facts.
 */
public final class SmokeTestIntegrationSnapshotRepository {
    private final ConnectionProvider connections;
    private final BrowserTypeProvider browserTypes;

    /** Production constructor. The connection is still opened only when {@link #load} is called. */
    public SmokeTestIntegrationSnapshotRepository() {
        this(
                PerformDataBase.getInstance()::getConnection,
                () -> ARPropertyManager.getInstance().getProperty(ARPropertyEnum.BROWSER));
    }

    /** Testable constructor retaining the production browser configuration as source of truth. */
    public SmokeTestIntegrationSnapshotRepository(ConnectionProvider connections) {
        this(
                connections,
                () -> ARPropertyManager.getInstance().getProperty(ARPropertyEnum.BROWSER));
    }

    public SmokeTestIntegrationSnapshotRepository(
            ConnectionProvider connections,
            BrowserTypeProvider browserTypes) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.browserTypes = Objects.requireNonNull(browserTypes, "browserTypes");
    }

    /**
     * Loads and validates the complete frozen plan for the requested active scope.
     *
     * @throws SQLException when the owner, environment, requested scope or stored relationships
     *     are unavailable or inconsistent
     */
    public Plan load(Owner owner, Scope requestedScope) throws SQLException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(requestedScope, "requestedScope");

        Connection opened = connections.open();
        if (opened == null) {
            throw new SQLException("Smoke integration database connection is unavailable");
        }
        try (Connection connection = opened) {
            boolean beginTransaction = connection.getAutoCommit();
            if (beginTransaction) {
                connection.setAutoCommit(false);
            }
            try {
                Environment environment = loadEnvironment(connection, owner);
                List<BlockSnapshot> allBlocks = loadBlocks(connection, owner);
                List<BlockSnapshot> selectedBlocks = selectActiveBlocks(allBlocks, requestedScope);
                Scope normalizedScope = normalizeScope(requestedScope, selectedBlocks);
                Set<Integer> selectedBlockIds = blockIds(selectedBlocks);

                List<InstructionRow> rows = loadInstructionRows(connection, owner, selectedBlockIds);
                Set<Integer> instructionIds = instructionIds(rows);
                Map<Integer, List<ReferenceSnapshot>> references =
                        loadReferences(connection, owner, instructionIds);
                Map<Integer, Map<String, Integer>> slots =
                        loadVariableSlots(connection, owner, instructionIds);
                Map<Integer, BlockSnapshot> blocksById = indexBlocks(selectedBlocks);
                List<InstructionSnapshot> instructions = materializeInstructions(
                        owner, environment, rows, blocksById, references, slots);
                validateInstructionOrder(instructions);

                String revision = revision(
                        owner, environment, normalizedScope, selectedBlocks, instructions);
                Plan plan = new Plan(
                        owner,
                        environment,
                        normalizedScope,
                        selectedBlocks,
                        instructions,
                        revision);
                if (beginTransaction) {
                    connection.commit();
                }
                return plan;
            } catch (SQLException | RuntimeException failure) {
                if (beginTransaction) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    public Plan load(int homeBankingId, int botJobId, Scope scope) throws SQLException {
        return load(new Owner(homeBankingId, botJobId), scope);
    }

    private Environment loadEnvironment(Connection connection, Owner owner) throws SQLException {
        String sql =
                "SELECT bj.id AS bot_job_id, bj.name AS bot_job_name,"
                        + " bj.priority AS bot_job_priority, bj.active AS bot_job_active,"
                        + " bj.home_banking_id, bj.home_url_id,"
                        + " hb.name AS organization_name, hb.options_config,"
                        + " hu.name AS environment_name, hu.url AS environment_url"
                        + " FROM bot_job bj"
                        + " JOIN home_banking hb ON hb.id=bj.home_banking_id"
                        + " JOIN home_url hu ON hu.id=bj.home_url_id"
                        + " AND hu.home_banking_id=bj.home_banking_id"
                        + " WHERE bj.id=? AND bj.home_banking_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.botJobId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException(
                            "Smoke integration Bot Job #"
                                    + owner.botJobId()
                                    + " is not owned by organization #"
                                    + owner.homeBankingId()
                                    + " or has no owned environment");
                }
                Environment environment = new Environment(
                        rows.getInt("home_banking_id"),
                        requiredDatabaseText(rows, "organization_name", "organization name"),
                        rows.getInt("bot_job_id"),
                        requiredDatabaseText(rows, "bot_job_name", "Bot Job name"),
                        text(rows, "bot_job_priority"),
                        requiredPositive(rows, "home_url_id", "home URL id"),
                        requiredDatabaseText(rows, "environment_name", "environment name"),
                        requiredDatabaseText(rows, "environment_url", "environment URL"),
                        text(rows, "options_config"),
                        requiredConfiguredText(browserTypes.browserType(), "browser type"));
                if (!rows.getBoolean("bot_job_active")) {
                    throw new SQLException(
                            "Smoke integration Bot Job #" + owner.botJobId() + " is inactive");
                }
                if (rows.next()) {
                    throw new SQLException(
                            "Smoke integration owner query returned duplicate environments for Bot Job #"
                                    + owner.botJobId());
                }
                return environment;
            }
        }
    }

    private List<BlockSnapshot> loadBlocks(Connection connection, Owner owner) throws SQLException {
        String sql =
                "SELECT id,block_order_number,name,description,type_id,export_file,active,wait"
                        + " FROM block WHERE bot_job_id=? ORDER BY block_order_number,id";
        List<BlockSnapshot> result = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    BlockSnapshot block = new BlockSnapshot(
                            requiredPositive(rows, "id", "Block id"),
                            requiredPositive(rows, "block_order_number", "Block order"),
                            requiredDatabaseText(rows, "name", "Block name"),
                            text(rows, "description"),
                            nullableInteger(rows, "type_id"),
                            text(rows, "export_file"),
                            rows.getBoolean("active"),
                            nullableInteger(rows, "wait"));
                    if (!ids.add(block.id())) {
                        throw new SQLException(
                                "Smoke integration found duplicate Block #" + block.id());
                    }
                    result.add(block);
                }
            }
        }
        return List.copyOf(result);
    }

    private List<BlockSnapshot> selectActiveBlocks(
            List<BlockSnapshot> allBlocks,
            Scope scope)
            throws SQLException {
        Map<Integer, BlockSnapshot> byId = new LinkedHashMap<>();
        for (BlockSnapshot block : allBlocks) {
            byId.put(block.id(), block);
        }

        Set<Integer> requested = new LinkedHashSet<>();
        if (scope.kind() == ScopeKind.BLOCKS) {
            for (Integer blockId : scope.blockIds()) {
                BlockSnapshot block = byId.get(blockId);
                if (block == null) {
                    throw new SQLException(
                            "Smoke integration requested Block #"
                                    + blockId
                                    + " outside the active Bot Job");
                }
                if (!block.active()) {
                    throw new SQLException(
                            "Smoke integration requested inactive Block #" + blockId);
                }
                requested.add(blockId);
            }
        }

        List<BlockSnapshot> selected = new ArrayList<>();
        Set<Integer> activeOrders = new HashSet<>();
        for (BlockSnapshot block : allBlocks) {
            if (!block.active()) {
                continue;
            }
            if (scope.kind() == ScopeKind.BLOCKS && !requested.contains(block.id())) {
                continue;
            }
            if (!activeOrders.add(block.order())) {
                throw new SQLException(
                        "Smoke integration active Block order "
                                + block.order()
                                + " is not unique");
            }
            selected.add(block);
        }
        return List.copyOf(selected);
    }

    private Scope normalizeScope(Scope requested, List<BlockSnapshot> selected) {
        if (requested.kind() == ScopeKind.ALL) {
            return Scope.all();
        }
        return Scope.blocks(selected.stream().map(BlockSnapshot::id).toList());
    }

    private List<InstructionRow> loadInstructionRows(
            Connection connection,
            Owner owner,
            Set<Integer> selectedBlockIds)
            throws SQLException {
        if (selectedBlockIds.isEmpty()) {
            return List.of();
        }
        String sql =
                "SELECT i.id,i.block_id,i.instruction_order_number,i.actions,i.name,"
                        + " i.client_named,i.operation,i.xpath,i.coordinates,i.force_coordinates,"
                        + " i.iframe_xpath,i.tag_name,i.shadow_host,i.shadow_root,i.css_selector,"
                        + " i.description,i.default_value,i.optional,i.block_marked,"
                        + " i.action_custom_max_wait_sec,i.on_hold_seconds,i.codified,"
                        + " i.export_to_abr,i.active,i.parent_id,i.parent_block_id"
                        + " FROM instruction i"
                        + " JOIN block b ON b.id=i.block_id AND b.bot_job_id=i.bot_job_id"
                        + " WHERE i.bot_job_id=? AND b.bot_job_id=?"
                        + " AND i.active=1 AND b.active=1"
                        + " ORDER BY b.block_order_number,i.instruction_order_number,i.id";
        List<InstructionRow> result = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.botJobId());
            statement.setInt(2, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int blockId = requiredPositive(rows, "block_id", "instruction Block id");
                    if (!selectedBlockIds.contains(blockId)) {
                        continue;
                    }
                    InstructionRow instruction = new InstructionRow(
                            requiredPositive(rows, "id", "instruction id"),
                            blockId,
                            requiredPositive(
                                    rows,
                                    "instruction_order_number",
                                    "instruction order"),
                            requiredDatabaseText(rows, "actions", "instruction action"),
                            text(rows, "name"),
                            rawNullableText(rows, "client_named"),
                            text(rows, "operation"),
                            text(rows, "xpath"),
                            text(rows, "coordinates"),
                            text(rows, "force_coordinates"),
                            text(rows, "iframe_xpath"),
                            text(rows, "tag_name"),
                            text(rows, "shadow_host"),
                            text(rows, "shadow_root"),
                            text(rows, "css_selector"),
                            text(rows, "description"),
                            rawNullableText(rows, "default_value"),
                            rows.getBoolean("optional"),
                            rows.getBoolean("block_marked"),
                            nullableInteger(rows, "action_custom_max_wait_sec"),
                            nullableInteger(rows, "on_hold_seconds"),
                            rows.getBoolean("codified"),
                            rows.getBoolean("export_to_abr"),
                            rows.getBoolean("active"),
                            nullablePositive(rows, "parent_id", "instruction parent id"),
                            nullablePositive(
                                    rows,
                                    "parent_block_id",
                                    "instruction parent Block id"));
                    if (!ids.add(instruction.id())) {
                        throw new SQLException(
                                "Smoke integration found duplicate instruction #"
                                        + instruction.id());
                    }
                    result.add(instruction);
                }
            }
        }
        return List.copyOf(result);
    }

    private Map<Integer, List<ReferenceSnapshot>> loadReferences(
            Connection connection,
            Owner owner,
            Set<Integer> selectedInstructionIds)
            throws SQLException {
        if (selectedInstructionIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                "SELECT r.id,r.instruction_id,r.reference_type,r.value"
                        + " FROM reference r"
                        + " JOIN instruction i ON i.id=r.instruction_id AND i.bot_job_id=r.bot_job_id"
                        + " JOIN block b ON b.id=i.block_id AND b.bot_job_id=i.bot_job_id"
                        + " WHERE r.bot_job_id=? AND i.bot_job_id=?"
                        + " AND i.active=1 AND b.active=1"
                        + " ORDER BY r.instruction_id,r.id";
        Map<Integer, List<ReferenceSnapshot>> result = new LinkedHashMap<>();
        Set<Integer> referenceIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.botJobId());
            statement.setInt(2, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int instructionId =
                            requiredPositive(rows, "instruction_id", "reference instruction id");
                    if (!selectedInstructionIds.contains(instructionId)) {
                        continue;
                    }
                    ReferenceSnapshot reference = new ReferenceSnapshot(
                            requiredPositive(rows, "id", "reference id"),
                            instructionId,
                            text(rows, "reference_type"),
                            text(rows, "value"));
                    if (!referenceIds.add(reference.id())) {
                        throw new SQLException(
                                "Smoke integration found duplicate reference #" + reference.id());
                    }
                    result.computeIfAbsent(instructionId, ignored -> new ArrayList<>())
                            .add(reference);
                }
            }
        }
        return immutableListMap(result);
    }

    private Map<Integer, Map<String, Integer>> loadVariableSlots(
            Connection connection,
            Owner owner,
            Set<Integer> selectedInstructionIds)
            throws SQLException {
        if (selectedInstructionIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                "SELECT s.instruction_id,s.slot,s.variable_id,"
                        + " v.id AS owned_variable_id"
                        + " FROM instruction_variable_slot s"
                        + " JOIN instruction i ON i.id=s.instruction_id AND i.bot_job_id=s.bot_job_id"
                        + " JOIN block b ON b.id=i.block_id AND b.bot_job_id=i.bot_job_id"
                        + " LEFT JOIN bot_job_variable_definition v"
                        + " ON v.home_banking_id=s.home_banking_id"
                        + " AND v.bot_job_id=s.bot_job_id AND v.id=s.variable_id"
                        + " WHERE s.home_banking_id=? AND s.bot_job_id=?"
                        + " AND i.active=1 AND b.active=1"
                        + " ORDER BY s.instruction_id,s.slot";
        Map<Integer, Map<String, Integer>> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int instructionId =
                            requiredPositive(rows, "instruction_id", "slot instruction id");
                    if (!selectedInstructionIds.contains(instructionId)) {
                        continue;
                    }
                    String slot = normalizeSlot(
                            requiredDatabaseText(rows, "slot", "variable slot"));
                    int variableId =
                            requiredPositive(rows, "variable_id", "slot variable id");
                    Integer ownedVariableId = nullableInteger(rows, "owned_variable_id");
                    if (ownedVariableId == null || ownedVariableId != variableId) {
                        throw new SQLException(
                                "Smoke integration variable #"
                                        + variableId
                                        + " in instruction #"
                                        + instructionId
                                        + " slot "
                                        + slot
                                        + " is not owned by the Bot Job");
                    }
                    Map<String, Integer> instructionSlots =
                            result.computeIfAbsent(instructionId, ignored -> new TreeMap<>());
                    if (instructionSlots.putIfAbsent(slot, variableId) != null) {
                        throw new SQLException(
                                "Smoke integration found duplicate "
                                        + slot
                                        + " slot for instruction #"
                                        + instructionId);
                    }
                }
            }
        }
        return immutableMapMap(result);
    }

    private List<InstructionSnapshot> materializeInstructions(
            Owner owner,
            Environment environment,
            List<InstructionRow> rows,
            Map<Integer, BlockSnapshot> blocks,
            Map<Integer, List<ReferenceSnapshot>> references,
            Map<Integer, Map<String, Integer>> slots)
            throws SQLException {
        List<InstructionSnapshot> result = new ArrayList<>();
        for (InstructionRow row : rows) {
            BlockSnapshot block = blocks.get(row.blockId());
            if (block == null) {
                throw new SQLException(
                        "Smoke integration instruction #"
                                + row.id()
                                + " is outside the selected active Blocks");
            }
            result.add(new InstructionSnapshot(
                    owner,
                    environment.botJobName(),
                    environment.botJobPriority(),
                    block,
                    row.id(),
                    row.order(),
                    row.action(),
                    row.name(),
                    row.clientNamed(),
                    row.operation(),
                    row.xpath(),
                    row.coordinates(),
                    row.forceCoordinates(),
                    row.iframeXpath(),
                    row.tagName(),
                    row.shadowHost(),
                    row.shadowRoot(),
                    row.cssSelector(),
                    row.description(),
                    row.defaultValue(),
                    row.optional(),
                    row.blockMarked(),
                    row.actionCustomMaxWaitSec(),
                    row.onHoldSeconds(),
                    row.codified(),
                    row.exportToAbr(),
                    row.active(),
                    row.parentId(),
                    row.parentBlockId(),
                    references.getOrDefault(row.id(), List.of()),
                    slots.getOrDefault(row.id(), Map.of())));
        }
        return List.copyOf(result);
    }

    private void validateInstructionOrder(List<InstructionSnapshot> instructions)
            throws SQLException {
        Set<String> orders = new HashSet<>();
        for (InstructionSnapshot instruction : instructions) {
            String key = instruction.block().id() + ":" + instruction.order();
            if (!orders.add(key)) {
                throw new SQLException(
                        "Smoke integration instruction order "
                                + instruction.order()
                                + " is not unique in Block #"
                                + instruction.block().id());
            }
        }
    }

    private String revision(
            Owner owner,
            Environment environment,
            Scope scope,
            List<BlockSnapshot> blocks,
            List<InstructionSnapshot> instructions) {
        DigestWriter digest = new DigestWriter();
        digest.put("SMOKE_INTEGRATION_PLAN_V1");
        digest.put(owner.homeBankingId());
        digest.put(owner.botJobId());
        digest.put(environment.organizationName());
        digest.put(environment.botJobName());
        digest.put(environment.botJobPriority());
        digest.put(environment.homeUrlId());
        digest.put(environment.environmentName());
        digest.put(environment.url());
        digest.put(environment.optionsConfig());
        digest.put(environment.browserType());
        digest.put(scope.kind().name());
        digest.put(scope.blockIds().size());
        for (Integer blockId : scope.blockIds()) {
            digest.put(blockId);
        }
        digest.put(blocks.size());
        for (BlockSnapshot block : blocks) {
            digest.put(block.id());
            digest.put(block.order());
            digest.put(block.name());
            digest.put(block.description());
            digest.put(block.typeId());
            digest.put(block.exportFile());
            digest.put(block.active());
            digest.put(block.waitSeconds());
        }
        digest.put(instructions.size());
        for (InstructionSnapshot instruction : instructions) {
            digest.put(instruction.id());
            digest.put(instruction.block().id());
            digest.put(instruction.order());
            digest.put(instruction.action());
            digest.put(instruction.name());
            digest.put(instruction.clientNamed());
            digest.put(instruction.operation());
            digest.put(instruction.xpath());
            digest.put(instruction.coordinates());
            digest.put(instruction.forceCoordinates());
            digest.put(instruction.iframeXpath());
            digest.put(instruction.tagName());
            digest.put(instruction.shadowHost());
            digest.put(instruction.shadowRoot());
            digest.put(instruction.cssSelector());
            digest.put(instruction.description());
            digest.put(instruction.defaultValue());
            digest.put(instruction.optional());
            digest.put(instruction.blockMarked());
            digest.put(instruction.actionCustomMaxWaitSec());
            digest.put(instruction.onHoldSeconds());
            digest.put(instruction.codified());
            digest.put(instruction.exportToAbr());
            digest.put(instruction.active());
            digest.put(instruction.parentId());
            digest.put(instruction.parentBlockId());
            digest.put(instruction.references().size());
            for (ReferenceSnapshot reference : instruction.references()) {
                digest.put(reference.id());
                digest.put(reference.instructionId());
                digest.put(reference.type());
                digest.put(reference.value());
            }
            digest.put(instruction.variableSlots().size());
            instruction.variableSlots().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        digest.put(entry.getKey());
                        digest.put(entry.getValue());
                    });
        }
        return digest.finish();
    }

    private static Set<Integer> blockIds(List<BlockSnapshot> blocks) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (BlockSnapshot block : blocks) {
            ids.add(block.id());
        }
        return Set.copyOf(ids);
    }

    private static Set<Integer> instructionIds(List<InstructionRow> instructions) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (InstructionRow instruction : instructions) {
            ids.add(instruction.id());
        }
        return Set.copyOf(ids);
    }

    private static Map<Integer, BlockSnapshot> indexBlocks(List<BlockSnapshot> blocks) {
        Map<Integer, BlockSnapshot> result = new LinkedHashMap<>();
        for (BlockSnapshot block : blocks) {
            result.put(block.id(), block);
        }
        return Collections.unmodifiableMap(result);
    }

    private static <T> Map<Integer, List<T>> immutableListMap(Map<Integer, List<T>> source) {
        Map<Integer, List<T>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, Map<String, Integer>> immutableMapMap(
            Map<Integer, Map<String, Integer>> source) {
        Map<Integer, Map<String, Integer>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(
                key,
                Collections.unmodifiableMap(new LinkedHashMap<>(value))));
        return Collections.unmodifiableMap(result);
    }

    private static String normalizeSlot(String value) throws SQLException {
        String slot = value.trim().toUpperCase(Locale.ROOT);
        if (slot.length() > 32) {
            throw new SQLException("Smoke integration variable slot is too long");
        }
        return slot;
    }

    private static int requiredPositive(ResultSet rows, String column, String label)
            throws SQLException {
        Integer value = nullableInteger(rows, column);
        if (value == null || value <= 0) {
            throw new SQLException("Smoke integration " + label + " must be positive");
        }
        return value;
    }

    private static Integer nullablePositive(ResultSet rows, String column, String label)
            throws SQLException {
        Integer value = nullableInteger(rows, column);
        if (value != null && value <= 0) {
            throw new SQLException("Smoke integration " + label + " must be positive when set");
        }
        return value;
    }

    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long parsed = number.longValue();
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                throw new SQLException(
                        "Smoke integration " + column + " is outside the supported integer range");
            }
            return (int) parsed;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            throw new SQLException(
                    "Smoke integration " + column + " is not an integer", invalid);
        }
    }

    private static String requiredDatabaseText(ResultSet rows, String column, String label)
            throws SQLException {
        String value = nullableText(rows, column);
        if (value == null) {
            throw new SQLException("Smoke integration " + label + " is required");
        }
        return value;
    }

    private static String requiredConfiguredText(String value, String label) throws SQLException {
        if (value == null || value.isBlank()) {
            throw new SQLException("Smoke integration " + label + " is not configured");
        }
        return value.trim();
    }

    private static String nullableText(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String rawNullableText(ResultSet rows, String column) throws SQLException {
        return rows.getString(column);
    }

    private static String text(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? "" : value;
    }

    public record Owner(int homeBankingId, int botJobId) {
        public Owner {
            if (homeBankingId <= 0) {
                throw new IllegalArgumentException("Smoke integration homeBankingId must be positive");
            }
            if (botJobId <= 0) {
                throw new IllegalArgumentException("Smoke integration botJobId must be positive");
            }
        }
    }

    public record Environment(
            int homeBankingId,
            String organizationName,
            int botJobId,
            String botJobName,
            String botJobPriority,
            int homeUrlId,
            String environmentName,
            String url,
            String optionsConfig,
            String browserType) {
        public Environment {
            if (homeBankingId <= 0 || botJobId <= 0 || homeUrlId <= 0) {
                throw new IllegalArgumentException(
                        "Smoke integration environment IDs must be positive");
            }
            organizationName = requireRecordText(organizationName, "organizationName");
            botJobName = requireRecordText(botJobName, "botJobName");
            botJobPriority = botJobPriority == null ? "" : botJobPriority;
            environmentName = requireRecordText(environmentName, "environmentName");
            url = requireRecordText(url, "url");
            optionsConfig = optionsConfig == null ? "" : optionsConfig;
            browserType = requireRecordText(browserType, "browserType");
        }
    }

    public record BlockSnapshot(
            int id,
            int order,
            String name,
            String description,
            Integer typeId,
            String exportFile,
            boolean active,
            Integer waitSeconds) {
        public BlockSnapshot {
            if (id <= 0 || order <= 0) {
                throw new IllegalArgumentException(
                        "Smoke integration Block IDs and order must be positive");
            }
            name = requireRecordText(name, "Block name");
            description = description == null ? "" : description;
            exportFile = exportFile == null ? "" : exportFile;
            if (typeId != null && typeId < 0) {
                throw new IllegalArgumentException("Smoke integration Block typeId cannot be negative");
            }
            if (waitSeconds != null && waitSeconds < 0) {
                throw new IllegalArgumentException("Smoke integration Block wait cannot be negative");
            }
        }
    }

    public record ReferenceSnapshot(int id, int instructionId, String type, String value) {
        public ReferenceSnapshot {
            if (id <= 0 || instructionId <= 0) {
                throw new IllegalArgumentException(
                        "Smoke integration reference IDs must be positive");
            }
            type = type == null ? "" : type;
            value = value == null ? "" : value;
        }
    }

    /** One fully materialized instruction with every Playwright-relevant stored fact. */
    public record InstructionSnapshot(
            Owner owner,
            String botJobName,
            String botJobPriority,
            BlockSnapshot block,
            int id,
            int order,
            String action,
            String name,
            String clientNamed,
            String operation,
            String xpath,
            String coordinates,
            String forceCoordinates,
            String iframeXpath,
            String tagName,
            String shadowHost,
            String shadowRoot,
            String cssSelector,
            String description,
            String defaultValue,
            boolean optional,
            boolean blockMarked,
            Integer actionCustomMaxWaitSec,
            Integer onHoldSeconds,
            boolean codified,
            boolean exportToAbr,
            boolean active,
            Integer parentId,
            Integer parentBlockId,
            List<ReferenceSnapshot> references,
            Map<String, Integer> variableSlots) {
        public InstructionSnapshot {
            owner = Objects.requireNonNull(owner, "Smoke integration instruction owner is required");
            botJobName = requireRecordText(botJobName, "instruction Bot Job name");
            botJobPriority = botJobPriority == null ? "" : botJobPriority;
            block = Objects.requireNonNull(block, "Smoke integration instruction Block is required");
            if (id <= 0 || order <= 0) {
                throw new IllegalArgumentException(
                        "Smoke integration instruction ID and order must be positive");
            }
            action = requireRecordText(action, "instruction action");
            name = name == null ? "" : name;
            clientNamed = normalizeNullable(clientNamed);
            operation = operation == null ? "" : operation;
            xpath = xpath == null ? "" : xpath;
            coordinates = coordinates == null ? "" : coordinates;
            forceCoordinates = forceCoordinates == null ? "" : forceCoordinates;
            iframeXpath = iframeXpath == null ? "" : iframeXpath;
            tagName = tagName == null ? "" : tagName;
            shadowHost = shadowHost == null ? "" : shadowHost;
            shadowRoot = shadowRoot == null ? "" : shadowRoot;
            cssSelector = cssSelector == null ? "" : cssSelector;
            description = description == null ? "" : description;
            // Empty string, whitespace and SQL NULL have distinct input semantics.
            requireNonNegative(actionCustomMaxWaitSec, "actionCustomMaxWaitSec");
            requireNonNegative(onHoldSeconds, "onHoldSeconds");
            requirePositiveWhenPresent(parentId, "parentId");
            requirePositiveWhenPresent(parentBlockId, "parentBlockId");
            references = List.copyOf(references == null ? List.of() : references);
            Map<String, Integer> normalizedSlots = new TreeMap<>();
            if (variableSlots != null) {
                variableSlots.forEach((slot, variableId) -> {
                    String normalized = slot == null ? "" : slot.trim().toUpperCase(Locale.ROOT);
                    if (normalized.isEmpty() || variableId == null || variableId <= 0) {
                        throw new IllegalArgumentException(
                                "Smoke integration instruction variable slots are invalid");
                    }
                    normalizedSlots.put(normalized, variableId);
                });
            }
            variableSlots = Collections.unmodifiableMap(normalizedSlots);
        }

        public String displayKey() {
            return clientNamed == null || clientNamed.isBlank() ? name : clientNamed;
        }

        public Integer variableId(String slot) {
            if (slot == null || slot.isBlank()) {
                return null;
            }
            return variableSlots.get(slot.trim().toUpperCase(Locale.ROOT));
        }

        public Integer primaryVariableId() {
            String canonical = action.trim().toUpperCase(Locale.ROOT);
            return switch (canonical) {
                case "CK", "CHECKVALUE", "CSV CHECK", "PDF CHECK" -> variableId("LEFT");
                case "GET" -> variableId("GET_WRITE");
                case "SET" -> variableId("READ_SET");
                case "E", "EXCELWRITE" -> variableId("READ");
                default -> null;
            };
        }

        /** Creates a fresh mutable legacy DTO only at the Playwright adapter boundary. */
        public InstructionLoad toInstructionLoad() {
            InstructionLoad target = new InstructionLoad();
            target.setHomeBankingId(owner.homeBankingId());
            target.setBotJobId(owner.botJobId());
            target.setBotJobName(botJobName);
            target.setPriority(botJobPriority);
            target.setId(id);
            target.setInstructionOrderNumber(order);
            target.setActions(action);
            target.setName(name);
            target.setClientNamed(clientNamed);
            target.setOperation(operation);
            target.setXpath(xpath);
            target.setCoordinates(coordinates);
            target.setForceCoordinates(forceCoordinates);
            target.setIFrameXPath(iframeXpath);
            target.setTagName(tagName);
            target.setShadowHost(shadowHost);
            target.setShadowRoot(shadowRoot);
            target.setCssSelector(cssSelector);
            target.setDescription(description);
            target.setDefaultValue(defaultValue);
            target.setOptional(optional);
            target.setBlockMarked(blockMarked);
            target.setActionCustomMaxWaitSec(actionCustomMaxWaitSec);
            target.setOnHoldSeconds(onHoldSeconds);
            target.setCodified(codified);
            target.setExportToABR(exportToAbr);
            target.setExecuted(false);
            target.setInstructionActive(active);
            target.setParentId(parentId);
            target.setParentBlockId(parentBlockId);
            target.setVariableId(primaryVariableId());
            target.setBlockId(block.id());
            target.setBlockOrderNumber(block.order());
            target.setBlockName(block.name());
            target.setBlockActive(block.active());
            target.setBlockWait(block.waitSeconds());
            target.setExportFile(block.exportFile());

            List<ReferenceLoadDTO> copiedReferences = new ArrayList<>();
            for (ReferenceSnapshot reference : references) {
                ReferenceLoadDTO copied = new ReferenceLoadDTO();
                copied.setId(reference.id());
                copied.setHomeBankingId(owner.homeBankingId());
                copied.setBotJobId(owner.botJobId());
                copied.setInstructionId(reference.instructionId());
                copied.setReferenceType(reference.type());
                copied.setValue(reference.value());
                copiedReferences.add(copied);
            }
            target.setReferenceLoadDTOList(copiedReferences);
            return target;
        }
    }

    public static final class Plan {
        private static final Comparator<InstructionSnapshot> INSTRUCTION_ORDER = Comparator
                .comparingInt((InstructionSnapshot instruction) -> instruction.block().order())
                .thenComparingInt(InstructionSnapshot::order)
                .thenComparingInt(InstructionSnapshot::id);

        private final Owner owner;
        private final Environment environment;
        private final Scope scope;
        private final List<BlockSnapshot> blocks;
        private final List<InstructionSnapshot> instructions;
        private final String planRevision;
        private final Map<Integer, BlockSnapshot> blocksById;
        private final Map<Integer, InstructionSnapshot> instructionsById;

        public Plan(
                Owner owner,
                Environment environment,
                Scope scope,
                List<BlockSnapshot> blocks,
                List<InstructionSnapshot> instructions,
                String planRevision) {
            this.owner = Objects.requireNonNull(
                    owner, "Smoke integration plan owner is required");
            this.environment = Objects.requireNonNull(
                    environment, "Smoke integration plan environment is required");
            this.scope = Objects.requireNonNull(
                    scope, "Smoke integration plan scope is required");
            this.blocks = List.copyOf(blocks == null ? List.of() : blocks);
            List<InstructionSnapshot> ordered = new ArrayList<>(
                    instructions == null ? List.of() : instructions);
            ordered.sort(INSTRUCTION_ORDER);
            this.instructions = List.copyOf(ordered);
            if (planRevision == null || !planRevision.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Smoke integration planRevision must be a SHA-256 value");
            }
            this.planRevision = planRevision;

            Map<Integer, BlockSnapshot> indexedBlocks = new LinkedHashMap<>();
            for (BlockSnapshot block : this.blocks) {
                if (indexedBlocks.putIfAbsent(block.id(), block) != null) {
                    throw new IllegalArgumentException(
                            "Smoke integration plan contains duplicate Block IDs");
                }
            }
            this.blocksById = Collections.unmodifiableMap(indexedBlocks);

            Map<Integer, InstructionSnapshot> indexedInstructions = new LinkedHashMap<>();
            for (InstructionSnapshot instruction : this.instructions) {
                if (indexedInstructions.putIfAbsent(instruction.id(), instruction) != null) {
                    throw new IllegalArgumentException(
                            "Smoke integration plan contains duplicate instruction IDs");
                }
                if (!indexedBlocks.containsKey(instruction.block().id())) {
                    throw new IllegalArgumentException(
                            "Smoke integration plan instruction references an absent Block");
                }
            }
            this.instructionsById = Collections.unmodifiableMap(indexedInstructions);
        }

        public Owner owner() {
            return owner;
        }

        public Environment environment() {
            return environment;
        }

        public Scope scope() {
            return scope;
        }

        public List<BlockSnapshot> blocks() {
            return blocks;
        }

        public List<InstructionSnapshot> instructions() {
            return instructions;
        }

        public String planRevision() {
            return planRevision;
        }

        public Map<Integer, BlockSnapshot> blocksById() {
            return blocksById;
        }

        public Map<Integer, InstructionSnapshot> instructionsById() {
            return instructionsById;
        }

        public BlockSnapshot block(int blockId) {
            return blocksById.get(blockId);
        }

        public InstructionSnapshot instruction(int instructionId) {
            return instructionsById.get(instructionId);
        }

        public Integer variableId(int instructionId, String slot) {
            InstructionSnapshot instruction = instruction(instructionId);
            return instruction == null ? null : instruction.variableId(slot);
        }
    }

    private record InstructionRow(
            int id,
            int blockId,
            int order,
            String action,
            String name,
            String clientNamed,
            String operation,
            String xpath,
            String coordinates,
            String forceCoordinates,
            String iframeXpath,
            String tagName,
            String shadowHost,
            String shadowRoot,
            String cssSelector,
            String description,
            String defaultValue,
            boolean optional,
            boolean blockMarked,
            Integer actionCustomMaxWaitSec,
            Integer onHoldSeconds,
            boolean codified,
            boolean exportToAbr,
            boolean active,
            Integer parentId,
            Integer parentBlockId) {}

    private static String requireRecordText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Smoke integration " + field + " is required");
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : value;
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(
                    "Smoke integration " + field + " cannot be negative");
        }
    }

    private static void requirePositiveWhenPresent(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(
                    "Smoke integration " + field + " must be positive when set");
        }
    }

    private static final class DigestWriter {
        private final MessageDigest digest;

        private DigestWriter() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }

        private void put(Object value) {
            if (value == null) {
                putLength(-1);
                return;
            }
            byte[] encoded = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            putLength(encoded.length);
            digest.update(encoded);
        }

        private void putLength(int length) {
            digest.update((byte) (length >>> 24));
            digest.update((byte) (length >>> 16));
            digest.update((byte) (length >>> 8));
            digest.update((byte) length);
        }

        private String finish() {
            byte[] result = digest.digest();
            StringBuilder hex = new StringBuilder(result.length * 2);
            for (byte value : result) {
                hex.append(Character.forDigit((value >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        }
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    @FunctionalInterface
    public interface BrowserTypeProvider {
        String browserType();
    }
}
