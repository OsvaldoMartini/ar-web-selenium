package com.allinweb.ch.socket;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;

/**
 * Raises an exact Chromium desktop-app window after its authoritative WebSocket marks the native
 * title with a one-use token.
 *
 * <p>Chrome commonly hands a new {@code --app} request to an existing browser process, so a launch
 * PID cannot identify one AR Web window reliably. The short-lived title token binds the native
 * HWND to the already-validated WebSocket transport without depending on a fuzzy page title.
 */
@Slf4j
final class DesktopWindowFocusService {
    private static final String TOKEN_PREFIX = "ARWEB_FOCUS_";
    private static final long POLL_INTERVAL_NANOS = Duration.ofMillis(25).toNanos();
    private static final HWND HWND_TOPMOST = new HWND(Pointer.createConstant(-1));
    private static final HWND HWND_NOTOPMOST = new HWND(Pointer.createConstant(-2));
    private static final int POSITION_FLAGS =
            WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_SHOWWINDOW;

    String createTitleToken() {
        return TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Waits briefly for Chromium to expose the token as its native title, then raises that exact
     * top-level window. Non-Windows platforms keep the browser-level WebSocket focus fallback.
     */
    boolean focusWindow(String titleToken, Duration timeout) {
        if (!isWindows() || !isValidTitleToken(titleToken)) return false;
        long timeoutNanos = timeout == null ? 0 : Math.max(0, timeout.toNanos());
        long deadline = System.nanoTime() + timeoutNanos;

        try {
            do {
                HWND target = findWindow(titleToken);
                if (target != null) return forceForeground(target);
                if (System.nanoTime() >= deadline) break;
                LockSupport.parkNanos(POLL_INTERVAL_NANOS);
            } while (!Thread.currentThread().isInterrupted());
        } catch (RuntimeException | LinkageError nativeFailure) {
            log.warn("Native AR Web window focus failed: {}", nativeFailure.getMessage());
        }
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isValidTitleToken(String titleToken) {
        if (titleToken == null || !titleToken.startsWith(TOKEN_PREFIX)) return false;
        if (titleToken.length() != TOKEN_PREFIX.length() + 32) return false;
        for (int index = TOKEN_PREFIX.length(); index < titleToken.length(); index++) {
            char value = titleToken.charAt(index);
            boolean hexadecimal = (value >= '0' && value <= '9')
                    || (value >= 'a' && value <= 'f')
                    || (value >= 'A' && value <= 'F');
            if (!hexadecimal) return false;
        }
        return true;
    }

    /**
     * Finds the exact token-bearing top-level window even when Chromium has hidden that HWND.
     *
     * <p>The one-use token is issued only to the authoritative WebSocket transport, so visibility
     * is not an identity boundary. Filtering hidden windows here prevented {@link #forceForeground}
     * from calling {@code ShowWindow} and made a connected detached workspace impossible to
     * recover from Pages Open.
     */
    private static HWND findWindow(String titleToken) {
        User32 user32 = User32.INSTANCE;
        AtomicReference<HWND> match = new AtomicReference<>();
        user32.EnumWindows(
                (window, ignored) -> {
                    int titleLength = user32.GetWindowTextLength(window);
                    if (titleLength < titleToken.length()) return true;
                    char[] title = new char[titleLength + 1];
                    user32.GetWindowText(window, title, title.length);
                    String value = com.sun.jna.Native.toString(title);
                    if (!titleContainsToken(value, titleToken)) return true;
                    match.set(window);
                    return false;
                },
                null);
        return match.get();
    }

    static boolean titleContainsToken(String windowTitle, String titleToken) {
        return isValidTitleToken(titleToken)
                && windowTitle != null
                && windowTitle.contains(titleToken);
    }

    private static boolean forceForeground(HWND target) {
        User32 user32 = User32.INSTANCE;
        restoreIfMinimized(user32, target);

        HWND previousForeground = user32.GetForegroundWindow();
        int currentThreadId = Kernel32.INSTANCE.GetCurrentThreadId();
        int foregroundThreadId = windowThreadId(user32, previousForeground);
        int targetThreadId = windowThreadId(user32, target);
        boolean attachedForeground = attachThreadInput(user32, currentThreadId, foregroundThreadId);
        boolean attachedTarget = attachThreadInput(user32, currentThreadId, targetThreadId);

        try {
            user32.BringWindowToTop(target);
            user32.SetForegroundWindow(target);
            user32.SetFocus(target);
            if (sameWindow(user32.GetForegroundWindow(), target)) return true;

            // Windows may still enforce foreground-lock rules. A short topmost pulse is the final
            // fallback and immediately restores the target's normal non-topmost behavior.
            user32.SetWindowPos(target, HWND_TOPMOST, 0, 0, 0, 0, POSITION_FLAGS);
            user32.SetWindowPos(target, HWND_NOTOPMOST, 0, 0, 0, 0, POSITION_FLAGS);
            user32.BringWindowToTop(target);
            user32.SetForegroundWindow(target);
            user32.SetFocus(target);
            return sameWindow(user32.GetForegroundWindow(), target);
        } finally {
            if (attachedTarget) {
                user32.AttachThreadInput(
                        new DWORD(Integer.toUnsignedLong(currentThreadId)),
                        new DWORD(Integer.toUnsignedLong(targetThreadId)),
                        false);
            }
            if (attachedForeground) {
                user32.AttachThreadInput(
                        new DWORD(Integer.toUnsignedLong(currentThreadId)),
                        new DWORD(Integer.toUnsignedLong(foregroundThreadId)),
                        false);
            }
        }
    }

    private static void restoreIfMinimized(User32 user32, HWND target) {
        WinUser.WINDOWPLACEMENT placement = new WinUser.WINDOWPLACEMENT();
        placement.length = placement.size();
        boolean placementLoaded = user32.GetWindowPlacement(target, placement).booleanValue();
        if (placementLoaded
                && (placement.showCmd == WinUser.SW_SHOWMINIMIZED
                        || placement.showCmd == WinUser.SW_SHOWMINNOACTIVE)) {
            user32.ShowWindow(target, WinUser.SW_RESTORE);
        } else {
            user32.ShowWindow(target, WinUser.SW_SHOW);
        }
    }

    private static int windowThreadId(User32 user32, HWND window) {
        if (window == null) return 0;
        return user32.GetWindowThreadProcessId(window, new IntByReference());
    }

    private static boolean attachThreadInput(User32 user32, int currentThreadId, int otherThreadId) {
        if (currentThreadId == 0 || otherThreadId == 0 || currentThreadId == otherThreadId) {
            return false;
        }
        return user32.AttachThreadInput(
                new DWORD(Integer.toUnsignedLong(currentThreadId)),
                new DWORD(Integer.toUnsignedLong(otherThreadId)),
                true);
    }

    private static boolean sameWindow(HWND first, HWND second) {
        if (first == null || second == null) return false;
        return Pointer.nativeValue(first.getPointer()) == Pointer.nativeValue(second.getPointer());
    }
}
