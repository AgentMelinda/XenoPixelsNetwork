package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.WeakHashMap;

/** Resolves a requested SDB mode without allowing a config reload to strand existing planetary bodies. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DragonBallModeGuard {
    private static final long CENSUS_INTERVAL_TICKS = 40;
    private static final Map<MinecraftServer, Cached> CACHE = new WeakHashMap<>();

    private record Cached(String requested, DragonBallModePolicy.Decision decision,
                          long nextCensusTick, String lastReportedRefusal) {}

    private DragonBallModeGuard() {}

    public static DragonBallModePolicy.Decision decision(MinecraftServer server) {
        String requested = XenoSpaceConfig.cosmicDragonBallMode();
        if (server == null)
            return DragonBallModePolicy.resolve(requested, false, false, DragonBallModePolicy.BodyCensus.UNAVAILABLE);
        long tick = server.getTickCount();
        synchronized (CACHE) {
            Cached cached = CACHE.get(server);
            if (cached != null && cached.requested().equals(requested) && tick < cached.nextCensusTick())
                return cached.decision();

            DragonBallModePolicy.Decision decision;
            if (DragonBallModePolicy.parse(requested) == DragonBallModePolicy.Mode.PLANETARY) {
                decision = DragonBallModePolicy.resolve(requested, true, false, DragonBallModePolicy.BodyCensus.EMPTY);
            } else {
                DragonBallSavedData saved = DragonBallSavedData.get(server);
                boolean valid = saved.isGameplayStateValid();
                boolean savedBinding = valid && saved.hasAnyPlanetBodyBinding();
                DragonBallModePolicy.BodyCensus census = DragonBallModePolicy.BodyCensus.UNAVAILABLE;
                if (valid && !savedBinding && DragonBallBodyManager.hasInFlightBuild()) {
                    census = DragonBallModePolicy.BodyCensus.PRESENT;
                } else if (valid && !savedBinding) {
                    ServerLevel host = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
                    var slots = DragonBallBodyScan.tagged(host, CosmicDragonBalls.COUNT);
                    if (slots != null) {
                        boolean found = slots.values().stream().anyMatch(ids -> ids == null || !ids.isEmpty());
                        census = found ? DragonBallModePolicy.BodyCensus.PRESENT : DragonBallModePolicy.BodyCensus.EMPTY;
                    }
                }
                decision = DragonBallModePolicy.resolve(requested, valid, savedBinding, census);
            }

            String priorRefusal = cached == null ? "" : cached.lastReportedRefusal();
            String currentRefusal = decision.refused() ? decision.refusalReason() : "";
            if (!currentRefusal.isEmpty() && !currentRefusal.equals(priorRefusal))
                XenoPixelsMod.LOGGER.warn("SDB mode change refused: requested {}, effective planetary: {}",
                        requested, currentRefusal);
            long nextCensus = tick > Long.MAX_VALUE - CENSUS_INTERVAL_TICKS
                    ? Long.MAX_VALUE : tick + CENSUS_INTERVAL_TICKS;
            CACHE.put(server, new Cached(requested, decision, nextCensus, currentRefusal));
            return decision;
        }
    }

    public static boolean planetaryEnabled(MinecraftServer server) {
        return decision(server).effective() == DragonBallModePolicy.Mode.PLANETARY;
    }

    public static boolean legacyEnabled(MinecraftServer server) {
        return decision(server).effective() == DragonBallModePolicy.Mode.LEGACY;
    }

    public static String status(MinecraftServer server) {
        var decision = decision(server);
        return "mode requested=" + decision.requested().key() + " effective=" + decision.effective().key()
                + (decision.refused() ? " switch-refused=" + decision.refusalReason() : "");
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        synchronized (CACHE) { CACHE.remove(event.getServer()); }
    }
}
