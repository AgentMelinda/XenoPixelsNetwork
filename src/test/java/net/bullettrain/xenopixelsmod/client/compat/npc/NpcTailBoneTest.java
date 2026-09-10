package net.bullettrain.xenopixelsmod.client.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which bones count as a tail.
 *
 * <p>This replaced a per-race list, and the bug it fixes was silent: a race whose tail bone was not
 * on that list simply ignored the NPC's tail colour with nothing to show for it. These cases pin
 * down both halves — every shape a tail bone actually takes, and the near-misses that must not be
 * swept up with them.
 */
class NpcTailBoneTest {

    @Test
    void everyKnownTailBoneShapeMatches() {
        assertTrue(NpcFullDmzRenderer.isTailBone("tail"));
        assertTrue(NpcFullDmzRenderer.isTailBone("tail1"));
        assertTrue(NpcFullDmzRenderer.isTailBone("tail2"));
        assertTrue(NpcFullDmzRenderer.isTailBone("tail12"));
        assertTrue(NpcFullDmzRenderer.isTailBone("cola"));
        assertTrue(NpcFullDmzRenderer.isTailBone("tailenrolled"));
    }

    @Test
    void matchingIgnoresCaseAndSurroundingSpace() {
        assertTrue(NpcFullDmzRenderer.isTailBone("Tail"));
        assertTrue(NpcFullDmzRenderer.isTailBone("TAILENROLLED"));
        assertTrue(NpcFullDmzRenderer.isTailBone(" tail1 "));
    }

    @Test
    void nonTailBonesAreLeftAlone() {
        // The match is exact rather than a "starts with tail" prefix, or a coat would be tinted.
        assertFalse(NpcFullDmzRenderer.isTailBone("tailcoat"));
        assertFalse(NpcFullDmzRenderer.isTailBone("tailfin"));
        assertFalse(NpcFullDmzRenderer.isTailBone("head"));
        assertFalse(NpcFullDmzRenderer.isTailBone("body"));
        assertFalse(NpcFullDmzRenderer.isTailBone("colar"));
    }

    @Test
    void nothingBlowsUpOnMissingNames() {
        assertFalse(NpcFullDmzRenderer.isTailBone(null));
        assertFalse(NpcFullDmzRenderer.isTailBone(""));
        assertFalse(NpcFullDmzRenderer.isTailBone("   "));
        assertFalse(NpcFullDmzRenderer.isTailBone("tailx"));
    }
}
