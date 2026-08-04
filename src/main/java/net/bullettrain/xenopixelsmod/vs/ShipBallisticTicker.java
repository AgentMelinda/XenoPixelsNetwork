package net.bullettrain.xenopixelsmod.vs;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.bullettrain.xenopixelsmod.missile.MissilePhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.QueryableShipData;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Game-thread side of ship-as-missile.
 * Only iterates ships currently in ballistic flight.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ShipBallisticTicker {
    private ShipBallisticTicker() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ShipBallisticController.anyActiveFlights()) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (Long shipId : ShipBallisticController.activeFlightShipIds()) {
            if (shipId == null) continue;
            tickActiveShip(server, shipId);
        }
    }

    private static void tickActiveShip(MinecraftServer server, long shipId) {
        // Prefer dimension cached on controller (O(1)); fallback multi-dim only if missing
        LoadedServerShip loaded = null;
        ServerLevel level = null;

        // Normal path: launch registered the dimension, so resolve one world directly.
        ResourceKey<Level> cachedDim = ShipBallisticController.activeFlightDimension(shipId);
        if (cachedDim != null) {
            ServerLevel cachedLevel = server.getLevel(cachedDim);
            if (cachedLevel != null) {
                LoadedServerShip cachedShip = resolveLoaded(cachedLevel, shipId);
                if (cachedShip != null) {
                    loaded = cachedShip;
                    level = cachedLevel;
                }
            }
        }

        // Compatibility fallback for flights created before the dimension cache existed.
        if (loaded == null) {
        for (ServerLevel candidate : server.getAllLevels()) {
            // Fast path: if we already found nothing, still must scan once per dim without dim cache
            // First try: get controller from ship if loaded in this dim
            LoadedServerShip ls = resolveLoaded(candidate, shipId);
            if (ls == null) continue;
            ShipBallisticController peek = ShipBallisticController.get(ls);
            if (peek == null || !peek.isFlying()) {
                ShipBallisticController.unregisterFlight(shipId);
                return;
            }
            ResourceKey<Level> dim = peek.getFlightDim();
            if (dim != null && !candidate.dimension().equals(dim)) {
                // Wrong dim — keep looking for the bound world
                ServerLevel bound = server.getLevel(dim);
                if (bound != null) {
                    LoadedServerShip inBound = resolveLoaded(bound, shipId);
                    if (inBound != null) {
                        loaded = inBound;
                        level = bound;
                        break;
                    }
                }
                // Fall through: use whatever dim has the ship
            }
            loaded = ls;
            level = candidate;
            if (dim == null) {
                peek.setFlightDim(candidate.dimension());
            }
            break;
        }
        }

        if (loaded == null || level == null) {
            ShipBallisticController.unregisterFlight(shipId);
            return;
        }

        ShipBallisticController ctrl = ShipBallisticController.get(loaded);
        if (ctrl == null || !ctrl.isFlying()) {
            ShipBallisticController.unregisterFlight(shipId);
            return;
        }

        ctrl.gameTick(loaded);

        Vector3dc pos = loaded.getTransform().getPositionInWorld();
        double px = pos.x(), py = pos.y(), pz = pos.z();
        long time = level.getGameTime();

        // Chunk tickets: target preferred (vehicle skipped when forceChunksTargetOnly)
        if (time % 40 == 0) {
            MissileChunkLoadManager.forceNear(level, BlockPos.containing(px, py, pz), 1, 20 * 6,
                    MissileChunkLoadManager.Role.VEHICLE);
            MissileChunkLoadManager.forceNear(level,
                    BlockPos.containing(ctrl.getTargetX(), ctrl.getTargetY(), ctrl.getTargetZ()),
                    1, 20 * 10, MissileChunkLoadManager.Role.TARGET);
        }

        // Sparse COM plume every 10t (was 5) — fewer network packets
        MissilePhase phase = ctrl.getPhase();
        if ((phase == MissilePhase.BOOST || phase == MissilePhase.EJECT
                || phase == MissilePhase.TERMINAL) && time % 10 == 0) {
            level.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.35, 0.35, 0.35, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, px, py, pz, 1, 0.25, 0.25, 0.25, 0.005);
        }

        if (ctrl.consumeImpact()) {
            softArrive(level, loaded, ctrl, px, py, pz);
        }
    }

    private static LoadedServerShip resolveLoaded(ServerLevel level, long shipId) {
        try {
            ServerShipWorld world = VSGameUtilsKt.getShipObjectWorld(level);
            if (world == null) return null;
            QueryableShipData<LoadedServerShip> loaded = world.getLoadedShips();
            if (loaded == null) return null;
            Ship s = loaded.getById(shipId);
            return s instanceof LoadedServerShip ls ? ls : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void softArrive(ServerLevel level, LoadedServerShip ship,
                                   ShipBallisticController ctrl,
                                   double x, double y, double z) {
        XenoPixelsMod.LOGGER.info("Ship ballistic ARRIVED ship={} at ({}, {}, {}) — no explosion",
                ship.getId(), (int) x, (int) y, (int) z);

        // abort() clears flying + ACTIVE_FLIGHTS; thrusters released below
        // (guidance BE clears commandingFlight on next sync when !isFlying)
        if (ctrl.isFlying()) {
            ctrl.abort();
        }
        shutdownShipThrusters(level, ship);

        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 8, 0.6, 0.25, 0.6, 0.02);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 1.0f, 0.9f);

        try {
            ship.setStatic(true);
        } catch (Throwable ignored) {
        }
    }

    private static void shutdownShipThrusters(ServerLevel level, LoadedServerShip ship) {
        try {
            XenoThrusterControl control = ship.getAttachment(XenoThrusterControl.class);
            if (control != null) {
                control.clearAll();
            }

            var chunks = ship.getActiveChunksSet();
            if (chunks == null) return;
            final int[] remaining = {48};
            chunks.forEach((cx, cz) -> {
                if (remaining[0] <= 0) return;
                try {
                    if (!level.hasChunk(cx, cz)) return;
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (remaining[0] <= 0) break;
                        if (be instanceof ShipThrusterBlockEntity thruster && thruster.isGuidanceOwned()) {
                            thruster.forceShutdown();
                            remaining[0]--;
                        }
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.debug("shutdownShipThrusters: {}", t.toString());
        }
    }
}
