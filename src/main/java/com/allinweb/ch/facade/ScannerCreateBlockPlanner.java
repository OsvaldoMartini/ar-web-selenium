package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import java.util.ArrayList;
import java.util.List;

public final class ScannerCreateBlockPlanner {

    public int computeInsertOrderNumber(String positionLabel, List<BlockLoadDTO> existingSorted) {
        if (positionLabel == null || positionLabel.startsWith("At end")) {
            int max = 0;
            for (BlockLoadDTO block : existingSorted) {
                if (block.getBlockOrderNumber() != null && block.getBlockOrderNumber() > max) {
                    max = block.getBlockOrderNumber();
                }
            }
            return max + 1;
        }

        try {
            int hash = positionLabel.indexOf('#');
            int start = "Before ".length();
            if (hash > start) {
                return Integer.parseInt(positionLabel.substring(start, hash).trim());
            }
        } catch (NumberFormatException ignore) {
            // Invalid labels fall through to append-at-end compatibility.
        }
        return existingSorted.size() + 1;
    }

    public String buildCreateBlockPreview(int targetOrder, List<BlockLoadDTO> existingSorted) {
        List<BlockLoadDTO> shifted = shiftedBlocks(targetOrder, existingSorted);
        if (shifted.isEmpty()) {
            return "New block will be #" + targetOrder + ". No existing blocks are affected.";
        }

        StringBuilder preview = new StringBuilder();
        preview.append("New block will be #")
                .append(targetOrder)
                .append(". Existing blocks will shift down by one:");
        for (BlockLoadDTO block : shifted) {
            preview.append(System.lineSeparator())
                    .append("  \u2022 ")
                    .append(block.getBlockOrderNumber())
                    .append("# ")
                    .append(block.getName())
                    .append("  \u2192  ")
                    .append(block.getBlockOrderNumber() + 1)
                    .append("# ")
                    .append(block.getName());
        }
        return preview.toString();
    }

    public List<BlockLoadDTO> buildRenumberPlan(int botJobId, int targetOrder, List<BlockLoadDTO> blocks) {
        List<BlockLoadDTO> toRenumber = new ArrayList<>();
        for (BlockLoadDTO block : blocks) {
            if (block.getBotJobId() == null || !block.getBotJobId().equals(botJobId)) {
                continue;
            }
            if (block.getBlockOrderNumber() == null || block.getBlockOrderNumber() < targetOrder) {
                continue;
            }

            BlockLoadDTO shifted = new BlockLoadDTO();
            shifted.setId(block.getId());
            shifted.setBlockOrderNumber(block.getBlockOrderNumber() + 1);
            shifted.setBotJobId(botJobId);
            shifted.setHomeBankingId(block.getHomeBankingId());
            toRenumber.add(shifted);
        }
        return toRenumber;
    }

    private List<BlockLoadDTO> shiftedBlocks(int targetOrder, List<BlockLoadDTO> existingSorted) {
        List<BlockLoadDTO> shifted = new ArrayList<>();
        for (BlockLoadDTO block : existingSorted) {
            if (block.getBlockOrderNumber() != null && block.getBlockOrderNumber() >= targetOrder) {
                shifted.add(block);
            }
        }
        return shifted;
    }
}
