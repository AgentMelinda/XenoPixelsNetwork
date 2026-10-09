package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.bullettrain.xenopixelsmod.api.event.GrabEvent;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * V3 grab and throw: guard + light at arm's length.
 *
 * <p>The victim is held in front of the grabber for a short hold, then thrown the way the grabber
 * is steering. A grabbed player can break out early in the hold. The victim is always the
 * grabber's approved lock, so every lock-on protection rule already applies to it.
 */
public final class V3Grab {
    static final int HOLD_TICKS = 16;
    static final int TECH_TICKS = 10;
    static final int COOLDOWN_TICKS = 60;
    static final double RANGE = 3.2;
    /** Legacy impulse scale kept for pure unit tests of direction signs. */
    static final double THROW = 1.3;
    static final double THROW_LIFT = 0.35;
    static final float THROW_DAMAGE_SCALE = 1.2f;
    static final float CONNECT_DAMAGE_SCALE = THROW_DAMAGE_SCALE * 0.25f;
    private static final Map<UUID, UUID> HELD_BY = new HashMap<>();

    private V3Grab() {}

    static boolean techAllowed(long heldTicks) {
        return heldTicks >= 0 && heldTicks <= TECH_TICKS;
    }

    /**
     * Throw direction from look + steer. Grab chord: Guard+Light to grab, then while holding
     * W/S/A/D steers forward/back/left/right and Jump throws up. Magnitude comes from
     * {@code v3.grabThrowDistance}.
     */
    static Vec3 throwDirection(V3Direction direction, double lookX, double lookZ) {
        if (direction == V3Direction.UP) return new Vec3(0, 1, 0);
        double length = Math.hypot(lookX, lookZ);
        double fx = length < 1.0e-6 ? 0 : lookX / length;
        double fz = length < 1.0e-6 ? 1 : lookZ / length;
        return switch (direction == null ? V3Direction.NONE : direction) {
            case BACK -> new Vec3(-fx, 0, -fz);
            case RIGHT -> new Vec3(-fz, 0, fx);
            case LEFT -> new Vec3(fz, 0, -fx);
            case UP -> new Vec3(0, 1, 0);
            case FORWARD, NONE -> new Vec3(fx, 0, fz);
        };
    }

    /** Prefer {@link #throwDirection} + {@code v3.grabThrowDistance} arc for gameplay throws. */
    @Deprecated
    static Vec3 throwVelocity(V3Direction direction, double lookX, double lookZ) {
        Vec3 dir = throwDirection(direction, lookX, lookZ);
        return new Vec3(dir.x * THROW, THROW_LIFT, dir.z * THROW);
    }

    static boolean claimAvailable(UUID victim, UUID grabber) {
        UUID owner = HELD_BY.get(victim);
        return owner == null || owner.equals(grabber);
    }

    /** An uncommitted connection always rolls its movement leases back, including thrown hooks. */
    static boolean connect(BooleanSupplier contact, Runnable rollback) {
        boolean accepted = false;
        try {
            accepted = contact.getAsBoolean();
            return accepted;
        } finally {
            if (!accepted) rollback.run();
        }
    }

    private static void releaseGrabLease(ServerPlayer player, UUID session) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter != null && fighter.motion.heldBy(V3Motion.Owner.GRAB, session)) V3Motion.release(player);
    }

    /** One entry for the grab chord: break out when held, steer when holding, otherwise grab. */
    public static boolean handle(ServerPlayer player, LivingEntity target, V3Direction direction, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return false;
        if (fighter.state == V3State.GRABBED) return tech(player, fighter, now);
        if (fighter.state == V3State.GRAB_HOLD) {
            fighter.grabDirection = direction == null ? V3Direction.NONE : direction;
            return true;
        }
        if (fighter.state != V3State.IDLE || target == null || now < fighter.grabReadyTick
                || !V3Heavy.emptyHands(player) || V3Heavy.stunned(player)
                || player.distanceTo(target) > RANGE) return false;
        if (!claimAvailable(target.getUUID(), player.getUUID()) || target.isPassenger() || target.isVehicle()
                || player.isPassenger() || !CombatKnockback.canKnockBack(target)) return false;
        V3Fighter heldFighter = target instanceof ServerPlayer held ? V3FighterStore.get(held) : null;
        if (heldFighter != null && (heldFighter.state != V3State.IDLE || V3Motion.owns((ServerPlayer) target)
                || net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.owns((ServerPlayer) target))) {
            return false;
        }
        if (NeoForge.EVENT_BUS.post(new GrabEvent.Connect(player, target)).isCanceled()) return false;
        UUID grabberSession = fighter.session();
        UUID victimSession = heldFighter == null ? null : heldFighter.session();
        if (!connect(() -> {
            if (!V3Motion.acquire(player, V3Motion.Owner.GRAB, grabberSession)) return false;
            if (heldFighter != null && !V3Motion.acquire((ServerPlayer) target, V3Motion.Owner.GRAB, victimSession)) {
                return false;
            }
            return V3Heavy.strike(player, target, CONNECT_DAMAGE_SCALE) > 0f;
        }, () -> {
            try {
                releaseGrabLease(player, grabberSession);
            } finally {
                if (target instanceof ServerPlayer held) releaseGrabLease(held, victimSession);
            }
        })) return false;
        HELD_BY.put(target.getUUID(), player.getUUID());
        fighter.state = V3State.GRAB_HOLD;
        fighter.grabVictim = target.getUUID();
        fighter.grabStartTick = now;
        fighter.grabDirection = direction == null ? V3Direction.NONE : direction;
        fighter.grabReadyTick = (long) now + COOLDOWN_TICKS;
        fighter.window = V3Window.GRAB;
        fighter.windowTarget = target.getUUID();
        fighter.windowTicksLeft = fighter.windowTicksTotal = HOLD_TICKS;
        if (target instanceof ServerPlayer held) {
            V3Fighter victim = V3FighterStore.get(held);
            V3Chase.stop(held);
            V3Charge.cancel(held);
            victim.state = V3State.GRABBED;
            victim.grabbedBy = player.getUUID();
            victim.grabStartTick = now;
            victim.window = V3Window.GRAB;
            victim.windowTicksLeft = victim.windowTicksTotal = TECH_TICKS;
            V3CombatServer.syncState(held);
        }
        return true;
    }

    private static boolean tech(ServerPlayer victim, V3Fighter held, int now) {
        if (!techAllowed(now - held.grabStartTick)) return false;
        Entity raw = held.grabbedBy == null ? null : victim.serverLevel().getEntity(held.grabbedBy);
        if (raw instanceof ServerPlayer grabber) release(grabber);
        free(victim, held);
        return true;
    }

    public static void tick(ServerPlayer player, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        if (fighter.state == V3State.GRABBED) {
            // Never leave a victim stuck if the grabber vanished from the world or the hold ran out.
            Entity raw = fighter.grabbedBy == null ? null : player.serverLevel().getEntity(fighter.grabbedBy);
            V3Fighter grabber = raw instanceof ServerPlayer other ? V3FighterStore.peek(other) : null;
            if (grabber == null || grabber.state != V3State.GRAB_HOLD || !player.getUUID().equals(grabber.grabVictim)
                    || now - fighter.grabStartTick > HOLD_TICKS + 5) free(player, fighter);
            return;
        }
        if (fighter.grabVictim == null) return;
        if (fighter.state != V3State.GRAB_HOLD) {
            // Lock loss or a session change reset the grabber: let go without a throw.
            letGo(player, fighter);
            return;
        }
        LivingEntity victim = V3Targeting.resolve(player);
        if (victim == null || !victim.getUUID().equals(fighter.grabVictim) || V3Heavy.stunned(player)) {
            release(player);
            return;
        }
        if (!player.getUUID().equals(HELD_BY.get(victim.getUUID()))) {
            release(player);
            return;
        }
        if (victim instanceof ServerPlayer held) {
            V3Fighter heldState = V3FighterStore.peek(held);
            if (heldState == null || heldState.state != V3State.GRABBED
                    || !player.getUUID().equals(heldState.grabbedBy)) {
                release(player);
                return;
            }
        }
        if (now - fighter.grabStartTick < HOLD_TICKS) {
            Vec3 look = player.getLookAngle();
            double length = Math.hypot(look.x, look.z);
            Vec3 front = length < 1.0e-6 ? new Vec3(0, 0, 1) : new Vec3(look.x / length, 0, look.z / length);
            Vec3 hold = player.position().add(front.scale(1.2));
            if (!safeHold(victim, hold)) {
                release(player);
                return;
            }
            victim.teleportTo(hold.x, hold.y, hold.z);
            CombatKnockback.set(victim, Vec3.ZERO);
            return;
        }
        V3Direction direction = fighter.grabDirection;
        // Release the hold before the throw, so nothing pins the victim against its own velocity.
        release(player);
        float accepted = V3Heavy.strike(player, victim, THROW_DAMAGE_SCALE - CONNECT_DAMAGE_SCALE);
        Vec3 look = player.getLookAngle();
        Vec3 dir = throwDirection(direction, look.x, look.z);
        if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
        double distance = V3Config.get().grabThrowDistance();
        if (accepted > 0f) {
            double apex = direction == V3Direction.UP ? Math.max(4.0, distance * 0.85) : 4.0;
            // Pure-up throws still need a horizontal unit for the arc stepper; lift is in apex.
            Vec3 arcDir = direction == V3Direction.UP
                    ? (Math.hypot(look.x, look.z) < 1.0e-6 ? new Vec3(0, 0, 1) : new Vec3(look.x, 0, look.z).normalize())
                    : dir;
            if (direction == V3Direction.UP) {
                net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc.start(victim, arcDir, now, Math.max(2.0, distance * 0.25), apex);
            } else {
                net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc.start(victim, arcDir, now, distance, apex);
            }
            if (player.level() instanceof ServerLevel level) {
                CombatFx.impact(level, player, victim, dir, CombatFx.Weight.HEAVY);
            }
            NeoForge.EVENT_BUS.post(new GrabEvent.Throw(player, victim, accepted));
        }
    }

    /** Ends this fighter's hold and frees whoever was in it. Safe when idle. */
    public static void release(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        if (fighter.grabVictim == null) {
            if (fighter.motion.owner() == V3Motion.Owner.GRAB) V3Motion.release(player);
            return;
        }
        if (fighter.state == V3State.GRAB_HOLD) fighter.state = V3State.IDLE;
        letGo(player, fighter);
        V3Motion.release(player);
        V3CombatServer.syncState(player);
    }

    /** Rollback/logout entry for either side of a grab. */
    public static void abort(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        if (fighter.grabVictim != null) release(player);
        if (fighter.grabbedBy != null) {
            Entity raw = player.serverLevel().getEntity(fighter.grabbedBy);
            if (raw instanceof ServerPlayer grabber) release(grabber);
            HELD_BY.remove(player.getUUID(), fighter.grabbedBy);
            free(player, fighter);
        }
        if (fighter.motion.owner() == V3Motion.Owner.GRAB) V3Motion.release(player);
    }

    private static void letGo(ServerPlayer player, V3Fighter fighter) {
        Entity raw = player.serverLevel().getEntity(fighter.grabVictim);
        UUID victimId = fighter.grabVictim;
        fighter.grabVictim = null;
        HELD_BY.remove(victimId, player.getUUID());
        if (fighter.window == V3Window.GRAB) {
            fighter.window = V3Window.NONE;
            fighter.windowTarget = null;
            fighter.windowTicksLeft = fighter.windowTicksTotal = 0;
        }
        if (raw instanceof ServerPlayer held) {
            V3Fighter victim = V3FighterStore.peek(held);
            if (victim != null && victim.state == V3State.GRABBED && player.getUUID().equals(victim.grabbedBy)) {
                free(held, victim);
            }
        }
    }

    private static void free(ServerPlayer victim, V3Fighter held) {
        HELD_BY.remove(victim.getUUID(), held.grabbedBy);
        held.grabbedBy = null;
        if (held.state == V3State.GRABBED) held.state = V3State.IDLE;
        if (held.window == V3Window.GRAB) {
            held.window = V3Window.NONE;
            held.windowTicksLeft = held.windowTicksTotal = 0;
        }
        if (held.motion.owner() == V3Motion.Owner.GRAB) V3Motion.release(victim);
        V3CombatServer.syncState(victim);
    }

    private static boolean safeHold(LivingEntity victim, Vec3 hold) {
        if (!V3TargetingRules.finite(hold)) return false;
        var moved = victim.getBoundingBox().move(hold.subtract(victim.position()));
        if (!(victim.level() instanceof ServerLevel level) || !V3ChunkWindow.loaded(level, moved)) return false;
        return victim.level().noCollision(victim, moved);
    }
}
