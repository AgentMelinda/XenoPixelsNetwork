package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.effect.ModEffects;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.HakaiFadePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side Hakai dissolve. Amplifier 0..255 = erase progress.
 *
 * <p>The visual fade is {@link HakaiFadePacket}, sent with
 * {@link ModNetwork#sendToTrackingAndSelf}. The hidden {@code hakai_dissolve} effect is still
 * written (and still vanilla-broadcast) as a fallback, but drawing must not depend on it:
 * vanilla never syncs a non-player living entity's effects to tracker clients, and
 * {@code forceAddEffect} can no-op through {@code CommonHooks.canMobEffectBeApplied}.
 *
 * <p>{@code forceAddEffect} is used instead of {@code addEffect} because
 * {@code MobEffectInstance.update} never lowers an amplifier, which made the restore ramp unable
 * to fade the body back in. Packet and effect signatures verified with {@code javap} against
 * {@code neoforge-21.1.248-merged.jar}.
 */
public final class NpcDissolve {
    private static final int DURATION_TICKS = 40;

    private NpcDissolve() {}

    public static int amplifierForProgress(float eraseProgress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, eraseProgress));
        return Math.max(0, Math.min(255, Math.round(clamped * 255.0f)));
    }

    public static void apply(LivingEntity target, float eraseProgress) {
        if (target == null) return;
        int amplifier = amplifierForProgress(eraseProgress);
        if (amplifier <= 0) {
            clear(target);
            return;
        }
        MobEffectInstance instance = new MobEffectInstance(ModEffects.HAKAI_DISSOLVE, DURATION_TICKS,
                amplifier, true, false, false);
        target.forceAddEffect(instance, null);
        broadcast(target, new ClientboundUpdateMobEffectPacket(target.getId(), instance, false));
        sendFade(target, amplifier);
    }

    public static void clear(LivingEntity target) {
        if (target == null) return;
        if (target.removeEffect(ModEffects.HAKAI_DISSOLVE)) {
            broadcast(target, new ClientboundRemoveMobEffectPacket(target.getId(), ModEffects.HAKAI_DISSOLVE));
        }
        sendFade(target, 0);
    }

    private static void sendFade(LivingEntity target, int amplifier) {
        if (target.level() instanceof ServerLevel) {
            ModNetwork.sendToTrackingAndSelf(target, new HakaiFadePacket(target.getId(), amplifier));
        }
    }

    private static void broadcast(LivingEntity target, Packet<?> packet) {
        if (target.level() instanceof ServerLevel level) {
            level.getChunkSource().broadcast(target, packet);
        }
    }
}
