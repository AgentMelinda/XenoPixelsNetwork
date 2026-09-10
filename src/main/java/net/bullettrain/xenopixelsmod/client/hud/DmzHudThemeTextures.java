package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Selects Xeno replacements for DragonMineZ HUD textures while the Xeno menu theme is active. */
public final class DmzHudThemeTextures {
    private static final String DRAGONMINEZ = "dragonminez";
    private static final String THEME_ROOT = "textures/gui/dmz_theme/";
    private static final Map<String, String> REPLACEMENTS = Map.of(
            "textures/gui/lock_on.png", "lock_on.png",
            "textures/gui/radar.png", "radar.png",
            "textures/gui/scouter/scouter_blue.png", "scouter_blue.png",
            "textures/gui/scouter/scouter_green.png", "scouter_green.png",
            "textures/gui/scouter/scouter_purple.png", "scouter_purple.png",
            "textures/gui/scouter/scouter_red.png", "scouter_red.png"
    );

    private DmzHudThemeTextures() {
    }

    public static ResourceLocation remap(ResourceLocation original) {
        if (!XenoHudConfig.dmzMenusThemed() || original == null
                || !DRAGONMINEZ.equals(original.getNamespace())) {
            return original;
        }
        String replacement = REPLACEMENTS.get(original.getPath());
        return replacement == null ? original : ResourceLocation.fromNamespaceAndPath(
                "xenopixelsmod", THEME_ROOT + replacement);
    }
}
