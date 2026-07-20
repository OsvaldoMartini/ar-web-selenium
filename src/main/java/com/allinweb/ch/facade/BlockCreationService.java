package com.allinweb.ch.facade;

import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates a new block at a chosen position for the front-end "Create new block" flow
 * (CreateNewBlock.tsx): a name plus a position — at the END, or BEFORE / AFTER a selected block.
 *
 * <p>Extracted into its own service so {@code SimpleWebSocketServer} and {@code PerformDataBase}
 * stay thin — the socket handler is a one-line delegation. It reuses existing persistence
 * ({@link PerformDataBase#insertNewBlock}, {@link PerformDataBase#loadBlocks}) and only adds the
 * position math: to insert at a slot it first shifts the existing {@code block_order_number}s up by
 * one from that slot, so ordering stays contiguous (mirrors the FE memory-reorder-then-persist).
 *
 * <p>WebSocket contract ({@code type = "CREATE_BLOCK"} on a {@link SplitDTO}):
 * <ul>
 *   <li>{@code botJobId} — required</li>
 *   <li>{@code blockName} — new block name (defaults to "New Block" if blank)</li>
 *   <li>{@code operationId} — position: {@code "END"} (default) | {@code "BEFORE"} | {@code "AFTER"}</li>
 *   <li>{@code blockId} — the reference block for BEFORE/AFTER (ignored for END)</li>
 * </ul>
 */
@Slf4j
public final class BlockCreationService {

    private static volatile BlockCreationService instance;

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();

    private BlockCreationService() {}

    public static BlockCreationService getInstance() {
        if (instance == null) {
            synchronized (BlockCreationService.class) {
                if (instance == null) {
                    instance = new BlockCreationService();
                }
            }
        }
        return instance;
    }

    public enum Position {
        END,
        BEFORE
    }

    /** Outcome: {@code error} is null on success and {@code newBlockId} carries the created id. */
    public record Result(ErrorMessage error, Integer newBlockId, Integer newBlockOrderNumber) {}

    /** Entry for the {@code BLOCK_CREATE} websocket message; maps the SplitDTO to createBlock. */
    public Result createFrom(SplitDTO dto) {
        if (dto == null || dto.getBotJobId() == null || dto.getBotJobId() <= 0) {
            return failure("Missing bot job id", null);
        }
        if (dto.getBlockName() == null || dto.getBlockName().trim().isEmpty()) {
            return failure("Missing block name", null);
        }
        return createBlock(
                dto.getBotJobId(),
                dto.getBlockName().trim(),
                parsePosition(dto.getInsertPosition()),
                dto.getBeforeBlockId(),
                dto.getBeforeBlockOrderNumber());
    }

    /**
     * Create a block named {@code name} at {@code position} (relative to {@code referenceBlockId} for
     * BEFORE/AFTER), keeping {@code block_order_number} contiguous. Refreshes the in-memory block list.
     */
    public synchronized Result createBlock(
            int botJobId, String blockName, Position position, Integer beforeBlockId, Integer beforeBlockOrderNumber) {
        int targetOrder;
        Integer newId;
        try (Connection conn = performDataBase.getConnection()) {
            InsertedBlock inserted = insertBlockTransaction(
                    conn, botJobId, blockName, position, beforeBlockId, beforeBlockOrderNumber);
            targetOrder = inserted.orderNumber();
            newId = inserted.blockId();
        } catch (Exception e) {
            log.warn("createBlock - atomic insert failed (bot={}): {}", botJobId, e.getMessage());
            return failure("Could not create the new block", e.getMessage());
        }

        ErrorMessage err = performDataBase.loadBlocks(botJobId, "", "block");
        if (err == null) {
            err = performDBEngine.loadCompleteJobs(botJobId);
        }
        if (err != null) {
            return new Result(err, newId, targetOrder);
        }

        log.info(
                "createBlock - '{}' at order {} (position={}, ref={}) -> id {}",
                blockName,
                targetOrder,
                position,
                beforeBlockId,
                newId);
        return new Result(null, newId, targetOrder);
    }

    static InsertedBlock insertBlockTransaction(
            Connection conn,
            int botJobId,
            String blockName,
            Position position,
            Integer beforeBlockId,
            Integer beforeBlockOrderNumber) throws SQLException {
        conn.setAutoCommit(false);
        try {
            int targetOrder = computeOrderAndShift(
                    conn, botJobId, position, beforeBlockId, beforeBlockOrderNumber);
            int newId = insertBlock(conn, botJobId, blockName, targetOrder);
            conn.commit();
            return new InsertedBlock(newId, targetOrder);
        } catch (SQLException | RuntimeException failure) {
            try {
                conn.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static Integer insertBlock(
            Connection conn, int botJobId, String blockName, int targetOrder) throws SQLException {
        String insertSql = "INSERT INTO block "
                + "(block_order_number, description, name, type_id, active, wait, bot_job_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert =
                        conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, targetOrder);
            insert.setString(2, blockName + " description");
            insert.setString(3, blockName);
            insert.setInt(4, 1);
            insert.setInt(5, 1);
            insert.setInt(6, 3);
            insert.setInt(7, botJobId);
            if (insert.executeUpdate() != 1) {
                throw new SQLException("Block insert did not create exactly one row");
            }
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Block insert returned no generated id");
                }
                return keys.getInt(1);
            }
        }
    }

    /**
     * Determine the new block's order number and open a slot for it by shifting existing blocks:
     * END → max+1 (no shift); BEFORE ref → ref's order (shift ref and everything after up by 1);
     * AFTER ref → ref's order + 1 (shift everything after ref up by 1). Falls back to END when the
     * reference block can't be found.
     */
    private static int computeOrderAndShift(
            Connection conn, int botJobId, Position position, Integer beforeBlockId, Integer beforeBlockOrderNumber)
            throws SQLException {
        int maxOrder = 0;
        Integer refOrder = null;
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT id, block_order_number FROM block WHERE bot_job_id = ?")) {
            ps.setInt(1, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ord = rs.getInt("block_order_number");
                    if (ord > maxOrder) {
                        maxOrder = ord;
                    }
                    if (beforeBlockId != null && rs.getInt("id") == beforeBlockId) {
                        refOrder = ord;
                    }
                }
            }
        }

        if (position == Position.END) {
            return maxOrder + 1;
        }

        int target = refOrder != null ? refOrder : valueOrMinusOne(beforeBlockOrderNumber);
        if (target <= 0) {
            throw new SQLException("BEFORE requires a valid beforeBlockId or beforeBlockOrderNumber");
        }

        try (PreparedStatement up = conn.prepareStatement("UPDATE block SET block_order_number = "
                + "block_order_number + 1 WHERE bot_job_id = ? AND block_order_number >= ?")) {
            up.setInt(1, botJobId);
            up.setInt(2, target);
            up.executeUpdate();
        }
        return target;
    }

    private static Result failure(String message, String detail) {
        return new Result(new ErrorMessage("Create Block", message, detail), null, null);
    }

    private static int valueOrMinusOne(Integer value) {
        return value == null ? -1 : value;
    }

    private static Position parsePosition(String insertPosition) {
        if (insertPosition == null) {
            return Position.END;
        }
        switch (insertPosition.trim().toUpperCase(Locale.ROOT)) {
            case "BEFORE":
                return Position.BEFORE;
            default:
                return Position.END;
        }
    }

    record InsertedBlock(int blockId, int orderNumber) {}
}
