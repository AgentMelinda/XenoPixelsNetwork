package net.bullettrain.xenopixelsmod.client.combat.fx;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Drives {@link CombatFxClient}: advances its envelopes each tick and applies the camera kick.
 *
 * <p>The camera is nudged through {@link ViewportEvent.ComputeCameraAngles}, which NeoForge
 * fires after vanilla has resolved the view — so this composes with the player's own look
 * rather than fighting it, and no mixin is needed. Offsets are added, never assigned, so any
 * other mod doing the same thing still gets its say.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class CombatFxEvents {

    private CombatFxEvents() {
    }

    /**
     * Tick the envelopes once per client tick.
     *
     * <p>Hung off the local player's tick rather than a level tick so it stops cleanly while the
     * game is paused in single-player — a shake frozen mid-swing on the pause screen looks like
     * a bug.
     */
    @SubscribeEvent
    public static void onClientTick(PlayerTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) return;
        CombatFxClient.tick();
    }

    /**
     * Eased camera offset, in degrees, one entry per axis (yaw, pitch, roll).
     *
     * <p>Assigned rather than added so a frame that produces no cue relaxes toward zero instead of
     * freezing at the last kick. {@link CombatFxClient#cameraOffset} is recomputed from the shake
     * envelope every frame and is deliberately jittery — three sine terms at three frequencies —
     * so applying it raw makes a fast rush chain whip the view around. Easing toward it keeps the
     * impact readable while removing the snap, which is the same trick the lock-on reticle uses.
     */
    private static final float[] SMOOTHED = new float[3];
    /** Fraction of the remaining distance covered per frame. ~0.35 is snappy but not a snap. */
    private static final float SMOOTH_RATE = 0.35f;

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float[] target = CombatFxClient.shakeActive()
                ? CombatFxClient.cameraOffset((float) event.getPartialTick())
                : ZERO;
        for (int i = 0; i < 3; i++) {
            SMOOTHED[i] += (target[i] - SMOOTHED[i]) * SMOOTH_RATE;
            if (Math.abs(SMOOTHED[i]) < 0.0005f) SMOOTHED[i] = 0.0f;
        }
        if (SMOOTHED[0] == 0.0f && SMOOTHED[1] == 0.0f && SMOOTHED[2] == 0.0f) return;
        event.setYaw(event.getYaw() + SMOOTHED[0]);
        event.setPitch(event.getPitch() + SMOOTHED[1]);
        event.setRoll(event.getRoll() + SMOOTHED[2]);
    }

    private static final float[] ZERO = {0.0f, 0.0f, 0.0f};

    /** Never carry a shake across a disconnect or a dimension change into the next world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CombatFxClient.reset();
        resetSmoothing();
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        CombatFxClient.reset();
        resetSmoothing();
    }

    /** Never carry a half-decayed offset into the next world. */
    private static void resetSmoothing() {
        SMOOTHED[0] = 0.0f;
        SMOOTHED[1] = 0.0f;
        SMOOTHED[2] = 0.0f;
    }
}
