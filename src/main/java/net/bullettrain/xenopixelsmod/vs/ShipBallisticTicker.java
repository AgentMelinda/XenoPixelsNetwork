package net.bullettrain.xenopixelsmod.vs;

import net.neoforged.fml.common.EventBusSubscriber;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.missile.MissileChunkLoadManager;
import net.bullettrain.xenopixelsmod.missile.MissilePhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.joml.Vector3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Game-thread side of ship-as-missile.
 * Only iterates ships currently in ballistic flight.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ShipBallisticTicker {
    private ShipBallisticTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ShipBallisticController.anyActiveFlights()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Long shipId : ShipBallisticController.activeFlightShipIds()) {
            if (shipId == null) continue;
            tickActiveShip(server, shipId);
        }
    }

    private static void tickActiveShip(MinecraftServer server, long shipId) {
        // Prefer dimension cached on controller (O(1)); fallback multi-dim only if missing
        ServerSubLevel loaded = null;
        ServerLevel level = null;

        // Normal path: launch registered the dimension, so resolve one world directly.
        ResourceKey<Level> cachedDim = ShipBallisticController.activeFlightDimension(shipId);
        if (cachedDim != null) {
            ServerLevel cachedLevel = server.getLevel(cachedDim);
            if (cachedLevel != null) {
                ServerSubLevel cachedShip = resolveLoaded(cachedLevel, shipId);
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
            ServerSubLevel ls = resolveLoaded(candidate, shipId);
            if (ls == null) continue;
            ShipBallisticController peek = ShipBallisticController.get(ls);
            if (peek == null || !peek.isFlying()) {
                ShipBallisticController.forgetFlight(shipId);
                return;
            }
            ResourceKey<Level> dim = peek.getFlightDim();
            if (dim != null && !candidate.dimension().equals(dim)) {
                // Wrong dim — keep looking for the bound world
                ServerLevel bound = server.getLevel(dim);
                if (bound != null) {
                    ServerSubLevel inBound = resolveLoaded(bound, shipId);
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
            ShipBallisticController.forgetFlight(shipId);
            return;
        }

        ShipBallisticController ctrl = ShipBallisticController.get(loaded);
        if (ctrl == null || !ctrl.isFlying()) {
            ShipBallisticController.forgetFlight(shipId);
            return;
        }

        ctrl.gameTick(loaded);

        Vector3dc pos = VsShipHelper.worldPosition(loaded);
        double px = pos.x(), py = pos.y(), pz = pos.z();
        long time = level.getGameTime();

        Vec3 shipPos = new Vec3(px, py, pz);
        BlockPos shipTarget = BlockPos.containing(ctrl.getTargetX(), ctrl.getTargetY(), ctrl.getTargetZ());
        Vec3 toward = new Vec3(ctrl.getTargetX() - px, 0.0, ctrl.getTargetZ() - pz);
        MissileChunkLoadManager.trackLiveMissile(level, shipPos, toward, shipTarget);

        // Sparse COM plume every 10t (was 5) — fewer network packets
        MissilePhase phase = ctrl.getPhase();
        if ((phase == MissilePhase.BOOST || phase == MissilePhase.EJECT
                || phase == MissilePhase.TERMINAL)
                && net.bullettrain.xenopixelsmod.fx.effek.MissileEffectRules.thrusterPulseDue(time)) {
            // Effekseer exhaust at the tail, opposite the flight direction; vanilla puffs only
            // when it does not play.
            Vector3dc v = VsShipHelper.velocity(level, loaded);
            Vec3 vel = v == null ? Vec3.ZERO : new Vec3(v.x(), v.y(), v.z());
            Vec3 nozzle = net.bullettrain.xenopixelsmod.fx.effek.MissileEffectRules.nozzle(
                    new Vec3(px, py, pz), vel, 2.5);
            boolean effek = net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.play(level,
                    // A ship is not an entity, so its plume is positional: ship_thruster is
                    // authored along +Z for exactly that (missile_thruster is the bound plume).
                    net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.SHIP_THRUSTER, nozzle,
                    vel.lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : vel.reverse(), 1.5f, -1);
            if (!effek && time % 10 == 0) {
                level.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.35, 0.35, 0.35, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, px, py, pz, 1, 0.25, 0.25, 0.25, 0.005);
            }
        }

        if (ctrl.consumeImpact()) {
            softArrive(level, loaded, ctrl, px, py, pz);
        }
    }

    private static ServerSubLevel resolveLoaded(ServerLevel level, long shipId) {
        return VsShipHelper.getLoadedShipById(level, shipId);
    }

    private static void softArrive(ServerLevel level, ServerSubLevel ship,
                                   ShipBallisticController ctrl,
                                   double x, double y, double z) {
        XenoPixelsMod.LOGGER.info("Ship ballistic ARRIVED ship={} at ({}, {}, {}) — no explosion",
                VsShipHelper.getShipId(ship), (int) x, (int) y, (int) z);

        // abort() clears flying + ACTIVE_FLIGHTS; thrusters released below
        // (guidance BE clears commandingFlight on next sync when !isFlying)
        if (ctrl.isFlying()) {
            ctrl.abort();
        }
        shutdownShipThrusters(level, ship);

        if (!net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.play(level,
                net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.MISSILE_EXPLOSION, new Vec3(x, y, z),
                null, 1.0f, -1)) {  // size: effekseerExplosionScale (default 15)
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 8, 0.6, 0.25, 0.6, 0.02);
        }
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 1.0f, 0.9f);

    }

    private static void shutdownShipThrusters(ServerLevel level, ServerSubLevel ship) {
        try {
            XenoThrusterControl control = XenoThrusterControl.get(ship);
            if (control != null) {
                control.clearAll();
            }
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.debug("shutdownShipThrusters: {}", t.toString());
        }
    }
}
