package net.bullettrain.xenopixelsmod.api.anim;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoAnimApiTest {

    @AfterEach
    void clearRuntimeNames() {
        Bt3AnimationCatalog.clearDynamicAnimations();
    }

    @Test
    void aShippedClipResolvesFromEitherForm() {
        String full = Bt3AnimationCatalog.customAnimationNames().iterator().next();
        String bare = full.substring(XenoAnimApi.PREFIX.length());
        assertEquals(full, XenoAnimApi.resolve(full));
        assertEquals(full, XenoAnimApi.resolve(bare));
        assertTrue(XenoAnimApi.isClipAvailable(bare));
    }

    @Test
    void anUnknownClipResolvesToNothing() {
        assertNull(XenoAnimApi.resolve("no_such_clip_anywhere"));
        assertNull(XenoAnimApi.resolve(""));
        assertNull(XenoAnimApi.resolve(null));
        assertFalse(XenoAnimApi.isClipAvailable("no_such_clip_anywhere"));
    }

    /** The whole point of the runtime registry: a clip the build never heard of becomes playable. */
    @Test
    void aLibraryClipBecomesPlayableOnceRegistered() {
        assertFalse(XenoAnimApi.isClipAvailable("published_later"));
        Bt3AnimationCatalog.registerDynamicAnimation("combat.xeno_published_later");
        assertEquals("combat.xeno_published_later", XenoAnimApi.resolve("published_later"));
        assertTrue(XenoAnimApi.listClips().contains("combat.xeno_published_later"));
    }

    @Test
    void aRuntimeNameOutsideOurPrefixIsIgnored() {
        Bt3AnimationCatalog.registerDynamicAnimation("animation.somebody_else.walk");
        assertFalse(XenoAnimApi.listClips().contains("animation.somebody_else.walk"));
    }

    @Test
    void resolveSanitisesTheBareFormTheSameWayAClipNameIs() {
        Bt3AnimationCatalog.registerDynamicAnimation("combat.xeno_my_clip_");
        assertEquals("combat.xeno_my_clip_", XenoAnimApi.resolve("My Clip!"));
    }

    @Test
    void shippedNamesSurviveClearingTheRuntimeOnes() {
        String full = Bt3AnimationCatalog.customAnimationNames().iterator().next();
        Bt3AnimationCatalog.registerDynamicAnimation("combat.xeno_temporary");
        Bt3AnimationCatalog.clearDynamicAnimations();
        assertFalse(XenoAnimApi.isClipAvailable("temporary"));
        assertTrue(XenoAnimApi.isClipAvailable(full), "the shipped table is not touched");
    }

    @Test
    void speedAndDurationClampToTheDocumentedRange() {
        assertEquals(0.15f, XenoAnimApi.clampSpeed(0.01f), 1.0e-6f);
        assertEquals(4.0f, XenoAnimApi.clampSpeed(99.0f), 1.0e-6f);
        assertEquals(1, XenoAnimApi.clampDuration(0));
        assertEquals(6000, XenoAnimApi.clampDuration(99999));
    }

    @Test
    void clipDurationReadsTheCatalogLength() {
        String full = Bt3AnimationCatalog.customAnimationNames().iterator().next();
        int ticks = XenoAnimApi.clipDuration(full);
        assertTrue(ticks > 0, "shipped clips have an authored length");
        assertEquals(ticks, XenoAnimApi.clipDuration(full.substring(XenoAnimApi.PREFIX.length())));
        assertEquals(-1, XenoAnimApi.clipDuration("no_such_clip_anywhere"));
    }

    @Test
    void aLibraryNameIsPlayableAndHasUnknownDurationUntilPublished() {
        Bt3AnimationCatalog.registerDynamicAnimation("combat.xeno_scripted_later");
        assertTrue(XenoAnimApi.isClipAvailable("scripted_later"));
        assertEquals(-1, XenoAnimApi.clipDuration("scripted_later"));
    }
}
