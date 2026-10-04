package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcNativeCombatPropertiesTest {
    @Test
    void allFourCombatPropertyGroupsRoundTrip() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.npcMeleeDamage = 12.5f;
        profile.npcMeleeRange = 6.0f;
        profile.npcMeleeSpeed = 2.0f;
        profile.npcMeleeKnockback = 1.5f;
        profile.npcMeleeEffect = "minecraft:slowness";
        profile.npcMeleeEffectDuration = 80;
        profile.npcMeleeEffectAmplifier = 2;
        profile.npcRangedAccuracy = 0.4f;
        profile.npcRangedRange = 30.0f;
        profile.npcRangedMinRange = 4.0f;
        profile.npcRangedMinDelay = 12;
        profile.npcRangedMaxDelay = 60;
        profile.npcRangedBurstCount = 3;
        profile.npcRangedBurstRate = 5;
        profile.npcRangedIndirect = true;
        profile.npcRangedAimMode = "hidden";
        profile.npcRangedFireSound = "minecraft:entity.blaze.shoot";
        profile.npcRangedHitSound = "minecraft:entity.arrow.hit_player";
        profile.npcRangedGroundSound = "minecraft:block.stone.hit";
        profile.npcProjectileStrength = 18.0f;
        profile.npcProjectileKnockback = 2.0f;
        profile.npcProjectileSize = 1.4f;
        profile.npcProjectileSpeed = 1.8f;
        profile.npcProjectileGravity = "accelerate";
        profile.npcProjectileExplosion = 2.0f;
        profile.npcProjectileEffect = "minecraft:weakness";
        profile.npcKnockbackResistance = 25;
        profile.npcArrowResistance = 35;
        profile.npcMeleeResistance = 45;
        profile.npcExplosionResistance = 55;

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(12.5f, decoded.npcMeleeDamage);
        assertEquals(6.0f, decoded.npcMeleeRange);
        assertEquals("minecraft:slowness", decoded.npcMeleeEffect);
        assertEquals(0.4f, decoded.npcRangedAccuracy);
        assertEquals(60, decoded.npcRangedMaxDelay);
        assertEquals(3, decoded.npcRangedBurstCount);
        assertTrue(decoded.npcRangedIndirect);
        assertEquals("hidden", decoded.npcRangedAimMode);
        assertEquals("minecraft:entity.blaze.shoot", decoded.npcRangedFireSound);
        assertEquals("minecraft:entity.arrow.hit_player", decoded.npcRangedHitSound);
        assertEquals("minecraft:block.stone.hit", decoded.npcRangedGroundSound);
        assertEquals(18.0f, decoded.npcProjectileStrength);
        assertEquals("accelerate", decoded.npcProjectileGravity);
        assertEquals("minecraft:weakness", decoded.npcProjectileEffect);
        assertEquals(25, decoded.npcKnockbackResistance);
        assertEquals(35, decoded.npcArrowResistance);
        assertEquals(45, decoded.npcMeleeResistance);
        assertEquals(55, decoded.npcExplosionResistance);
    }

    @Test
    void networkedSettingsClampAndBadEffectIdsClear() {
        CompoundTag tag = new NpcCombatProfile().toTag();
        CompoundTag melee = tag.getCompound("NpcMeleeProps");
        melee.putFloat("Damage", Float.NaN);
        melee.putFloat("Range", 900.0f);
        melee.putString("Effect", "bad effect id");
        tag.put("NpcMeleeProps", melee);
        CompoundTag ranged = tag.getCompound("NpcRangedProps");
        ranged.putFloat("Accuracy", -10.0f);
        ranged.putInt("MinDelay", 100);
        ranged.putInt("MaxDelay", 1);
        ranged.putString("AimMode", "invalid");
        tag.put("NpcRangedProps", ranged);
        CompoundTag resistance = tag.getCompound("NpcResistanceProps");
        resistance.putInt("Arrow", 500);
        tag.put("NpcResistanceProps", resistance);

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(tag);
        assertEquals(0.0f, decoded.npcMeleeDamage);
        assertEquals(64.0f, decoded.npcMeleeRange);
        assertEquals("", decoded.npcMeleeEffect);
        assertEquals(0.0f, decoded.npcRangedAccuracy);
        assertEquals(100, decoded.npcRangedMaxDelay);
        assertEquals(100, decoded.npcArrowResistance);
        assertFalse(decoded.npcRangedIndirect);
        assertEquals("distant", decoded.npcRangedAimMode);
    }

    @Test
    void theFourResistanceChannelsAreSelectedFromDamageType() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.npcKnockbackResistance = 11;
        profile.npcArrowResistance = 22;
        profile.npcMeleeResistance = 33;
        profile.npcExplosionResistance = 44;
        assertEquals(44, NpcDamageCategory.resistanceFor(true, true, true, profile));
        assertEquals(22, NpcDamageCategory.resistanceFor(false, true, false, profile));
        assertEquals(33, NpcDamageCategory.resistanceFor(false, false, true, profile));
        assertEquals(0, NpcDamageCategory.resistanceFor(false, false, false, profile));
    }
}
