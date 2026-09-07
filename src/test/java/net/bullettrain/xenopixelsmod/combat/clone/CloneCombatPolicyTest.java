package net.bullettrain.xenopixelsmod.combat.clone;

import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.bullettrain.xenopixelsmod.combat.clone.CloneCombatPolicy.Action.*;

class CloneCombatPolicyTest {
    @Test void inactiveInvalidAndLeashedCopiesReturnToFormation() {
        assertEquals(FORMATION, CloneCombatPolicy.decide(false, true, 1, 1, true, 0));
        assertEquals(FORMATION, CloneCombatPolicy.decide(true, false, 1, 1, true, 0));
        assertEquals(FORMATION, CloneCombatPolicy.decide(true, true, 33, 1, true, 0));
        assertEquals(FORMATION, CloneCombatPolicy.decide(true, true, 1, Double.NaN, true, 0));
    }
    @Test void pursueOccludedTargetsAndRespectAttackRangesAndRecovery() {
        assertEquals(APPROACH, CloneCombatPolicy.decide(true, true, 1, 1, false, 0));
        assertEquals(APPROACH, CloneCombatPolicy.decide(true, true, 1, 25, true, 0));
        assertEquals(MELEE, CloneCombatPolicy.decide(true, true, 1, 2, true, 0));
        assertEquals(RANGED, CloneCombatPolicy.decide(true, true, 1, 15, true, 0));
        assertEquals(RECOVER, CloneCombatPolicy.decide(true, true, 1, 2, true, 1));
        assertEquals(APPROACH, CloneCombatPolicy.decide(true, true, 1, 15, true, 1));
    }
    @Test void sharedBudgetCannotOverspendOrUseInvalidCosts() {
        double energy = 5;
        for (int body = 0; body < 4; body++) {
            boolean accepted = CloneCombatPolicy.canSpend(energy, 5, 2, 1);
            assertEquals(body < 2, accepted);
            if (accepted) energy -= 2;
        }
        assertEquals(1, energy);
        assertFalse(CloneCombatPolicy.canSpend(5, 0, 1, 1));
        assertFalse(CloneCombatPolicy.canSpend(5, 5, -1, 0));
        assertFalse(CloneCombatPolicy.canSpend(5, 5, Double.NaN, 0));
        assertFalse(CloneCombatPolicy.canSpend(Double.POSITIVE_INFINITY, 5, 1, 0));
        assertTrue(CloneCombatPolicy.canSpend(1, 1, 1, 1));
    }
    @Test void ownerAggregatesMasteryOncePerTwentyTicksNotPerBody() {
        assertTrue(CloneCombatPolicy.earnsMastery(0, Long.MIN_VALUE, 1, 0));
        assertFalse(CloneCombatPolicy.earnsMastery(0, 0, 1, 0));
        assertFalse(CloneCombatPolicy.earnsMastery(19, 0, 1, 0));
        assertTrue(CloneCombatPolicy.earnsMastery(20, 0, 1, 999));
        assertFalse(CloneCombatPolicy.earnsMastery(20, 0, 1, 1000));
        assertFalse(CloneCombatPolicy.earnsMastery(20, 0, 0, 0));
        assertFalse(CloneCombatPolicy.earnsMastery(20, 0, Float.NaN, 0));
    }
    @Test void masterySurvivesSaveRelogAndRespawnCopyAndClamps() {
        XenoPlayerData original = new XenoPlayerData();
        original.setMultiFormMastery(999);
        original.addMultiFormMastery(1);
        original.addMultiFormMastery(Integer.MAX_VALUE);
        assertEquals(1000, original.getMultiFormMastery());
        CompoundTag tag = new CompoundTag();
        original.saveNBT(tag);
        XenoPlayerData loaded = new XenoPlayerData();
        loaded.loadNBT(tag);
        XenoPlayerData respawn = new XenoPlayerData();
        respawn.copyFrom(loaded);
        assertEquals(1000, respawn.getMultiFormMastery());
        tag.putInt("MultiFormMastery", -1);
        loaded.loadNBT(tag);
        assertEquals(0, loaded.getMultiFormMastery());
        tag.putInt("MultiFormMastery", 5000);
        loaded.loadNBT(tag);
        assertEquals(1000, loaded.getMultiFormMastery());
    }
    @Test void effectiveStatsAreNotRecomputedFromRawStatsOrDoubleSplit() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.strength = 1;
        profile.kiPower = 2;
        profile.setEffectiveDamage(120.5, 98.25, 300.75);
        assertEquals(120.5f, profile.meleeDamage());
        assertEquals(98.25f, profile.strikeDamage());
        assertEquals(300.75f, profile.kiDamage());
        assertEquals(30.125f, profile.meleeDamage() * CloneFormation.damageShare(4, 0));
        assertEquals(120.5f, profile.meleeDamage() * CloneFormation.damageShare(4, 1000));
        NpcCombatProfile normal = new NpcCombatProfile();
        assertEquals(1f, normal.meleeDamage());
    }
}
