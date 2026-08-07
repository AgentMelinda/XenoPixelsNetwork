package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;

/**
 * Central registry of texture resource locations used by the LDLib-backed
 * HUD view ({@link XenoHudView}). Phase 4 of the LDLib HUD migration plan.
 *
 * <p>{@link #WHITE} is a 1x1 opaque white pixel shipped with the mod
 * ({@code assets/xenopixelsmod/textures/gui/white.png}) used as a tintable
 * base for solid-color bars/frames via LDLib's {@code IGuiTexture#setColor}.
 * There is currently no dedicated XV2-style HUD chrome atlas, so the LDLib
 * view intentionally renders flat tinted rectangles rather than the ornate
 * rounded/shadowed chrome the legacy procedural renderer draws by hand.</p>
 */
public final class XenoHudTextures {
    private XenoHudTextures() {
    }

    public static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "textures/gui/white.png");

    /** Existing HUD atlas (currently only used for the skill-orb icon). */
    public static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "textures/gui/xeno_hud.png");
}
