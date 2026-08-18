package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DesktopWindowFocusServiceTest {
    @Test
    void acceptsOnlyTheExactServerIssuedTokenInAChromiumWindowTitle() {
        String token = "ARWEB_FOCUS_0123456789abcdef0123456789abcdef";

        assertTrue(DesktopWindowFocusService.titleContainsToken(
                token + " - Bot Job Details", token));
        assertFalse(DesktopWindowFocusService.titleContainsToken(
                "Bot Job Details", token));
        assertFalse(DesktopWindowFocusService.titleContainsToken(
                "ARWEB_FOCUS_not-a-valid-token - Bot Job Details",
                "ARWEB_FOCUS_not-a-valid-token"));
    }
}
