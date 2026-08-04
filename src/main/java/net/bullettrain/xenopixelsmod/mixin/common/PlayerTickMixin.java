package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.SyncXenoStatsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Light per-player server tick work for XenoPixels fallback stats.
 *
 * <p>Performance notes (30+ players):
 * <ul>
 *   <li>Does not run every tick — interval from {@link XenoPerfConfig}</li>
 *   <li>Only sends a packet when values actually change (dirty), plus a slow heartbeat</li>
 *   <li>Skips regen work when already at cap</li>
 * </ul>
 */
@Mixin(Player.class)
public class PlayerTickMixin {

    @Unique
    private float xenopixelsmod$lastSyncedHp = Float.NaN;
    @Unique
    private float xenopixelsmod$lastSyncedMaxHp = Float.NaN;
    @Unique
    private float xenopixelsmod$lastSyncedKi = Float.NaN;
    @Unique
    private float xenopixelsmod$lastSyncedMaxKi = Float.NaN;
    @Unique
    private float xenopixelsmod$lastSyncedStm = Float.NaN;
    @Unique
    private float xenopixelsmod$lastSyncedMaxStm = Float.NaN;
    @Unique
    private int xenopixelsmod$lastSyncTick = -99999;

    @Inject(method = "tick", at = @At("TAIL"))
    private void xenopixelsmod$onTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.isRemoved() || serverPlayer.connection == null) {
            return;
        }

        int interval = Math.max(5, XenoPerfConfig.statsSyncIntervalTicks);
        // Spread capability/packet work across ticks instead of syncing every player together.
        if ((serverPlayer.tickCount + serverPlayer.getId()) % interval != 0) {
            return;
        }

        serverPlayer.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            // Cheap regen only when below cap (no-op at full)
            if (data.getKi() < data.getMaxKi()) {
                data.setKi(data.getKi() + 0.4f * (interval / 5f));
            }
            if (data.getStamina() < data.getMaxStamina()) {
                data.setStamina(data.getStamina() + 0.6f * (interval / 5f));
            }

            float hp = serverPlayer.getHealth();
            float maxHp = serverPlayer.getMaxHealth();
            float ki = data.getKi();
            float maxKi = data.getMaxKi();
            float stm = data.getStamina();
            float maxStm = data.getMaxStamina();

            int heartbeat = Math.max(interval, XenoPerfConfig.statsSyncHeartbeatTicks);
            boolean dueHeartbeat = serverPlayer.tickCount - xenopixelsmod$lastSyncTick >= heartbeat;
            boolean dirty = !XenoPerfConfig.statsSyncOnlyWhenDirty
                    || dueHeartbeat
                    || changed(hp, xenopixelsmod$lastSyncedHp)
                    || changed(maxHp, xenopixelsmod$lastSyncedMaxHp)
                    || changed(ki, xenopixelsmod$lastSyncedKi)
                    || changed(maxKi, xenopixelsmod$lastSyncedMaxKi)
                    || changed(stm, xenopixelsmod$lastSyncedStm)
                    || changed(maxStm, xenopixelsmod$lastSyncedMaxStm);

            if (!dirty) {
                return;
            }

            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncXenoStatsPacket(hp, maxHp, ki, maxKi, stm, maxStm));

            xenopixelsmod$lastSyncedHp = hp;
            xenopixelsmod$lastSyncedMaxHp = maxHp;
            xenopixelsmod$lastSyncedKi = ki;
            xenopixelsmod$lastSyncedMaxKi = maxKi;
            xenopixelsmod$lastSyncedStm = stm;
            xenopixelsmod$lastSyncedMaxStm = maxStm;
            xenopixelsmod$lastSyncTick = serverPlayer.tickCount;
        });
    }

    @Unique
    private static boolean changed(float a, float b) {
        if (Float.isNaN(b)) {
            return true;
        }
        // ~0.05 absolute — HUD-visible without flooding the pipe
        return Math.abs(a - b) > 0.05f;
    }
}
