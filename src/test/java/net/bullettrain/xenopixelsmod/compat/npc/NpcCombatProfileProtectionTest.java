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

    @Test
    void kiSenseRetaliationLockRoundTripsAndDefaultsOn() {
        NpcCombatProfile profile = NpcCombatProfile.fromTag(new CompoundTag());
        assertTrue(profile.kiSenseLockOnRetaliator);
        assertTrue(profile.skills.isActive(NpcSkillSet.KI_SENSE));

        profile.skills.set(NpcSkillSet.KI_SENSE, true, 1);
        profile.kiSenseLockOnRetaliator = true;
        NpcCombatProfile decoded = NpcCombatProfile.fromTag(profile.toTag());
        assertTrue(decoded.skills.isActive(NpcSkillSet.KI_SENSE));
        assertTrue(decoded.kiSenseLockOnRetaliator);
        decoded.kiSenseLockOnRetaliator = false;
        decoded.skills.setActive(NpcSkillSet.KI_SENSE, false);
        NpcCombatProfile disabled = NpcCombatProfile.fromTag(decoded.toTag());
        assertFalse(disabled.kiSenseLockOnRetaliator);
        assertFalse(disabled.skills.isActive(NpcSkillSet.KI_SENSE));
    }
}
