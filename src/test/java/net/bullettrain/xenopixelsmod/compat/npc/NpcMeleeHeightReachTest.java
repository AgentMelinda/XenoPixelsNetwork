package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-29 owner: "sometimes ai brain v9 cant hit me with a diffrance in y levvel".
 *
 * <p>The saga tree picks melee anywhere inside DMZ's 4.5-block band, but the hit gate measured a
 * straight 3D line from foot to foot against a ~1.2-block reach. A target a block or two above or
 * below - on a step, a slab, a pillar, mid-jump - was "in melee" to the brain and out of reach to
 * the gate, so the NPC stood under its target swinging at nothing. The height rule measures reach
 * across the ground and the vertical gap between the two hitboxes separately.
 */
class NpcMeleeHeightReachTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();

    @AfterEach
    void restore() {
        XenoServerConfig.apply(saved);
    }

    @Test
    void theGapBetweenHitboxesIsZeroWhenTheyOverlapInHeight() {
        // NPC 0..1.8, player standing one block up 1..2.8: the boxes overlap.
        assertEquals(0.0, NpcCombatRanges.verticalGap(0.0, 1.8, 1.0, 2.8), 1.0e-9);
        // Player on a 3-block pillar: 3..4.8 against 0..1.8 leaves 1.2 of air.
        assertEquals(1.2, NpcCombatRanges.verticalGap(0.0, 1.8, 3.0, 4.8), 1.0e-9);
        // Symmetric: NPC above the player.
        assertEquals(1.2, NpcCombatRanges.verticalGap(3.0, 4.8, 0.0, 1.8), 1.0e-9);
    }

    @Test
    void aTargetOneBlockUpIsInReach() {
        // Horizontal 1.0, the player's feet 1.0 higher: the 3D line is 1.41 > 1.2 and the old
        // rule refused; the boxes overlap in height, so the height rule lands it.
        assertFalse(NpcCombatRanges.reachable(false, 1.0, Math.sqrt(2.0), 0.0, 1.2, 1.5));
        assertTrue(NpcCombatRanges.reachable(true, 1.0, Math.sqrt(2.0), 0.0, 1.2, 1.5));
    }

    @Test
    void aTargetStandingOnItsHeadIsInReach() {
        // Straight above: horizontal 0, a 2-block pillar leaves 0.2 of air between the boxes.
        assertTrue(NpcCombatRanges.reachable(true, 0.0, 2.0, 0.2, 1.2, 1.5));
    }

    @Test
    void farAboveIsStillOutOfReach() {
        assertFalse(NpcCombatRanges.reachable(true, 0.3, 6.0, 4.2, 1.2, 1.5),
                "a target on a tall pillar is for ki or flight, not a punch");
        assertFalse(NpcCombatRanges.reachable(true, 3.0, 3.0, 0.0, 1.2, 1.5),
                "the ground reach itself is unchanged");
    }

    @Test
    void aTargetStandingWellAboveItsHeadIsNotPunched() {
        // Same x/z, a block of air between the NPC's head and the target's feet: the swing is level
        // with the NPC's head, so allowing it punched the air (2026-09-30).
        assertFalse(NpcCombatRanges.reachable(true, 0.0, 3.0, 1.2, 1.2,
                new XenoServerConfig.Data().npcMeleeHeightReach));
        assertTrue(NpcCombatRanges.reachable(true, 0.0, 2.0, 0.3, 1.2,
                new XenoServerConfig.Data().npcMeleeHeightReach), "a jump still gets hit");
    }

    @Test
    void theHeightRuleIsOnByDefaultAndTheOldRuleStaysSwitchable() {
        XenoServerConfig.Data defaults = new XenoServerConfig.Data();
        assertTrue(defaults.npcMeleeHeightRule);
        assertEquals(0.5, defaults.npcMeleeHeightReach, 1.0e-9);
        assertEquals(0.0, XenoServerConfig.clampNpcMeleeHeightReach(-3.0), 1.0e-9);
        assertEquals(8.0, XenoServerConfig.clampNpcMeleeHeightReach(99.0), 1.0e-9);
        assertEquals(0.5, XenoServerConfig.clampNpcMeleeHeightReach(Double.NaN), 1.0e-9);
    }
}
