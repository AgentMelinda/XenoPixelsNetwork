package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative pacing for BT3 combat.
 *
 * <p>The client is never trusted for cooldowns, costs, or the combo step: a forged client can
 * replay {@code Bt3CombatPacket} at network speed, so every gating decision here is re-derived
 * from server state. All timestamps use the monotonic server tick (the same clock
 * {@link Bt3CombatEvents} and {@link Bt3SparkingSystem} standardize on) so windows are correct
 * for players who joined a long-running server.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3CombatLimiter {

    /**
     * Minimum interval between any two damaging/moving actions. Packet spam (many per tick) is
     * rejected; any human swing cadence (well over 100ms) is unaffected. Kept as a constant to
     * avoid touching the config schema / sync wire for what is an anti-spam floor, not a tuning
     * knob.
     */
    private static final int MIN_ACTION_INTERVAL_TICKS = 4;
    /** A combo string decays to step 0 after this many ticks without a hit. */
    static final int COMBO_DECAY_TICKS = 40;

    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, Integer> ULTIMATE_LAST = new HashMap<>();
    private static final Map<UUID, Integer> HAKAI_LAST = new HashMap<>();
    private static final Map<UUID, Integer> SWAY_LAST = new HashMap<>();
    private static final Map<UUID, Integer> RUSH_STEP = new HashMap<>();
    /**
     * Per-player string. Route and airborne are latched when the string starts so a jump
     * mid-combo cannot swap the whole choreography between one punch and the next.
     */
    private static final Map<UUID, ComboState> COMBO = new HashMap<>();

    private Bt3CombatLimiter() {}

    private static int tick(ServerPlayer p) {
        if (p == null || p.level() == null) return 0;
        MinecraftServer server = p.level().getServer();
        return server != null ? server.getTickCount() : 0;
    }

    /**
     * General pacing gate for every damaging/moving action. Returns false (reject) when the
     * player acted too recently. Advances the timestamp when allowed.
     */
    public static boolean canAct(ServerPlayer player) {
        int now = tick(player);
        int last = LAST_ACTION.getOrDefault(player.getUUID(), -MIN_ACTION_INTERVAL_TICKS);
        if (now - last < MIN_ACTION_INTERVAL_TICKS) return false;
        LAST_ACTION.put(player.getUUID(), now);
        return true;
    }

    /** Ultimate cooldown gate. The client-side cooldown is decorative; this is the real one. */
    public static boolean canUltimate(ServerPlayer player) {
        int now = tick(player);
        int last = ULTIMATE_LAST.getOrDefault(player.getUUID(), -Integer.MAX_VALUE);
        if (now - last < XenoServerConfig.ultimateCooldownTicks) return false;
        ULTIMATE_LAST.put(player.getUUID(), now);
        return true;
    }

    /** True when Hakai is off cooldown. Does not stamp the timer. */
    public static boolean hakaiReady(ServerPlayer player) {
        int now = tick(player);
        int last = HAKAI_LAST.getOrDefault(player.getUUID(), -Integer.MAX_VALUE);
        return now - last >= XenoServerConfig.hakaiCooldownTicks;
    }

    public static void markHakaiUsed(ServerPlayer player) {
        HAKAI_LAST.put(player.getUUID(), tick(player));
    }

    /** Hakai cooldown gate. The client-side cooldown is decorative; this is the real one. */
    public static boolean canHakai(ServerPlayer player) {
        if (!hakaiReady(player)) return false;
        markHakaiUsed(player);
        return true;
    }

    /** Sonic-sway cooldown gate (its 18-tick cooldown also subsumes the general action floor). */
    public static boolean canSonicSway(ServerPlayer player) {
        int now = tick(player);
        int last = SWAY_LAST.getOrDefault(player.getUUID(), -Integer.MAX_VALUE);
        if (now - last < XenoServerConfig.sonicSwayCooldownTicks) return false;
        SWAY_LAST.put(player.getUUID(), now);
        return true;
    }

    /**
     * Server-side combo step, replacing the client-supplied one. Advances the string by one per
     * accepted hit and decays it to 0 after a gap, so the finisher ({@code step % finisherEvery})
     * is earned rather than forged by sending the finisher step every swing.
     */
    public static int nextComboStep(ServerPlayer player) {
        return nextComboStep(player, null);
    }

    /**
     * Advances a string for a concrete victim. A target switch deliberately starts beat one so a
     * player cannot carry an earned finisher from one opponent onto another.
     */
    public static int nextComboStep(ServerPlayer player, LivingEntity target) {
        UUID id = player.getUUID();
        UUID targetId = target == null ? null : target.getUUID();
        ComboState previous = COMBO.get(id);
        if (targetId != null && previous != null && previous.targetId != null
                && !targetId.equals(previous.targetId)) {
            previous = null;
        }
        ComboState next = advance(previous, tick(player), !player.onGround(), targetId);
        COMBO.put(id, next);
        return next.step;
    }

    /** Clears a string after a whiff or after a completed special beat. */
    public static void resetCombo(ServerPlayer player) {
        COMBO.remove(player.getUUID());
    }

    public static int nextRushStep(ServerPlayer player) {
        UUID id = player.getUUID();
        int step = Math.floorMod(RUSH_STEP.getOrDefault(id, 0), 4) + 1;
        RUSH_STEP.put(id, step);
        return step;
    }

    public static void resetRush(ServerPlayer player) {
        RUSH_STEP.remove(player.getUUID());
    }

    /**
     * Which rush route the player's current string is using, for
     * {@link Bt3ComboChoreography#resolve}. Valid only alongside the step from
     * {@link #nextComboStep}; returns route 0 for a player with no live string.
     */
    public static int comboRoute(ServerPlayer player) {
        ComboState c = COMBO.get(player.getUUID());
        return c == null ? 0 : c.routeIndex;
    }

    /**
     * Whether the current string was airborne when it started. Latched for the whole string so a
     * jump mid-combo cannot swap {@link Bt3ComboChoreography} onto the air route (or off it).
     * False when the player has no live string.
     */
    public static boolean comboAirborne(ServerPlayer player) {
        ComboState c = COMBO.get(player.getUUID());
        return c != null && c.airborne;
    }

    /**
     * Advance one hit on {@code previous}, starting a new string when there is no prior state or
     * the decay window has elapsed. Package-visible so the latch rule can be tested without a
     * {@code ServerPlayer}.
     */
    static ComboState advance(ComboState previous, int now, boolean currentlyAirborne) {
        return advance(previous, now, currentlyAirborne, previous == null ? null : previous.targetId);
    }

    static ComboState advance(ComboState previous, int now, boolean currentlyAirborne, UUID targetId) {
        ComboState c = previous;
        if (c == null || now - c.lastHitTick > COMBO_DECAY_TICKS) {
            // A fresh string picks the next rush route, so two strings in a row do not replay the
            // same choreography. Rotating rather than randomising keeps the choice reproducible.
            int nextRoute = c == null
                    ? 0
                    : Math.floorMod(c.routeIndex + 1, Math.max(1, Bt3ComboChoreography.routeCount()));
            c = new ComboState(0, now, nextRoute, currentlyAirborne, targetId);
        }
        c.lastHitTick = now;
        c.targetId = targetId;
        c.step++;
        return c;
    }

    /**
     * One live rush string. {@code routeIndex} and {@code airborne} are fixed at start; {@code step}
     * and {@code lastHitTick} move with each accepted hit.
     */
    static final class ComboState {
        int step;
        int lastHitTick;
        final int routeIndex;
        final boolean airborne;
        UUID targetId;

        ComboState(int step, int lastHitTick, int routeIndex, boolean airborne, UUID targetId) {
            this.step = step;
            this.lastHitTick = lastHitTick;
            this.routeIndex = routeIndex;
            this.airborne = airborne;
            this.targetId = targetId;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            UUID id = p.getUUID();
            LAST_ACTION.remove(id);
            ULTIMATE_LAST.remove(id);
            HAKAI_LAST.remove(id);
            SWAY_LAST.remove(id);
            RUSH_STEP.remove(id);
            COMBO.remove(id);
        }
    }
}
