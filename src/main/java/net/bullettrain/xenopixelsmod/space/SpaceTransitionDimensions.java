package net.bullettrain.xenopixelsmod.space;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Dimensions treated as planet surfaces by the client handoff-screen registration. */
public final class SpaceTransitionDimensions {
    public static final List<ResourceLocation> PLANET_SURFACES = List.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            ResourceLocation.fromNamespaceAndPath("dragonminez", "namek"),
            ResourceLocation.fromNamespaceAndPath("xenospace_spike", "moon_surface"),
            ResourceLocation.fromNamespaceAndPath("xenospace_spike", "surface"),
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_surfaces"));

    private SpaceTransitionDimensions() {}
}
