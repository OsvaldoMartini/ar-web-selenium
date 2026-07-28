package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * COMPONENTS-ONLY integration test of the real row-move pipeline
 * (InstructionMoveGroupService → InstructionMoveValidator → PerformDataBase.updateMoveRowsOrder)
 * against REAL BancaStato data.
 *
 * Safety: the real database file is COPIED into a JUnit temp dir and PATH_DB is pointed at the
 * copy, so the production database.db is NEVER modified. Each test gets a fresh copy.
 *
 * Scenarios (as specified by the user):
 *  1. Move the FIRST row of the FIRST component block to the LAST position of the SAME block.
 *  2. Move the SECOND row of the FIRST component block to position 3 of the SECOND block.
 *
 * The fixture submits the complete versioned layout, including its parent relationship fields.
 *
 * Tests are skipped (not failed) when the real DB/config are absent (e.g. CI machines).
 */
class ComponentRowMoveRealDbTest {

    private static final String REAL_CONFIG = "D:\\Projects\\ARWebBancaStato\\Config-4.2\\ARWeb.config";
    private static final String REAL_DB = "D:\\Projects\\ARWebBancaStato\\ARWeb\\database.db";

    private final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();

    @TempDir
    Path tempDir;

    private Path dbCopy;
    private int homeBankingId;

    @BeforeEach
    void setup() throws IOException {
        File configFile = new File(REAL_CONFIG);
        File realDb = new File(REAL_DB);
        assumeTrue(configFile.isFile(), "Real config not available: " + REAL_CONFIG);
        assumeTrue(realDb.isFile(), "Real database not available: " + REAL_DB);

        // Load the real config, then repoint PATH_DB at a fresh COPY of the real database so the
        // production file is never mutated. DATABASE_TYPE is forced to TEXT (SQLite) defensively.
        arPropertyManager.setConfigurationFileName(REAL_CONFIG);
        try (FileInputStream fis = new FileInputStream(configFile)) {
            arPropertyManager.loadProperties(fis);
        }
        dbCopy = tempDir.resolve("database.db");
        Files.copy(realDb.toPath(), dbCopy, StandardCopyOption.REPLACE_EXISTING);
        arPropertyManager.setProperty(ARPropertyEnum.PATH_DB.getValue(), tempDir.toString());
        arPropertyManager.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), "TEXT");

        homeBankingId = discoverComponentOwner();
        assumeTrue(homeBankingId > 0, "No component_instruction rows found in the database.");
    }

    // ── Scenario 1: first row of first block → last position of the SAME block ────────────────

    @Test
    @DisplayName("Components: move first row of first block to last position of the same block")
    void movesFirstRowToEndOfSameBlock() {
        List<InstructionLoad> current = loadComponents();
        List<Integer> orderedBlockIds = orderedBlockIds(current);
        assumeTrue(!orderedBlockIds.isEmpty(), "Owner has no populated component blocks.");
        int firstBlockId = orderedBlockIds.get(0);

        List<InstructionLoad> blockRows = blockRows(current, firstBlockId);
        assumeTrue(blockRows.size() >= 2, "First block needs at least 2 rows for this scenario.");

        InstructionLoad movedRow = independentRoot(current, blockRows);
        assumeTrue(movedRow != null, "First block needs an independent root row for this structural fixture.");

        // Build the final layout exactly like the frontend applyDragMove: remove the group from
        // the block, append it at the end, renumber 1..N.
        List<InstructionLoad> remainder = blockRows.stream()
                .filter(row -> !movedRow.getId().equals(row.getId()))
                .toList();
        List<InstructionLoad> reordered = new ArrayList<>(remainder);
        reordered.add(movedRow);
        List<UpdatedRow> updates = fullLayout(current, Map.of(firstBlockId, reordered));

        assertNull(performDataBase.updateMoveRowsOrder("component_block", homeBankingId, updates, null, 2),
                "updateMoveRowsOrder reported an error");

        // Assert against the database copy: the group now occupies the LAST positions in order.
        Map<Integer, List<int[]>> persisted = persistedOrders();
        List<int[]> persistedBlock = persisted.get(firstBlockId);
        assertContiguous(persistedBlock, firstBlockId);
        List<Integer> lastIds = persistedBlock.stream()
                .skip(Math.max(0, persistedBlock.size() - 1))
                .map(row -> row[0])
                .toList();
        assertEquals(List.of(movedRow.getId()), lastIds, "Moved row must occupy the last position of the block");
    }

    // ── Scenario 2: second row of first block → position 3 of the SECOND block ────────────────

    @Test
    @DisplayName("Components: move second row of first block to position 3 of the second block")
    void movesSecondRowOfFirstBlockToThirdPositionOfSecondBlock() {
        List<InstructionLoad> current = loadComponents();
        List<Integer> orderedBlockIds = orderedBlockIds(current);
        assumeTrue(orderedBlockIds.size() >= 2, "Owner needs at least 2 populated component blocks.");
        int firstBlockId = orderedBlockIds.get(0);
        int secondBlockId = orderedBlockIds.get(1);

        List<InstructionLoad> firstBlockRows = blockRows(current, firstBlockId);
        List<InstructionLoad> secondBlockRows = blockRows(current, secondBlockId);
        assumeTrue(firstBlockRows.size() >= 2, "First block needs at least 2 rows.");
        assumeTrue(secondBlockRows.size() >= 2, "Second block needs at least 2 rows for position 3.");

        InstructionLoad movedRow = independentRoot(current, firstBlockRows);
        assumeTrue(movedRow != null, "First block needs an independent root row for this structural fixture.");

        // Destination position 3 → index 2 (clamped to the destination size, like a real drop).
        int destinationIndex = Math.min(2, secondBlockRows.size());

        List<InstructionLoad> sourceRemainder = firstBlockRows.stream()
                .filter(row -> !movedRow.getId().equals(row.getId()))
                .toList();
        List<InstructionLoad> destinationRows = new ArrayList<>(secondBlockRows);
        destinationRows.add(destinationIndex, movedRow);

        List<UpdatedRow> updates = fullLayout(
                current, Map.of(firstBlockId, sourceRemainder, secondBlockId, destinationRows));

        assertNull(performDataBase.updateMoveRowsOrder("component_block", homeBankingId, updates, null, 2),
                "updateMoveRowsOrder reported an error");

        Map<Integer, List<int[]>> persisted = persistedOrders();
        List<int[]> persistedSecond = persisted.get(secondBlockId);
        assertContiguous(persistedSecond, secondBlockId);
        if (persisted.containsKey(firstBlockId)) {
            assertContiguous(persisted.get(firstBlockId), firstBlockId);
        }
        // The moved group sits at the destination position, in order.
        List<Integer> movedSlice = persistedSecond.stream()
                .skip(destinationIndex)
                .limit(1)
                .map(row -> row[0])
                .toList();
        assertEquals(List.of(movedRow.getId()), movedSlice,
                "Moved row must sit at position " + (destinationIndex + 1) + " of the second block");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    /** Owner (home_banking_id) with the most populated component blocks. */
    private int discoverComponentOwner() {
        String sql = "SELECT home_banking_id, COUNT(DISTINCT block_id) AS blocks "
                + "FROM component_instruction GROUP BY home_banking_id ORDER BY blocks DESC, home_banking_id";
        try (Connection conn = openCopy();
                PreparedStatement statement = conn.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt("home_banking_id") : -1;
        } catch (SQLException error) {
            throw new IllegalStateException("Could not inspect component_instruction: " + error.getMessage(), error);
        }
    }

    /** Loads the owner's components through the REAL backend loader (fills getListInstructionComp). */
    private List<InstructionLoad> loadComponents() {
        assertNull(performDataBase.loadInstructions(homeBankingId, -1, -1, "component_instruction"),
                "loadInstructions(component_instruction) reported an error");
        List<InstructionLoad> rows = new ArrayList<>(performLists.getListInstructionComp());
        assumeTrue(!rows.isEmpty(), "Owner " + homeBankingId + " has no component instructions.");
        return rows;
    }

    /** Block ids that contain instructions, ordered by component_block.block_order_number. */
    private List<Integer> orderedBlockIds(List<InstructionLoad> current) {
        List<Integer> populated = current.stream().map(InstructionLoad::getBlockId).distinct().toList();
        List<Integer> ordered = new ArrayList<>();
        String sql = "SELECT id FROM component_block WHERE home_banking_id=? ORDER BY block_order_number, id";
        try (Connection conn = openCopy(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int blockId = rows.getInt("id");
                    if (populated.contains(blockId)) ordered.add(blockId);
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read component_block order: " + error.getMessage(), error);
        }
        return ordered;
    }

    private List<InstructionLoad> blockRows(List<InstructionLoad> current, int blockId) {
        return current.stream()
                .filter(row -> row.getBlockId() != null && row.getBlockId() == blockId)
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
    }

    private InstructionLoad independentRoot(List<InstructionLoad> allRows, List<InstructionLoad> candidates) {
        return candidates.stream()
                .filter(row -> row.getParentId() == null)
                .filter(candidate -> allRows.stream().noneMatch(row -> candidate.getId().equals(row.getParentId())))
                .findFirst()
                .orElse(null);
    }

    private List<UpdatedRow> fullLayout(
            List<InstructionLoad> current, Map<Integer, List<InstructionLoad>> replacements) {
        Map<Integer, Integer> blockOrders = blockOrders();
        List<UpdatedRow> updates = new ArrayList<>();
        for (Integer blockId : orderedBlockIds(current)) {
            List<InstructionLoad> rows = replacements.getOrDefault(blockId, blockRows(current, blockId));
            for (int index = 0; index < rows.size(); index++) {
                InstructionLoad source = rows.get(index);
                UpdatedRow update = new UpdatedRow();
                update.setInstructionId(source.getId());
                update.setBlockId(blockId);
                update.setBlockOrderNumber(blockOrders.get(blockId));
                update.setInstructionOrderNumber(index + 1);
                update.setParentId(source.getParentId());
                update.setParentBlockId(source.getParentBlockId());
                updates.add(update);
            }
        }
        return updates;
    }

    private Map<Integer, Integer> blockOrders() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        String sql = "SELECT id,block_order_number FROM component_block WHERE home_banking_id=?";
        try (Connection conn = openCopy(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getInt("id"), rows.getInt("block_order_number"));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read component block orders: " + error.getMessage(), error);
        }
        return result;
    }

    /** blockId → [instructionId, order] rows read straight from the DB copy, order-sorted. */
    private Map<Integer, List<int[]>> persistedOrders() {
        Map<Integer, List<int[]>> byBlock = new LinkedHashMap<>();
        String sql = "SELECT id, block_id, instruction_order_number FROM component_instruction "
                + "WHERE home_banking_id=? ORDER BY block_id, instruction_order_number";
        try (Connection conn = openCopy(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    byBlock.computeIfAbsent(rows.getInt("block_id"), ignored -> new ArrayList<>())
                            .add(new int[] {rows.getInt("id"), rows.getInt("instruction_order_number")});
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read persisted orders: " + error.getMessage(), error);
        }
        return byBlock;
    }

    private void assertContiguous(List<int[]> blockRows, int blockId) {
        assertTrue(blockRows != null && !blockRows.isEmpty(), "Block " + blockId + " has no rows after the move");
        for (int index = 0; index < blockRows.size(); index++) {
            assertEquals(index + 1, blockRows.get(index)[1],
                    "Block " + blockId + " must be renumbered 1..N without gaps");
        }
    }

    private Connection openCopy() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbCopy);
    }
}
