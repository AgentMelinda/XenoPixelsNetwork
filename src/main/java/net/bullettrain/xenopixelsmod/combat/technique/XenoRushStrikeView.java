package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Who owns the attacker's view during a Xeno rush strike thrown at a locked target: the lock-on
 * camera, not the server.
 *
 * <p>The four rush strikes (left, right, breaker, finisher) run in DragonMineZ's strike lifecycle,
 * which keeps cost, cooldown, damage and animation timing DragonMineZ's. That lifecycle also aims
 * the attacker's view: once when it teleports them to the target, and again on every tick of the
 * strike, each time by a server {@code lookAt} that snaps the client's camera to the target's
 * eyes. Against a locked target the lock-on camera is easing the same view toward the target's
 * body every frame, so for the length of the strike the two take turns: a snap up twenty times a
 * second, an ease down in between. That is the twitch.
 *
 * <p>The rush and lift combos never had it, because nothing on the server touches the view there:
 * lock-on eases it and that is all. For a strike thrown at the attacker's lock this class makes
 * the four strikes the same. The server stops aiming the attacker ({@code StrikeAttackRushViewMixin}
 * asks here before DragonMineZ does it) and lands them on their own side of the target, so there
 * is nothing to swing round to ({@link RushStrikeArrival}). Lock-on is then the only thing moving
 * the view, as it is in the combos.
 *
 * <p><b>Only with a lock on the strike's target.</b> DragonMineZ's own strike request carries the
 * id of the entity the caster is locked on, or -1, and that is what is read here. A rush strike
 * thrown with nothing locked, or one that lands on something other than the lock, is left exactly
 * as DragonMineZ runs it: with no lock-on camera there is nothing for the server's aiming to
 * fight, and nothing else that would turn the attacker to face what they are hitting.
 *
 * <p>Only the four Xeno ids. Every DragonMineZ strike is aimed exactly as before.
 *
 * <p>Server thread only.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoRushStrikeView {

    /** Ticks past the end of the strike that it still counts as running, for the last facing call. */
    private static final int SETTLE_TICKS = 2;
    /**
     * DragonMineZ faces the attacker in the same call that starts the strike and on every tick
     * after. A strike this old that the hook has not seen is one the hook is not attached to.
     */
    private static final int HOOK_GRACE_TICKS = 3;

    /** One attacker's strike in progress. */
    private static final class Strike {
        final int startTick;
        final int ticks;
        /** The facing hook has fired for this strike. */
        boolean seen;

        Strike(int startTick, int ticks) {
            this.startTick = startTick;
            this.ticks = ticks;
        }

        boolean over(int now) {
            return now > startTick + ticks + SETTLE_TICKS;
        }
    }

    /** Entity id each caster was locked on when they last asked DragonMineZ for a strike; -1 for none. */
    private static final Map<UUID, Integer> LOCK_AT_CAST = new HashMap<>();
    private static final Map<UUID, Strike> ACTIVE = new HashMap<>();
    private static boolean hookMissingReported;

    private XenoRushStrikeView() {}

    /**
     * A player has asked DragonMineZ for a strike. {@code lockedTargetId} is the number
     * DragonMineZ's client sent with the request: the entity it is locked on, or -1.
     */
    public static void noteCast(ServerPlayer player, int lockedTargetId) {
        LOCK_AT_CAST.put(player.getUUID(), lockedTargetId > 0 ? lockedTargetId : -1);
    }

    /** Whether {@code player} threw their current strike while locked on {@code target}. */
    public static boolean castWithLockOn(ServerPlayer player, LivingEntity target) {
        Integer locked = LOCK_AT_CAST.get(player.getUUID());
        return locked != null && target != null && locked == target.getId();
    }

    /** Whether the strike {@code player} has selected, the one DragonMineZ is about to run, is a Xeno rush. */
    public static boolean selectedIsRush(ServerPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            return data != null && data.getTechniques() != null
                    && data.getTechniques().getSelectedTechnique() instanceof StrikeAttackData strike
                    && XenoRushTechniques.isRushId(strike.getId());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Puts the attacker on their own side of the target, leaving their view alone.
     *
     * @return false when this strike is not at the attacker's lock, or no spot there is free, so
     *         DragonMineZ's own arrival should run
     */
    public static boolean arrive(ServerPlayer player, LivingEntity target) {
        if (!castWithLockOn(player, target)) return false;
        double flat = Math.hypot(player.getX() - target.getX(), player.getZ() - target.getZ());
        if (RushStrikeArrival.alreadyInPlace(flat, player.getY() - target.getY())) {
            turnBody(player, target);
            return true;
        }
        return arriveBeside(player, target, target.getX(), target.getY(), target.getZ(), 0.0);
    }

    /** As {@link #arrive}, for a strike aimed at one part of a many-part target. */
    public static boolean arriveAtPart(ServerPlayer player, PartEntity<?> part, LivingEntity parent) {
        if (!castWithLockOn(player, parent)) return false;
        Vec3 centre = part.getBoundingBox().getCenter();
        // Eyes level with the part, as DragonMineZ places them.
        return arriveBeside(player, parent, centre.x, centre.y - player.getEyeHeight(), centre.z,
                part.getBbWidth() * 0.5);
    }

    private static boolean arriveBeside(ServerPlayer player, LivingEntity facing,
                                        double x, double y, double z, double extra) {
        for (double distance : RushStrikeArrival.DISTANCES) {
            double[] xz = RushStrikeArrival.nearSide(
                    player.getX(), player.getZ(), x, z, player.getYRot(), distance + extra);
            Vec3 spot = new Vec3(xz[0], y, xz[1]);
            if (!Bt3Landing.isSpotOpen(player, spot)) continue;
            // The three-argument teleport moves the body and keeps the owner's own view angles.
            player.teleportTo(spot.x, spot.y, spot.z);
            turnBody(player, facing);
            return true;
        }
        return false;
    }

    /**
     * A rush strike has started on {@code target} and will run for {@code durationTicks}. Taken
     * on only when it was thrown at the attacker's lock.
     *
     * @return true when the view of this strike is now the lock-on camera's
     */
    public static boolean begin(ServerPlayer player, LivingEntity target, int durationTicks) {
        MinecraftServer server = player.getServer();
        if (server == null || !castWithLockOn(player, target)) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        ACTIVE.put(player.getUUID(), new Strike(server.getTickCount(), Math.max(1, durationTicks)));
        return true;
    }

    /**
     * DragonMineZ is about to aim {@code player} at {@code target}. For a rush strike at the
     * attacker's lock this does the facing instead: the body other players see turns, and the
     * attacker's own view is left to their lock-on camera.
     *
     * @return true when the facing was handled here and DragonMineZ's must not run
     */
    public static boolean face(ServerPlayer player, LivingEntity target) {
        Strike strike = ACTIVE.get(player.getUUID());
        if (strike == null) return false;
        MinecraftServer server = player.getServer();
        if (server == null || strike.over(server.getTickCount())) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        strike.seen = true;
        turnBody(player, target);
        return true;
    }

    /**
     * Turns the body other players see toward the target. A player's view angles are their
     * client's; the head and body yaw are only how they are drawn for everyone else.
     */
    public static void turnBody(ServerPlayer player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        if (dx * dx + dz * dz < 1.0e-6) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
    }

    /** Drops finished strikes, and reports once if the facing hook is evidently not attached. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        int now = event.getServer().getTickCount();
        Iterator<Strike> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            if (!strike.seen && now - strike.startTick >= HOOK_GRACE_TICKS) {
                it.remove();
                if (!hookMissingReported) {
                    hookMissingReported = true;
                    XenoPixelsMod.LOGGER.warn("Xeno rush strike view: DragonMineZ's strike handler did not call "
                            + "the facing hook (StrikeAttackRushViewMixin is not applied to this DragonMineZ "
                            + "version). Rush strikes are aimed by DragonMineZ's stock camera snap.");
                }
            } else if (strike.over(now)) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ACTIVE.remove(id);
        LOCK_AT_CAST.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        LOCK_AT_CAST.clear();
    }
}
