package net.bullettrain.xenopixelsmod.dmz.race;

import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceAppearanceCatalogTest {

    @Test
    void generatedBodyUsesOwnNamespaceAndIsIncludedInResourcePack(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_custom", RacePackTemplate.defaults()).ok());
        var catalog = RaceAppearanceCatalog.load(dmz, "xeno_custom");
        var body = catalog.addGeneratedBody(dmz, "female", "#ffffff");
        catalog.save(dmz);
        var character = com.google.gson.JsonParser.parseString(
                Files.readString(dmz.resolve("races/xeno_custom/character.json"))).getAsJsonObject();
        assertEquals("xeno_custom", character.get("customModel").getAsString());
        assertTrue(Files.isRegularFile(RaceAssetPack.packRoot(root).resolve("assets/dragonminez/" + body.texturePath())));
    }

    @Test
    void genderlessNonVanillaBodyStartsAtZero(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        RacePackTemplate original = RacePackTemplate.defaults();
        RacePackTemplate template = new RacePackTemplate(false, false, true,
                original.racialSkill(), original.auraType(), original.defaultBodyColor(),
                original.defaultBodyColor2(), original.defaultBodyColor3(), original.defaultHairColor(),
                original.defaultEye1Color(), original.defaultEye2Color(), original.defaultAuraColor());
        assertTrue(RacePackService.createRacePack(dmz, "xeno_neutral", template).ok());
        var catalog = RaceAppearanceCatalog.load(dmz, "xeno_neutral");
        var body = catalog.addGeneratedBody(dmz, "female", "#ffffff");
        assertEquals(0, body.index());
        assertEquals("textures/entity/races/xeno_neutral/xeno_neutral_0_layer1.png", body.texturePath());
        catalog.save(dmz);
        assertEquals(0, RaceAppearanceCatalog.load(dmz, "xeno_neutral").bodies().getFirst().index());
    }

    @Test
    void catalogPathsCannotEscapeRaceDirectory(@TempDir Path root) {
        var catalog = RaceAppearanceCatalog.load(root.resolve("dragonminez"), "xeno_safe");
        var style = new RaceAppearanceCatalog.HairStyle("bad", "../../outside.json", "Bad");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> catalog.hairJson(root.resolve("dragonminez"), style));
        assertFalse(RaceAppearanceCatalog.load(root, "../outside").isWritable());
    }

    @Test
    void emptyCatalogRoundTrips(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_cat", RacePackTemplate.defaults()).ok());
        RaceAppearanceCatalog catalog = RaceAppearanceCatalog.load(dmz, "xeno_cat");
        assertEquals("xeno_cat", catalog.raceId());
        assertTrue(catalog.bodies().isEmpty());
        assertTrue(catalog.hairs().isEmpty());
        catalog.save(dmz);
        Path json = RaceAppearanceCatalog.catalogJson(dmz, "xeno_cat");
        assertTrue(Files.isRegularFile(json));
        String raw = Files.readString(json);
        assertTrue(raw.contains("xenopixels.race.catalog.v1"));
        RaceAppearanceCatalog reloaded = RaceAppearanceCatalog.load(dmz, "xeno_cat");
        assertEquals("xeno_cat", reloaded.raceId());
    }

    @Test
    void addBodyWritesPngAndCatalogEntry(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_body", RacePackTemplate.defaults()).ok());
        RaceAppearanceCatalog catalog = RaceAppearanceCatalog.load(dmz, "xeno_body");
        RaceAppearanceCatalog.BodyType added = catalog.addGeneratedBody(dmz, "male", "#C68642");
        assertEquals("male", added.gender());
        assertEquals(1, added.index());
        assertTrue(Files.isRegularFile(catalog.bodyPng(dmz, added)));
        assertEquals(64, RaceBodyPng.readSize(catalog.bodyPng(dmz, added)));
        catalog.save(dmz);

        RaceAppearanceCatalog reloaded = RaceAppearanceCatalog.load(dmz, "xeno_body");
        assertEquals(1, reloaded.bodies().size());
        assertEquals(1, reloaded.bodies().get(0).index());
        assertEquals(1, reloaded.maxBodyIndex("male"));
        assertEquals(
                "textures/entity/races/xeno_body/xeno_body_male_1_layer1.png",
                reloaded.bodies().get(0).texturePath());
    }

    @Test
    void addHairWritesProjectJsonAndReloads(@TempDir Path root) throws Exception {
        Path dmz = root.resolve("dragonminez");
        assertTrue(RacePackService.createRacePack(dmz, "xeno_hair", RacePackTemplate.defaults()).ok());
        RaceAppearanceCatalog catalog = RaceAppearanceCatalog.load(dmz, "xeno_hair");
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        doc.name("Fringe");
        RaceAppearanceCatalog.HairStyle style = catalog.addHairStyle(dmz, "Fringe", doc);
        assertEquals("Fringe", style.label());
        assertTrue(Files.isRegularFile(catalog.hairJson(dmz, style)));
        catalog.save(dmz);

        RaceAppearanceCatalog reloaded = RaceAppearanceCatalog.load(dmz, "xeno_hair");
        assertEquals(1, reloaded.hairs().size());
        HairMakerDocument loaded = reloaded.readHair(dmz, reloaded.hairs().get(0));
        assertEquals("Fringe", loaded.name());
        assertEquals("TOP", loaded.face());
        assertEquals(4, loaded.selected().length());
        assertEquals("#ffaa00", loaded.selected().color());
    }

    @Test
    void refusesDefaultRaceIds(@TempDir Path root) {
        Path dmz = root.resolve("dragonminez");
        RaceAppearanceCatalog catalog = RaceAppearanceCatalog.load(dmz, "saiyan");
        assertTrue(catalog.hairs().isEmpty());
        assertFalse(catalog.isWritable());
    }
}
