package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileRefreshPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players who opened an NPC's editor or DMZ screen, so a server-side profile change (a script, a
 * command, another editor) reaches their screen instead of leaving it on a stale copy that a later
 * save would write back. A watch lasts ten minutes from the last open and is dropped on logout.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcProfileWatchers {
    static final long WATCH_MS = 10 * 60 * 1000L;
    static final double RANGE_SQR = 64.0 * 64.0;
    private static final Map<UUID, Map<UUID, Long>> WATCHERS = new ConcurrentHashMap<>();

    private NpcProfileWatchers() {}

    /** Sends {@code player} the NPC's full server profile and keeps them updated. */
    public static void watchAndSend(ServerPlayer player, Entity npc) {
        WATCHERS.computeIfAbsent(npc.getUUID(), ignored -> new ConcurrentHashMap<>())
                .put(player.getUUID(), System.currentTimeMillis() + WATCH_MS);
        ModNetwork.sendToPlayer(player, new NpcProfileRefreshPacket(npc.getId(), NpcCombatProfile.read(npc).toTag()));
    }

    /** Keeps {@code player} updated without sending now (the editor payload already carries the profile). */
    public static void watch(ServerPlayer player, Entity npc) {
        WATCHERS.computeIfAbsent(npc.getUUID(), ignored -> new ConcurrentHashMap<>())
                .put(player.getUUID(), System.currentTimeMillis() + WATCH_MS);
    }

    /** Called after a server-side profile write that changed the stored tag. */
    static void changed(Entity npc, CompoundTag tag) {
        Map<UUID, Long> watchers = WATCHERS.get(npc.getUUID());
        if (watchers == null || watchers.isEmpty()) return;
        MinecraftServer server = npc.getServer();
        if (server == null) return;
        long now = System.currentTimeMillis();
        watchers.entrySet().removeIf(e -> e.getValue() < now);
        for (UUID id : watchers.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != npc.level() || player.distanceToSqr(npc) > RANGE_SQR) continue;
            ModNetwork.sendToPlayer(player, new NpcProfileRefreshPacket(npc.getId(), tag.copy()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        WATCHERS.values().forEach(map -> map.remove(id));
        WATCHERS.values().removeIf(Map::isEmpty);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { WATCHERS.clear(); }
}
