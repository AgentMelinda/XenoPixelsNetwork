package net.bullettrain.xenopixelsmod.combat.v3;

import net.bullettrain.xenopixelsmod.config.XenoConfigRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

class V3ConfigTest {
    @TempDir Path directory;
    private final V3Config.Values saved = V3Config.get();
    @AfterEach void restore() { V3Config.apply(saved); }

    @Test void fractionalAndOverflowSchemaVersionsNeverRelabelTheFileOrApplySettings() throws Exception {
        Path path = directory.resolve("combat.json");
        var effective = new V3Config.Values(999, 3, 4);
        V3Config.apply(effective);
        for (String version : new String[]{"1.5", "1.0000000000000001", "4294967297", "2", "-1"}) {
            String original = "{\"version\":" + version + ",\"dragonDashRange\":2,\"addon\":true}";
            Files.writeString(path, original);
            assertThrows(IllegalArgumentException.class, () -> V3Config.load(path), version);
            assertEquals(effective, V3Config.get(), version);
            assertEquals(original, Files.readString(path), version);
            assertThrows(IllegalArgumentException.class, () -> V3Config.save(path), version);
            assertEquals(effective, V3Config.get(), version);
            assertEquals(original, Files.readString(path), version);
        }
    }

    @Test void stringSchemaVersionIsNotAcceptedAsANumericSupportedSchema() throws Exception {
        Path path = directory.resolve("combat.json");
        String original = "{\"version\":\"1\",\"dragonDashRange\":2}";
        Files.writeString(path, original);
        var effective = V3Config.get();
        assertThrows(IllegalArgumentException.class, () -> V3Config.load(path));
        assertEquals(effective, V3Config.get());
        assertEquals(original, Files.readString(path));
    }

    @Test void missingFileDefaultsAndCommand999SurvivesReload() throws Exception {
        Path path = directory.resolve("combat.json");
        V3Config.load(path);
        assertEquals(24, V3Config.get().dragonDashRange());
        assertEquals(10, V3Config.get().heavyAttackerStaminaCost());
        assertEquals(10, V3Config.get().heavyVictimStaminaDrain());
        assertTrue(XenoConfigRegistry.set("v3.dragonDashRange", "999").ok);
        V3Config.save(path);
        V3Config.apply(new V3Config.Values());
        V3Config.load(path);
        assertEquals(999, V3Config.get().dragonDashRange());
    }
    @Test void unknownAddonFieldsSurviveWriteAndReload() throws Exception {
        Path path = directory.resolve("combat.json");
        Files.writeString(path, "{\"version\":1,\"dragonDashRange\":999,\"addon:settings\":{\"nested\":[1,2,3]}}");
        V3Config.load(path);
        assertTrue(XenoConfigRegistry.set("v3.heavyAttackerStaminaCost", "0").ok);
        assertTrue(XenoConfigRegistry.set("v3.heavyVictimStaminaDrain", "100001").ok);
        V3Config.save(path);
        V3Config.load(path);
        assertEquals(0, V3Config.get().heavyAttackerStaminaCost());
        assertEquals(100000, V3Config.get().heavyVictimStaminaDrain());
        assertEquals("[1,2,3]", JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonObject("addon:settings").get("nested").toString());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void corruptOrNonFiniteFileDoesNotOverwriteEffectiveValuesOrOriginal() throws Exception {
        Path path = directory.resolve("combat.json");
        V3Config.apply(new V3Config.Values(999, 3, 4));
        String original = "{\"version\":1,\"dragonDashRange\":\"NaN\",\"addon\":true}";
        Files.writeString(path, original);
        assertThrows(IllegalArgumentException.class, () -> V3Config.load(path));
        assertEquals(999, V3Config.get().dragonDashRange());
        assertEquals(original, Files.readString(path));
    }
    @Test void rangeCommandHasItsOwnStoreAndEffectiveFeedback() {
        var result = XenoConfigRegistry.set("v3.dragonDashRange", "999");
        assertTrue(result.ok, result.message);
        assertEquals("V3", result.store.name());
        assertTrue(result.message.contains("999"));
        result = XenoConfigRegistry.set("dragonDashRange", "1001");
        assertTrue(result.ok, result.message);
        assertEquals("V3", result.store.name());
        assertTrue(result.message.contains("999"));
        result = XenoConfigRegistry.set("v3.dragonDashRange", "1");
        assertTrue(result.ok, result.message);
        assertTrue(result.message.endsWith("= 2"));
    }
    @Test void tuningTailPersistsClampsAndKeepsTheThreeFieldConstructorCompatible() throws Exception {
        Path path = directory.resolve("combat.json");
        V3Config.load(path);
        assertTrue(XenoConfigRegistry.set("v3.dragonDashSpeed", "99").ok);
        assertEquals(12, V3Config.get().dragonDashSpeed(), "clamped to the documented bound");
        assertTrue(XenoConfigRegistry.set("dashLaunch", "15").ok);
        assertTrue(XenoConfigRegistry.set("v3.dragonDashFollowDistance", "3").ok);
        assertTrue(XenoConfigRegistry.set("dashFollowCooldown", "12").ok);
        assertTrue(XenoConfigRegistry.set("dashFollowWindow", "5").ok);
        assertEquals(10, V3Config.get().dragonDashFollowWindowTicks(), "lower bound");
        assertTrue(XenoConfigRegistry.set("strikeLaunch", "18").ok);
        assertTrue(XenoConfigRegistry.set("v3.strikeApproachRange", "40").ok);
        assertTrue(XenoConfigRegistry.set("strikeCamera", "off").ok);
        assertTrue(XenoConfigRegistry.set("v3.dashCamera", "on").ok);
        assertTrue(XenoConfigRegistry.set("v3Sounds", "false").ok);
        assertFalse(XenoConfigRegistry.set("v3.attackSounds", "maybe").ok);
        assertEquals(24, V3Config.get().dragonDashRange(), "untouched keys keep their value");
        V3Config.save(path);
        V3Config.apply(new V3Config.Values());
        V3Config.load(path);
        var loaded = V3Config.get();
        assertEquals(12, loaded.dragonDashSpeed());
        assertEquals(15, loaded.dragonDashLaunchDistance());
        assertEquals(3, loaded.dragonDashFollowDistance());
        assertEquals(12, loaded.dragonDashFollowCooldownTicks());
        assertEquals(10, loaded.dragonDashFollowWindowTicks());
        assertEquals(18, loaded.strikeLaunchDistance());
        assertEquals(40, loaded.strikeApproachRange());
        assertFalse(loaded.strikeCinematicCamera());
        assertTrue(loaded.dashCamera());
        assertFalse(loaded.attackSounds());
        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1, json.get("version").getAsInt());
        assertTrue(json.has("strikeCinematicCamera"));
        // Old three-field callers still build a full snapshot with every new default.
        var legacy = new V3Config.Values(999, 3, 4);
        assertEquals(new V3Config.Values().dragonDashSpeed(), legacy.dragonDashSpeed());
        assertEquals(new V3Config.Values().attackSounds(), legacy.attackSounds());
        assertEquals(999, legacy.dragonDashRange());
    }

    @Test void nonFiniteRangeIsRefused() {
        for (String value : new String[]{"NaN", "Infinity", "-Infinity"}) {
            var result = XenoConfigRegistry.set("v3.dragonDashRange", value);
            assertFalse(result.ok);
            assertTrue(result.message.contains("finite"), result.message);
        }
    }

    @Test void originalKeysPreserveEveryCustomizedTuningValue() {
        var edit = new V3Config.Values.Edit(new V3Config.Values());
        edit.dragonDashSpeed = 8;
        edit.strikeLaunchDistance = 31;
        edit.strikeCinematicCamera = false;
        edit.attackSoundVolume = 0.4;
        V3Config.apply(edit.build());
        for (String key : new String[] {"v3.dragonDashRange", "v3.heavyAttackerStaminaCost", "v3.heavyVictimStaminaDrain"}) {
            assertTrue(XenoConfigRegistry.set(key, "12").ok);
            assertEquals(8, V3Config.get().dragonDashSpeed());
            assertEquals(31, V3Config.get().strikeLaunchDistance());
            assertFalse(V3Config.get().strikeCinematicCamera());
            assertEquals(0.4, V3Config.get().attackSoundVolume());
        }
    }

    @Test void attackVolumePersistsAndRejectsNonFiniteValues() throws Exception {
        Path path = directory.resolve("sound.json");
        assertTrue(XenoConfigRegistry.set("v3.attackSoundVolume", "0.35").ok);
        assertEquals(0.35, V3Config.get().attackSoundVolume());
        V3Config.save(path);
        V3Config.apply(new V3Config.Values());
        V3Config.load(path);
        assertEquals(0.35, V3Config.get().attackSoundVolume());
        assertTrue(XenoConfigRegistry.set("v3.attackSoundVolume", "9").ok);
        assertEquals(2, V3Config.get().attackSoundVolume());
        assertFalse(XenoConfigRegistry.set("v3.attackSoundVolume", "NaN").ok);
        assertEquals(2, V3Config.get().attackSoundVolume());
    }

    @Test void strikeRequireLockIsIndependentOfStrikeKiRequireLock() {
        assertTrue(V3Config.get().strikeRequireLock());
        assertFalse(V3Config.get().strikeKiRequireLock());
        assertTrue(XenoConfigRegistry.set("v3.strikeRequireLock", "false").ok);
        assertFalse(V3Config.get().strikeRequireLock());
        assertFalse(V3Config.get().strikeKiRequireLock(), "Ki lock flag stays independent");
        assertTrue(XenoConfigRegistry.set("v3.strikeKiRequireLock", "true").ok);
        assertTrue(V3Config.get().strikeKiRequireLock());
        assertFalse(V3Config.get().strikeRequireLock(), "melee lock flag stays independent");
        assertTrue(XenoConfigRegistry.set("strikeLock", "true").ok);
        assertTrue(V3Config.get().strikeRequireLock());
    }

    @Test void postEndAndOpeningCameraHoldsAreIndependentXenosetKeys() throws Exception {
        Path path = directory.resolve("camera-hold.json");
        assertEquals(80, V3Config.get().strikeCameraHoldTicks());
        assertEquals(0, V3Config.get().strikeCinematicCameraHoldTicks());
        assertTrue(XenoConfigRegistry.set("v3.strikeCameraHoldTicks", "120").ok);
        assertEquals(120, V3Config.get().strikeCameraHoldTicks());
        assertEquals(0, V3Config.get().strikeCinematicCameraHoldTicks(), "opening hold stays independent");
        assertTrue(XenoConfigRegistry.set("v3.strikeCinematicCameraHoldTicks", "48").ok);
        assertEquals(48, V3Config.get().strikeCinematicCameraHoldTicks());
        assertEquals(120, V3Config.get().strikeCameraHoldTicks(), "post-END hold stays independent");
        assertTrue(XenoConfigRegistry.set("strikeCamHold", "999").ok);
        assertEquals(400, V3Config.get().strikeCameraHoldTicks(), "upper bound");
        V3Config.save(path);
        V3Config.apply(new V3Config.Values());
        V3Config.load(path);
        assertEquals(400, V3Config.get().strikeCameraHoldTicks());
        assertEquals(48, V3Config.get().strikeCinematicCameraHoldTicks());
        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(400, json.get("strikeCameraHoldTicks").getAsInt());
        assertEquals(48, json.get("strikeCinematicCameraHoldTicks").getAsInt());
    }
}
