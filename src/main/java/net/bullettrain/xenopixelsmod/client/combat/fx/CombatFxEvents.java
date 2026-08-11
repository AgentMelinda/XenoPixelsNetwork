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

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!CombatFxClient.shakeActive()) return;
        float[] offset = CombatFxClient.cameraOffset((float) event.getPartialTick());
        event.setYaw(event.getYaw() + offset[0]);
        event.setPitch(event.getPitch() + offset[1]);
        event.setRoll(event.getRoll() + offset[2]);
    }

    /** Never carry a shake across a disconnect or a dimension change into the next world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CombatFxClient.reset();
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        CombatFxClient.reset();
    }
}
