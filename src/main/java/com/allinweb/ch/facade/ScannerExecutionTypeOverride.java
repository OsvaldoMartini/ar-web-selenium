package com.allinweb.ch.facade;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.TargetElement;
import com.google.common.base.Strings;
import java.util.Locale;

/** Applies the transient Page Scanner INPUT/OUTPUT/CLICK choice to instruction staging. */
final class ScannerExecutionTypeOverride {
    enum Status {
        ABSENT,
        APPLIED,
        INVALID
    }

    private ScannerExecutionTypeOverride() {}

    static Status apply(ElementDTO source, TargetElement target) {
        if (source == null || target == null) return Status.INVALID;
        String value = source.getExecutionTypeOverride();
        if (value == null) return Status.ABSENT;

        final ExecutionType type;
        try {
            type = ExecutionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return Status.INVALID;
        }
        type.apply(target);

        // The override changes execution semantics only. Keep the scanned physical DOM tag so
        // locator metadata and later SET compatibility remain truthful.
        if (!Strings.isNullOrEmpty(source.getTagName())) {
            target.setTagName(source.getTagName());
        }
        return Status.APPLIED;
    }

    private enum ExecutionType {
        INPUT(WebElementTagNameEnum.INPUT, false),
        OUTPUT(WebElementTagNameEnum.OUTPUT, false),
        CLICK(WebElementTagNameEnum.BUTTON, true);

        private final WebElementTagNameEnum tagType;
        private final boolean clickElement;

        ExecutionType(WebElementTagNameEnum tagType, boolean clickElement) {
            this.tagType = tagType;
            this.clickElement = clickElement;
        }

        private void apply(TargetElement target) {
            target.setTagType(tagType);
            target.setClickElement(clickElement);
        }
    }
}
