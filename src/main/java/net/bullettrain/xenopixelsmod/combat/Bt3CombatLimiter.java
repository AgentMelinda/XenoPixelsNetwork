package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
    private static final int COMBO_DECAY_TICKS = 40;

    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, Integer> ULTIMATE_LAST = new HashMap<>();
    private static final Map<UUID, Integer> HAKAI_LAST = new HashMap<>();
    private static final Map<UUID, Integer> SWAY_LAST = new HashMap<>();
    /** Combo step + the tick of the last advancing hit: {@code [step, lastHitTick]}. */
    private static final Map<UUID, int[]> COMBO = new HashMap<>();

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

    /** Hakai cooldown gate. The client-side cooldown is decorative; this is the real one. */
    public static boolean canHakai(ServerPlayer player) {
        int now = tick(player);
        int last = HAKAI_LAST.getOrDefault(player.getUUID(), -Integer.MAX_VALUE);
        if (now - last < XenoServerConfig.hakaiCooldownTicks) return false;
        HAKAI_LAST.put(player.getUUID(), now);
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
        int now = tick(player);
        int[] c = COMBO.get(player.getUUID());
        if (c == null || now - c[1] > COMBO_DECAY_TICKS) {
            c = new int[]{0, now};
        }
        c[1] = now;
        c[0]++;
        COMBO.put(player.getUUID(), c);
        return c[0];
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            UUID id = p.getUUID();
            LAST_ACTION.remove(id);
            ULTIMATE_LAST.remove(id);
            HAKAI_LAST.remove(id);
            SWAY_LAST.remove(id);
            COMBO.remove(id);
        }
    }
}
