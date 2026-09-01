package net.bullettrain.xenopixelsmod.event;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.SyncXenoStatsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Per-player regeneration and stat sync for the XenoPixels fallback stats.
 *
 * <p>This was a Mixin into {@code Player#tick} — the hottest method in the game, and one that also
 * had to carry seven {@code @Unique} tracking fields on <em>every</em> player instance, client-side
 * ones included. {@link PlayerTickEvent.Post} fires for exactly the players this cares about and
 * needs no bytecode injection at all, so the Mixin was not earning its cost.
 *
 * <p>The gating is unchanged from that Mixin, and all three parts of it matter at 30+ players:
 * <ul>
 *   <li>Work runs on an interval from {@link XenoPerfConfig}, not every tick.</li>
 *   <li>The interval is offset by entity id, so players are spread across ticks instead of all
 *       syncing on the same one.</li>
 *   <li>A packet is only sent when a value actually moved, plus a slow heartbeat so a client that
 *       missed one still converges.</li>
 *   <li>Regen is skipped entirely at cap.</li>
 * </ul>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoStatsSyncHandler {

    private XenoStatsSyncHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isRemoved() || player.connection == null) return;

        int interval = Math.max(5, XenoPerfConfig.statsSyncIntervalTicks);
        // Spread capability and packet work across ticks instead of syncing every player together.
        if ((player.tickCount + player.getId()) % interval != 0) return;

        XenoCapabilities.get(player).ifPresent(data -> {
            // Cheap regen, and only below cap so a full player costs nothing.
            if (data.getKi() < data.getMaxKi()) {
                data.setKi(data.getKi() + 0.4f * (interval / 5f));
            }
            if (data.getStamina() < data.getMaxStamina()) {
                data.setStamina(data.getStamina() + 0.6f * (interval / 5f));
            }

            float hp = player.getHealth();
            float maxHp = player.getMaxHealth();
            float ki = data.getKi();
            float maxKi = data.getMaxKi();
            float stm = data.getStamina();
            float maxStm = data.getMaxStamina();

            int heartbeat = Math.max(interval, XenoPerfConfig.statsSyncHeartbeatTicks);
            boolean dueHeartbeat = player.tickCount - data.getLastSyncTick() >= heartbeat;
            boolean dirty = !XenoPerfConfig.statsSyncOnlyWhenDirty
                    || dueHeartbeat
                    || data.statsDifferFrom(hp, maxHp, ki, maxKi, stm, maxStm);
            if (!dirty) return;

            ModNetwork.sendToPlayer(player,
                    new SyncXenoStatsPacket(hp, maxHp, ki, maxKi, stm, maxStm));
            data.markSynced(hp, maxHp, ki, maxKi, stm, maxStm, player.tickCount);
        });
    }
}
