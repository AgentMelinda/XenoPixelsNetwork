package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.server.events.players.combat.TaiyokenHandler;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Method;

/**
 * V3 Solar Flare → DMZ Taiyoken blind.
 *
 * <p>{@link TaiyokenHandler#cast} requires unlocked {@code taiyoken}, empty hands, kicontrol and
 * power-release gates. Strike Solar Flare already spent V3 ki and plays its own pose, so those
 * gates silently no-op the blind. We invoke DMZ's private {@code applyBlind} directly (same LOS /
 * look-dot / duration math + {@code TaiyokenBlindS2C} / mob NBT) and play the stock flash FX.
 */
public final class V3SolarFlare {
    private static final String CAST_ANIMATION = "ki.solarflare_fire";
    private static final Method APPLY_BLIND = resolveApplyBlind();

    private V3SolarFlare() {
    }

    public static boolean isSolarFlare(String techniqueId, String name) {
        String id = techniqueId == null ? "" : techniqueId.toLowerCase();
        String n = name == null ? "" : name.toLowerCase();
        return id.contains("esplosione_solare") || id.contains("solar_flare") || id.contains("solar_explosion")
                || n.contains("solar flare") || n.contains("taiyoken");
    }

    /** Flash + blind victims looking at the caster (DMZ Taiyoken rules). */
    public static void flash(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return;
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(
                    new TriggerAnimationS2C(player.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION,
                            0, -1, CAST_ANIMATION),
                    player);
        } catch (Throwable ignored) {
            // Presentation only.
        }
        try {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    (SoundEvent) MainSounds.KI_EXPLOSION_CHARGE.get(), SoundSource.PLAYERS, 1.2f, 1.6f);
        } catch (Throwable ignored) {
            // Sound optional if DMZ registry shifts.
        }
        if (APPLY_BLIND == null) {
            XenoPixelsMod.LOGGER.warn("Solar Flare: TaiyokenHandler.applyBlind unavailable — no blind");
            return;
        }
        try {
            APPLY_BLIND.invoke(null, player);
        } catch (Throwable failure) {
            XenoPixelsMod.LOGGER.warn("Solar Flare: Taiyoken blind failed: {}", failure.toString());
        }
    }

    private static Method resolveApplyBlind() {
        try {
            Method method = TaiyokenHandler.class.getDeclaredMethod("applyBlind", ServerPlayer.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException missing) {
            XenoPixelsMod.LOGGER.warn("Solar Flare: cannot bind TaiyokenHandler.applyBlind: {}", missing.toString());
            return null;
        }
    }
}
