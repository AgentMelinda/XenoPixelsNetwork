package net.bullettrain.xenopixelsmod.client.npc;

import net.minecraft.resources.ResourceLocation;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoNpcGeoModelTest {
    @Test
    void sagaGokuSkinsSelectTheActualSharedRigsIncludingNumberedVariants() {
        var goku = ResourceLocation.parse("dragonminez:geo/entity/sagas/saga_goku.geo.json");
        for (String skin : new String[]{"saga_goku_early", "saga_goku_early_2", "saga_goku_early_noweights",
                "saga_goku_mid_base", "saga_goku_mid_base_1", "saga_goku_end_base"}) {
            var selected = ResourceLocation.parse("dragonminez:textures/entity/sagas/" + skin + ".png");
            assertEquals(goku, XenoNpcGeoModel.resolveModelResource(selected, Set.of(goku)::contains), skin);
        }
        for (String form : new String[]{"ssj", "ssj2", "ssj3"}) {
            var model = ResourceLocation.parse("dragonminez:geo/entity/sagas/saga_goku_" + form + ".geo.json");
            var selected = ResourceLocation.parse("dragonminez:textures/entity/sagas/saga_goku_end_" + form + ".png");
            assertEquals(model, XenoNpcGeoModel.resolveModelResource(selected, Set.of(model)::contains));
        }
    }

    @Test
    void unrelatedTextureIsNotOfferedAsAGeckoModelButResourcePackRigsStillWin() {
        var gui = ResourceLocation.parse("dragonminez:textures/gui/icon.png");
        assertNull(XenoNpcGeoModel.findModelResource(gui, ignored -> false));
        var selected = ResourceLocation.parse("dragonminez:textures/entity/sagas/saga_goku_early.png");
        var packRig = ResourceLocation.parse("dragonminez:geo/entity/sagas/saga_goku_early.geo.json");
        var shared = ResourceLocation.parse("dragonminez:geo/entity/sagas/saga_goku.geo.json");
        assertEquals(packRig, XenoNpcGeoModel.findModelResource(selected, Set.of(packRig, shared)::contains));
    }

    @Test
    void sagaAliasesResolveRealPinnedGeometryAndSaibamanKeepsItsOwnAnimations() throws Exception {
        var project = java.nio.file.Path.of(System.getProperty("xenopixels.projectDir"));
        try (var jar = new java.util.zip.ZipFile(project.resolve("libs/dragonminez-2.1.3.jar").toFile())) {
            var exists = (java.util.function.Predicate<ResourceLocation>) id -> jar.getEntry("assets/"
                    + id.getNamespace() + "/" + id.getPath()) != null;
            for (String skin : new String[]{"saga_goku_early", "saga_goku_end_ssj3", "saga_vegeta_end_base",
                    "saga_vegeta_majin", "saga_fgohan_base", "saga_ftrunks_ssg3", "saga_broly_ssj_restricted",
                    "saga_piccolo_kami", "saga_saibaman1", "saga_saibaman6"}) {
                var selected = ResourceLocation.parse("dragonminez:textures/entity/sagas/" + skin + ".png");
                assertTrue(exists.test(selected), skin);
                var model = XenoNpcGeoModel.findModelResource(selected, exists);
                assertTrue(model != null && exists.test(model), skin);
                assertTrue(exists.test(XenoNpcGeoModel.resolveAnimationResource(selected, exists)), skin);
            }
            assertEquals(ResourceLocation.parse("dragonminez:animations/entity/sagas/saga_saibaman.animation.json"),
                    XenoNpcGeoModel.resolveAnimationResource(
                            ResourceLocation.parse("dragonminez:textures/entity/sagas/saga_saibaman3.png"), exists));
        }
    }

    @Test
    void masterGohanTextureSelectionResolvesItsOwnRigAndAnimation() {
        ResourceLocation selected = ResourceLocation.parse(
                "dragonminez:textures/entity/master/master_gohan.png");
        ResourceLocation model = ResourceLocation.parse(
                "dragonminez:geo/entity/master/master_gohan.geo.json");
        ResourceLocation animation = ResourceLocation.parse(
                "dragonminez:animations/entity/master/master_gohan.animation.json");
        assertEquals(model, XenoNpcGeoModel.resolveModelResource(selected, Set.of(model)::contains));
        assertEquals(animation, XenoNpcGeoModel.resolveAnimationResource(
                selected, Set.of(animation)::contains));
    }

    @Test
    void textureOnlySlugVariantUsesItsExistingBaseRig() {
        ResourceLocation selected = ResourceLocation.parse(
                "dragonminez:textures/entity/sagas/saga_slug_giant.png");
        ResourceLocation base = ResourceLocation.parse(
                "dragonminez:geo/entity/sagas/saga_slug.geo.json");
        assertEquals(base, XenoNpcGeoModel.resolveModelResource(selected, Set.of(base)::contains));
        assertEquals(base, XenoNpcGeoModel.resolveModelResource(
                ResourceLocation.parse("dragonminez:textures/entity/sagas/saga_slug_fp.png"),
                Set.of(base)::contains));
        assertEquals(ResourceLocation.parse("dragonminez:animations/entity/sagas/saga_base.animation.json"),
                XenoNpcGeoModel.resolveAnimationResource(selected, ignored -> false));
    }

    @Test
    void missingRigFallsBackWithoutCrashingTheWorldRender() {
        ResourceLocation selected = ResourceLocation.parse("dragonminez:textures/entity/sagas/missing.png");
        assertEquals(ResourceLocation.parse("dragonminez:geo/entity/enemies/robotxv.geo.json"),
                XenoNpcGeoModel.resolveModelResource(selected, ignored -> false));
    }

    @Test
    void explicitAnimationAssetWinsAndMissingOneFallsBack() {
        ResourceLocation model = ResourceLocation.parse("mypack:ogre");
        ResourceLocation animation = ResourceLocation.parse("mypack:animations/ogre_combat.animation.json");
        ResourceLocation derived = ResourceLocation.parse("mypack:animations/ogre.animation.json");
        assertEquals(animation, XenoNpcGeoModel.resolveAnimationResource(
                model, animation, Set.of(animation, derived)::contains));
        assertEquals(derived, XenoNpcGeoModel.resolveAnimationResource(
                model, ResourceLocation.parse("mypack:animations/missing.animation.json"),
                Set.of(derived)::contains));

        NpcCombatProfile profile = new NpcCombatProfile();
        profile.modelAnimation = animation.toString();
        assertEquals(animation.toString(), NpcCombatProfile.fromTag(profile.toTag()).modelAnimation);
        NpcCombatProfile client = new NpcCombatProfile();
        client.applyVisualOptions(profile.visualOptionsTag());
        assertEquals(animation.toString(), client.modelAnimation);
    }
}
