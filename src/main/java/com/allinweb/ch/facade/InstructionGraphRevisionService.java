package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/** Computes a deterministic revision for an authoritative instruction graph. */
public final class InstructionGraphRevisionService {
    public String revision(List<InstructionLoad> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            rows.stream()
                    .filter(row -> row != null)
                    .sorted(Comparator.comparing(row -> row.getId() == null ? Integer.MAX_VALUE : row.getId()))
                    .forEach(row -> digest.update(graphRow(row).getBytes(StandardCharsets.UTF_8)));
            byte[] hash = digest.digest();
            StringBuilder revision = new StringBuilder(hash.length * 2);
            for (byte value : hash) revision.append(String.format("%02x", value));
            return revision.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String graphRow(InstructionLoad row) {
        return String.join("|",
                String.valueOf(row.getId()), String.valueOf(row.getBlockId()),
                String.valueOf(row.getInstructionOrderNumber()), String.valueOf(row.getActions()),
                String.valueOf(row.getParentId()), String.valueOf(row.getParentBlockId()),
                String.valueOf(row.getVariableId()), String.valueOf(row.getOperation())) + "\n";
    }
}
