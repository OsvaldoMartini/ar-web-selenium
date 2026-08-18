package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.model.GridItemTestActionContracts.Action;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads one owner-scoped GridItem instruction without touching process-wide legacy lists. */
public final class GridItemTestInstructionRepository {
    private final ConnectionProvider connections;

    public GridItemTestInstructionRepository() {
        this(PerformDataBase.getInstance()::getConnection);
    }

    GridItemTestInstructionRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public InstructionSnapshot load(
            int homeBankingId, int botJobId, int instructionId) throws SQLException {
        try (Connection connection = connections.open()) {
            if (connection == null) {
                throw new SQLException("GridItem test database connection is unavailable");
            }
            boolean transaction = connection.getAutoCommit();
            if (transaction) connection.setAutoCommit(false);
            try {
                InstructionSnapshot instruction = loadInstruction(
                        connection, homeBankingId, botJobId, instructionId);
                List<ReferenceSnapshot> references = loadReferences(
                        connection, botJobId, instructionId);
                InstructionSnapshot result = instruction.withReferences(references);
                if (transaction) connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                if (transaction) {
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

    private InstructionSnapshot loadInstruction(
            Connection connection, int homeBankingId, int botJobId, int instructionId)
            throws SQLException {
        String sql =
                "SELECT i.id,i.instruction_order_number,i.actions,i.name,i.client_named,"
                        + " i.operation,i.xpath,i.coordinates,i.force_coordinates,i.iframe_xpath,"
                        + " i.tag_name,i.shadow_host,i.shadow_root,i.css_selector,i.description,"
                        + " i.default_value,i.optional,i.block_marked,i.action_custom_max_wait_sec,"
                        + " i.on_hold_seconds,i.codified,i.export_to_abr,i.active,i.parent_id,"
                        + " i.parent_block_id,b.id AS block_id,b.block_order_number,b.name AS block_name,"
                        + " b.active AS block_active,b.wait AS block_wait,b.export_file,"
                        + " bj.name AS bot_job_name,bj.priority AS bot_job_priority"
                        + " FROM instruction i"
                        + " JOIN block b ON b.id=i.block_id AND b.bot_job_id=i.bot_job_id"
                        + " JOIN bot_job bj ON bj.id=i.bot_job_id"
                        + " WHERE i.id=? AND i.bot_job_id=? AND b.bot_job_id=?"
                        + " AND bj.home_banking_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, instructionId);
            statement.setInt(2, botJobId);
            statement.setInt(3, botJobId);
            statement.setInt(4, homeBankingId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException(
                            "The GridItem instruction does not belong to the active Bot Job.");
                }
                InstructionSnapshot result = new InstructionSnapshot(
                        homeBankingId,
                        botJobId,
                        requiredText(row, "bot_job_name", "Bot Job name"),
                        text(row, "bot_job_priority"),
                        requiredPositive(row, "block_id", "Block id"),
                        requiredPositive(row, "block_order_number", "Block order"),
                        requiredText(row, "block_name", "Block name"),
                        row.getBoolean("block_active"),
                        nullableInteger(row, "block_wait"),
                        text(row, "export_file"),
                        requiredPositive(row, "id", "instruction id"),
                        requiredPositive(row, "instruction_order_number", "instruction order"),
                        requiredText(row, "actions", "instruction action"),
                        text(row, "name"),
                        rawText(row, "client_named"),
                        text(row, "operation"),
                        text(row, "xpath"),
                        text(row, "coordinates"),
                        text(row, "force_coordinates"),
                        text(row, "iframe_xpath"),
                        text(row, "tag_name"),
                        text(row, "shadow_host"),
                        text(row, "shadow_root"),
                        text(row, "css_selector"),
                        text(row, "description"),
                        rawText(row, "default_value"),
                        row.getBoolean("optional"),
                        row.getBoolean("block_marked"),
                        nullableInteger(row, "action_custom_max_wait_sec"),
                        nullableInteger(row, "on_hold_seconds"),
                        row.getBoolean("codified"),
                        row.getBoolean("export_to_abr"),
                        row.getBoolean("active"),
                        nullableInteger(row, "parent_id"),
                        nullableInteger(row, "parent_block_id"),
                        List.of());
                if (row.next()) {
                    throw new SQLException("GridItem test instruction lookup returned duplicates.");
                }
                return result;
            }
        }
    }

    private List<ReferenceSnapshot> loadReferences(
            Connection connection, int botJobId, int instructionId) throws SQLException {
        String sql =
                "SELECT r.id,r.reference_type,r.value"
                        + " FROM reference r"
                        + " JOIN instruction i ON i.id=r.instruction_id AND i.bot_job_id=r.bot_job_id"
                        + " WHERE r.bot_job_id=? AND r.instruction_id=? ORDER BY r.id";
        List<ReferenceSnapshot> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            statement.setInt(2, instructionId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new ReferenceSnapshot(
                            requiredPositive(rows, "id", "reference id"),
                            text(rows, "reference_type"),
                            text(rows, "value")));
                }
            }
        }
        return List.copyOf(result);
    }

    private static int requiredPositive(ResultSet row, String column, String label)
            throws SQLException {
        Integer value = nullableInteger(row, column);
        if (value == null || value <= 0) {
            throw new SQLException("GridItem test " + label + " must be positive.");
        }
        return value;
    }

    private static Integer nullableInteger(ResultSet row, String column) throws SQLException {
        Object value = row.getObject(column);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            throw new SQLException("GridItem test " + column + " must be an integer.", invalid);
        }
    }

    private static String requiredText(ResultSet row, String column, String label)
            throws SQLException {
        String value = rawText(row, column);
        if (value == null || value.isBlank()) {
            throw new SQLException("GridItem test " + label + " is required.");
        }
        return value.trim();
    }

    private static String text(ResultSet row, String column) throws SQLException {
        String value = row.getString(column);
        return value == null ? "" : value;
    }

    private static String rawText(ResultSet row, String column) throws SQLException {
        return row.getString(column);
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    public record ReferenceSnapshot(int id, String type, String value) {
        public ReferenceSnapshot {
            if (id <= 0) throw new IllegalArgumentException("Reference id must be positive");
            type = type == null ? "" : type;
            value = value == null ? "" : value;
        }
    }

    /** Immutable database facts for the one GridItem row being tested. */
    public record InstructionSnapshot(
            int homeBankingId,
            int botJobId,
            String botJobName,
            String botJobPriority,
            int blockId,
            int blockOrder,
            String blockName,
            boolean blockActive,
            Integer blockWait,
            String exportFile,
            int id,
            int instructionOrder,
            String storedAction,
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
            List<ReferenceSnapshot> references) {
        public InstructionSnapshot {
            if (homeBankingId <= 0 || botJobId <= 0 || blockId <= 0 || id <= 0) {
                throw new IllegalArgumentException("GridItem test snapshot IDs must be positive");
            }
            botJobName = Objects.requireNonNull(botJobName, "botJobName");
            botJobPriority = botJobPriority == null ? "" : botJobPriority;
            blockName = Objects.requireNonNull(blockName, "blockName");
            exportFile = exportFile == null ? "" : exportFile;
            storedAction = Objects.requireNonNull(storedAction, "storedAction");
            name = name == null ? "" : name;
            clientNamed = clientNamed == null || clientNamed.isBlank() ? null : clientNamed;
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
            references = List.copyOf(references == null ? List.of() : references);
        }

        InstructionSnapshot withReferences(List<ReferenceSnapshot> next) {
            return new InstructionSnapshot(
                    homeBankingId, botJobId, botJobName, botJobPriority, blockId, blockOrder,
                    blockName, blockActive, blockWait, exportFile, id, instructionOrder,
                    storedAction, name, clientNamed, operation, xpath, coordinates,
                    forceCoordinates, iframeXpath, tagName, shadowHost, shadowRoot, cssSelector,
                    description, defaultValue, optional, blockMarked, actionCustomMaxWaitSec,
                    onHoldSeconds, codified, exportToAbr, active, parentId, parentBlockId, next);
        }

        public String displayKey() {
            return clientNamed == null ? name : clientNamed;
        }

        public InstructionLoad toInstructionLoad(Action requestedAction) {
            InstructionLoad target = new InstructionLoad();
            target.setHomeBankingId(homeBankingId);
            target.setBotJobId(botJobId);
            target.setBotJobName(botJobName);
            target.setPriority(botJobPriority);
            target.setBlockId(blockId);
            target.setBlockOrderNumber(blockOrder);
            target.setBlockName(blockName);
            target.setBlockActive(blockActive);
            target.setBlockWait(blockWait);
            target.setExportFile(exportFile);
            target.setId(id);
            target.setInstructionOrderNumber(instructionOrder);
            target.setActions(requestedAction == Action.CLICK
                    ? ARConstantsEngine.CLICK : ARConstantsEngine.INSERT);
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
            target.setInstructionActive(active);
            target.setParentId(parentId);
            target.setParentBlockId(parentBlockId);
            List<ReferenceLoadDTO> copied = new ArrayList<>();
            for (ReferenceSnapshot reference : references) {
                ReferenceLoadDTO value = new ReferenceLoadDTO();
                value.setId(reference.id());
                value.setHomeBankingId(homeBankingId);
                value.setBotJobId(botJobId);
                value.setInstructionId(id);
                value.setReferenceType(reference.type());
                value.setValue(reference.value());
                copied.add(value);
            }
            target.setReferenceLoadDTOList(copied);
            return target;
        }
    }
}
