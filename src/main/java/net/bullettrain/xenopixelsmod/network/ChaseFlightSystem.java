package net.bullettrain.xenopixelsmod.network;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.compat.util.LazyOptional;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoAuraCommands;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.packet.ChaseFlightStatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BT3 chase: fly at the landing point with velocity only. No {@code connection.teleport}.
 * Lock-on already tracks the camera; this only moves the body.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ChaseFlightSystem {

    /** Close enough to the landing point to snap in and finish. */
    private static final double ARRIVAL_DIST = 0.75;
    /** Long enough to reach the ground from a realistic chase height without dying to it. */
    private static final int CHASE_LANDING_GRACE_TICKS = 200;
    /**
     * Hard ceiling regardless of distance/config. {@code chaseFlightTimeoutTicks} and the
     * dist/speed extension can otherwise grow unbounded for a very distant target on an
     * unlimited-range server, leaving a player stuck flying for minutes with no way out other
     * than a relog. This is a safety net, not the primary way to end a chase -- most chases
     * finish long before this via arrival, lost target, or the manual cancel below.
     */
    private static final int ABSOLUTE_MAX_TIMEOUT_TICKS = 600;
    private static final double OBSTACLE_CLEARANCE = 2.0;
    private static final double MAX_OBSTACLE_CLIMB = 16.0;
    private static final Map<UUID, ChaseState> ACTIVE = new ConcurrentHashMap<>();

    private ChaseFlightSystem() {
    }

    private static final float LOOK_EASE = 0.12f;

    /** DMZ flight mode 0 is Search Fly, 1 is Combat Fly ({@code FlySkillEvent}: {@code flightMode == 1}). */
    private static final int SEARCH_FLIGHT_MODE = 0;
    /** Sentinel for "this player has no DMZ fly skill, so nothing was changed and nothing is restored." */
    private static final int FLIGHT_MODE_UNTOUCHED = -1;

    /** What DMZ flight looked like before a chase turned Search Fly on. */
    private record FlySnapshot(boolean wasActive, int modeBefore, boolean flyingBefore, boolean mayflyBefore) {
        static final FlySnapshot UNTOUCHED = new FlySnapshot(false, FLIGHT_MODE_UNTOUCHED, false, false);
    }

    private static final class ChaseState {
        private enum RoutePhase { DIRECT, ASCEND, DIVE }

        int targetId;
        net.minecraft.world.level.Level level;
        int elapsedTicks;
        final boolean turnedAuraOn;
        final boolean noGravityBefore;
        /** DMZ fly-skill active state before the chase, restored when it ends. */
        final boolean flyActiveBefore;
        /** DMZ flight mode before the chase, or {@link #FLIGHT_MODE_UNTOUCHED} if fly was left alone. */
        final int flightModeBefore;
        final boolean flyingBefore;
        final boolean mayflyBefore;
        float yaw;
        float pitch;
        RoutePhase routePhase = RoutePhase.DIRECT;
        Vec3 elevatedWaypoint;

        ChaseState(int targetId, boolean turnedAuraOn, boolean noGravityBefore,
                   boolean flyActiveBefore, int flightModeBefore,
                   boolean flyingBefore, boolean mayflyBefore, float yaw, float pitch) {
            this.targetId = targetId;
            this.turnedAuraOn = turnedAuraOn;
            this.noGravityBefore = noGravityBefore;
            this.flyActiveBefore = flyActiveBefore;
            this.flightModeBefore = flightModeBefore;
            this.flyingBefore = flyingBefore;
            this.mayflyBefore = mayflyBefore;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public static void startAutomatic(ServerPlayer player, LivingEntity target) {
        start(player, target);
    }

    /** Starts or retargets pursuit without treating the same target as a cancel input. */
    public static void start(ServerPlayer player, LivingEntity target) {
        if (player == null || target == null || !target.isAlive()) return;
        ChaseState existing = ACTIVE.get(player.getUUID());
        if (existing != null) {
            existing.targetId = target.getId();
            existing.elapsedTicks = 0;
            existing.routePhase = ChaseState.RoutePhase.DIRECT;
            existing.elevatedWaypoint = null;
            syncState(player);
            return;
        }
        boolean wasOn = XenoAuraCommands.isOn(player);
        if (!wasOn) {
            XenoAuraCommands.apply(player, true);
        }
        boolean noGravity = player.isNoGravity();
        player.setNoGravity(true);
        FlySnapshot fly = beginSearchFly(player);
        ACTIVE.put(player.getUUID(), new ChaseState(
                target.getId(), !wasOn, noGravity,
                fly.wasActive(), fly.modeBefore(),
                fly.flyingBefore(), fly.mayflyBefore(),
                player.getYRot(), player.getXRot()));
        ACTIVE.get(player.getUUID()).level = player.level();
        syncState(player);
    }

    /**
     * Turns DMZ's Search Fly on for the chase.
     *
     * <p>Does nothing at all when the player has not learned Fly — a chase must not hand out an
     * unlearned skill — and skips DMZ's own per-toggle energy toll, because the chase action has
     * already charged ki for the move.
     *
     * @return the state to restore when the chase ends, or {@link FlySnapshot#UNTOUCHED} when
     *         nothing was changed.
     */
    private static FlySnapshot beginSearchFly(ServerPlayer player) {
        StatsData data = statsOf(player);
        if (data == null) return FlySnapshot.UNTOUCHED;
        Skill fly = data.getSkills() == null ? null : data.getSkills().getSkill("fly");
        if (fly == null || fly.getLevel() <= 0) return FlySnapshot.UNTOUCHED;

        boolean wasActive = fly.isActive();
        // Clamped so a real mode can never collide with the FLIGHT_MODE_UNTOUCHED sentinel.
        int modeBefore = Math.max(0, data.getStatus().getFlightMode());
        boolean flyingBefore = player.getAbilities().flying;
        boolean mayflyBefore = player.getAbilities().mayfly;
        if (!wasActive) {
            fly.setActive(true);
            data.getStatus().setFlightMode(SEARCH_FLIGHT_MODE);
            syncStats(player);
        }
        if (!wasActive && (!player.getAbilities().mayfly || !player.getAbilities().flying)) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        return new FlySnapshot(wasActive, modeBefore, flyingBefore, mayflyBefore);
    }

    /** Releases our unchanged temporary grant; native disables and mode changes take precedence. */
    private static void restoreFly(ServerPlayer player, ChaseState state) {
        if (state.flightModeBefore == FLIGHT_MODE_UNTOUCHED) return;
        StatsData data = statsOf(player);
        if (data == null) return;
        Skill fly = data.getSkills() == null ? null : data.getSkills().getSkill("fly");
        if (fly == null) return;

        // Active Search/Combat Fly was never changed. A manual mode switch or depletion
        // invalidates our grant; never resurrect an intervening native flight disable.
        if (!ChaseFlightOwnership.mayRestoreGrant(state.flyActiveBefore, fly.isActive(),
                data.getStatus().getFlightMode())) return;
        data.getStatus().setFlightMode(state.flightModeBefore);
        fly.setActive(false);
        player.resetFallDistance();
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = state.mayflyBefore;
            player.getAbilities().flying = state.flyingBefore;
            player.onUpdateAbilities();
        }
        syncStats(player);
    }

    public static boolean isChasing(ServerPlayer player, LivingEntity target) {
        ChaseState state = ACTIVE.get(player.getUUID());
        return state != null && state.level == player.level() && state.targetId == target.getId();
    }

    public static void syncState(ServerPlayer player) {
        ModNetwork.sendToPlayer(player, new ChaseFlightStatePacket(ACTIVE.containsKey(player.getUUID())));
    }

    private static StatsData statsOf(ServerPlayer player) {
        try {
            LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            return opt.orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void syncStats(ServerPlayer player) {
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChaseState state = ACTIVE.get(player.getUUID());
        if (state == null) return;

        StatsData data = statsOf(player);
        Skill fly = data == null || data.getSkills() == null ? null : data.getSkills().getSkill("fly");
        if (ChaseFlightOwnership.mustStop(state.flightModeBefore != FLIGHT_MODE_UNTOUCHED,
                fly != null && fly.isActive(), player.isAlive() && !player.isSpectator(), state.level == player.level(),
                player.getVehicle() instanceof net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity,
                XenoServerConfig.bt3CombatEnabled && XenoServerConfig.bt3ChaseDashEnabled)) {
            stop(player);
            return;
        }

        Entity raw = player.level().getEntity(state.targetId);
        if (!(raw instanceof LivingEntity target) || !target.isAlive()) {
            cancel(player, "§7Chase lost the target");
            return;
        }
        if (!XenoServerConfig.chaseRangeUnlimited()
                && player.distanceTo(target) > XenoServerConfig.chaseMaxRange * 1.5) {
            cancel(player, "§7Chase out of range");
            return;
        }

        Vec3 landing = Bt3CombatPacket.chaseLanding(player, target);
        Vec3 pos = player.position();
        if (state.routePhase == ChaseState.RoutePhase.DIRECT
                && directPathBlocked(player, target, landing)) {
            state.elevatedWaypoint = elevatedWaypoint(player, target, landing);
            if (state.elevatedWaypoint != null) {
                state.routePhase = ChaseState.RoutePhase.ASCEND;
            }
        }

        Vec3 destination = state.routePhase == ChaseState.RoutePhase.ASCEND
                ? state.elevatedWaypoint : landing;
        if (state.routePhase == ChaseState.RoutePhase.ASCEND
                && destination != null
                && pos.distanceTo(destination) <= ARRIVAL_DIST) {
            state.routePhase = ChaseState.RoutePhase.DIVE;
            destination = landing;
        }
        if (state.routePhase == ChaseState.RoutePhase.DIVE
                && !directPathBlocked(player, target, landing)) {
            destination = landing;
        }
        if (state.routePhase == ChaseState.RoutePhase.DIVE
                && directPathBlocked(player, target, landing)) {
            state.routePhase = ChaseState.RoutePhase.DIRECT;
            state.elevatedWaypoint = null;
            destination = landing;
        }

        double dist = pos.distanceTo(destination);
        Vec3 toLanding = destination.subtract(pos);
        float wantYaw = yawOf(toLanding);
        float wantPitch = pitchOf(toLanding);
        double arriveAt = Math.max(ARRIVAL_DIST, XenoServerConfig.chaseStopGap + 0.35);
        if (player.distanceTo(target) <= arriveAt || dist < ARRIVAL_DIST) {
            lookToward(player, state, wantYaw, wantPitch, 0.45f);
            stopMotion(player);
            Bt3CombatPacket.playItSound(player, player.getX(), player.getY(), player.getZ(), false);
            finish(player);
            return;
        }

        int timeout = Math.max(XenoServerConfig.chaseFlightTimeoutTicks, 40);
        double speed = Math.max(0.1, XenoServerConfig.chaseFlightSpeed);
        int travelTicks = (int) (dist / speed) + 40;
        if (XenoServerConfig.chaseRangeUnlimited()) {
            timeout = Math.max(timeout, travelTicks);
        } else {
            timeout = Math.min(Math.max(timeout, travelTicks), ABSOLUTE_MAX_TIMEOUT_TICKS);
        }
        if (++state.elapsedTicks > timeout) {
            cancel(player, "§7Chase timed out");
            return;
        }

        double step = Math.min(XenoServerConfig.chaseFlightSpeed, dist);
        Vec3 dir = toLanding.scale(1.0 / dist);
        lookToward(player, state, wantYaw, wantPitch, LOOK_EASE);
        Vec3 movement = dir.scale(step);
        if (!player.level().noCollision(player, player.getBoundingBox().move(movement))) {
            if (state.routePhase == ChaseState.RoutePhase.ASCEND) {
                state.routePhase = ChaseState.RoutePhase.DIRECT;
                state.elevatedWaypoint = null;
            }
            stopMotion(player);
            return;
        }
        player.setDeltaMovement(movement);
        player.hasImpulse = true;
        player.fallDistance = 0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /**
     * Aim the path test at the target's body rather than the landing point at its feet, so the
     * surface the target stands on is not mistaken for a wall.
     */
    private static Vec3 pathAim(LivingEntity target, Vec3 destination) {
        return target == null ? destination
                : new Vec3(destination.x, destination.y + target.getBbHeight() * 0.5, destination.z);
    }

    private static boolean directPathBlocked(ServerPlayer player, LivingEntity target, Vec3 destination) {
        Vec3 start = player.getBoundingBox().getCenter();
        Vec3 aim = pathAim(target, destination);
        if (!ChaseRouting.detourWorthwhile(Math.sqrt(
                (aim.x - start.x) * (aim.x - start.x) + (aim.z - start.z) * (aim.z - start.z)))) {
            return false;
        }
        BlockHitResult hit = player.level().clip(new ClipContext(
                start, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && ChaseRouting.obstructs(hit.getLocation().distanceTo(start), start.distanceTo(aim));
    }

    private static Vec3 elevatedWaypoint(ServerPlayer player, LivingEntity target, Vec3 destination) {
        Vec3 start = player.position();
        Vec3 center = player.getBoundingBox().getCenter();
        Vec3 aim = pathAim(target, destination);
        BlockHitResult hit = player.level().clip(new ClipContext(
                center, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;

        double requiredY = ChaseRouting.clearanceY(hit.getLocation().y, player.getBbHeight(),
                OBSTACLE_CLEARANCE);
        if (!ChaseRouting.detourReachable(requiredY, start.y, MAX_OBSTACLE_CLIMB)) return null;

        Vec3 waypoint = new Vec3(start.x, requiredY, start.z);
        if (!player.level().noCollision(player, player.getBoundingBox().move(waypoint.subtract(start)))) {
            return null;
        }
        return waypoint;
    }

    private static void stopMotion(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.fallDistance = 0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /**
     * Turns the body other players see, and deliberately leaves the owner's own view alone.
     *
     * <p>The client already eases the chase camera itself. Writing {@code setYRot}/{@code setXRot}
     * here as well put two authorities on one camera — the server's ease, the client's ease and the
     * player's mouse all fighting each tick — which is what made the view swing. Head and body yaw
     * are cosmetic for observers and do not move the owner's camera, so they stay.
     */
    private static void lookToward(ServerPlayer player, ChaseState state, float wantYaw, float wantPitch, float ease) {
        state.yaw = Mth.rotLerp(ease, state.yaw, wantYaw);
        state.pitch = Mth.rotLerp(ease, state.pitch, wantPitch);
        player.setYHeadRot(state.yaw);
        player.yBodyRot = state.yaw;
    }

    private static float yawOf(Vec3 dir) {
        return (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
    }

    private static float pitchOf(Vec3 dir) {
        double xz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return (float) (-Math.toDegrees(Math.atan2(dir.y, xz)));
    }

    private static void cancel(ServerPlayer player, String message) {
        stop(player);
        player.displayClientMessage(Component.literal(message), true);
    }

    private static void finish(ServerPlayer player) {
        stop(player);
    }

    /** Client released the chase hold. */
    public static void stopChase(ServerPlayer player) {
        stop(player);
    }

    private static void stop(ServerPlayer player) {
        ChaseState state = ACTIVE.remove(player.getUUID());
        if (state != null) {
            player.setNoGravity(state.noGravityBefore);
            stopMotion(player);
            // A chase ends where it ends, frequently far above the ground. Handing gravity back
            // without this meant the fighter fell from altitude and died to the landing rather
            // than to the fight.
            player.resetFallDistance();
            net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.grantFallGrace(
                    player, CHASE_LANDING_GRACE_TICKS);
            if (state.turnedAuraOn) {
                XenoAuraCommands.apply(player, false);
            }
            restoreFly(player, state);
            ModNetwork.sendToPlayer(player, new ChaseFlightStatePacket(false));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        if (p == null) return;
        UUID id = p.getUUID();
        ChaseState state = ACTIVE.remove(id);
        if (state != null && p instanceof ServerPlayer serverPlayer) {
            serverPlayer.setNoGravity(state.noGravityBefore);
            serverPlayer.setDeltaMovement(Vec3.ZERO);
            if (state.turnedAuraOn) {
                XenoAuraCommands.apply(serverPlayer, false);
            }
            restoreFly(serverPlayer, state);
            ModNetwork.sendToPlayer(serverPlayer, new ChaseFlightStatePacket(false));
        }
    }
}
