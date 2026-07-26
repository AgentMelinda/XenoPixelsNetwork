package net.bullettrain.xenopixelsmod.perf;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Soft Create multiplayer hooks (no hard Create dependency).
 * Detects contraption-like entities and enforces global budget by
 * freezing the farthest idle ones (zero velocity / no gravity),
 * without discarding builds.
 *
 * <p>Coupled sleep with VS2: when ships are Cold-parked, Create thrusters
 * on those hulls effectively do nothing — see {@link Vs2ShipSleepManager}.
 */
public final class CreatePerfHooks {
    private static Boolean createLoaded;
    private static int lastContraptionCount;
    private static int lastFrozenCount;
    private static final Set<Integer> weFroze = new HashSet<>();

    private CreatePerfHooks() {}

    public static boolean isCreateLoaded() {
        if (createLoaded == null) {
            createLoaded = ModList.get().isLoaded("create");
            if (createLoaded) {
                XenoPixelsMod.LOGGER.info("Create detected — soft perf budgets active");
            }
        }
        return createLoaded;
    }

    public static int lastContraptionCount() {
        return lastContraptionCount;
    }

    public static int lastFrozenCount() {
        return lastFrozenCount;
    }

    public static void onServerTick(MinecraftServer server) {
        if (!XenoPerfConfig.perfEnabled || !XenoPerfConfig.createPerfEnabled) return;
        if (!isCreateLoaded()) return;
        if (server.getTickCount() % 40 != 0) return; // every 2s

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<Entity> contraptions = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (isContraptionLike(e)) {
                    contraptions.add(e);
                }
            }
        }
        lastContraptionCount = contraptions.size();

        double hot = XenoPerfConfig.createHotRange;
        double hotSq = hot * hot;

        // Wake any we froze that now have a player nearby
        weFroze.removeIf(id -> {
            for (Entity e : contraptions) {
                if (e.getId() != id) continue;
                if (nearAnyPlayer(e, players, hotSq)) {
                    unfreeze(e);
                    return true;
                }
            }
            return true; // entity gone
        });

        // Count hot contraptions (near players)
        List<Entity> farIdle = new ArrayList<>();
        int hotCount = 0;
        for (Entity e : contraptions) {
            if (nearAnyPlayer(e, players, hotSq)) {
                hotCount++;
                if (weFroze.contains(e.getId())) {
                    unfreeze(e);
                    weFroze.remove(e.getId());
                }
            } else {
                farIdle.add(e);
            }
        }

        int max = XenoPerfConfig.createMaxContraptionsGlobal;
        int over = contraptions.size() - max;
        int frozen = 0;
        if (over > 0 && !farIdle.isEmpty()) {
            // Freeze farthest first
            farIdle.sort(Comparator.comparingDouble(e -> -minDistSq(e, players)));
            int toFreeze = Math.min(over, farIdle.size());
            for (int i = 0; i < toFreeze; i++) {
                Entity e = farIdle.get(i);
                if (softFreeze(e)) {
                    weFroze.add(e.getId());
                    frozen++;
                }
            }
        }
        lastFrozenCount = weFroze.size();

        if (server.getTickCount() % 200 == 0 && contraptions.size() > max) {
            XenoPixelsMod.LOGGER.debug(
                    "Create perf: contraptions={} hot~{} frozen={} cap={}",
                    contraptions.size(), hotCount, lastFrozenCount, max);
        }
    }

    /** Called when a VS ship is cold-parked (coupled sleep bookkeeping). */
    public static void onShipColdParked(long shipId) {
        if (!XenoPerfConfig.createCoupledSleep || !isCreateLoaded()) return;
        // Future: reflect into ship-local kinetic graphs. For now metrics only.
        AtomicInteger ignored = new AtomicInteger();
        ignored.incrementAndGet();
    }

    public static void onShipWoken(long shipId) {
        // reserved for kinetic wake
    }

    private static boolean isContraptionLike(Entity e) {
        if (e == null) return false;
        String n = e.getClass().getName().toLowerCase(Locale.ROOT);
        // Create: ControlledContraptionEntity, ContraptionEntity, CarriageContraptionEntity, …
        return n.contains("contraption") && n.contains("create");
    }

    private static boolean nearAnyPlayer(Entity e, List<ServerPlayer> players, double rangeSq) {
        for (ServerPlayer p : players) {
            if (p.level() != e.level()) continue;
            if (p.distanceToSqr(e) <= rangeSq) return true;
        }
        return false;
    }

    private static double minDistSq(Entity e, List<ServerPlayer> players) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            if (p.level() != e.level()) continue;
            best = Math.min(best, p.distanceToSqr(e));
        }
        return best;
    }

    /**
     * Soft freeze: stop drifting; do not discard entity (gameplay-safe).
     * Create may still tick internals, but far excess load is reduced.
     */
    private static boolean softFreeze(Entity e) {
        try {
            e.setDeltaMovement(0, 0, 0);
            e.setNoGravity(true);
            e.hasImpulse = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void unfreeze(Entity e) {
        try {
            e.setNoGravity(false);
        } catch (Throwable ignored) {
        }
    }

    public static void clear() {
        weFroze.clear();
        lastContraptionCount = 0;
        lastFrozenCount = 0;
    }
}
