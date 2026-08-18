package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockOptions;
import java.util.Collection;
import java.util.Optional;

public final class ScannerCreatedBlockSelectionService {
    private final ScannerBlockOptionSelectionService blockOptionSelectionService =
            new ScannerBlockOptionSelectionService();

    public Optional<BlockOptions> findCreatedBlock(Collection<BlockOptions> options, String blockName) {
        if (options == null || blockName == null) {
            return Optional.empty();
        }

        return options.stream()
                .filter(option -> option != null
                        && option.getBlockId() != null
                        && !blockOptionSelectionService.isCreateBlockSentinel(option)
                        && blockName.equalsIgnoreCase(option.getValue()))
                .findFirst();
    }
}
