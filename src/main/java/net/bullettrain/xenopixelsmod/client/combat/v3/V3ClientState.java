package net.bullettrain.xenopixelsmod.client.combat.v3;

import net.bullettrain.xenopixelsmod.combat.v3.V3Config;
import net.bullettrain.xenopixelsmod.combat.v3.V3Direction;
import net.bullettrain.xenopixelsmod.combat.v3.V3Input;
import net.bullettrain.xenopixelsmod.combat.v3.V3State;
import net.bullettrain.xenopixelsmod.combat.v3.V3TargetSnapshot;
import net.bullettrain.xenopixelsmod.combat.v3.V3TargetingRules;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.FistInputPolicy;
import net.bullettrain.xenopixelsmod.mixin.client.DmzLockOnAccessor;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3InputPacket;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import java.util.LinkedHashSet;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3ConfigPacket;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket;
import java.util.UUID;

/** Client mirror only. Integrated-server V3Config statics remain server-owned. */
public final class V3ClientState {
    private static V3Config.Values config = new V3Config.Values();
    private static CombatV3StatePacket state;
    private static long targetWatermark = -1;
    private static long outgoingSequence;
    private static final LinkedHashSet<UUID> RETIRED_SESSIONS = new LinkedHashSet<>();
    private V3ClientState() {}
    public static void apply(CombatV3ConfigPacket packet) { if (packet != null) config = packet.values(); }
    public static void apply(CombatV3StatePacket packet) {
        if (packet == null) return;
        if (state == null || !state.session().equals(packet.session())) {
            if (RETIRED_SESSIONS.contains(packet.session())) return;
            if (state != null) {
                RETIRED_SESSIONS.add(state.session());
                if (RETIRED_SESSIONS.size() > 16) RETIRED_SESSIONS.remove(RETIRED_SESSIONS.iterator().next());
            }
            // S2C uses the ordered reliable channel. Keep only recent retired sessions as a
            // bounded duplicate/lag guard; server admission remains the security boundary.
            targetWatermark = -1;
            outgoingSequence = 0;
        } else {
            if (packet.acknowledgedSequence() < state.acknowledgedSequence()) return;
            V3TargetSnapshot incoming = packet.target();
            if (incoming != null && (incoming.revision() < targetWatermark
                    || (incoming.revision() == targetWatermark && (state.target() == null
                    || !V3TargetingRules.matches(state.target().target(), incoming.target()))))) return;
        }
        if (packet.target() != null) targetWatermark = Math.max(targetWatermark, packet.target().revision());
        outgoingSequence = Math.max(outgoingSequence, (long) packet.acknowledgedSequence() + 1);
        if (state != null && state.session().equals(packet.session()) && state.target() != null && packet.target() != null
                && state.target().revision() == packet.target().revision()) {
            // State/charge updates may reuse the target revision; its motion cannot rewind.
            packet = new CombatV3StatePacket(packet.session(), packet.state(), state.target(),
                    packet.acknowledgedSequence(), packet.chargeTicks(), packet.windowTicksLeft(), packet.windowTicksTotal(),
                    packet.window());
        }
        state = packet;
    }
    public static V3Config.Values config() { return config; }
    public static CombatV3StatePacket state() { return state; }
    /** Glow inputs come from the server's approved charge state, never the local button. */
    public static boolean charging() {
        return state != null && (state.state() == V3State.CHARGING_PUNCH || state.state() == V3State.CHARGING_KICK);
    }
    public static net.bullettrain.xenopixelsmod.combat.v3.V3Window window() {
        return state == null || state.windowTicksLeft() <= 0 ? net.bullettrain.xenopixelsmod.combat.v3.V3Window.NONE : state.window();
    }
    public static V3State fighterState() { return state == null ? V3State.IDLE : state.state(); }
    public static boolean kickCharging() { return state != null && state.state() == V3State.CHARGING_KICK; }
    public static float chargeProgress() {
        return charging() ? net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules.progress(state.chargeTicks()) : 0f;
    }
    public static UUID session() { return state == null ? null : state.session(); }
    public static V3TargetSnapshot target() { return state == null ? null : state.target(); }
    public static LivingEntity trackedTarget() {
        V3TargetSnapshot target = target();
        var level = Minecraft.getInstance().level;
        if (target == null || level == null) return null;
        var entity = level.getEntity(target.entityId());
        return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                && V3TargetingRules.matches(target.target(), living.getUUID()) ? living : null;
    }
    /** Stance depends on the approved identity, including targets outside client tracking. */
    public static boolean stance() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        return target() != null && player != null && mc.level != null && mc.screen == null
                && player.isAlive() && !player.isSpectator() && XenoClientConfig.bt3CombatClient
                && XenoServerClientState.v3Controller()
                && !(player.getVehicle() instanceof net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity)
                && FistInputPolicy.emptyHands(player.getMainHandItem().isEmpty(), player.getOffhandItem().isEmpty(),
                PlayerAttackHelper.isKiWeaponActive(player));
    }
    static int nextSequence() {
        return state == null || outgoingSequence > Integer.MAX_VALUE ? -1 : (int) outgoingSequence++;
    }
    /** Shared sender for later V3 gestures: one monotonic counter per approved server session. */
    public static boolean send(V3Input input, UUID candidate, V3Direction direction) {
        if (!XenoServerClientState.v3Controller() || Minecraft.getInstance().getConnection() == null || session() == null) return false;
        int sequence = nextSequence();
        if (sequence < 0) return false;
        ModNetwork.sendToServer(new CombatV3InputPacket(input, session(), candidate, direction, sequence));
        return true;
    }
    public static void toggleLock() {
        if (com.dragonminez.client.systems.taiyoken.TaiyokenBlindState.isActive()) {
            if (target() != null) send(V3Input.LOCK_CLEAR, null, V3Direction.NONE);
            clear();
            return;
        }
        send(target() == null ? V3Input.LOCK_ACQUIRE : V3Input.LOCK_CLEAR, null, V3Direction.NONE);
    }
    /** Only real tracked entities are adopted into DMZ's portrait and marker integration. */
    public static void syncNativeLock() {
        if (com.dragonminez.client.systems.taiyoken.TaiyokenBlindState.isActive() && target() != null) {
            send(V3Input.LOCK_CLEAR, null, V3Direction.NONE);
            clear();
        }
        LivingEntity tracked = trackedTarget();
        DmzLockOnAccessor.xenopixels$setLockedTarget(tracked);
        if (tracked == null) DmzLockOnAccessor.xenopixels$setMarkerVisible(false);
    }
    /** Same-session teardown preserves both revision watermark and outgoing sequence. */
    public static void clear() {
        if (state != null) state = new CombatV3StatePacket(state.session(), V3State.IDLE, null,
                state.acknowledgedSequence(), 0, 0, 0);
    }
    public static void reset() {
        config = new V3Config.Values(); state = null; targetWatermark = -1; outgoingSequence = 0; RETIRED_SESSIONS.clear();
    }
}
