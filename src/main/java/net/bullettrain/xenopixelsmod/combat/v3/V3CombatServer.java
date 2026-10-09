package net.bullettrain.xenopixelsmod.combat.v3;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerService;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3ConfigPacket;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Internal V3 session foundation. Gameplay intents stay refused until V3 handlers exist. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class V3CombatServer {
    private V3CombatServer() {}
    public static void handleInput(ServerPlayer player, V3Input input, UUID session, UUID target,
                                   V3Direction direction, int sequence) {
        if (player == null || input == null || direction == null) return;
        // A config write through another path must invalidate its old session before admission.
        if (CombatControllerService.reconcile(player.getServer())) {
            net.bullettrain.xenopixelsmod.command.DmzHudCommands.broadcast();
        }
        if (XenoServerConfig.controllerMode() != CombatControllerMode.V3 || !XenoServerConfig.bt3CombatEnabled
                || !player.isAlive() || player.isSpectator() || player.getVehicle() instanceof XenoPilotSeatEntity) return;
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        // Targeting, heavy taps, charges and safe cancellation. Later tasks own movement.
        if (input != V3Input.LIGHT_TAP && input != V3Input.LOCK_ACQUIRE && input != V3Input.LOCK_CYCLE && input != V3Input.LOCK_CLEAR
                && input != V3Input.HEAVY_TAP && input != V3Input.LIGHT_CHARGE_START
                && input != V3Input.HEAVY_CHARGE_START && input != V3Input.CHARGE_RELEASE
                && input != V3Input.DRAGON_DASH && input != V3Input.DASH_CROSS
                && input != V3Input.CHASE_START && input != V3Input.VANISH && input != V3Input.COUNTER
                && input != V3Input.GRAB
                && input != V3Input.CHARGE_CANCEL && input != V3Input.CHASE_STOP
                && input != V3Input.TECHNIQUE_RELEASE) return;
        if (!fighter.admit(session, sequence)) return;
        int tick = player.getServer().getTickCount();
        if (fighter.state == V3State.GRABBED) {
            // A held victim may use the session-bound escape even when combat permission is denied.
            if (input == V3Input.GRAB) V3Grab.handle(player, null, direction, tick);
            syncState(player);
            return;
        }
        // Intentional alias: preserve existing admin grants/denials for rewritten combat.
        if (!XenoPermissions.hasPermission(player, XenoPermissions.COMBAT_V2_USE)) return;
        if (fighter.state == V3State.GRAB_HOLD) {
            if (input == V3Input.GRAB) V3Grab.handle(player, V3Targeting.resolve(player), direction, tick);
            syncState(player);
            return;
        }
        if (fighter.state == V3State.CINEMATIC && input != V3Input.LOCK_CLEAR) {
            // A technique timeline owns the fighter until it ends; dropping the lock is the way out.
            if (input == V3Input.TECHNIQUE_RELEASE) {
                net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.release(player);
            }
            syncState(player);
            return;
        }
        if (input == V3Input.LIGHT_TAP) {
            V3Melee.tap(player, V3Targeting.resolve(player), tick);
        } else if (input == V3Input.LOCK_ACQUIRE) {
            if (V3Targeting.acquire(player, target, player.getServer().getTickCount())) return;
        } else if (input == V3Input.LOCK_CYCLE) {
            if (V3Targeting.cycle(player, target, player.getServer().getTickCount())) return;
        } else if (input == V3Input.HEAVY_TAP) {
            // The client's UUID is ignored: a heavy only ever goes at the approved lock.
            V3Heavy.tap(player, V3Targeting.resolve(player), direction, player.getServer().getTickCount());
        } else if (input == V3Input.LIGHT_CHARGE_START || input == V3Input.HEAVY_CHARGE_START) {
            V3Charge.start(player, V3Targeting.resolve(player), input == V3Input.HEAVY_CHARGE_START,
                    player.getServer().getTickCount());
        } else if (input == V3Input.CHARGE_RELEASE) {
            // Which strike is being released is the server's memory of what was started.
            V3Charge.release(player, V3Targeting.resolve(player), fighter.chargeKick, player.getServer().getTickCount());
        } else if (input == V3Input.DRAGON_DASH) {
            V3Dash.start(player, V3Targeting.resolve(player), direction, player.getServer().getTickCount());
        } else if (input == V3Input.DASH_CROSS) {
            if (V3Dash.cross(player, direction, player.getServer().getTickCount())) return;
        } else if (input == V3Input.CHASE_START) {
            V3Chase.start(player, V3Targeting.resolve(player), tick);
        } else if (input == V3Input.CHASE_STOP) {
            V3Chase.stop(player);
        } else if (input == V3Input.VANISH || input == V3Input.COUNTER) {
            if (V3Defense.handle(player, input, direction, tick)) return;
        } else if (input == V3Input.GRAB) {
            V3Grab.handle(player, V3Targeting.resolve(player), direction, tick);
        } else if (input == V3Input.CHARGE_CANCEL) {
            // Letting go of a charge is not letting go of the lock.
            V3Charge.cancel(player);
        } else if (input == V3Input.LOCK_CLEAR) {
            V3Targeting.clear(player);
            return;
        }
        syncState(player);
    }
    // ---- narrow surface for the technique runtime, which lives in its own package ----

    /** The fighter's currently valid approved lock, or null. */
    public static net.minecraft.world.entity.LivingEntity lockedTarget(ServerPlayer player) {
        return V3Targeting.resolve(player);
    }
    public static boolean freeToAct(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        return fighter != null && fighter.state == V3State.IDLE && !V3Heavy.stunned(player);
    }
    /** Direct technique entry has the same authority requirements as a gesture packet. */
    public static boolean canStartTechnique(ServerPlayer player) {
        return owns(player) && player.isAlive() && !player.isSpectator()
                && !(player.getVehicle() instanceof XenoPilotSeatEntity)
                && XenoPermissions.hasPermission(player, XenoPermissions.COMBAT_V2_USE)
                && freeToAct(player);
    }
    public static boolean spendKi(ServerPlayer player, float ki) {
        boolean sparking = net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem.isSparking(player);
        return V3Resources.spend(player, V3Resources.kiCost(ki, sparking), 0f);
    }
    public static boolean inCinematic(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        return fighter != null && fighter.state == V3State.CINEMATIC;
    }
    public static void setCinematic(ServerPlayer player, boolean active) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        if (active) fighter.state = V3State.CINEMATIC;
        else if (fighter.state == V3State.CINEMATIC) fighter.state = V3State.IDLE;
        syncState(player);
    }
    /** A timeline contact: lands only if the target is actually within reach. */
    public static float techniqueStrike(ServerPlayer player, net.minecraft.world.entity.LivingEntity target, float scale) {
        return V3Heavy.inReach(player, target) ? V3Heavy.strike(player, target, scale) : 0f;
    }
    /** A timeline hit whose own beat already decided range (an area burst). */
    public static float techniqueHit(ServerPlayer player, net.minecraft.world.entity.LivingEntity target, float scale) {
        return V3Heavy.strike(player, target, scale);
    }
    public static void techniqueShove(ServerPlayer player, net.minecraft.world.entity.LivingEntity target, V3Direction direction) {
        var look = player.getLookAngle();
        net.bullettrain.xenopixelsmod.combat.CombatKnockback.set(target, V3Heavy.reaction(direction, look.x, look.y, look.z));
    }
    public static void techniqueHold(ServerPlayer player, net.minecraft.world.entity.LivingEntity target, int ticks) {
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, Math.min(ticks, 200), 6, false, false), player);
        net.bullettrain.xenopixelsmod.combat.CombatKnockback.set(target, net.minecraft.world.phys.Vec3.ZERO);
    }
    /** Pins a technique's victim to {@code spot}: no walking, no drifting, no knockback until released. */
    public static void techniqueFreeze(ServerPlayer player, net.minecraft.world.entity.LivingEntity target,
                                       net.minecraft.world.phys.Vec3 spot) {
        if (!net.bullettrain.xenopixelsmod.combat.CombatKnockback.canKnockBack(target)) return;
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false), player);
        net.bullettrain.xenopixelsmod.combat.CombatKnockback.set(target, net.minecraft.world.phys.Vec3.ZERO);
        target.fallDistance = 0f;
        if (net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.drifted(target.position().distanceToSqr(spot))) {
            target.teleportTo(spot.x, spot.y, spot.z);
        }
    }
    public static boolean loaded(ServerPlayer player, net.minecraft.world.phys.Vec3 position) {
        return V3ChunkWindow.update(player, position);
    }

    public static boolean owns(ServerPlayer player) {
        return player != null && XenoServerConfig.controllerMode() == CombatControllerMode.V3
                && XenoServerConfig.bt3CombatEnabled;
    }
    /** Cancels every V3 subsystem owner while preserving the current session. */
    static void cancelLiveState(ServerPlayer player) {
        if (player == null) return;
        net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.cancel(player);
        net.bullettrain.xenopixelsmod.combat.v3.ki.V3KiShots.cancelOwner(player.getUUID());
        V3Grab.abort(player);
        V3Charge.cancel(player);
        V3Chase.stop(player);
        V3Dash.abort(player);
        V3Travel.stop(player);
        V3Motion.release(player);
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter != null) fighter.cancelLiveState();
    }

    /** Whether an existing cast/session may keep applying delayed work. */
    public static boolean canContinueTechnique(ServerPlayer player, UUID session, UUID target) {
        V3Fighter fighter = V3FighterStore.peek(player);
        return owns(player) && fighter != null && fighter.session().equals(session)
                && target != null
                && net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.castTargetMatches(
                        net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.activeCastTarget(player.getUUID()),
                        fighter.approvedTarget, target)
                && player.isAlive() && !player.isSpectator()
                && !(player.getVehicle() instanceof XenoPilotSeatEntity)
                && XenoPermissions.hasPermission(player, XenoPermissions.COMBAT_V2_USE)
                && !V3Heavy.stunned(player);
    }

    public static UUID session(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        return fighter == null ? null : fighter.session();
    }

    /** Rotate identity and cancel all live owners, retaining cooldowns. */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        cancelLiveState(player);
        V3FighterStore.get(player).rotateSession();
    }
    public static void syncState(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.get(player);
        ModNetwork.sendToPlayer(player, new CombatV3StatePacket(fighter.session(), fighter.state, fighter.target,
                fighter.acknowledgedSequence(), fighter.chargeTicks, fighter.windowTicksLeft, fighter.windowTicksTotal,
                fighter.window));
    }
    public static void syncTo(ServerPlayer player) {
        ModNetwork.sendToPlayer(player, new CombatV3ConfigPacket(V3Config.get()));
        // V3 is the opt-in mode these attacks exist for: under it every catalog attack is equippable.
        if (XenoServerConfig.controllerMode() == CombatControllerMode.V3) {
            net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog.unlockAll(player);
        }
        syncState(player);
    }
    public static void resyncAll(MinecraftServer server) {
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) syncTo(player);
    }
    /** Tuning reload/change invalidates queued inputs before the next snapshot is sent. */
    public static void clearAll(MinecraftServer server) {
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) clear(player);
    }
    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        if (XenoServerConfig.controllerMode() != CombatControllerMode.V3) {
            net.bullettrain.xenopixelsmod.combat.v3.ki.V3KiShots.clear();
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                V3Fighter fighter = V3FighterStore.peek(player);
                if (fighter != null && fighter.hasLiveOwnership()) clear(player);
            }
            return;
        }
        net.bullettrain.xenopixelsmod.combat.v3.ki.V3KiShots.tick(event.getServer(), event.getServer().getTickCount());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!XenoServerConfig.bt3CombatEnabled || !player.isAlive() || player.isSpectator()
                    || player.getVehicle() instanceof XenoPilotSeatEntity
                    || !XenoPermissions.hasPermission(player, XenoPermissions.COMBAT_V2_USE)) {
                clear(player);
                continue;
            }
            V3Targeting.tick(player, event.getServer().getTickCount());
            V3Charge.tick(player, event.getServer().getTickCount());
            V3Dash.tick(player, event.getServer().getTickCount());
            V3Grab.tick(player, event.getServer().getTickCount());
            net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.tick(player, event.getServer().getTickCount());
        }
    }
    @SubscribeEvent public static void onServerStarting(ServerStartingEvent event) {
        net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.clearAll();
        V3Config.load();
    }
    @SubscribeEvent public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) clear(player);
        net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.clearAll();
        net.bullettrain.xenopixelsmod.combat.v3.ki.V3KiShots.clear();
        V3FighterStore.clearAll();
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
            net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.forget(player);
        }
        V3FighterStore.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { clear(player); syncTo(player); }
    }
    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { clear(player); syncTo(player); }
    }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { clear(player); syncTo(player); }
    }
}
