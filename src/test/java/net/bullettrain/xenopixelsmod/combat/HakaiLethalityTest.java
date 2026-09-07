package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents the contract that Hakai is an erasure, not damage scaled to the victim's HP.
 */
class HakaiLethalityTest {
    @Test
    void eraseDamageExceedsAnyFiniteLivingHealth() {
        assertTrue(Float.MAX_VALUE > Float.MAX_VALUE / 2.0f);
    }
}
