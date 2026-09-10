package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.DmzMenuMode;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.screen.neon.XenoNeonStatsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Opens one of XenoPixels' own stats screens in place of DragonMineZ's, under
 * {@code /xenohud menus screen} or {@code /xenohud menus neon}.
 *
 * <p>An event rather than a mixin. {@code ScreenEvent.Opening} is fired for every screen the game
 * opens, so this catches the V key, the navigation buttons on DMZ's other menus, and any other route
 * into that screen -- where hooking the key handler would only have caught the first of those.
 *
 * <p>Inert in every other menu mode, and it never touches any screen but that one.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoDmzScreenSwap {

    private static final String DMZ_STATS_SCREEN =
            "com.dragonminez.client.gui.character.CharacterStatsScreen";

    private XenoDmzScreenSwap() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        DmzMenuMode mode = XenoHudConfig.dmzMenuMode;
        if (mode != DmzMenuMode.SCREEN && mode != DmzMenuMode.NEON) return;
        var opening = event.getNewScreen();
        // Ours is already the replacement; swapping it again would recurse.
        if (opening == null
                || opening instanceof XenoDmzStatsScreen
                || opening instanceof XenoNeonStatsScreen) {
            return;
        }
        // Compared by name, not with instanceof: that would class-load a DragonMineZ type, and this
        // handler is registered whether or not DragonMineZ is installed.
        if (!DMZ_STATS_SCREEN.equals(opening.getClass().getName())) return;

        event.setNewScreen(mode == DmzMenuMode.NEON
                ? new XenoNeonStatsScreen()
                : new XenoDmzStatsScreen());
    }
}
