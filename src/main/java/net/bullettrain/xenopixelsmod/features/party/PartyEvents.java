package net.bullettrain.xenopixelsmod.features.party;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.PartyCommands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Command registration and lifecycle cleanup for {@link PartyManager}. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PartyEvents {

    private PartyEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(PartyCommands.build());
    }

    /**
     * Drop a leaving player from their party.
     *
     * <p>Without this the rest of the party keeps a HUD chip for someone who is gone, and worse,
     * the membership map keeps growing across a server's uptime with UUIDs that will never be
     * cleaned up.
     */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PartyManager.onLogout(player);
        }
    }

    /** Parties are session state; make sure a single-player world does not carry them over. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PartyManager.clear();
    }
}
