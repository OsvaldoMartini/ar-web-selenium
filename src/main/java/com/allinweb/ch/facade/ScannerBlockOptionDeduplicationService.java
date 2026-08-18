package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockOptions;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class ScannerBlockOptionDeduplicationService {

    public Predicate<BlockOptions> distinctByText() {
        Set<String> seen = new HashSet<>();
        return option -> seen.add(option.getText());
    }

    public Predicate<BlockOptions> distinctByTextAndBlockId() {
        Set<String> seen = new HashSet<>();
        return option -> seen.add(option.getText() + "#" + option.getBlockId());
    }
}
