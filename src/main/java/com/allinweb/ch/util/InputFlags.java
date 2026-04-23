package com.allinweb.ch.util;

/**
 * Post-input behaviour flags, stored as a bitfield in the existing
 * {@code instruction.force_coordinates} INTEGER column.
 *
 * <p>Bit layout:
 * <pre>
 *   bit 0  (1)  F  — Force coordinates (use elementFromPoint even when XPath matches)
 *   bit 1  (2)  E  — Press ENTER after input (pressEnterStrong: sendKeys + JS KeyboardEvent)
 *   bit 2  (4)  T  — Press TAB after input
 *   bit 3  (8)  N  — Press NEXT after input (mobile IME "Next"; cascades N→T→E when solo)
 * </pre>
 * </p>
 *
 * <p>Historical compatibility: a row with {@code force_coordinates = 1} (previous
 * boolean "true") is still interpreted as F only, which is exactly the previous
 * behaviour. {@code 0} means no flags, same as before.</p>
 *
 * <p>Combining rule (cascade, Rule A): when ONLY N is set, execution cascades
 * N → T → E on failure. For any explicit combination (NT, NE, NTE, …) every
 * checked key fires in the order N, E, T with NO cascade — the user opted in
 * deliberately.</p>
 */
public final class InputFlags {

    public static final int NONE = 0;
    public static final int FORCE = 1 << 0; // 1
    public static final int ENTER = 1 << 1; // 2
    public static final int TAB = 1 << 2; // 4
    public static final int NEXT = 1 << 3; // 8

    private final int value;

    private InputFlags(int value) {
        this.value = value & 0xF; // keep within 4 bits
    }

    public static InputFlags of(int value) {
        return new InputFlags(value);
    }

    public static InputFlags of(Integer value) {
        return new InputFlags(value == null ? 0 : value);
    }

    /**
     * Parse a flag string (any order, case-insensitive) like {@code "FE"}, {@code "NT"},
     * {@code ""}, etc. This is the primary constructor once the DB column is VARCHAR.
     */
    public static InputFlags of(String flags) {
        if (flags == null) return new InputFlags(0);
        int v = 0;
        for (int i = 0; i < flags.length(); i++) {
            char c = Character.toUpperCase(flags.charAt(i));
            switch (c) {
                case 'F':
                    v |= FORCE;
                    break;
                case 'E':
                    v |= ENTER;
                    break;
                case 'T':
                    v |= TAB;
                    break;
                case 'N':
                    v |= NEXT;
                    break;
                default: /* ignore stray chars */
                    break;
            }
        }
        return new InputFlags(v);
    }

    /** Convenience for legacy callers that still pass a boolean press-enter flag. */
    public static InputFlags ofLegacy(String forceCoordinates, boolean pressEnterAfter) {
        int v = of(forceCoordinates).asInt();
        if (pressEnterAfter) v |= ENTER;
        return new InputFlags(v);
    }

    public int asInt() {
        return value;
    }

    public boolean hasForce() {
        return (value & FORCE) != 0;
    }

    public boolean hasEnter() {
        return (value & ENTER) != 0;
    }

    public boolean hasTab() {
        return (value & TAB) != 0;
    }

    public boolean hasNext() {
        return (value & NEXT) != 0;
    }

    /**
     * True when NEXT is set AND no other key flag is set — triggers the
     * N → T → E cascade-with-fallback path in PerformActions.
     * (FORCE is a locator concern, not a key; it doesn't disqualify cascade.)
     */
    public boolean isNextSolo() {
        return hasNext() && !hasEnter() && !hasTab();
    }

    /** Short label for logs, e.g. "F", "FE", "NET". Empty string for NONE. */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder(4);
        if (hasForce()) sb.append('F');
        if (hasEnter()) sb.append('E');
        if (hasTab()) sb.append('T');
        if (hasNext()) sb.append('N');
        return sb.toString();
    }

    @Override
    public String toString() {
        String c = toCompactString();
        return "InputFlags[" + value + (c.isEmpty() ? "" : "," + c) + "]";
    }
}
