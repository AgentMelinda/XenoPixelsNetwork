package net.bullettrain.xenopixelsmod.client.music;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Wires the music player: tick playback, add the plate to the Escape menu, let go on logout. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoMusicEvents {
    private XenoMusicEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        XenoMusicPlayer.tick();
    }

    /** Only the real Escape menu ({@code showsPauseMenu}), not the F3+Esc blank pause. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause) || !pause.showsPauseMenu()) return;
        int x = XenoMusicPauseWidget.MARGIN;
        int y = pause.height - XenoMusicPauseWidget.MARGIN - 40;
        event.addListener(new XenoMusicPauseWidget(x, y));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        XenoMusicPlayer.onLogout();
    }
}
