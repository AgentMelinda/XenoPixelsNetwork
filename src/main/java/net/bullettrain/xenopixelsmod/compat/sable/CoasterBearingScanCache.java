package net.bullettrain.xenopixelsmod.compat.sable;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Spark {@code mYjF2cjFbf}: Simulated Coasters
 * {@code CoasterCartPlotScan.scanBearingCells} walks every loaded plot cell
 * (PalettedContainer.get) from rail-sound tick, cart render, and Sable collide.
 * Cache one scan per plot+block for a short TTL.
 */
public final class CoasterBearingScanCache {

    /** One second. Bearings are not placed that often. */
    private static final int TTL_TICKS = 20;

    private static final WeakHashMap<LevelPlot, Map<Block, Stamp>> CACHE = new WeakHashMap<>();

    private CoasterBearingScanCache() {
    }

    public static Object get(LevelPlot plot, Block block) {
        if (plot == null || block == null) return null;
        synchronized (CACHE) {
            Map<Block, Stamp> byBlock = CACHE.get(plot);
            if (byBlock == null) return null;
            Stamp stamp = byBlock.get(block);
            if (stamp == null) return null;
            long now = gameTime(plot);
            if (now < 0L || now - stamp.atTick >= TTL_TICKS) return null;
            return stamp.result;
        }
    }

    public static void put(LevelPlot plot, Block block, Object result) {
        if (plot == null || block == null || result == null) return;
        long now = gameTime(plot);
        if (now < 0L) return;
        synchronized (CACHE) {
            CACHE.computeIfAbsent(plot, unused -> new HashMap<>())
                    .put(block, new Stamp(result, now));
        }
    }

    private static long gameTime(LevelPlot plot) {
        try {
            SubLevel sub = plot.getSubLevel();
            if (sub == null || sub.getLevel() == null) return -1L;
            return sub.getLevel().getGameTime();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private record Stamp(Object result, long atTick) {
    }
}
