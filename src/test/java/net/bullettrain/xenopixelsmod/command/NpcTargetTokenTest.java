package net.bullettrain.xenopixelsmod.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcTargetTokenTest {

    @Test
    void theDialogPlayerTokensAreRecognised() {
        assertTrue(NpcTargetToken.isDialogPlayer("@dp"));
        assertTrue(NpcTargetToken.isDialogPlayer("{RefPlayer}"));
        assertTrue(NpcTargetToken.isDialogPlayer("  @DP  "));
    }

    @Test
    void ordinarySelectorsAreNotDialogPlayerTokens() {
        // These must fall through to the vanilla selector, not be hijacked.
        assertFalse(NpcTargetToken.isDialogPlayer("@a"));
        assertFalse(NpcTargetToken.isDialogPlayer("@p"));
        assertFalse(NpcTargetToken.isDialogPlayer("Steve"));
        assertFalse(NpcTargetToken.isDialogPlayer(null));
    }

    @Test
    void aPlayerIsFoundByUsername() {
        assertTrue(NpcTargetToken.matches("Steve", "Steve", "§6[VIP] Stevie"));
        assertTrue(NpcTargetToken.matches("steve", "Steve", "Stevie"));
    }

    @Test
    void aPlayerIsAlsoFoundByDisplayName() {
        // This is the case that actually broke: the NPC mods substitute the display name, and a
        // nicknamed player's display name is not their username.
        assertTrue(NpcTargetToken.matches("Stevie", "Steve", "Stevie"));
    }

    @Test
    void formattingCodesInADisplayNameAreIgnored() {
        assertTrue(NpcTargetToken.matches("[VIP] Stevie", "Steve", "§6[VIP] §fStevie"));
    }

    @Test
    void anUnrelatedNameMatchesNobody() {
        assertFalse(NpcTargetToken.matches("Alex", "Steve", "Stevie"));
        assertFalse(NpcTargetToken.matches("", "Steve", "Stevie"));
        assertFalse(NpcTargetToken.matches(null, "Steve", "Stevie"));
    }

    @Test
    void usernameMatchingIgnoresTheDisplayName() {
        // Precedence: a nickname that collides with another account's username must not win.
        assertTrue(NpcTargetToken.matchesUsername("Steve", "Steve"));
        assertFalse(NpcTargetToken.matchesUsername("Stevie", "Steve"));
    }
}
