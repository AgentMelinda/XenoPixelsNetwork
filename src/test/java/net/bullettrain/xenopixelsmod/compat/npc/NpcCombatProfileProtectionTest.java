package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatProfileProtectionTest {
    @Test
    void legacyProfilesRemainDamageableAndKnockable() {
        NpcCombatProfile profile = NpcCombatProfile.fromTag(new CompoundTag());
        assertTrue(profile.knockable);
        assertTrue(profile.punchable);
    }

    @Test
    void protectionFlagsRoundTrip() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.knockable = false;
        profile.punchable = false;
        NpcCombatProfile decoded = NpcCombatProfile.fromTag(profile.toTag());
        assertFalse(decoded.knockable);
        assertFalse(decoded.punchable);
    }
}
