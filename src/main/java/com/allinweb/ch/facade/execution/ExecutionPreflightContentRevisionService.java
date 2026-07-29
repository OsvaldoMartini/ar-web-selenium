package com.allinweb.ch.facade.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

/**
 * Computes the content revision of every fact consumed by execution relationship preflight.
 *
 * <p>This is deliberately separate from the mutation graph revision. Until P5 gives every writer
 * one shared database graph version, this digest identifies the exact loaded content that was
 * observed but is not advertised as an atomic concurrency token.
 */
public final class ExecutionPreflightContentRevisionService {

    public String revision(ExecutionPreflightSnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(
                    digest,
                    "OWNER|"
                            + snapshot.owner().homeBankingId()
                            + "|"
                            + snapshot.owner().botJobId());

            snapshot.blocks().stream()
                    .sorted(Comparator.comparingInt(ExecutionPreflightSnapshot.BlockFact::id))
                    .forEach(block -> update(
                            digest,
                            "BLOCK|"
                                    + block.id()
                                    + "|"
                                    + block.order()
                                    + "|"
                                    + block.active()));

            snapshot.instructions().stream()
                    .sorted(Comparator.comparingInt(
                            ExecutionPreflightSnapshot.InstructionFact::id))
                    .forEach(row -> update(
                            digest,
                            "ROW|"
                                    + row.id()
                                    + "|"
                                    + row.blockId()
                                    + "|"
                                    + row.order()
                                    + "|"
                                    + value(row.action())
                                    + "|"
                                    + value(row.tagName())
                                    + "|"
                                    + row.active()
                                    + "|"
                                    + value(row.parentId())
                                    + "|"
                                    + value(row.parentBlockId())
                                    + "|"
                                    + value(row.variableId())));

            snapshot.variables().stream()
                    .sorted(Comparator.comparingInt(
                            ExecutionPreflightSnapshot.VariableFact::id))
                    .forEach(variable -> update(
                            digest,
                            "VARIABLE|"
                                    + variable.id()
                                    + "|"
                                    + value(variable.type())
                                    + "|"
                                    + value(variable.ownerInstructionId())));

            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, String fact) {
        digest.update((fact + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String value(Object value) {
        return String.valueOf(value);
    }
}
