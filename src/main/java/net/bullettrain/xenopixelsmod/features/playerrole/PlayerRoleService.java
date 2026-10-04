package net.bullettrain.xenopixelsmod.features.playerrole;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server authority for player roles. Grant/revoke persist via {@link PlayerRoleSavedData} and
 * sync to the client for UI only — no combat modifiers.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerRoleService {
    private PlayerRoleService() {
    }

    public static PlayerRoleId get(ServerPlayer player) {
        if (player == null || player.getServer() == null) return PlayerRoleId.NONE;
        return PlayerRoleSavedData.get(player.getServer()).roleOf(player.getUUID());
    }

    /**
     * Grants {@code role} and syncs. {@code source} is logged (e.g. {@code admin:/xenorole},
     * {@code tournament:&lt;matchId&gt;}).
     */
    public static void grant(ServerPlayer player, PlayerRoleId role, String source) {
        if (player == null || player.getServer() == null) return;
        PlayerRoleId next = role == null ? PlayerRoleId.NONE : role;
        PlayerRoleSavedData.get(player.getServer()).setRole(player.getUUID(), next);
        XenoPixelsMod.LOGGER.info("PlayerRole grant {} → {} source={}",
                player.getGameProfile().getName(), next.id(), source == null ? "" : source);
        PlayerRoleNetwork.syncTo(player);
    }

    /** Clears the role to {@link PlayerRoleId#NONE} and syncs. */
    public static void revoke(ServerPlayer player, String source) {
        grant(player, PlayerRoleId.NONE, source);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRoleNetwork.syncTo(player);
        }
    }
}
