package com.allinweb.ch.facade;

import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates blocks owned by an organization in the reusable Components workspace.
 *
 * <p>This is intentionally separate from {@link BlockCreationService}. Bot Job blocks are scoped
 * by {@code bot_job_id}; component blocks are scoped by {@code home_banking_id}. Reusing the Bot
 * Job service for a {@code componentTasks} request would write into the production Bot Job instead
 * of the component library.
 */
@Slf4j
public final class ComponentBlockCreationService {

    private static final ComponentBlockCreationService INSTANCE =
            new ComponentBlockCreationService();
    private static final int MAX_COMPLETED_REQUESTS = 256;

    private final PerformDataBase database = PerformDataBase.getInstance();
    private final LinkedHashMap<String, CompletedRequest> completedRequests =
            new LinkedHashMap<>(16, 0.75f, true);

    private ComponentBlockCreationService() {}

    public static ComponentBlockCreationService getInstance() {
        return INSTANCE;
    }

    public enum Position {
        END,
        BEFORE
    }

    public record Result(ErrorMessage error, Integer newBlockId, Integer newBlockOrderNumber) {}

    public synchronized Result createFrom(SplitDTO dto) {
        if (dto == null || dto.getHomeBankingId() == null || dto.getHomeBankingId() <= 0) {
            return failure("Missing organization id", null);
        }
        if (dto.getBlockName() == null || dto.getBlockName().trim().isEmpty()) {
            return failure("Missing block name", null);
        }

        int ownerId = dto.getHomeBankingId();
        String requestKey = requestKey(dto);
        String fingerprint = fingerprint(dto);
        if (requestKey != null) {
            CompletedRequest completed = completedRequests.get(requestKey);
            if (completed != null) {
                if (completed.fingerprint().equals(fingerprint)) return completed.result();
                return failure(
                        "Request id was already used for different component block data",
                        dto.getRequestId());
            }
        }

        InsertedBlock inserted;
        Connection connection = null;
        try {
            connection = database.getConnection();
            inserted = insertBlockTransaction(
                    connection,
                    ownerId,
                    dto.getBlockName().trim(),
                    parsePosition(dto.getInsertPosition()),
                    dto.getBeforeBlockId(),
                    dto.getBeforeBlockOrderNumber());
        } catch (SQLException | RuntimeException error) {
            log.warn(
                    "Component block creation failed for organization {}: {}",
                    ownerId,
                    error.getMessage());
            return failure("Could not create the component block", error.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    // insertBlockTransaction has already committed. Reporting failure here would
                    // invite a retry that creates a duplicate block.
                    log.warn(
                            "Committed component block connection could not be closed for organization {}",
                            ownerId,
                            closeFailure);
                }
            }
        }

        ErrorMessage refresh = database.loadBlocks(ownerId, "", "component_block");
        if (refresh == null) {
            refresh = database.loadComponentsComplete(
                    ownerId,
                    dto.getBotJobId() == null ? -1 : dto.getBotJobId(),
                    dto.getBotJobName() == null ? "" : dto.getBotJobName());
        }
        if (refresh != null) {
            log.warn(
                    "Component block {} committed for organization {}, but cache refresh is pending: {}",
                    inserted.blockId(),
                    ownerId,
                    refresh.getErrorMessage());
        }
        Result result = new Result(null, inserted.blockId(), inserted.orderNumber());
        if (requestKey != null) {
            completedRequests.put(requestKey, new CompletedRequest(fingerprint, result));
            while (completedRequests.size() > MAX_COMPLETED_REQUESTS) {
                completedRequests.remove(completedRequests.keySet().iterator().next());
            }
        }
        return result;
    }

    static InsertedBlock insertBlockTransaction(
            Connection connection,
            int homeBankingId,
            String blockName,
            Position position,
            Integer beforeBlockId,
            Integer beforeBlockOrderNumber)
            throws SQLException {
        connection.setAutoCommit(false);
        try {
            int targetOrder = computeOrderAndShift(
                    connection,
                    homeBankingId,
                    position,
                    beforeBlockId,
                    beforeBlockOrderNumber);
            int newId = insertBlock(connection, homeBankingId, blockName, targetOrder);
            connection.commit();
            return new InsertedBlock(newId, targetOrder);
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException restoreFailure) {
                log.debug("Could not restore component block connection auto-commit", restoreFailure);
            }
        }
    }

    private static int computeOrderAndShift(
            Connection connection,
            int homeBankingId,
            Position position,
            Integer beforeBlockId,
            Integer beforeBlockOrderNumber)
            throws SQLException {
        int maximumOrder = 0;
        Integer referenceOrder = null;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, block_order_number FROM component_block WHERE home_banking_id = ?")) {
            select.setInt(1, homeBankingId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    int order = rows.getInt("block_order_number");
                    maximumOrder = Math.max(maximumOrder, order);
                    if (beforeBlockId != null && rows.getInt("id") == beforeBlockId) {
                        referenceOrder = order;
                    }
                }
            }
        }

        if (position == Position.END) {
            return maximumOrder + 1;
        }

        if (beforeBlockId != null && beforeBlockId > 0 && referenceOrder == null) {
            throw new SQLException(
                    "BEFORE component block is missing or belongs to another organization");
        }

        int targetOrder =
                referenceOrder != null ? referenceOrder : valueOrMinusOne(beforeBlockOrderNumber);
        if (targetOrder <= 0) {
            throw new SQLException(
                    "BEFORE requires a component block owned by the active organization");
        }
        try (PreparedStatement shift = connection.prepareStatement(
                "UPDATE component_block SET block_order_number = block_order_number + 1 "
                        + "WHERE home_banking_id = ? AND block_order_number >= ?")) {
            shift.setInt(1, homeBankingId);
            shift.setInt(2, targetOrder);
            shift.executeUpdate();
        }
        return targetOrder;
    }

    private static int insertBlock(
            Connection connection, int homeBankingId, String blockName, int targetOrder)
            throws SQLException {
        String sql = "INSERT INTO component_block "
                + "(home_banking_id, block_order_number, name, description, type_id, "
                + "export_file, active, wait) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, homeBankingId);
            insert.setInt(2, targetOrder);
            insert.setString(3, blockName);
            insert.setString(4, blockName + " description");
            insert.setInt(5, 1);
            insert.setString(6, null);
            insert.setInt(7, 1);
            insert.setInt(8, 3);
            if (insert.executeUpdate() != 1) {
                throw new SQLException("Component block insert did not create exactly one row");
            }
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Component block insert returned no generated id");
                }
                return keys.getInt(1);
            }
        }
    }

    private static Position parsePosition(String value) {
        if (value == null) return Position.END;
        return "BEFORE".equals(value.trim().toUpperCase(Locale.ROOT))
                ? Position.BEFORE
                : Position.END;
    }

    private static int valueOrMinusOne(Integer value) {
        return value == null ? -1 : value;
    }

    private static String requestKey(SplitDTO dto) {
        String requestId = dto.getRequestId();
        if (requestId == null || requestId.isBlank()) return null;
        return dto.getHomeBankingId() + ":" + requestId.trim();
    }

    private static String fingerprint(SplitDTO dto) {
        return String.join(
                "|",
                String.valueOf(dto.getHomeBankingId()),
                dto.getBlockName() == null ? "" : dto.getBlockName().trim(),
                parsePosition(dto.getInsertPosition()).name(),
                String.valueOf(dto.getBeforeBlockId()),
                String.valueOf(dto.getBeforeBlockOrderNumber()));
    }

    private static Result failure(String message, String detail) {
        return new Result(
                new ErrorMessage("Create Component Block", message, detail), null, null);
    }

    record InsertedBlock(int blockId, int orderNumber) {}

    private record CompletedRequest(String fingerprint, Result result) {}
}
