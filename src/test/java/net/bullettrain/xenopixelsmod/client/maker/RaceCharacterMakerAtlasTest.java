package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke: Race Character Maker atlas sizes, categories, Create Race READY, citation gaps.
 */
class RaceCharacterMakerAtlasTest {
    private static final String[] SHAPES = {
            "xeno_maker_race_card",
            "xeno_maker_category_col",
            "xeno_maker_part_grid",
            "mynpcs_small_panel",
            "icon_slot_lg",
            "pill_button",
            "xeno_editor_panel",
            "banner_top"
    };

    @Test
    void greenRaceMakerShapesResolveAtNativeSize() {
        assertEquals(72, XenoAtlasSprites.get("xeno_maker_race_card", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(56, XenoAtlasSprites.get("xeno_maker_race_card", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(96, XenoAtlasSprites.get("xeno_maker_category_col", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(240, XenoAtlasSprites.get("xeno_maker_category_col", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(240, XenoAtlasSprites.get("xeno_maker_part_grid", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(220, XenoAtlasSprites.get("xeno_maker_part_grid", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(176, XenoAtlasSprites.get("mynpcs_small_panel", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(222, XenoAtlasSprites.get("mynpcs_small_panel", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(32, XenoAtlasSprites.get("icon_slot_lg", XenoAtlasSprites.Theme.GREEN).width());
    }

    @Test
    void greenRaceMakerPngsAreOnClasspath() {
        for (String shape : SHAPES) {
            String path = "assets/xenopixelsmod/"
                    + XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN).rl().getPath();
            assertNotNull(RaceCharacterMakerAtlasTest.class.getClassLoader().getResource(path),
                    "missing green sprite: " + path);
        }
    }

    @Test
    void categoriesMatchScreenshotContract() {
        RaceMakerParts.Category[] cats = RaceMakerParts.Category.values();
        assertEquals(7, cats.length);
        assertEquals("Body", cats[0].label());
        assertEquals("Eyes", cats[1].label());
        assertEquals("Mouth", cats[2].label());
        assertEquals("Hair", cats[3].label());
        assertEquals("Aura", cats[4].label());
        assertEquals("Clothes", cats[5].label());
        assertEquals("Extra", cats[6].label());
    }

    @Test
    void auraAndClothesGridsStayEmptyWithCitationGap() {
        List<String> aura = RaceMakerParts.partIds(RaceMakerParts.Category.AURA, "saiyan", "male");
        List<String> clothes = RaceMakerParts.partIds(RaceMakerParts.Category.CLOTHES, "saiyan", "male");
        assertTrue(aura.isEmpty());
        assertTrue(clothes.isEmpty());
        assertTrue(RaceMakerParts.AURA_CITATION_GAP.contains("TextureCounter"));
        assertTrue(RaceMakerParts.CLOTHES_CITATION_GAP.contains("clothes"));
        assertEquals(RaceMakerParts.AURA_CITATION_GAP,
                RaceMakerParts.emptyReason(RaceMakerParts.Category.AURA));
        assertEquals(RaceMakerParts.CLOTHES_CITATION_GAP,
                RaceMakerParts.emptyReason(RaceMakerParts.Category.CLOTHES));
    }

    @Test
    void createRaceEnabledWhenTask7Ready() {
        assertTrue(RaceCharacterMakerScreen.CREATE_RACE_READY);
        assertTrue(RaceCharacterMakerScreen.CREATE_RACE_EVIDENCE.contains("race-registration-path"));
    }

    @Test
    void makerPermissionNodesRegistered() {
        assertEquals("xenopixelsmod.maker.open", XenoPermissions.MAKER_OPEN.getNodeName());
        assertEquals("xenopixelsmod.maker.race.create",
                XenoPermissions.MAKER_RACE_CREATE.getNodeName());
        assertEquals("xenopixelsmod.race.edit", XenoPermissions.RACE_EDIT.getNodeName());
        assertEquals("xenopixelsmod.race.give", XenoPermissions.RACE_GIVE.getNodeName());
    }

    @Test
    void frostdemonDisplaysAsArcosian() {
        assertEquals("Arcosian", RaceCharacterMakerScreen.displayRace("frostdemon"));
        assertEquals("Saiyan", RaceCharacterMakerScreen.displayRace("saiyan"));
    }

    @Test
    void displayRacePrefersLiteralOverFolderId() {
        net.bullettrain.xenopixelsmod.dmz.race.RaceLabelRegistry.applySnapshot("{}");
        net.bullettrain.xenopixelsmod.dmz.race.RaceLabelRegistry.put("10", "Bloodline", "desc");
        assertEquals("Bloodline", RaceCharacterMakerScreen.displayRace("10"));
        net.bullettrain.xenopixelsmod.dmz.race.RaceLabelRegistry.applySnapshot("{}");
        assertEquals("10", RaceCharacterMakerScreen.displayRace("10"));
    }

    @Test
    void glowTargetIncludesPartCategory() {
        assertNotNull(MakerPreviewController.GlowTarget.PART_CATEGORY);
        assertNotNull(MakerPreviewController.GlowTarget.RACE_CARD);
    }

    @Test
    void openPathHooksExist() {
        assertNotNull(net.bullettrain.xenopixelsmod.client.ClientScreens.openXenoMakerHub);
        assertNotNull(net.bullettrain.xenopixelsmod.client.ClientScreens.openRaceCharacterMaker);
        assertFalse(RaceMakerParts.Category.values().length == 0);
    }

    @Test
    void goldBannerAndGreenInnerSpritesResolve() {
        assertEquals(150, XenoAtlasSprites.get("banner_top", XenoAtlasSprites.Theme.GOLD).width());
        assertEquals(60, XenoAtlasSprites.get("banner_top", XenoAtlasSprites.Theme.GOLD).height());
        assertEquals(72, XenoAtlasSprites.get("xeno_maker_race_card", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(96, XenoAtlasSprites.get("xeno_maker_category_col", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(240, XenoAtlasSprites.get("xeno_maker_part_grid", XenoAtlasSprites.Theme.GREEN).width());
    }

    @Test
    void cycleRowXsNeverOverlap() {
        int cycleW = 108;
        int gap = 12;
        int[] xs = RaceCharacterMakerScreen.cycleRowXs(40, cycleW, gap);
        assertEquals(3, xs.length);
        assertEquals(40, xs[0]);
        assertEquals(40 + cycleW + gap, xs[1]);
        assertEquals(40 + 2 * (cycleW + gap), xs[2]);
        assertFalse(RaceCharacterMakerScreen.cycleSlotsOverlap(xs, cycleW));
        assertTrue(xs[1] >= xs[0] + cycleW);
        assertTrue(xs[2] >= xs[1] + cycleW);
        // Packed row stays left of the 280px preview that starts at originX+352.
        assertTrue(xs[2] + cycleW <= 40 + 352);
    }

    @Test
    void customRaceCycleLabelGetsStar() {
        assertEquals("*Xeno", RaceCharacterMakerScreen.cycleRaceLabel("xeno", true));
        assertEquals("Saiyan", RaceCharacterMakerScreen.cycleRaceLabel("saiyan", false));
    }

    @Test
    void racePagerXsKeepsFourCardsOffArrows() {
        int originX = 0;
        int previewX = 352;
        int arrowW = 22;
        int gutter = 4;
        int cardW = 72;
        int[] pager = RaceCharacterMakerScreen.racePagerXs(originX, previewX, arrowW, gutter, cardW);
        assertEquals(4, pager.length);
        int leftX = pager[0];
        int cardsX = pager[1];
        int rightX = pager[2];
        int maxVisible = pager[3];
        assertEquals(0, leftX);
        assertEquals(26, cardsX);
        assertEquals(330, rightX);
        assertEquals(4, maxVisible);
        assertTrue(leftX + arrowW + gutter <= cardsX);
        int lastCardRight = cardsX + (maxVisible - 1) * (cardW + gutter) + cardW;
        assertTrue(lastCardRight <= rightX);
        assertTrue(rightX + arrowW <= previewX);
    }

    @Test
    void colorSlotLabelsAreCategoryScoped() {
        assertEquals(List.of("Skin", "Skin 2", "Skin 3"),
                RaceCharacterMakerScreen.colorSlotLabels(RaceMakerParts.Category.BODY));
        assertEquals(List.of("Eye 1", "Eye 2"),
                RaceCharacterMakerScreen.colorSlotLabels(RaceMakerParts.Category.EYES));
        assertEquals(List.of("Hair"),
                RaceCharacterMakerScreen.colorSlotLabels(RaceMakerParts.Category.HAIR));
        assertEquals(List.of("Aura"),
                RaceCharacterMakerScreen.colorSlotLabels(RaceMakerParts.Category.AURA));
        assertTrue(RaceCharacterMakerScreen.colorSlotLabels(RaceMakerParts.Category.MOUTH).isEmpty());
        assertTrue(RaceCharacterMakerScreen.colorSlotLabels(RaceMakerParts.Category.CLOTHES).isEmpty());
        assertTrue(RaceCharacterMakerScreen.colorSlotLabels(RaceMakerParts.Category.EXTRA).isEmpty());
    }

    @Test
    void makerHudGateRecognizesMakerScreens() {
        assertTrue(MakerHudGate.isMakerScreenClass(RaceCharacterMakerScreen.class));
        assertTrue(MakerHudGate.isMakerScreenClass(XenoMakerHubScreen.class));
        assertTrue(MakerHudGate.isMakerScreenClass(HairMakerScreen.class));
        assertFalse(MakerHudGate.isMakerScreenClass(String.class));
    }

    @Test
    void presetCatalogWiredForCyclerLabels() {
        List<String> cats = MakerPresetCatalog.categoryLabels();
        assertEquals(7, cats.size());
        assertFalse(MakerPresetCatalog.FALLBACK_BODY.isEmpty());
        assertFalse(MakerPresetCatalog.FALLBACK_HAIR.isEmpty());
        // Aura stays empty — no invented fallback.
        assertTrue(MakerPresetCatalog.labels(RaceMakerParts.Category.AURA, "saiyan", "male").isEmpty());
    }
}
