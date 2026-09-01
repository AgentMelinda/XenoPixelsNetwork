package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoAuraCommands;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BT3 chase: fly at the landing point with velocity only. No {@code connection.teleport}.
 * Lock-on already tracks the camera; this only moves the body.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ChaseFlightSystem {

    /** Close enough to the landing point to snap in and finish. */
    private static final double ARRIVAL_DIST = 0.75;
    /**
     * Hard ceiling regardless of distance/config. {@code chaseFlightTimeoutTicks} and the
     * dist/speed extension can otherwise grow unbounded for a very distant target on an
     * unlimited-range server, leaving a player stuck flying for minutes with no way out other
     * than a relog. This is a safety net, not the primary way to end a chase -- most chases
     * finish long before this via arrival, lost target, or the manual cancel below.
     */
    private static final int ABSOLUTE_MAX_TIMEOUT_TICKS = 600;
    private static final Map<UUID, ChaseState> ACTIVE = new ConcurrentHashMap<>();

    private ChaseFlightSystem() {
    }

    private static final float LOOK_EASE = 0.12f;

    private static final class ChaseState {
        final int targetId;
        int elapsedTicks;
        final boolean turnedAuraOn;
        final boolean noGravityBefore;
        float yaw;
        float pitch;

        ChaseState(int targetId, boolean turnedAuraOn, boolean noGravityBefore, float yaw, float pitch) {
            this.targetId = targetId;
            this.turnedAuraOn = turnedAuraOn;
            this.noGravityBefore = noGravityBefore;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public static void start(ServerPlayer player, LivingEntity target) {
        ChaseState existing = ACTIVE.get(player.getUUID());
        if (existing != null) {
            if (existing.targetId == target.getId()) {
                // Same button, same target, while already mid-flight: read as "cancel" rather
                // than "restart at the same target" -- there was previously no way to manually
                // end a chase short of waiting out the timeout or relogging.
                cancel(player, "§7Chase cancelled");
                return;
            }
            // Different target while already mid-flight: retarget. By now player.isNoGravity()
            // and the aura state reflect the flight itself, not the player's pre-chase state --
            // re-snapshotting here would overwrite the real original values with the in-flight
            // ones, and stop() would then restore into flight forever instead of out of it.
            // Retarget only; keep the original restore snapshot and current look state.
            ACTIVE.put(player.getUUID(), new ChaseState(target.getId(), existing.turnedAuraOn,
                    existing.noGravityBefore, existing.yaw, existing.pitch));
            return;
        }
        boolean wasOn = XenoAuraCommands.isOn(player);
        if (!wasOn) {
            XenoAuraCommands.apply(player, true);
        }
        boolean noGravity = player.isNoGravity();
        player.setNoGravity(true);
        ACTIVE.put(player.getUUID(), new ChaseState(
                target.getId(), !wasOn, noGravity, player.getYRot(), player.getXRot()));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChaseState state = ACTIVE.get(player.getUUID());
        if (state == null) return;

        Entity raw = player.level().getEntity(state.targetId);
        if (!(raw instanceof LivingEntity target) || !target.isAlive()) {
            cancel(player, "§7Chase lost the target");
            return;
        }
        if (!XenoServerConfig.chaseRangeUnlimited()
                && player.distanceTo(target) > XenoServerConfig.chaseMaxRange * 1.5) {
            cancel(player, "§7Chase lost the target");
            return;
        }

        Vec3 landing = Bt3CombatPacket.chaseLanding(player, target);
        Vec3 pos = player.position();
        double dist = pos.distanceTo(landing);
        Vec3 toLanding = landing.subtract(pos);
        float wantYaw = yawOf(toLanding);
        float wantPitch = pitchOf(toLanding);
        if (dist < ARRIVAL_DIST) {
            lookToward(player, state, wantYaw, wantPitch, 0.45f);
            stopMotion(player);
            Bt3CombatPacket.playItSound(player, player.getX(), player.getY(), player.getZ(), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.4f);
            finish(player);
            return;
        }

        int timeout = Math.max(XenoServerConfig.chaseFlightTimeoutTicks, 40);
        double speed = Math.max(0.1, XenoServerConfig.chaseFlightSpeed);
        timeout = Math.min(Math.max(timeout, (int) (dist / speed) + 20), ABSOLUTE_MAX_TIMEOUT_TICKS);
        if (++state.elapsedTicks > timeout) {
            cancel(player, "§7Chase lost the target");
            return;
        }

        double step = Math.min(XenoServerConfig.chaseFlightSpeed, dist);
        Vec3 dir = toLanding.scale(1.0 / dist);
        lookToward(player, state, wantYaw, wantPitch, LOOK_EASE);
        player.setDeltaMovement(dir.scale(step));
        player.hasImpulse = true;
        player.fallDistance = 0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void stopMotion(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.fallDistance = 0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void lookToward(ServerPlayer player, ChaseState state, float wantYaw, float wantPitch, float ease) {
        state.yaw = Mth.rotLerp(ease, state.yaw, wantYaw);
        state.pitch = Mth.rotLerp(ease, state.pitch, wantPitch);
        player.setYRot(state.yaw);
        player.setYHeadRot(state.yaw);
        player.yBodyRot = state.yaw;
        player.setXRot(state.pitch);
    }

    private static float yawOf(Vec3 dir) {
        return (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
    }

    private static float pitchOf(Vec3 dir) {
        double xz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return (float) (-Math.toDegrees(Math.atan2(dir.y, xz)));
    }

    private static void cancel(ServerPlayer player, String message) {
        stop(player);
        player.displayClientMessage(Component.literal(message), true);
    }

    private static void finish(ServerPlayer player) {
        stop(player);
    }

    private static void stop(ServerPlayer player) {
        ChaseState state = ACTIVE.remove(player.getUUID());
        if (state != null) {
            player.setNoGravity(state.noGravityBefore);
            stopMotion(player);
            if (state.turnedAuraOn) {
                XenoAuraCommands.apply(player, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        if (p == null) return;
        UUID id = p.getUUID();
        ChaseState state = ACTIVE.remove(id);
        if (state != null && p instanceof ServerPlayer serverPlayer) {
            serverPlayer.setNoGravity(state.noGravityBefore);
            serverPlayer.setDeltaMovement(Vec3.ZERO);
            if (state.turnedAuraOn) {
                XenoAuraCommands.apply(serverPlayer, false);
            }
        }
    }
}
