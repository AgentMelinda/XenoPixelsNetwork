package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.config.DmzMenuMode;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DmzHudThemeTexturesTest {
    /**
     * Every test names the mode it is about.
     *
     * <p>The stock case used to rely on the shipped default being stock, so making the themed
     * menus the default turned a passing test into a failing one without anything about the remap
     * changing. A test of stock behaviour has to say "stock".
     */
    @BeforeEach
    void startFromStock() {
        XenoHudConfig.dmzMenuMode = DmzMenuMode.STOCK;
    }

    @AfterEach
    void restoreShippedDefault() {
        XenoHudConfig.dmzMenuMode = DmzMenuMode.DEFAULT;
    }

    @Test
    void stockModeLeavesDragonMineZTexturesUntouched() {
        ResourceLocation original = ResourceLocation.fromNamespaceAndPath(
                "dragonminez", "textures/gui/lock_on.png");

        assertSame(original, DmzHudThemeTextures.remap(original));
    }

    @Test
    void themedModesRemapEverySupportedHudAtlas() {
        XenoHudConfig.dmzMenuMode = DmzMenuMode.THEME;

        assertRemaps("textures/gui/lock_on.png", "lock_on.png");
        assertRemaps("textures/gui/radar.png", "radar.png");
        assertRemaps("textures/gui/scouter/scouter_blue.png", "scouter_blue.png");
        assertRemaps("textures/gui/scouter/scouter_green.png", "scouter_green.png");
        assertRemaps("textures/gui/scouter/scouter_purple.png", "scouter_purple.png");
        assertRemaps("textures/gui/scouter/scouter_red.png", "scouter_red.png");

        XenoHudConfig.dmzMenuMode = DmzMenuMode.SCREEN;
        assertRemaps("textures/gui/radar.png", "radar.png");
    }

    @Test
    void themedModeLeavesUnknownAndForeignTexturesUntouched() {
        XenoHudConfig.dmzMenuMode = DmzMenuMode.THEME;
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath(
                "dragonminez", "textures/gui/unknown.png");
        ResourceLocation foreign = ResourceLocation.fromNamespaceAndPath(
                "minecraft", "textures/gui/unknown.png");

        assertSame(unknown, DmzHudThemeTextures.remap(unknown));
        assertSame(foreign, DmzHudThemeTextures.remap(foreign));
    }

    private static void assertRemaps(String originalPath, String themedFile) {
        ResourceLocation original = ResourceLocation.fromNamespaceAndPath("dragonminez", originalPath);
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(
                "xenopixelsmod", "textures/gui/dmz_theme/" + themedFile);
        assertEquals(expected, DmzHudThemeTextures.remap(original));
    }
}
