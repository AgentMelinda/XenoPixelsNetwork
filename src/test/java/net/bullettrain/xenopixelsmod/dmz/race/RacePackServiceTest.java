package net.bullettrain.xenopixelsmod.dmz.race;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D6a — race pack validate + skeleton create (evidence READY).
 * Writes under a temp {@code dragonminez} root; does not call {@code ConfigManager.reload()}.
 */
class RacePackServiceTest {

    @Test
    void rejectsIllegalRaceId() {
        assertFalse(RacePackService.validateRaceId("Not Valid").ok());
        assertFalse(RacePackService.validateRaceId("../escape").ok());
        assertFalse(RacePackService.validateRaceId("").ok());
        assertFalse(RacePackService.validateRaceId(null).ok());
    }

    @Test
    void acceptsLegalRaceIdSyntax() {
        assertTrue(RacePackService.validateRaceId("xenopixels_custom").ok());
        assertTrue(RacePackService.validateRaceId("race-1").ok());
    }

    @Test
    void createRacePackWritesCharacterJsonWhenReady(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        Files.createDirectories(dmz);

        var result = RacePackService.createRacePack(dmz, "xenopixels_custom", RacePackTemplate.defaults());
        assertTrue(result.ok(), result.message());
        assertEquals("xenopixels_custom", result.raceId());
        assertTrue(Files.isRegularFile(result.characterJson()));
        assertTrue(Files.isDirectory(result.formsDir()));

        String json = Files.readString(result.characterJson(), StandardCharsets.UTF_8);
        JsonObject rootObj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(RacePackService.CONFIG_VERSION, rootObj.get("configVersion").getAsString());
        assertEquals("xenopixels_custom", rootObj.get("raceName").getAsString());
        assertTrue(rootObj.get("useVanillaSkin").getAsBoolean());
        assertEquals("human", rootObj.get("racialSkill").getAsString());
        assertTrue(rootObj.getAsJsonObject("formSkillsCosts").has("superforms"));
        JsonObject superforms = rootObj.getAsJsonObject("formSkillsCosts").getAsJsonObject("superforms");
        assertTrue(superforms.has("buyFromMaster"));
        assertTrue(superforms.has("prices"));
        assertTrue(Files.isDirectory(dmz.resolve("races/xenopixels_custom/forms")));
    }

    @Test
    void createRacePackRejectsDefaultRaceIds(@TempDir Path root) {
        Path dmz = root.resolve("dragonminez");
        var result = RacePackService.createRacePack(dmz, "saiyan", RacePackTemplate.defaults());
        assertFalse(result.ok());
        assertTrue(result.message().toLowerCase().contains("default"));
    }

    @Test
    void updateRacePackOverwritesCustomOnly(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_edit", RacePackTemplate.defaults()).ok());
        assertTrue(RacePackService.isCustomPack(dmz, "xeno_edit"));
        RacePackTemplate next = new RacePackTemplate(
                true, true, true, "human", "kakarot",
                "#111111", "#111111", "#111111",
                "#222222", "#333333", "#333333", "#7FFFFF");
        var updated = RacePackService.updateRacePack(dmz, "xeno_edit", next);
        assertTrue(updated.ok());
        String json = Files.readString(updated.characterJson());
        assertTrue(json.contains("#111111"));
        assertTrue(json.contains("#222222"));
        var blocked = RacePackService.updateRacePack(dmz, "saiyan", next);
        assertFalse(blocked.ok());
    }

    @Test
    void createRacePackRejectsDuplicate(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_dup", RacePackTemplate.defaults()).ok());
        var second = RacePackService.createRacePack(dmz, "xeno_dup", RacePackTemplate.defaults());
        assertFalse(second.ok());
    }

    @Test
    void knownRaceIdsIncludesDefaultsAndScannedPacks(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_listed", RacePackTemplate.defaults()).ok());

        Set<String> known = RacePackService.knownRaceIds(dmz);
        assertTrue(known.contains("saiyan"));
        assertTrue(known.contains("human"));
        assertTrue(known.contains("xeno_listed"));
    }

    @Test
    void readPackRoundTripsCustomAppearance(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        RacePackTemplate template = new RacePackTemplate(
                true, true, true, "human", "kakarot",
                "#AABBCC", "#AABBCC", "#AABBCC",
                "#112233", "#445566", "#445566", "#7FFFFF");
        RacePackService.RacePartDefaults parts =
                new RacePackService.RacePartDefaults(3, 5, 2, 1, 4, 0);
        assertTrue(RacePackService.createRacePack(dmz, "xeno_round", template, parts).ok());
        assertTrue(RacePackService.isCustomPack(dmz, "xeno_round"));

        var loaded = RacePackService.readPack(dmz, "xeno_round");
        assertTrue(loaded.isPresent());
        assertEquals("xeno_round", loaded.get().raceId());
        assertEquals("#AABBCC", loaded.get().template().defaultBodyColor());
        assertEquals("#112233", loaded.get().template().defaultHairColor());
        assertEquals(3, loaded.get().parts().bodyType());
        assertEquals(5, loaded.get().parts().hairType());
        assertEquals(2, loaded.get().parts().eyesType());
        assertEquals(4, loaded.get().parts().mouthType());

        RacePackService.RacePartDefaults nextParts =
                new RacePackService.RacePartDefaults(1, 2, 3, 0, 1, 2);
        RacePackTemplate next = new RacePackTemplate(
                true, true, true, "human", "kakarot",
                "#010101", "#010101", "#010101",
                "#020202", "#030303", "#030303", "#7FFFFF");
        assertTrue(RacePackService.updateRacePack(dmz, "xeno_round", next, nextParts).ok());
        var updated = RacePackService.readPack(dmz, "xeno_round").orElseThrow();
        assertEquals("#010101", updated.template().defaultBodyColor());
        assertEquals(1, updated.parts().bodyType());
        assertEquals(2, updated.parts().hairType());
        assertEquals(3, updated.parts().eyesType());
        assertEquals("races/xeno_round/character.json",
                RacePackService.characterRelativePath("xeno_round"));
    }

    @Test
    void updateRacePackMergesColoursAndKeepsTail(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_merge", RacePackTemplate.defaults()).ok());
        Path character = dmz.resolve("races").resolve("xeno_merge").resolve("character.json");
        JsonObject existing = JsonParser.parseString(Files.readString(character, StandardCharsets.UTF_8))
                .getAsJsonObject();
        existing.addProperty("hasSaiyanTail", true);
        existing.addProperty("customNote", "keep-me");
        Files.writeString(character, existing.toString(), StandardCharsets.UTF_8);

        RacePackTemplate next = new RacePackTemplate(
                true, true, true, "human", "kakarot",
                "#111111", "#222222", "#333333",
                "#444444", "#555555", "#666666", "#7FFFFF");
        assertTrue(RacePackService.updateRacePack(dmz, "xeno_merge", next,
                RacePackService.RacePartDefaults.defaults()).ok());
        JsonObject updated = JsonParser.parseString(Files.readString(character, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertTrue(updated.get("hasSaiyanTail").getAsBoolean());
        assertEquals("keep-me", updated.get("customNote").getAsString());
        assertEquals("#111111", updated.get("defaultBodyColor").getAsString());
        assertEquals("#222222", updated.get("defaultBodyColor2").getAsString());
        assertEquals("#666666", updated.get("defaultEye2Color").getAsString());
    }

    @Test
    void readPackSkipsDefaultRaces(@TempDir Path root) {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.readPack(dmz, "saiyan").isEmpty());
        assertTrue(RacePackService.readPack(dmz, "missing_custom").isEmpty());
    }
}
