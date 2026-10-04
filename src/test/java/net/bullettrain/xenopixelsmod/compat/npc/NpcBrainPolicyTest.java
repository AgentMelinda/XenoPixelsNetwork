package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam the XenoNPCs release uses to offer only the V9 brain (2026-09-29 owner: "on the
 * releasing one make so we only got combat brain v9"). In XenoPixels it changes nothing: every
 * version stays selectable and a save reads exactly what it stored.
 */
class NpcBrainPolicyTest {

    @Test
    void xenoPixelsKeepsEveryBrain() {
        assertEquals(List.of(NpcCombatBrainVersion.values()), NpcBrainPolicy.choices());
        for (NpcCombatBrainVersion v : NpcCombatBrainVersion.values()) {
            assertSame(v, NpcBrainPolicy.resolve(v));
        }
        assertSame(NpcCombatBrainVersion.V1, NpcBrainPolicy.resolve(null));
    }

    @Test
    void theProfileAndTheCycleGoThroughIt() {
        NpcCombatProfile p = new NpcCombatProfile();
        p.setBrainVersion(NpcCombatBrainVersion.V6);
        assertSame(NpcCombatBrainVersion.V6, p.brainVersion);
        assertSame(NpcCombatBrainVersion.V2, NpcCombatBrainVersion.V1.next(), "the cycle is unchanged");
        CompoundTag saved = p.toTag();
        assertSame(NpcCombatBrainVersion.V6, NpcCombatProfile.fromTag(saved).brainVersion);
        assertSame(NpcCombatBrainVersion.V1, NpcCombatProfile.fromTag(new CompoundTag()).brainVersion);
        assertTrue(NpcBrainPolicy.choices().size() > 1, "the editor shows the version picker");
    }
}
