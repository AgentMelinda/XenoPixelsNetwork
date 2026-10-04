package net.bullettrain.xenopixelsmod.dmz.race;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Custom race packs have no DragonMineZ {@code en_us} keys. RaceSelectionScreen still
 * asks for {@code race.dragonminez.<id>} and {@code race.dragonminez.<id>.desc}, so the
 * literals live in a sidecar and the language mixin serves them.
 */
class RaceLabelRegistryTest {

    @AfterEach
    void clear() {
        RaceLabelRegistry.applySnapshot("{}");
    }

    @Test
    void keysMatchRaceSelectionScreen() {
        assertEquals("race.dragonminez.10", RaceLabelRegistry.nameKey("10"));
        assertEquals("race.dragonminez.10.desc", RaceLabelRegistry.descKey("10"));
        assertEquals("race.dragonminez.test", RaceLabelRegistry.nameKey("test"));
        assertEquals("race.dragonminez.test.desc", RaceLabelRegistry.descKey("TEST"));
    }

    @Test
    void literalsResolveWithoutEnUs() {
        RaceLabelRegistry.put("10", "Bloodline", "A custom bloodline.");

        assertEquals("Bloodline", RaceLabelRegistry.translate("race.dragonminez.10", "en_us"));
        assertEquals("Bloodline", RaceLabelRegistry.translate("race.dragonminez.10", "es_es"));
        assertEquals("A custom bloodline.", RaceLabelRegistry.translate(
                "race.dragonminez.10.desc", "fr_fr"));
        assertEquals("Bloodline", RaceLabelRegistry.displayName("10"));
        assertEquals("A custom bloodline.", RaceLabelRegistry.description("10"));
    }

    @Test
    void missingOrBlankNameFallsThroughToVanillaKey() {
        RaceLabelRegistry.put("10", "", "desc only");
        assertNull(RaceLabelRegistry.translate("race.dragonminez.10", "en_us"));
        assertEquals("desc only", RaceLabelRegistry.translate("race.dragonminez.10.desc", "en_us"));
        assertNull(RaceLabelRegistry.translate("race.dragonminez.missing", "en_us"));
        assertNull(RaceLabelRegistry.translate("item.minecraft.diamond", "en_us"));
        assertNull(RaceLabelRegistry.translate(null, "en_us"));
    }

    @Test
    void formAndGroupKeysStayOwnedByFormRegistry() {
        RaceLabelRegistry.put("10", "Bloodline", "desc");
        assertNull(RaceLabelRegistry.translate(
                "race.dragonminez.human.form.xeno_custom.radiant", "en_us"));
        assertNull(RaceLabelRegistry.translate(
                "race.dragonminez.stack.group.xeno_stack", "en_us"));
        assertNull(RaceLabelRegistry.translate("race.dragonminez.10.form.x", "en_us"));
    }

    @Test
    void snapshotRoundTripAndClear() {
        RaceLabelRegistry.put("10", "Bloodline", "desc-10");
        RaceLabelRegistry.put("test", "Test Race", "desc-test");
        String json = RaceLabelRegistry.snapshotJson();
        RaceLabelRegistry.applySnapshot("{}");
        assertNull(RaceLabelRegistry.translate("race.dragonminez.10", "en_us"));

        RaceLabelRegistry.applySnapshot(json);
        assertEquals("Bloodline", RaceLabelRegistry.translate("race.dragonminez.10", "en_us"));
        assertEquals("Test Race", RaceLabelRegistry.translate("race.dragonminez.test", "en_us"));
        assertEquals("desc-test", RaceLabelRegistry.translate("race.dragonminez.test.desc", "en_us"));
    }

    @Test
    void sidecarWriteReadAndLoadFromRoot(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        Files.createDirectories(dmz.resolve("races").resolve("10"));
        var written = RaceLabelRegistry.writeSidecar(dmz, "10", "Bloodline",
                "Ki attacks cost 25% less Ki.");
        assertTrue(written.ok(), written.message());
        Path sidecar = dmz.resolve("races").resolve("10").resolve("xeno_labels.json");
        assertTrue(Files.isRegularFile(sidecar));
        String json = Files.readString(sidecar, StandardCharsets.UTF_8);
        assertTrue(json.contains("Bloodline"));
        assertFalse(json.contains("raceName"));

        RaceLabelRegistry.applySnapshot("{}");
        RaceLabelRegistry.loadFromRoot(dmz);
        assertEquals("Bloodline", RaceLabelRegistry.translate("race.dragonminez.10", "en_us"));
        assertEquals("Ki attacks cost 25% less Ki.",
                RaceLabelRegistry.translate("race.dragonminez.10.desc", "en_us"));
    }

    @Test
    void sidecarRefusesDefaultRacesAndPathEscape(@TempDir Path root) {
        Path dmz = root.resolve("dragonminez");
        assertFalse(RaceLabelRegistry.writeSidecar(dmz, "saiyan", "Nope", "nope").ok());
        assertFalse(RaceLabelRegistry.writeSidecar(dmz, "../escape", "Nope", "nope").ok());
        assertFalse(RaceLabelRegistry.writeSidecar(dmz, "Not Valid", "Nope", "nope").ok());
    }

    @Test
    void clampsLiteralLengths() {
        String longName = "N".repeat(RaceLabelRegistry.MAX_NAME + 20);
        String longDesc = "D".repeat(RaceLabelRegistry.MAX_DESC + 20);
        RaceLabelRegistry.put("10", longName, longDesc);
        assertEquals(RaceLabelRegistry.MAX_NAME, RaceLabelRegistry.displayName("10").length());
        assertEquals(RaceLabelRegistry.MAX_DESC, RaceLabelRegistry.description("10").length());
    }

    @Test
    void packCreateWritesSidecarWhenLabelsProvided(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        var result = RacePackService.createRacePack(dmz, "10", RacePackTemplate.defaults(),
                RacePackService.RacePartDefaults.defaults(),
                new RaceLabels("10", "Bloodline", "A custom bloodline."));
        assertTrue(result.ok(), result.message());
        Path sidecar = dmz.resolve("races").resolve("10").resolve("xeno_labels.json");
        assertTrue(Files.isRegularFile(sidecar));
        assertTrue(Files.readString(sidecar).contains("Bloodline"));
        var loaded = RacePackService.readPack(dmz, "10").orElseThrow();
        assertEquals("Bloodline", loaded.labels().displayName());
        assertEquals("A custom bloodline.", loaded.labels().description());
    }

    @Test
    void packCreateWithoutLabelsLeavesNoSidecar(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "test", RacePackTemplate.defaults()).ok());
        assertFalse(Files.exists(dmz.resolve("races").resolve("test").resolve("xeno_labels.json")));
        assertTrue(RacePackService.readPack(dmz, "test").orElseThrow().labels().displayName().isBlank());
    }

    @Test
    void updateRacePackMergesLabels(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "10", RacePackTemplate.defaults()).ok());
        var updated = RacePackService.updateRacePack(dmz, "10", RacePackTemplate.defaults(),
                RacePackService.RacePartDefaults.defaults(),
                new RaceLabels("10", "Named Later", "Now it has a desc."));
        assertTrue(updated.ok(), updated.message());
        var loaded = RacePackService.readPack(dmz, "10").orElseThrow();
        assertEquals("Named Later", loaded.labels().displayName());
        assertEquals("Now it has a desc.", loaded.labels().description());
    }
}
