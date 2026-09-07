package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDmzAppearanceTest {
    @Test
    void defaultsBothEyeColorsToBlack() {
        NpcDmzAppearance defaults = new NpcDmzAppearance();
        assertEquals("#000000", defaults.eye1Color);
        assertEquals("#000000", defaults.eye2Color);

        NpcDmzAppearance legacy = NpcDmzAppearance.fromTag(new CompoundTag());
        assertEquals("#000000", legacy.eye1Color);
        assertEquals("#000000", legacy.eye2Color);
    }

    @Test
    void roundTripsEveryAppearanceGroup() {
        NpcDmzAppearance source = new NpcDmzAppearance();
        source.mode = NpcDmzAppearance.Mode.FULL;
        source.gender = "female";
        source.characterClass = "martialartist";
        source.bodyType = 2;
        source.eyesType = 7;
        source.eyebrowsType = 6;
        source.noseType = 3;
        source.mouthType = 5;
        source.tattooType = 4;
        source.boobScale = 1.2f;
        source.bodyColor = "#112233";
        source.bodyColor2 = "#445566";
        source.bodyColor3 = "#778899";
        source.eye1Color = "#AABBCC";
        source.eye2Color = "#DDEEFF";
        source.tailColor = "#663311";
        source.activeHeadBone = "hair+antenna";
        source.saiyanTail = true;
        source.renderHairBase = false;

        NpcDmzAppearance decoded = NpcDmzAppearance.fromTag(source.toTag());
        assertEquals(NpcDmzAppearance.Mode.FULL, decoded.mode);
        assertEquals("female", decoded.gender);
        assertEquals("martialartist", decoded.characterClass);
        assertEquals(2, decoded.bodyType);
        assertEquals(7, decoded.eyesType);
        assertEquals(6, decoded.eyebrowsType);
        assertEquals(3, decoded.noseType);
        assertEquals(5, decoded.mouthType);
        assertEquals(4, decoded.tattooType);
        assertEquals(1.2f, decoded.boobScale, 1.0e-6f);
        assertEquals("#112233", decoded.bodyColor);
        assertEquals("#AABBCC", decoded.eye1Color);
        assertEquals("#663311", decoded.tailColor);
        assertEquals("hair+antenna", decoded.activeHeadBone);
        assertTrue(decoded.saiyanTail);
        assertFalse(decoded.renderHairBase);
    }

    @Test
    void tailColorDefaultsToDmzInheritance() {
        NpcDmzAppearance appearance = NpcDmzAppearance.fromTag(new CompoundTag());
        assertEquals("", appearance.tailColor);
        assertEquals("", appearance.toTag().getString("TailColor"));
        assertEquals(4, appearance.toTag().getInt("Schema"));
    }

    @Test
    void legacyAppearanceUsesItsEyeChoiceForMissingEyebrows() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", 1);
        tag.putInt("EyesType", 4);

        NpcDmzAppearance decoded = NpcDmzAppearance.fromTag(tag);
        assertEquals(4, decoded.eyesType);
        assertEquals(4, decoded.eyebrowsType);
    }

    @Test
    void corruptEyebrowTextureMigratesToTheValidEyeChoice() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", 2);
        tag.putInt("EyesType", 1);
        tag.putInt("EyebrowsType", NpcDmzAppearance.BROKEN_EYEBROW_TYPE);

        NpcDmzAppearance decoded = NpcDmzAppearance.fromTag(tag);
        assertEquals(1, decoded.eyebrowsType);
        assertEquals(4, decoded.toTag().getInt("Schema"));
    }

    @Test
    void corruptEyebrowAndEyeCombinationFallsBackToNearestValidBrow() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", 2);
        tag.putInt("EyesType", NpcDmzAppearance.BROKEN_EYEBROW_TYPE);
        tag.putInt("EyebrowsType", NpcDmzAppearance.BROKEN_EYEBROW_TYPE);

        NpcDmzAppearance decoded = NpcDmzAppearance.fromTag(tag);
        assertEquals(8, decoded.eyebrowsType);
        assertEquals(NpcDmzAppearance.BROKEN_EYEBROW_TYPE, decoded.eyesType);
    }

    @Test
    void eyebrowCyclingSkipsTheCorruptTextureInBothDirections() {
        assertEquals(10, NpcDmzAppearance.cycleEyebrowType(8, 1, 12));
        assertEquals(8, NpcDmzAppearance.cycleEyebrowType(10, -1, 12));
        assertEquals(0, NpcDmzAppearance.cycleEyebrowType(8, 1, 9));
    }

    @Test
    void invalidPayloadValuesAreSanitized() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", "not-a-mode");
        tag.putInt("EyesType", -9);
        tag.putInt("TattooType", 50000);
        tag.putFloat("BoobScale", Float.NaN);
        tag.putString("Eye1Color", "invalid");

        NpcDmzAppearance decoded = NpcDmzAppearance.fromTag(tag);
        assertEquals(NpcDmzAppearance.Mode.OFF, decoded.mode);
        assertEquals(0, decoded.eyesType);
        assertEquals(1024, decoded.tattooType);
        assertEquals(1.0f, decoded.boobScale, 1.0e-6f);
        assertEquals("#000000", decoded.eye1Color);
    }

    @Test
    void oldProfilesDefaultToOffWithoutLosingLegacyAuraAndHair() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("AuraColor", 0x3366FF);
        tag.putString("HairColor", "#FFD700");
        tag.putBoolean("HairEnabled", true);

        NpcCombatProfile profile = NpcCombatProfile.fromTag(tag);
        assertEquals(NpcDmzAppearance.Mode.OFF, profile.appearance.mode);
        assertEquals(0x3366FF, profile.auraColor);
        assertEquals("#FFD700", profile.hairColor);
        assertTrue(profile.hairEnabled);
        // No AuraScale key: schema-0 default 1.7 is normalized to 1.0 by the legacy rule,
        // then the schema-7 default migration promotes it back to the new default.
        assertEquals(1.7f, profile.auraScale, 1.0e-6f);
    }

    @Test
    void migratesTheOldAuraDefaultsOnceEachAndWritesSchema() {
        // Legacy baked 1.7 -> 1.0 (AURA_SCHEMA rule), then 1.0 -> 1.7 (schema-7 default change).
        CompoundTag legacyDefault = new CompoundTag();
        legacyDefault.putFloat("AuraScale", 1.7f);
        assertEquals(1.7f, NpcCombatProfile.fromTag(legacyDefault).auraScale, 1.0e-6f);

        CompoundTag legacyCustom = new CompoundTag();
        legacyCustom.putFloat("AuraScale", 1.3f);
        assertEquals(1.3f, NpcCombatProfile.fromTag(legacyCustom).auraScale, 1.0e-6f);

        NpcCombatProfile current = new NpcCombatProfile();
        current.auraScale = 1.7f;
        CompoundTag encoded = current.toTag();
        assertEquals(9, encoded.getInt("Schema"));
        assertEquals(1.7f, NpcCombatProfile.fromTag(encoded).auraScale, 1.0e-6f);
    }

    @Test
    void groundRingAndFlySkillRoundTripThroughBothTagForms() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.auraGroundRing = false;
        source.flySkillOn = true;
        source.flySkillLevel = 6;

        NpcCombatProfile full = NpcCombatProfile.fromTag(source.toTag());
        assertFalse(full.auraGroundRing);
        assertTrue(full.flySkillOn);
        assertEquals(6, full.flySkillLevel);

        // The compact visual-options subset is what actually reaches nearby clients, and both
        // the ring toggle and the fly pose are read on the client, so they must survive it too.
        NpcCombatProfile visual = new NpcCombatProfile();
        visual.applyVisualOptions(source.visualOptionsTag());
        assertFalse(visual.auraGroundRing);
        assertTrue(visual.flySkillOn);
        assertEquals(6, visual.flySkillLevel);
    }

    @Test
    void groundRingDefaultsOnAndFlySkillDefaultsOffForOlderProfiles() {
        // A pre-schema-8 save has neither key. Rings were unconditional before the toggle
        // existed, so absent must read as on; flight is new behavior and must stay off.
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Schema", 7);

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(legacy);
        assertTrue(decoded.auraGroundRing);
        assertFalse(decoded.flySkillOn);
        assertEquals(1, decoded.flySkillLevel);
    }

    @Test
    void aggroMultiplierAndAimAccuracyRoundTripAndClamp() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.aggroMultiplier = 3.5f;
        source.aimAccuracy = 0.25f;
        source.combatBrain = true;

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(source.toTag());
        assertEquals(3.5f, decoded.aggroMultiplier, 1.0e-6f);
        assertEquals(0.25f, decoded.aimAccuracy, 1.0e-6f);
        assertTrue(decoded.combatBrain);

        // Both are clamped rather than rejected, so a bad script value degrades to a sane one.
        assertEquals(0.25f, NpcCombatProfile.clampAggroMultiplier(-4.0f), 1.0e-6f);
        assertEquals(8.0f, NpcCombatProfile.clampAggroMultiplier(999.0f), 1.0e-6f);
        assertEquals(2.0f, NpcCombatProfile.clampAggroMultiplier(Float.NaN), 1.0e-6f);
        assertEquals(0.0f, NpcCombatProfile.clampAimAccuracy(-1.0f), 1.0e-6f);
        assertEquals(1.0f, NpcCombatProfile.clampAimAccuracy(4.0f), 1.0e-6f);
        assertEquals(0.85f, NpcCombatProfile.clampAimAccuracy(Float.NaN), 1.0e-6f);
    }

    @Test
    void aggroDefaultsToDoubleAndBrainDefaultsOff() {
        // "Double the aggro limit" is the shipped default, and autonomous combat must stay off
        // so an existing scripted NPC is untouched by the upgrade.
        NpcCombatProfile defaults = NpcCombatProfile.fromTag(new CompoundTag());
        assertEquals(2.0f, defaults.aggroMultiplier, 1.0e-6f);
        assertFalse(defaults.combatBrain);
    }

    @Test
    void preSchemaNineFlyStateMigratesIntoTheSkillMap() {
        // Schema 9 generalized the single fly toggle into a full skill map; a profile configured
        // to fly under schema 8 must still fly afterwards.
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Schema", 8);
        legacy.putBoolean("FlySkillOn", true);
        legacy.putInt("FlySkillLevel", 7);

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(legacy);
        assertTrue(decoded.flySkillOn);
        assertTrue(decoded.skills.isActive("fly"));
        assertEquals(7, decoded.skills.level("fly"));
    }

    @Test
    void flySkillLevelIsClampedToDmzUsableRange() {
        assertEquals(1, NpcCombatProfile.clampFlySkillLevel(0));
        assertEquals(1, NpcCombatProfile.clampFlySkillLevel(-4));
        assertEquals(10, NpcCombatProfile.clampFlySkillLevel(99));
        assertEquals(5, NpcCombatProfile.clampFlySkillLevel(5));

        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", 8);
        tag.putInt("FlySkillLevel", 250);
        assertEquals(10, NpcCombatProfile.fromTag(tag).flySkillLevel);
    }
}
