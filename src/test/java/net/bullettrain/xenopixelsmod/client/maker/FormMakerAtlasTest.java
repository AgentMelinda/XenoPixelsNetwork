package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke: Form Maker atlas sizes, settings keys, unknown-field guard, parts categories.
 */
class FormMakerAtlasTest {
    private static final String[] SHAPES = {
            "xeno_maker_form_list",
            "xeno_maker_form_settings",
            "xeno_maker_preview_sm",
            "xeno_maker_category_col",
            "xeno_maker_part_grid",
            "mynpcs_small_panel",
            "pill_button",
            "banner_top"
    };

    @Test
    void greenFormMakerShapesResolveAtNativeSize() {
        assertEquals(140, XenoAtlasSprites.get("xeno_maker_form_list", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(280, XenoAtlasSprites.get("xeno_maker_form_list", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(220, XenoAtlasSprites.get("xeno_maker_form_settings", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(240, XenoAtlasSprites.get("xeno_maker_form_settings", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(150, XenoAtlasSprites.get("xeno_maker_preview_sm", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(112, XenoAtlasSprites.get("xeno_maker_preview_sm", XenoAtlasSprites.Theme.GREEN).height());
    }

    @Test
    void greenFormMakerPngsAreOnClasspath() {
        for (String shape : SHAPES) {
            String path = "assets/xenopixelsmod/"
                    + XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN).rl().getPath();
            assertNotNull(FormMakerAtlasTest.class.getClassLoader().getResource(path),
                    "missing green sprite: " + path);
        }
    }

    @Test
    void goldBannerAndGreenInnerSpritesResolve() {
        assertEquals(150, XenoAtlasSprites.get("banner_top", XenoAtlasSprites.Theme.GOLD).width());
        assertEquals(60, XenoAtlasSprites.get("banner_top", XenoAtlasSprites.Theme.GOLD).height());
        assertEquals(140, XenoAtlasSprites.get("xeno_maker_form_list", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(220, XenoAtlasSprites.get("xeno_maker_form_settings", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(150, XenoAtlasSprites.get("xeno_maker_preview_sm", XenoAtlasSprites.Theme.GREEN).width());
    }

    @Test
    void settingsKeysAreCuratedVerifiedSubset() {
        assertTrue(FormMakerScreen.SETTINGS_KEYS.contains("$race"));
        assertTrue(FormMakerScreen.SETTINGS_KEYS.contains("$formType"));
        assertTrue(FormMakerScreen.SETTINGS_KEYS.contains("transformationAnimation"));
        assertTrue(FormMakerScreen.SETTINGS_KEYS.contains("auraType"));
        assertTrue(FormMakerScreen.SETTINGS_KEYS.contains("auraColor"));
        assertTrue(FormMakerScreen.SETTINGS_KEYS.contains("hairColor"));
        assertTrue(FormMakerScreen.SETTINGS_KEYS.contains(DmzFormDocument.SCALE_UNIFORM));
        assertFalse(FormMakerScreen.SETTINGS_KEYS.contains("raceType"),
                "do not invent raceType schema key");
        assertFalse(FormMakerScreen.SETTINGS_KEYS.contains("transformationType"),
                "do not invent transformationType; use transformationAnimation");
    }

    @Test
    void unknownFieldGuardRejectsInventedKeysOnDraft() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "saiyan");
        Set<String> known = FormMakerScreen.knownFieldKeys(document);
        assertTrue(known.contains("auraColor"));
        assertTrue(known.contains("hairColor"));
        assertTrue(known.contains("$formType"));
        assertFalse(known.contains("inventedSchemaKey"));
        assertFalse(known.contains("raceType"));
    }

    @Test
    void partsCategoriesMatchRaceMakerPartsStyleLabels() {
        assertEquals(4, FormMakerPartsScreen.PartCategory.values().length);
        assertEquals("Hair", FormMakerPartsScreen.PartCategory.HAIR.label());
        assertEquals("Body", FormMakerPartsScreen.PartCategory.BODY.label());
        assertEquals("Eyes", FormMakerPartsScreen.PartCategory.EYES.label());
        assertEquals("Extra", FormMakerPartsScreen.PartCategory.EXTRA.label());
        assertTrue(FormMakerPartsScreen.PartCategory.HAIR.keys().contains("hairColor"));
        assertTrue(FormMakerPartsScreen.PartCategory.BODY.keys().contains("bodyColor1"));
        assertTrue(FormMakerPartsScreen.categoryLabels().contains("Hair"));
    }

    @Test
    void glowTargetIncludesFormRow() {
        assertNotNull(MakerPreviewController.GlowTarget.FORM_ROW);
        assertNotNull(MakerPreviewController.GlowTarget.PART_CATEGORY);
    }

    @Test
    void openPathHooksExist() {
        assertNotNull(net.bullettrain.xenopixelsmod.client.ClientScreens.openFormMaker);
        assertNotNull(net.bullettrain.xenopixelsmod.client.ClientScreens.openXenoMakerHub);
        assertEquals("Base", FormMakerScreen.displayForm("base"));
    }

    @Test
    void draftSettingsKeysPresentOnDocumentFields() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "saiyan");
        Set<String> known = FormMakerScreen.knownFieldKeys(document);
        for (String key : FormMakerScreen.SETTINGS_KEYS) {
            assertTrue(known.contains(key), "settings key missing from draft fields: " + key);
        }
    }

    @Test
    void statMultiplierKeysAreVerifiedFormDataFields() {
        DmzFormDocument document = DmzFormDocument.create(DmzFormKind.NORMAL, "saiyan");
        Set<String> known = FormMakerScreen.knownFieldKeys(document);
        assertEquals(List.of(
                "strMultiplier", "skpMultiplier", "stmMultiplier", "defMultiplier",
                "vitMultiplier", "pwrMultiplier", "eneMultiplier", "speedMultiplier"),
                FormMakerScreen.STAT_MULTIPLIER_KEYS);
        for (String key : FormMakerScreen.STAT_MULTIPLIER_KEYS) {
            assertTrue(known.contains(key), "stat multiplier missing from FormData: " + key);
        }
        assertFalse(FormMakerScreen.STAT_MULTIPLIER_KEYS.contains("resMultiplier"),
                "RES is (DEF+STM)/2 in DMZ; there is no resMultiplier field");
        assertEquals("STR", FormMakerScreen.statAbbrev("strMultiplier"));
        assertEquals("SPD", FormMakerScreen.statAbbrev("speedMultiplier"));
        document.update("strMultiplier", "3.25");
        assertEquals("3.25", fieldValue(document, "strMultiplier"));
    }

    private static String fieldValue(DmzFormDocument document, String key) {
        for (DmzFormDocument.Field field : document.fields()) {
            if (key.equals(field.key())) {
                return field.value();
            }
        }
        return null;
    }
}
