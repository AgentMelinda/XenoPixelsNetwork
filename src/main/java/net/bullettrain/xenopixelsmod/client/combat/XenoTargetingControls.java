package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.flight.XenoSeatKeyContext;
import net.bullettrain.xenopixelsmod.combat.targeting.LockOnQuality;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.TargetLockPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Seated pilot targeting input: attempt/clear a lock, cycle to the next target, toggle the lead
 * marker. Every key here only ever produces a discrete {@link TargetLockPacket} on press — there
 * is no continuous stream, unlike flight input, so no client-side rate limiting is needed beyond
 * "a key can only be pressed so many times a second" (the server still rate-limits regardless).
 *
 * <p>Which entity id a lock request names comes from {@link #candidateUnderCrosshair()}, a
 * client-side nearest-in-cone search used purely for picking a target to <i>ask</i> for and for
 * the crosshair's hover-state color — it decides nothing. The server re-derives eligibility from
 * scratch in {@link net.bullettrain.xenopixelsmod.combat.targeting.LockOnValidator} and can
 * refuse a request for an id the client thought looked reasonable.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoTargetingControls {

    private static final String CATEGORY = "key.categories.xenopixelsmod";
    /** Client-side cone used only to pick a candidate to ask about; wider than the server's hard cone. */
    private static final double CANDIDATE_CONE_DEG = 8.0;
    private static final double CANDIDATE_RANGE = 400.0;

    public static final KeyMapping LOCK_TOGGLE = new KeyMapping("key.xenopixelsmod.target_lock",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORY);
    public static final KeyMapping CLEAR_LOCK = new KeyMapping("key.xenopixelsmod.target_clear",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    /**
     * Deliberately not T. T is vanilla's chat key, and {@code Minecraft.handleKeybinds()} opens
     * the chat screen before this class's tick handler runs — which then bails because a screen
     * is open, so the cycle never fired at all. Chat is the one vanilla action a cockpit must
     * not swallow, so the binding moved instead.
     */
    public static final KeyMapping CYCLE_TARGET = new KeyMapping("key.xenopixelsmod.target_cycle",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);
    public static final KeyMapping TOGGLE_LEAD = new KeyMapping("key.xenopixelsmod.target_toggle_lead",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    private static boolean leadMarkerVisible = true;
    /** Refreshed once per client tick; the HUD reads this rather than scanning every render frame. */
    private static @Nullable Entity cachedCandidate;
    /** Last lock quality a cue was played for; null means "no lock". */
    private static @Nullable LockOnQuality lastAudioQuality;

    private XenoTargetingControls() {
    }

    /** Nearest player near the crosshair, from the last client tick. For the HUD's hover state. */
    public static @Nullable Entity cachedCandidate() {
        return cachedCandidate;
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(LOCK_TOGGLE);
            event.register(CLEAR_LOCK);
            event.register(CYCLE_TARGET);
            event.register(TOGGLE_LEAD);
        }
    }

    public static boolean leadMarkerVisible() {
        return leadMarkerVisible && XenoClientConfig.showLeadMarker;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // Vanilla pick-block on the same button is swallowed while seated by
        // XenoFlightControls.onClientTickPre. KeyMapping.consumeClick() is not filtered by the
        // conflict context, so queued presses still have to be drained when not seated —
        // otherwise a click made on foot is banked and replayed the moment the player sits down.
        if (player == null || !(player.getVehicle() instanceof XenoPilotSeatEntity) || mc.screen != null) {
            drain();
            cachedCandidate = null;
            lastAudioQuality = null;
            return;
        }

        cachedCandidate = candidateUnderCrosshair();
        updateLockAudio(mc);

        while (LOCK_TOGGLE.consumeClick()) {
            if (ClientLockState.hasLock()) {
                ModNetwork.sendToServer(TargetLockPacket.clear());
            } else {
                Entity candidate = cachedCandidate;
                if (candidate != null) {
                    ModNetwork.sendToServer(TargetLockPacket.request(candidate.getId()));
                } else {
                    actionBar(mc, "No target");
                }
            }
        }
        while (CLEAR_LOCK.consumeClick()) {
            ModNetwork.sendToServer(TargetLockPacket.clear());
        }
        while (CYCLE_TARGET.consumeClick()) {
            ModNetwork.sendToServer(TargetLockPacket.cycle());
        }
        while (TOGGLE_LEAD.consumeClick()) {
            leadMarkerVisible = !leadMarkerVisible;
            actionBar(mc, leadMarkerVisible ? "Lead marker on" : "Lead marker off");
        }
    }

    /**
     * Nearest other player within a generous cone of the camera's look direction — a client-side
     * pick used only to name a candidate for a lock request. Bounded by the online player list,
     * exactly like the server's own {@code TargetLockManager.findNearestEligible}, since that
     * list is already small; no per-frame entity-world scan.
     */
    private static @Nullable Entity candidateUnderCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Entity best = null;
        double bestAngle = CANDIDATE_CONE_DEG;

        for (Player candidate : mc.level.players()) {
            if (candidate == player) continue;
            if (candidate.distanceToSqr(player) > CANDIDATE_RANGE * CANDIDATE_RANGE) continue;
            Vec3 toTarget = candidate.position().subtract(eye);
            if (toTarget.lengthSqr() < 1.0e-6) continue;
            double cos = look.dot(toTarget.normalize());
            double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
            if (angleDeg < bestAngle) {
                bestAngle = angleDeg;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Audible lock feedback, driven off the authoritative state rather than off the key press.
     *
     * <p>Deliberately keyed to what the server actually granted: a cue on the press would fire
     * for requests that are then refused, which is exactly backwards for a sound whose entire job
     * is to tell the pilot they may shoot. Transitions are read once per client tick rather than
     * in the packet handler so a burst of updates in one tick cannot stack the same cue.
     */
    private static void updateLockAudio(Minecraft mc) {
        if (mc.player == null) return;
        boolean locked = ClientLockState.hasLock();
        LockOnQuality quality = locked ? ClientLockState.quality() : null;
        if (quality != lastAudioQuality) {
            if (quality == null && lastAudioQuality != null) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.35f, 0.7f);
            } else if (quality == LockOnQuality.HARD) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.8f);
            } else if (lastAudioQuality == null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.4f);
            }
            lastAudioQuality = quality;
        }
    }

    private static void drain() {
        while (LOCK_TOGGLE.consumeClick()) { }
        while (CLEAR_LOCK.consumeClick()) { }
        while (CYCLE_TARGET.consumeClick()) { }
        while (TOGGLE_LEAD.consumeClick()) { }
    }

    private static void actionBar(Minecraft mc, String message) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("§b" + message), true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLockState.clear();
        leadMarkerVisible = true;
        cachedCandidate = null;
        lastAudioQuality = null;
    }
}
