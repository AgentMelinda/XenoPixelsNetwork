package net.bullettrain.xenopixelsmod.fx.effek;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;

/**
 * Whether an effect may play: the server config switches, the per-tick punch cap, and one punch
 * effect per target per tick (a combo hit reaches both {@code CombatFx} and the damage hook).
 * Pure and single-threaded: only the server thread plays effects.
 */
public final class EffectGate {
    public enum Category { PUNCH, HAKAI, MISSILE, SPARKING, SHIP, KI }

    /** Why an effect may or may not play. */
    public enum Result { OK, OFF, CAPPED, DUPLICATE }

    private long tick = Long.MIN_VALUE;
    private int punchesThisTick;
    private final IntOpenHashSet punchedThisTick = new IntOpenHashSet();

    /** Check and, when allowed, record it. @param targetId entity id of the target, or -1 */
    public boolean allow(EffectSlot slot, long gameTick, int targetId) {
        if (check(slot, gameTick, targetId) != Result.OK) return false;
        commit(slot, gameTick, targetId);
        return true;
    }

    /** Whether it may play, without recording anything. */
    public Result check(EffectSlot slot, long gameTick, int targetId) {
        if (!XenoServerConfig.effekseerEnabled || !categoryOn(slot.category())) return Result.OFF;
        roll(gameTick);
        if (slot.category() != Category.PUNCH) return Result.OK;
        if (targetId >= 0 && punchedThisTick.contains(targetId)) return Result.DUPLICATE;
        if (punchesThisTick >= XenoServerConfig.effekseerPunchesPerTick) return Result.CAPPED;
        return Result.OK;
    }

    /** Records a played effect (punch cap and per-target slot). */
    public void commit(EffectSlot slot, long gameTick, int targetId) {
        roll(gameTick);
        if (slot.category() != Category.PUNCH) return;
        punchesThisTick++;
        if (targetId >= 0) punchedThisTick.add(targetId);
    }

    private void roll(long gameTick) {
        if (gameTick != tick) {
            tick = gameTick;
            punchesThisTick = 0;
            punchedThisTick.clear();
        }
    }

    /** How far away players are still sent this effect, in blocks. */
    public double range(EffectSlot slot) {
        // Rocket plumes are seen from far away; punches, Hakai and Sparking are close-up.
        return slot.category() == Category.MISSILE || slot.category() == Category.SHIP
                ? XenoServerConfig.effekseerMissileRange : XenoServerConfig.effekseerRange;
    }

    public void reset() {
        tick = Long.MIN_VALUE;
        punchesThisTick = 0;
        punchedThisTick.clear();
    }

    private static boolean categoryOn(Category category) {
        return switch (category) {
            case PUNCH -> XenoServerConfig.effekseerPunches;
            case HAKAI -> XenoServerConfig.effekseerHakai;
            case MISSILE -> XenoServerConfig.effekseerMissiles;
            case SPARKING -> XenoServerConfig.effekseerSparking;
            case SHIP -> XenoServerConfig.effekseerShipThrusters;
            case KI -> XenoServerConfig.effekseerKiImpacts;
        };
    }
}
