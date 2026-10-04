package net.bullettrain.xenopixelsmod.client.maker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MakerPresetCatalog: live labels when RaceMakerParts has counts; documented FALLBACK otherwise.
 *
 * <p>Unit classpath typically has TextureCounter / HairManager counts of 0, so Body/Eyes/Mouth/
 * Hair/Extra assert FALLBACK. Aura/Clothes stay empty (citation gap).
 */
class MakerPresetCatalogTest {

    @Test
    void fallbackBodyIsOneThroughEight() {
        assertEquals(8, MakerPresetCatalog.FALLBACK_BODY.size());
        assertEquals("Body 1", MakerPresetCatalog.FALLBACK_BODY.get(0));
        assertEquals("Body 8", MakerPresetCatalog.FALLBACK_BODY.get(7));
    }

    @Test
    void fallbackEyesIsOneThroughSix() {
        assertEquals(6, MakerPresetCatalog.FALLBACK_EYES.size());
        assertEquals("Eyes 1", MakerPresetCatalog.FALLBACK_EYES.get(0));
        assertEquals("Eyes 6", MakerPresetCatalog.FALLBACK_EYES.get(5));
    }

    @Test
    void fallbackHairIncludesDefaultAndSsjStyleNames() {
        List<String> hair = MakerPresetCatalog.FALLBACK_HAIR;
        assertTrue(hair.contains("Default"));
        assertTrue(hair.contains("SSJ-style"));
        assertTrue(hair.contains("SSJ2-style"));
        assertTrue(hair.contains("SSJ3-style"));
    }

    @Test
    void labelsUseFallbackWhenLiveCountsAreZero() {
        // Without client race textures / HairManager presets, RaceMakerParts returns empty → FALLBACK.
        if (RaceMakerParts.partIds(RaceMakerParts.Category.BODY, "saiyan", "male").isEmpty()) {
            assertEquals(MakerPresetCatalog.FALLBACK_BODY,
                    MakerPresetCatalog.labels(RaceMakerParts.Category.BODY, "saiyan", "male"));
            assertFalse(MakerPresetCatalog.isLive(RaceMakerParts.Category.BODY, "saiyan", "male"));
        }
        if (RaceMakerParts.partIds(RaceMakerParts.Category.EYES, "saiyan", "male").isEmpty()) {
            assertEquals(MakerPresetCatalog.FALLBACK_EYES,
                    MakerPresetCatalog.labels(RaceMakerParts.Category.EYES, "saiyan", "male"));
        }
        if (RaceMakerParts.partIds(RaceMakerParts.Category.HAIR, "saiyan", "male").isEmpty()) {
            assertEquals(MakerPresetCatalog.FALLBACK_HAIR,
                    MakerPresetCatalog.labels(RaceMakerParts.Category.HAIR, "saiyan", "male"));
        }
        if (RaceMakerParts.partIds(RaceMakerParts.Category.MOUTH, "saiyan", "male").isEmpty()) {
            assertEquals(MakerPresetCatalog.FALLBACK_MOUTH,
                    MakerPresetCatalog.labels(RaceMakerParts.Category.MOUTH, "saiyan", "male"));
        }
        if (RaceMakerParts.partIds(RaceMakerParts.Category.EXTRA, "saiyan", "male").isEmpty()) {
            assertEquals(MakerPresetCatalog.FALLBACK_EXTRA,
                    MakerPresetCatalog.labels(RaceMakerParts.Category.EXTRA, "saiyan", "male"));
        }
        if (RaceMakerParts.partIds(RaceMakerParts.Category.TATTOO, "saiyan", "male").isEmpty()
                || RaceMakerParts.partIds(RaceMakerParts.Category.TATTOO, "saiyan", "male")
                .equals(List.of(RaceMakerParts.TAOTTO_PART))) {
            List<String> tattoo = MakerPresetCatalog.labels(RaceMakerParts.Category.TATTOO, "saiyan", "male");
            assertTrue(tattoo.contains("Taotto") || tattoo.get(0).equals("Taotto"));
        }
    }

    @Test
    void partIdsParallelLabelsForFallbackBody() {
        if (!RaceMakerParts.partIds(RaceMakerParts.Category.BODY, "human", "male").isEmpty()) {
            return;
        }
        List<String> labels = MakerPresetCatalog.labels(RaceMakerParts.Category.BODY, "human", "male");
        List<String> ids = MakerPresetCatalog.partIds(RaceMakerParts.Category.BODY, "human", "male");
        assertEquals(labels.size(), ids.size());
        assertEquals("body:1", ids.get(0));
        assertEquals("body:8", ids.get(7));
        assertEquals("Body 1", labels.get(0));
    }

    @Test
    void auraAndClothesStayEmptyNoInventedFallback() {
        assertTrue(MakerPresetCatalog.labels(RaceMakerParts.Category.AURA, "saiyan", "male").isEmpty());
        assertTrue(MakerPresetCatalog.partIds(RaceMakerParts.Category.AURA, "saiyan", "male").isEmpty());
        assertTrue(MakerPresetCatalog.labels(RaceMakerParts.Category.CLOTHES, "saiyan", "female").isEmpty());
        assertTrue(MakerPresetCatalog.partIds(RaceMakerParts.Category.CLOTHES, "saiyan", "female").isEmpty());
    }

    @Test
    void categoryLabelsMatchScreenshotContract() {
        List<String> cats = MakerPresetCatalog.categoryLabels();
        assertEquals(List.of("Body", "Eyes", "Mouth", "Hair", "Tattoo", "Aura", "Clothes", "Extra"), cats);
    }

    @Test
    void labelForPartIdFormatsKnownPrefixes() {
        assertEquals("Body 2", MakerPresetCatalog.labelForPartId("body:2"));
        assertEquals("Eyes 0", MakerPresetCatalog.labelForPartId("eyes:0"));
        assertEquals("Hair 5", MakerPresetCatalog.labelForPartId("hair:5"));
        assertEquals("Nose 1", MakerPresetCatalog.labelForPartId("extra:nose:1"));
        assertEquals("Tattoo 3", MakerPresetCatalog.labelForPartId("extra:tattoo:3"));
    }

    @Test
    void livePathUsesRaceMakerPartsWhenNonEmpty() {
        List<String> live = RaceMakerParts.partIds(RaceMakerParts.Category.HAIR, "saiyan", "male");
        if (live.isEmpty()) {
            return;
        }
        List<String> labels = MakerPresetCatalog.labels(RaceMakerParts.Category.HAIR, "saiyan", "male");
        assertEquals(live.size(), labels.size());
        assertTrue(MakerPresetCatalog.isLive(RaceMakerParts.Category.HAIR, "saiyan", "male"));
        assertEquals(MakerPresetCatalog.labelForPartId(live.get(0)), labels.get(0));
    }
}
