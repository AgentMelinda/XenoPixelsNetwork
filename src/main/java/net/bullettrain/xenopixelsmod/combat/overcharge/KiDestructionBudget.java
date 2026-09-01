package net.bullettrain.xenopixelsmod.combat.overcharge;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-level, per-tick block-break budget shared by charge craters and disk slices.
 *
 * <p>DMZ's own cubic scans are separately capped by {@code KiDestructionRadiusMixin}.
 * This only meters <em>our</em> extra grief so two overcharge systems cannot
 * cooperate into a stall.
 */
public final class KiDestructionBudget {

    private static final Map<String, Integer> USED = new HashMap<>();
    private static final Map<String, Long> TICK = new HashMap<>();

    private KiDestructionBudget() {
    }

    public static boolean tryConsume(ServerLevel level, int n) {
        if (level == null || n <= 0) return false;
        String key = level.dimension().location().toString();
        long now = level.getGameTime();
        Long last = TICK.get(key);
        if (last == null || last != now) {
            USED.put(key, 0);
            TICK.put(key, now);
        }
        int cap = Math.max(1, XenoServerConfig.kiDestructionBlocksPerTick);
        int used = USED.getOrDefault(key, 0);
        if (used >= cap) return false;
        if (used + n > cap) return false;
        USED.put(key, used + n);
        return true;
    }

    public static void clear() {
        USED.clear();
        TICK.clear();
    }
}
