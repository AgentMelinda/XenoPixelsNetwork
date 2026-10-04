package net.bullettrain.xenopixelsmod.client.npc;

import net.minecraft.resources.ResourceLocation;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XenoNpcGeoModelTest {
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
