package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatProfileBrainFlagsTest {
    @Test
    void legacyProfilesKeepMeleeAndKiOnAndSpecialsOff() {
        NpcCombatProfile decoded = NpcCombatProfile.fromTag(new CompoundTag());
        assertFalse(decoded.combatBrain);
        assertEquals(NpcCombatBrainVersion.V1, decoded.brainVersion);
        assertTrue(decoded.brainStrike);
        assertTrue(decoded.brainCharge);
        assertTrue(decoded.brainKiBlast);
        assertTrue(decoded.brainKiWave);
        assertTrue(decoded.brainKiDisk);
        assertTrue(decoded.brainKiNamed);
        assertTrue(decoded.brainLand);
        assertTrue(decoded.brainAscend);
        assertTrue(decoded.brainDisengage);
        assertTrue(decoded.brainVanish);
        assertTrue(decoded.brainZanzoken);
        assertEquals(100, decoded.brainChance("zanzoken"));
        assertTrue(decoded.brainChase);
        assertFalse(decoded.brainFlyingFist);
        assertFalse(decoded.brainHeavyHit);
        assertFalse(decoded.brainBoneCrusher);
        assertFalse(decoded.brainKiai);
        assertFalse(decoded.brainRandomKi);
        assertTrue(decoded.brainFly,
                "a profile saved before the Fly action key must keep flying like before it existed");
        assertTrue(decoded.brainDeflectBlast);
        assertFalse(decoded.brainDeflectWave);
        assertEquals(35, decoded.brainChance("deflectBlast"));
        assertEquals(20, decoded.brainChance("deflectWave"));
        assertEquals(100, decoded.brainChance("charge"));
        assertEquals(1.0f, decoded.brainModifier("fly"), 1.0e-6f);
        assertEquals(80, decoded.brainSpecialCooldown);
    }

    @Test
    void brainFlagsRoundTrip() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.combatBrain = true;
        source.brainVersion = NpcCombatBrainVersion.V2;
        source.brainStrike = false;
        source.brainFlyingFist = true;
        source.brainKiai = true;
        source.brainFly = true;
        source.canUseFlight = false;
        source.brainKiWave = false;
        source.brainSpecialCooldown = 120;
        source.aimAccuracy = 0.4f;

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(source.toTag());
        assertTrue(decoded.combatBrain);
        assertEquals(NpcCombatBrainVersion.V2, decoded.brainVersion);
        assertFalse(decoded.brainStrike);
        assertTrue(decoded.brainFlyingFist);
        assertTrue(decoded.brainKiai);
        assertTrue(decoded.brainFly);
        assertFalse(decoded.canUseFlight,
                "the master flight permission must survive the round trip when switched off");
        assertFalse(decoded.brainKiWave);
        assertEquals(120, decoded.brainSpecialCooldown);
        assertEquals(0.4f, decoded.aimAccuracy, 1.0e-6f);
    }

    @Test
    void canUseFlightDefaultsToOnForProfilesSavedBeforeTheKey() {
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.remove("CanUseFlight");
        assertTrue(NpcCombatProfile.fromTag(tag).canUseFlight);
    }

    @Test
    void brainFlyDefaultsToOnForProfilesSavedBeforeTheKey() {
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.remove("BrainFly");
        assertTrue(NpcCombatProfile.fromTag(tag).brainFly,
                "a skill-configured flyer must not be silently grounded by the decoupled action key");
        tag.putBoolean("BrainFly", false);
        assertFalse(NpcCombatProfile.fromTag(tag).brainFly,
                "an explicit operator off-switch must still be honored");
    }

    @Test
    void withCombatBrainDoesNotDropOtherFlags() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.brainFlyingFist = true;
        source.brainSpecialCooldown = 200;
        CompoundTag after = NpcCombatProfile.withCombatBrain(source.toTag(), true);
        NpcCombatProfile decoded = NpcCombatProfile.fromTag(after);
        assertTrue(decoded.combatBrain);
        assertTrue(decoded.brainFlyingFist);
        assertEquals(200, decoded.brainSpecialCooldown);
    }

    @Test
    void specialCooldownClamps() {
        assertEquals(40, NpcCombatProfile.clampSpecialCooldown(1));
        assertEquals(400, NpcCombatProfile.clampSpecialCooldown(9999));
        assertEquals(80, NpcCombatProfile.clampSpecialCooldown(80));
    }

    @Test
    void brainFlagNamesRoundTripThroughSetter() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setBrainFlag("flyingFist", true);
        profile.setBrainFlag("land", false);
        profile.setBrainFlag("zanzoken", false);
        profile.setBrainChance("zanzoken", 40);
        assertTrue(profile.brainFlag("flyingfist"));
        assertFalse(profile.brainFlag("land"));
        assertFalse(profile.brainFlag("zanzoken"));
        assertEquals(40, profile.brainChance("zanzoken"));
        assertTrue((Boolean) profile.brainFlagMap().get("flyingFist"));
        assertFalse((Boolean) profile.brainFlagMap().get("zanzoken"));
    }

    @Test
    void visualOverlayWithoutBrainVersionKeepsExistingV2() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V2;
        CompoundTag compact = new CompoundTag();
        compact.putBoolean("FlySkillOn", true);
        profile.applyVisualOptions(compact);
        assertEquals(NpcCombatBrainVersion.V2, profile.brainVersion);
        assertTrue(profile.flySkillOn);
    }

    @Test
    void visualOverlayWithoutBrainChargeKeepsExistingFalse() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainCharge = false;
        profile.brainStrike = false;
        profile.brainKiWave = false;
        net.minecraft.nbt.CompoundTag compact = new net.minecraft.nbt.CompoundTag();
        compact.putBoolean("FlySkillOn", true);
        compact.putBoolean("KiWeaponOn", true);
        profile.applyVisualOptions(compact);
        assertFalse(profile.brainCharge);
        assertFalse(profile.brainStrike);
        assertFalse(profile.brainKiWave);
        assertTrue(profile.flySkillOn);
        assertTrue(profile.kiWeaponOn);
        assertEquals(35, profile.brainChance("deflectBlast"));
    }

    @Test
    void visualOverlayWithoutChanceKeysKeepsDeflectDefaultsAndCustomChance() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setBrainChance("charge", 10);
        profile.setBrainModifier("fly", 2.0f);
        net.minecraft.nbt.CompoundTag compact = new net.minecraft.nbt.CompoundTag();
        compact.putBoolean("FlySkillOn", true);
        profile.applyVisualOptions(compact);
        assertEquals(10, profile.brainChance("charge"));
        assertEquals(2.0f, profile.brainModifier("fly"), 1.0e-6f);
        assertEquals(35, profile.brainChance("deflectBlast"));
    }

    @Test
    void chanceZeroNeverAllowsAndHundredAlwaysDoes() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainCharge = true;
        profile.setBrainChance("charge", 0);
        assertFalse(profile.allowBrainAction("charge", null));
        profile.setBrainChance("charge", 100);
        assertTrue(profile.allowBrainAction("charge", null));
        profile.brainCharge = false;
        assertFalse(profile.allowBrainAction("charge", null));
    }

    @Test
    void brainChanceAndModifierRoundTrip() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.brainDeflectWave = true;
        source.setBrainChance("deflectWave", 40);
        source.setBrainModifier("deflectBlast", 1.5f);
        NpcCombatProfile decoded = NpcCombatProfile.fromTag(source.toTag());
        assertTrue(decoded.brainDeflectWave);
        assertEquals(40, decoded.brainChance("deflectWave"));
        assertEquals(1.5f, decoded.brainModifier("deflectBlast"), 1.0e-6f);
    }

    @Test
    void visualOverlayWithoutZanzokenKeepsExistingOffAndChance() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainZanzoken = false;
        profile.setBrainChance("zanzoken", 25);
        CompoundTag compact = new CompoundTag();
        compact.putBoolean("FlySkillOn", true);
        profile.applyVisualOptions(compact);
        assertFalse(profile.brainZanzoken);
        assertEquals(25, profile.brainChance("zanzoken"));
        assertTrue(profile.flySkillOn);
    }
}
