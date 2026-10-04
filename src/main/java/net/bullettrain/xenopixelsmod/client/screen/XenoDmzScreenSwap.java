package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.DmzMenuMode;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.screen.neon.XenoNeonStatsScreen;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiDocumentScreen;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiRuntime;
import net.bullettrain.xenopixelsmod.ui.DmzMenuPage;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
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
        var opening = event.getNewScreen();
        if (opening == null
                || opening instanceof XenoDmzStatsScreen
                || opening instanceof XenoNeonStatsScreen
                || opening instanceof UiDocumentScreen) {
            return;
        }
        if (mode == DmzMenuMode.STUDIO) {
            DmzMenuPage page = DmzMenuPage.fromDmzScreenClass(opening.getClass().getName());
            String documentId = page == null ? null : UiRuntime.assignments().documentId(page);
            UiDocument document = documentId == null ? null : UiRuntime.document(documentId);
            if (document != null) {
                event.setNewScreen(new UiDocumentScreen(document));
            }
            return;
        }
        if (mode != DmzMenuMode.SCREEN && mode != DmzMenuMode.NEON) return;
        // Compared by name, not with instanceof: that would class-load a DragonMineZ type, and this
        // handler is registered whether or not DragonMineZ is installed.
        if (!DMZ_STATS_SCREEN.equals(opening.getClass().getName())) return;

        event.setNewScreen(mode == DmzMenuMode.NEON
                ? new XenoNeonStatsScreen()
                : new XenoDmzStatsScreen());
    }
}
