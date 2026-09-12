package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatProfileKiWeaponTest {
    @Test
    void fullProfileRoundTripCanonicalizesWeaponType() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.kiWeaponOn = true;
        source.kiWeaponType = " SCYTHE ";

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(source.toTag());

        assertTrue(decoded.kiWeaponOn);
        assertEquals("scythe", decoded.kiWeaponType);
    }

    @Test
    void togglingKiWeaponChangesThatOneFieldAndNothingElse() {
        // The Stats tab's KI Weapon button saves the whole profile, so the payload it builds must
        // be the NPC's current profile with only this flag flipped - anything else would reset
        // appearance, transformation, mastery or combat values owned by the other DMZ screens.
        NpcCombatProfile source = new NpcCombatProfile();
        source.kiWeaponOn = false;
        source.kiWeaponType = "scythe";
        source.raceId = "saiyan";
        source.formGroup = "superforms";
        source.formId = "ssj1";
        source.stackGroup = "kaioken";
        source.stackId = "kaioken";
        source.auraOn = true;
        source.auraColorHex = "#123456";
        source.auraScale = 2.25f;
        source.knockable = false;
        source.punchable = false;
        source.techniques.addAll(List.of("kamehameha", "final_flash"));

        CompoundTag before = source.toTag();
        CompoundTag after = NpcCombatProfile.withKiWeapon(before.copy(), true);

        assertEquals(before.getAllKeys(), after.getAllKeys(), "no field may appear or disappear");
        for (String key : before.getAllKeys()) {
            if ("KiWeaponOn".equals(key)) continue;
            assertEquals(before.get(key), after.get(key), key + " must be preserved");
        }
        assertNotEquals(before.get("KiWeaponOn"), after.get("KiWeaponOn"));
        assertTrue(NpcCombatProfile.fromTag(after).kiWeaponOn);
        assertEquals("scythe", NpcCombatProfile.fromTag(after).kiWeaponType);

        // Turning it back off is likewise lossless.
        CompoundTag off = NpcCombatProfile.withKiWeapon(after.copy(), false);
        assertFalse(NpcCombatProfile.fromTag(off).kiWeaponOn);
        assertEquals(before.getAllKeys(), off.getAllKeys());
        for (String key : before.getAllKeys()) {
            assertEquals(before.get(key), off.get(key), key + " must survive a full toggle cycle");
        }
    }

    @Test
    void visualOptionsRoundTripCarriesWeaponState() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.kiWeaponOn = true;
        source.kiWeaponType = "clawlance";

        NpcCombatProfile decoded = new NpcCombatProfile();
        decoded.applyVisualOptions(source.visualOptionsTag());

        assertTrue(decoded.kiWeaponOn);
        assertEquals("clawlance", decoded.kiWeaponType);
    }

    @Test
    void missingOrInvalidWeaponTypeFallsBackToBlade() {
        CompoundTag missing = new CompoundTag();
        NpcCombatProfile missingDecoded = NpcCombatProfile.fromTag(missing);
        assertFalse(missingDecoded.kiWeaponOn);
        assertEquals("blade", missingDecoded.kiWeaponType);

        CompoundTag invalid = new NpcCombatProfile().toTag();
        invalid.putString("KiWeaponType", "coral_blade");
        assertEquals("blade", NpcCombatProfile.fromTag(invalid).kiWeaponType);
    }

    @Test
    void schemaFourProfilesDoNotRepeatAuraMigrations() {
        CompoundTag schemaFour = new CompoundTag();
        schemaFour.putInt("Schema", 4);
        schemaFour.putInt("AuraColor", 0x123456);
        schemaFour.putString("AuraColorHex", "");
        schemaFour.putFloat("AuraScale", 1.7f);

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(schemaFour);

        assertEquals("", decoded.auraColorHex);
        assertEquals(1.7f, decoded.auraScale, 1.0e-6f);
    }

    @Test
    void preSchemaFourProfilesStillRunAuraMigrations() {
        // Lower bound of the AURA_SCHEMA guard: a genuinely old save must still be migrated,
        // otherwise the schema bump to 5 would have silently disabled legacy backfill entirely.
        // The scale chains through both migrations: the legacy baked 1.7 is normalized to 1.0,
        // then the schema-7 default change promotes it back to the new 1.7 default.
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Schema", 3);
        legacy.putInt("AuraColor", 0x123456);
        legacy.putString("AuraColorHex", "");
        legacy.putFloat("AuraScale", 1.7f);

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(legacy);

        assertEquals("#123456", decoded.auraColorHex);
        assertEquals(1.7f, decoded.auraScale, 1.0e-6f);
    }

    @Test
    void schemaSixProfilesMigrateTheOldAuraScaleDefault() {
        CompoundTag schemaSix = new CompoundTag();
        schemaSix.putInt("Schema", 6);
        schemaSix.putFloat("AuraScale", 1.0f);

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(schemaSix);

        assertEquals(1.7f, decoded.auraScale, 1.0e-6f);
    }

    @Test
    void explicitAuraScaleSurvivesAfterTheMigrationWindow() {
        CompoundTag schemaSeven = new CompoundTag();
        schemaSeven.putInt("Schema", 7);
        schemaSeven.putFloat("AuraScale", 1.0f);

        assertEquals(1.0f, NpcCombatProfile.fromTag(schemaSeven).auraScale, 1.0e-6f);

        CompoundTag noScale = new CompoundTag();
        noScale.putInt("Schema", 7);
        assertEquals(1.7f, NpcCombatProfile.fromTag(noScale).auraScale, 1.0e-6f);
    }

    @Test
    void weaponTypeValidationContractMatchesScriptApi() {
        // NpcXenoScriptApi.setKiWeaponType gates on isKiWeaponType and stores
        // canonicalKiWeaponType; that path needs a live NPC, so lock the contract here.
        assertFalse(NpcCombatProfile.isKiWeaponType(null));
        assertFalse(NpcCombatProfile.isKiWeaponType("coral_blade"));
        assertFalse(NpcCombatProfile.isKiWeaponType(""));
        assertTrue(NpcCombatProfile.isKiWeaponType("  ClawLance  "));

        assertEquals("clawlance", NpcCombatProfile.canonicalKiWeaponType("  ClawLance  "));
        assertEquals("blade", NpcCombatProfile.canonicalKiWeaponType(null));
        assertEquals("blade", NpcCombatProfile.canonicalKiWeaponType("nonsense"));
    }
}
