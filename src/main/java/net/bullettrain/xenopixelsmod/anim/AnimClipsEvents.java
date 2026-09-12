package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.AnimClipsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Loads the clip library when the server starts, and hands it to each player that joins. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class AnimClipsEvents {
    private AnimClipsEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        XenoClipLibrary.load();
        XenoTechniqueAnimBindings.load();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AnimClipsNetwork.sendTo(player);
        }
    }
}
