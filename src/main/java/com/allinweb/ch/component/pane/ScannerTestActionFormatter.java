package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.util.InputFlags;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ScannerTestActionFormatter {

    String describeInputFlags(InputFlags flags) {
        List<String> parts = new ArrayList<>(5);
        if (flags.hasScroll()) parts.add("Scroll");
        if (flags.hasNext()) parts.add("Next (mobile)");
        if (flags.hasTab()) parts.add("Tab");
        if (flags.hasEnter()) parts.add("Enter");
        if (flags.hasForce()) parts.add("Force Coordinates");
        return parts.isEmpty() ? "(no flags)" : String.join(", ", parts);
    }

    String safeTargetLabel(TargetElement target) {
        String definedName = target.getDefinedName();
        String tag = target.getTagName();
        if (!Strings.isNullOrEmpty(definedName)) {
            return definedName + (Strings.isNullOrEmpty(tag) ? "" : " &lt;" + tag + "&gt;");
        }
        if (!Strings.isNullOrEmpty(tag)) {
            return "&lt;" + tag + "&gt;";
        }
        return "(unnamed)";
    }

    boolean sameUrl(String first, String second) {
        return canonicalUrl(first).equals(canonicalUrl(second));
    }

    private String canonicalUrl(String url) {
        if (url == null) return "";
        String value = url.trim();
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.toLowerCase(Locale.ROOT);
    }
}
