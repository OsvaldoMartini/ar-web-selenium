package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.BlockLoadDTO;
import java.util.ArrayList;
import java.util.List;

final class ScannerCreateBlockPlanner {

    int computeInsertOrderNumber(String positionLabel, List<BlockLoadDTO> existingSorted) {
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

    String buildCreateBlockPreview(int targetOrder, List<BlockLoadDTO> existingSorted) {
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
